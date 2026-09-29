package com.salestracker.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record CustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 32) String phone,
            @Size(max = 500) String address,
            Long sourcePlatformId,
            @Size(max = 5000) String notes) {}

    /**
     * A customer's history over all time. spent is what they paid on PAID orders (products + delivery charge).
     * returnRatePct is the share of their settled orders (paid, returned or cancelled; pending ones are still open)
     * that came back or were cancelled, null until one settles. flagged marks a customer who returns or cancels often.
     */
    public record Insights(long orders, long paid, long pending, long returned, long cancelled,
                           BigDecimal spent, Integer returnRatePct, boolean flagged, LocalDateTime lastOrderAt) {
        /** At least this many returned or cancelled orders... */
        static final int FLAG_MIN_PROBLEMS = 2;
        /** ...making up at least this share of the settled ones. */
        static final int FLAG_MIN_RATE_PCT = 30;

        static Insights of(long paid, long pending, long returned, long cancelled, BigDecimal spent,
                           LocalDateTime lastOrderAt) {
            long problems = returned + cancelled;
            long settled = paid + problems;
            Integer rate = settled == 0 ? null : (int) Math.round(problems * 100.0 / settled);
            boolean flagged = problems >= FLAG_MIN_PROBLEMS && rate >= FLAG_MIN_RATE_PCT;
            return new Insights(paid + pending + problems, paid, pending, returned, cancelled, spent, rate, flagged,
                    lastOrderAt);
        }

        static final Insights NONE = of(0, 0, 0, 0, BigDecimal.ZERO, null);
    }

    public record CustomerResponse(Long id, String name, String phone, String address,
                                   Long sourcePlatformId, String notes, Insights insights) {
        static CustomerResponse of(Customer c, Insights insights) {
            return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getAddress(),
                    c.getSourcePlatformId(), c.getNotes(), insights);
        }
    }
}
