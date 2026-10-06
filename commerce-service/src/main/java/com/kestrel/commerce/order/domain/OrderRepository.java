package com.kestrel.commerce.order.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /**
     * Loads an order and its lines in a single query. Without the entity graph, reading the lines would trigger a
     * second query, or fail with a {@code LazyInitializationException} outside the transaction.
     */
    @EntityGraph(attributePaths = "lines")
    Optional<Order> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findWithLinesByIdAndCustomerId(UUID id, UUID customerId);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderNumber();
}
