package com.farmplatform.common;

import com.farmplatform.entity.ProductCategory;
import com.farmplatform.entity.Role;
import com.farmplatform.entity.User;
import com.farmplatform.repository.Repos.CategoryRepo;
import com.farmplatform.repository.Repos.RoleRepo;
import com.farmplatform.repository.Repos.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the four roles, the first administrator and two starter categories on an empty database. */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepo roles;
    private final UserRepo users;
    private final CategoryRepo categories;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminFirst;
    private final String adminLast;

    public DataSeeder(RoleRepo roles, UserRepo users, CategoryRepo categories, PasswordEncoder encoder,
                      @Value("${app.admin.email}") String adminEmail,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.admin.first-name}") String adminFirst,
                      @Value("${app.admin.last-name}") String adminLast) {
        this.roles = roles;
        this.users = users;
        this.categories = categories;
        this.encoder = encoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFirst = adminFirst;
        this.adminLast = adminLast;
    }

    @Override
    public void run(String... args) {
        ensureRole("ADMIN", "Platform owner / administrator");
        ensureRole("FARMER", "Supplies agricultural products");
        ensureRole("CUSTOMER", "Bulk customer");
        ensureRole("DRIVER", "Delivers customer orders");

        if (users.findByRoleRoleName("ADMIN").isEmpty()) {
            User admin = new User();
            admin.role = roles.findByRoleName("ADMIN").orElseThrow();
            admin.firstName = adminFirst;
            admin.lastName = adminLast;
            admin.email = adminEmail.toLowerCase();
            admin.password = encoder.encode(adminPassword);
            users.save(admin);
            log.warn("Created initial administrator {} - change this password immediately.", adminEmail);
        }

        if (categories.count() == 0) {
            category("Crops", "Crop products such as maize, vegetables and fruit");
            category("Animal Products", "Meat, eggs, milk and other livestock products");
        }
    }

    private void ensureRole(String name, String description) {
        if (roles.findByRoleName(name).isEmpty()) {
            roles.save(new Role(name, description));
        }
    }

    private void category(String name, String description) {
        ProductCategory c = new ProductCategory();
        c.categoryName = name;
        c.description = description;
        categories.save(c);
    }
}
