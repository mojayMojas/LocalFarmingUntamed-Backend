package com.farmplatform.web;

import com.farmplatform.entity.Product;
import com.farmplatform.entity.ProductCategory;
import com.farmplatform.repository.Repos.CategoryRepo;
import com.farmplatform.repository.Repos.ProductRepo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only product reference data for any logged-in user (farmers pick what they supply, customers what they need). */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {
    private final CategoryRepo categories;
    private final ProductRepo products;

    public ReferenceController(CategoryRepo categories, ProductRepo products) {
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/categories")
    public List<ProductCategory> categories() {
        return categories.findAll();
    }

    @GetMapping("/products")
    public List<Product> products() {
        return products.findAll();
    }
}
