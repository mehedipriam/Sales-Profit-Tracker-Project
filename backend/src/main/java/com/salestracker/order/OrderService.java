package com.salestracker.order;

import com.salestracker.auth.ApiException;
import com.salestracker.common.PageResponse;
import com.salestracker.common.Search;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.OrderDtos.*;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {
    private final SaleOrderRepository orders;
    private final PlatformRepository platforms;
    private final CustomerRepository customers;
    private final ProductRepository products;

    public OrderService(SaleOrderRepository orders, PlatformRepository platforms,
                        CustomerRepository customers, ProductRepository products) {
        this.orders = orders;
        this.platforms = platforms;
        this.customers = customers;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(Long tenantId, Long platformId, OrderStatus status, int page, int size) {
        Collection<OrderStatus> statuses = status == null ? List.of(OrderStatus.values()) : List.of(status);
        Pageable newestFirst = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id")));
        Page<SaleOrder> result = orders.search(tenantId, platformId == null ? 0L : platformId, statuses, newestFirst);

        Map<Long, Platform> platformById = byId(platforms.findAllById(
                result.stream().map(SaleOrder::getPlatformId).collect(Collectors.toSet())), Platform::getId);
        Map<Long, Customer> customerById = byId(customers.findAllById(
                result.stream().map(SaleOrder::getCustomerId).collect(Collectors.toSet())), Customer::getId);

        List<OrderSummary> content = result.getContent().stream().map(o -> new OrderSummary(
                o.getId(), o.getOrderedAt(),
                platformById.get(o.getPlatformId()).getName(),
                customerById.get(o.getCustomerId()).getName(),
                o.getStatus(), o.getItems().size(),
                sum(o, OrderItem::lineRevenue), sum(o, OrderItem::lineCost), sum(o, OrderItem::lineProfit))).toList();
        return new PageResponse<>(content, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long tenantId, Long id) {
        return detail(find(tenantId, id));
    }

    public OrderDetail create(Long tenantId, OrderRequest req) {
        return detail(orders.save(fill(new SaleOrder(tenantId), req)));
    }

    public OrderDetail update(Long tenantId, Long id, OrderRequest req) {
        return detail(orders.save(fill(find(tenantId, id), req)));
    }

    public OrderDetail changeStatus(Long tenantId, Long id, OrderStatus status) {
        SaleOrder order = find(tenantId, id);
        order.setStatus(status);
        return detail(order);
    }

    public void delete(Long tenantId, Long id) {
        orders.delete(find(tenantId, id));
    }

    // ---- internals ----

    private SaleOrder find(Long tenantId, Long id) {
        return orders.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    /** Validates the request against the tenant's data and (re)builds the order and its items. */
    private SaleOrder fill(SaleOrder order, OrderRequest req) {
        Long tenantId = order.getTenantId();
        boolean isNew = order.getId() == null;

        Platform platform = platforms.findByIdAndTenantId(req.platformId(), tenantId)
                .filter(p -> p.isActive() || req.platformId().equals(order.getPlatformId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown platform"));

        Long customerId = resolveCustomer(tenantId, platform, req);

        Set<Long> productIds = new HashSet<>();
        for (ItemRequest item : req.items()) {
            if (!productIds.add(item.productId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A product appears on more than one line; combine them");
            }
        }
        Map<Long, Product> productById = byId(products.findAllById(productIds).stream()
                .filter(p -> p.getTenantId().equals(tenantId)).toList(), Product::getId);

        // On edit, lines that keep the same product retain their original cost snapshot.
        Map<Long, BigDecimal> keptCost = new HashMap<>();
        order.getItems().forEach(i -> keptCost.put(i.getProductId(), i.getCostPriceSnapshot()));

        List<OrderItem> newItems = new ArrayList<>();
        for (ItemRequest item : req.items()) {
            Product product = productById.get(item.productId());
            boolean keeps = keptCost.containsKey(item.productId());
            if (product == null || (!product.isActive() && !keeps)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown product: " + item.productId());
            }
            BigDecimal cost = keeps ? keptCost.get(item.productId()) : product.getCostPrice();
            newItems.add(new OrderItem(order, tenantId, product.getId(), item.quantity(), cost, item.soldPrice()));
        }

        order.apply(platform.getId(), customerId, req.status(),
                req.orderedAt() != null ? req.orderedAt() : LocalDateTime.now().withNano(0),
                Search.blankToNull(req.notes()));
        order.getItems().clear();
        order.getItems().addAll(newItems);
        return order;
    }

    private Long resolveCustomer(Long tenantId, Platform platform, OrderRequest req) {
        if ((req.customerId() == null) == (req.newCustomer() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide either customerId or newCustomer");
        }
        if (req.customerId() != null) {
            return customers.findByIdAndTenantId(req.customerId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown customer")).getId();
        }
        NewCustomer nc = req.newCustomer();
        Customer c = new Customer(tenantId);
        c.apply(nc.name().trim(), Search.blankToNull(nc.phone()), Search.blankToNull(nc.address()),
                platform.getId(), null);
        return customers.save(c).getId();
    }

    private OrderDetail detail(SaleOrder o) {
        Platform platform = platforms.findById(o.getPlatformId()).orElseThrow();
        Customer customer = customers.findById(o.getCustomerId()).orElseThrow();
        Map<Long, Product> productById = byId(products.findAllById(
                o.getItems().stream().map(OrderItem::getProductId).toList()), Product::getId);

        List<ItemResponse> items = o.getItems().stream().map(i -> new ItemResponse(
                i.getProductId(), productById.get(i.getProductId()).getName(), i.getQuantity(),
                i.getCostPriceSnapshot(), i.getSoldPrice(), i.lineRevenue(), i.lineCost(), i.lineProfit())).toList();

        return new OrderDetail(o.getId(), o.getOrderedAt(), platform.getId(), platform.getName(),
                customer.getId(), customer.getName(), customer.getPhone(), o.getStatus(), o.getNotes(), items,
                sum(o, OrderItem::lineRevenue), sum(o, OrderItem::lineCost), sum(o, OrderItem::lineProfit));
    }

    private static BigDecimal sum(SaleOrder o, Function<OrderItem, BigDecimal> f) {
        return o.getItems().stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> Map<Long, T> byId(Collection<T> values, Function<T, Long> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
