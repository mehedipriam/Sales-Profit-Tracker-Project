package com.salestracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Auth is JWT-only; skip Boot's default in-memory user (and its generated password log line).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SalesTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesTrackerApplication.class, args);
    }
}
