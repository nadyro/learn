package com.kestrel.commerce.customer.api;

import com.kestrel.commerce.customer.application.CustomerService;
import com.kestrel.commerce.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "The authenticated customer's profile")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get my customer profile", description = "Creates the customer on first call.")
    public CustomerResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return CustomerResponse.from(customerService.getOrRegister(CurrentUser.from(jwt)));
    }
}
