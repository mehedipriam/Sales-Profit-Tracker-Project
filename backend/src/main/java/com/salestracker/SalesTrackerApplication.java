package com.salestracker;

import com.salestracker.tenant.TenantScopedRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Auth is JWT-only; skip Boot's default in-memory user (and its generated password log line).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// repositoryBaseClass: every repository is backed by TenantScopedRepositoryImpl (Phase 7a tenant isolation).
@EnableJpaRepositories(repositoryBaseClass = TenantScopedRepositoryImpl.class)
public class SalesTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesTrackerApplication.class, args);
    }
}
