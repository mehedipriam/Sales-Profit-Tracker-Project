package com.salestracker.agent;

import com.salestracker.common.Search;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Finds the saved customer a message means, by phone or by name, within one shop. */
@Component
class CustomerMatcher {
    static final int CHOICES = 5;

    private final CustomerRepository customers;

    CustomerMatcher(CustomerRepository customers) {
        this.customers = customers;
    }

    /** Everyone the name or phone could mean (at most CHOICES + 1, so "and more" can be told). A full name wins over a part. */
    List<Customer> find(Long tenantId, String name, String phone) {
        String suffix = DraftService.phoneSuffix(phone);
        if (suffix != null) {
            List<Customer> byPhone = customers.findByPhoneEndingWith(tenantId, suffix);
            if (!byPhone.isEmpty()) return byPhone.stream().limit(CHOICES + 1).toList();
        }
        if (name == null || name.isBlank()) return List.of();
        List<Customer> found = customers.search(tenantId, Search.pattern(name), 0L,
                PageRequest.of(0, CHOICES + 1, Sort.by("name"))).getContent();
        List<Customer> exact = found.stream().filter(c -> c.getName().equalsIgnoreCase(name.trim())).toList();
        return exact.size() == 1 ? exact : found;
    }

    /** "Which Imdad did you mean? Imdad Hossain (...1111), Imdad Khan (...2222)" */
    static String which(String said, List<Customer> found) {
        String choices = found.stream().limit(CHOICES).map(CustomerMatcher::describe).collect(Collectors.joining(", "));
        return "Which " + said.trim() + " did you mean? " + choices + (found.size() > CHOICES ? " ..." : "");
    }

    /** A customer told apart from others of the same name by the end of their phone number. */
    static String describe(Customer c) {
        String phone = c.getPhone() == null ? "" : c.getPhone().replaceAll("\\D", "");
        return phone.length() >= 3 ? c.getName() + " (..." + phone.substring(Math.max(0, phone.length() - 4)) + ")" : c.getName();
    }
}
