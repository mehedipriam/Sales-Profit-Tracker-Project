package com.salestracker.agent;

import com.salestracker.order.SaleOrder;
import com.salestracker.order.SaleOrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the order a message means by the number as people write it, within one shop. */
@Component
class OrderLookup {
    /** ORD-2610-0005, also written ord2610-5. */
    private static final Pattern FULL_NUMBER = Pattern.compile("(?i)^#?\\s*ORD\\s*-?\\s*(\\d{4})\\s*-?\\s*(\\d{1,6})$");
    /** "4" or "#4": the order's yearly count, in the current year. */
    private static final Pattern SHORT_NUMBER = Pattern.compile("^#?\\s*(\\d{1,6})$");

    private final SaleOrderRepository orders;

    OrderLookup(SaleOrderRepository orders) {
        this.orders = orders;
    }

    /** The order a number means: the full number, or just its count (in the current year). */
    Optional<SaleOrder> byNumber(Long tenant, String ref) {
        Matcher full = FULL_NUMBER.matcher(ref);
        if (full.matches()) {
            return orders.findByTenantIdAndOrderNo(tenant, "ORD-" + full.group(1) + "-" + pad(full.group(2)));
        }
        Matcher count = SHORT_NUMBER.matcher(ref);
        if (count.matches()) {
            String yy = String.format("%02d", LocalDate.now().getYear() % 100);
            return orders.findByTenantIdAndOrderNoLike(tenant, "ORD-" + yy + "__-" + pad(count.group(1))).stream().findFirst();
        }
        return orders.findByTenantIdAndOrderNo(tenant, ref.toUpperCase());
    }

    private static String pad(String digits) {
        return String.format("%04d", Integer.parseInt(digits));
    }
}
