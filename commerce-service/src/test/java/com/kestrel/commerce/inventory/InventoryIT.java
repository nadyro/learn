package com.kestrel.commerce.inventory;

import static com.kestrel.commerce.support.TestJwts.admin;
import static com.kestrel.commerce.support.TestJwts.newCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

class InventoryIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void a_restock_increases_the_stock_and_is_recorded_for_audit() throws Exception {
        UUID productId = createProduct("10.00", 5);

        adjust(productId, 20, "RESTOCK", "Delivery note DN-42")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(25))
                .andExpect(jsonPath("$.available").value(25));

        Integer onHandAfter =
                jdbc.sql("""
                        select on_hand_after from stock_adjustments
                        where product_id = :productId and reason = 'RESTOCK'
                        """).param("productId", productId).query(Integer.class).single();
        assertThat(onHandAfter).isEqualTo(25);
    }

    @Test
    void stock_reserved_for_open_orders_cannot_be_written_off() throws Exception {
        UUID productId = createProduct("10.00", 5);
        placeOrder(newCustomer(), productId, 4);

        adjust(productId, -2, "DAMAGED", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STOCK_ADJUSTMENT"))
                .andExpect(jsonPath("$.reserved").value(4));

        adjust(productId, -1, "DAMAGED", "Torn fabric")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(4));
    }

    @Test
    void the_stock_of_an_unknown_product_is_not_found() throws Exception {
        getInventory(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_NOT_FOUND"));
    }

    private ResultActions adjust(UUID productId, int delta, String reason, String note) throws Exception {
        String body = note == null
                ? "{\"delta\":%d,\"reason\":\"%s\"}".formatted(delta, reason)
                : "{\"delta\":%d,\"reason\":\"%s\",\"note\":\"%s\"}".formatted(delta, reason, note);
        return mvc.perform(post("/api/v1/admin/inventory/{productId}/adjustments", productId)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
