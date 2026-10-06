package com.kestrel.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.kestrel.commerce.customer.application.CustomerService;
import com.kestrel.commerce.customer.domain.Customer;
import com.kestrel.commerce.inventory.domain.InsufficientStockException;
import com.kestrel.commerce.order.application.OrderService;
import com.kestrel.commerce.order.application.PlaceOrderCommand;
import com.kestrel.commerce.order.domain.ShippingAddress;
import com.kestrel.commerce.shared.security.CurrentUser;
import com.kestrel.commerce.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Race conditions only show up under real concurrency against a real database, so these tests fire parallel requests
 * at the service layer and check that the invariants hold.
 */
class ConcurrentOrdersIT extends IntegrationTest {

    private static final int PARALLEL_REQUESTS = 8;

    @Autowired
    OrderService orderService;

    @Autowired
    CustomerService customerService;

    @Test
    void the_last_unit_in_stock_is_sold_only_once() throws Exception {
        UUID productId = createProduct("99.00", 1);

        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            CurrentUser buyer = new CurrentUser("buyer-" + UUID.randomUUID(), null, null, null, null);
            attempts.add(() -> {
                try {
                    orderService.placeOrder(buyer, command(productId));
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            });
        }

        List<Boolean> outcomes = runInParallel(attempts);

        assertThat(outcomes).containsOnlyOnce(true);
        getInventory(productId)
                .andExpect(jsonPath("$.reserved").value(1))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void parallel_first_requests_of_a_new_user_register_a_single_customer() throws Exception {
        CurrentUser newUser = new CurrentUser("new-user-" + UUID.randomUUID(), "new", "new@example.com", "N", "U");

        List<Callable<UUID>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            calls.add(() -> customerService.getOrRegister(newUser).getId());
        }

        List<UUID> customerIds = runInParallel(calls);

        assertThat(customerIds).hasSize(PARALLEL_REQUESTS).containsOnly(customerIds.getFirst());
        assertThat(customerService.findBySubject(newUser.subject()))
                .map(Customer::getEmail)
                .contains("new@example.com");
    }

    private static PlaceOrderCommand command(UUID productId) {
        return new PlaceOrderCommand(
                List.of(new PlaceOrderCommand.Item(productId, 1)),
                new ShippingAddress("Racer", "1 Fast Lane", null, "Lyon", "69000", "FR"));
    }

    /** Starts all tasks at the same instant (behind a latch) to maximise contention. */
    private static <T> List<T> runInParallel(List<Callable<T>> tasks) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    return task.call();
                }));
            }
            startSignal.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
