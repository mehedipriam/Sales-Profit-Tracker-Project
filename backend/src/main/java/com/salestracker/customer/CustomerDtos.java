package com.salestracker.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record CustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 32) String phone,
            @Size(max = 500) String address,
            Long sourcePlatformId,
            @Size(max = 5000) String notes) {}

    public record CustomerResponse(Long id, String name, String phone, String address,
                                   Long sourcePlatformId, String notes) {
        static CustomerResponse of(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getAddress(),
                    c.getSourcePlatformId(), c.getNotes());
        }
    }
}
