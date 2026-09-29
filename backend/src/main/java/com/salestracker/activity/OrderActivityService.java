package com.salestracker.activity;

import com.salestracker.auth.AuthUser;
import com.salestracker.common.DateRange;
import com.salestracker.common.PageResponse;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrder;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.user.Role;
import com.salestracker.user.User;
import com.salestracker.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Writes the activity log from the order write paths, and reads it back for the Activity page. */
@Service
@Transactional
public class OrderActivityService {

    /** One log entry as the Activity page shows it; orderExists is false once the order has been deleted. */
    public record ActivityRow(Long id, LocalDateTime at, Long userId, String userName, Role userRole,
                              OrderAction action, Long orderId, boolean orderExists, String customerName,
                              BigDecimal amountBefore, BigDecimal amount, OrderStatus fromStatus, OrderStatus toStatus,
                              List<OrderChange> changes) {}

    private final OrderActivityRepository activity;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final SaleOrderRepository orders;

    public OrderActivityService(OrderActivityRepository activity, CustomerRepository customers, UserRepository users,
                                SaleOrderRepository orders) {
        this.activity = activity;
        this.customers = customers;
        this.users = users;
        this.orders = orders;
    }

    // ---- writing: called by OrderService and CourierPayoutService ----

    public void created(AuthUser actor, SaleOrder o) {
        write(actor, o, OrderAction.CREATED, null, OrderSnapshot.total(o), null, o.getStatus(), null);
    }

    /** Logs an edit only when it changed something; before is the order as it was when the edit began. */
    public void edited(AuthUser actor, OrderSnapshot before, SaleOrder o) {
        List<OrderChange> changes = before.changesTo(o);
        if (changes.isEmpty()) return;
        boolean statusChanged = changes.contains(OrderChange.STATUS);
        BigDecimal total = OrderSnapshot.total(o);
        write(actor, o, OrderAction.EDITED, before.total().compareTo(total) == 0 ? null : before.total(), total,
                statusChanged ? before.status() : null, statusChanged ? o.getStatus() : null,
                changes.stream().map(Enum::name).collect(Collectors.joining(",")));
    }

    public void statusChanged(AuthUser actor, SaleOrder o, OrderStatus from) {
        if (from == o.getStatus()) return;
        write(actor, o, OrderAction.STATUS_CHANGED, null, OrderSnapshot.total(o), from, o.getStatus(), null);
    }

    public void deleted(AuthUser actor, SaleOrder o) {
        write(actor, o, OrderAction.DELETED, null, OrderSnapshot.total(o), o.getStatus(), null, null);
    }

    /** The courier paid out (or, undone, didn't pay out after all) the cash it collected: amount is that cash. */
    public void payout(AuthUser actor, SaleOrder o, boolean undone) {
        write(actor, o, undone ? OrderAction.PAYOUT_UNDONE : OrderAction.PAID_OUT, null, o.getCodAmount(),
                null, null, null);
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public PageResponse<ActivityRow> list(Long tenantId, Long orderId, Long userId, OrderAction action,
                                          DateRange range, int page, int size) {
        Collection<OrderAction> actions = action == null ? List.of(OrderAction.values()) : List.of(action);
        Page<OrderActivity> result = activity.search(tenantId, orderId == null ? 0L : orderId,
                userId == null ? 0L : userId, actions, range.from(), range.toExclusive(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        Map<Long, User> userById = users.findAllById(result.map(OrderActivity::getUserId).toSet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> existing = orders.findByTenantIdAndIdIn(tenantId, result.map(OrderActivity::getOrderId).toSet())
                .stream().map(SaleOrder::getId).collect(Collectors.toSet());

        return PageResponse.of(result, a -> {
            User u = userById.get(a.getUserId());
            return new ActivityRow(a.getId(), a.getCreatedAt(), a.getUserId(), u == null ? null : u.getFullName(),
                    u == null ? null : u.getRole(), a.getAction(), a.getOrderId(), existing.contains(a.getOrderId()),
                    a.getCustomerName(), a.getAmountBefore(), a.getAmount(), a.getFromStatus(), a.getToStatus(),
                    a.getChanges() == null ? List.of()
                            : Arrays.stream(a.getChanges().split(",")).map(OrderChange::valueOf).toList());
        });
    }

    // ---- internals ----

    private void write(AuthUser actor, SaleOrder o, OrderAction action, BigDecimal amountBefore, BigDecimal amount,
                       OrderStatus from, OrderStatus to, String changes) {
        String customerName = customers.findById(o.getCustomerId()).map(Customer::getName).orElse("—");
        activity.save(new OrderActivity(actor.tenantId(), o.getId(), actor.userId(), action, customerName,
                amountBefore, amount, from, to, changes));
    }
}
