package com.farmplatform.web;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.CategoryRequest;
import com.farmplatform.dto.Dtos.ProductRequest;
import com.farmplatform.entity.Product;
import com.farmplatform.entity.ProductCategory;
import com.farmplatform.repository.Repos.CategoryRepo;
import com.farmplatform.repository.Repos.ProductRepo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administrator: product categories and products. */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {
    private final CategoryRepo categories;
    private final ProductRepo products;

    public AdminCatalogController(CategoryRepo categories, ProductRepo products) {
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/categories")
    public List<ProductCategory> categories() {
        return categories.findAll();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCategory createCategory(@Valid @RequestBody CategoryRequest req) {
        ProductCategory c = new ProductCategory();
        c.categoryName = req.categoryName().trim();
        c.description = req.description();
        return categories.save(c);
    }

    @PutMapping("/categories/{id}")
    public ProductCategory updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        ProductCategory c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
        c.categoryName = req.categoryName().trim();
        c.description = req.description();
        return categories.save(c);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categories.deleteById(id); // a category still in use fails with a 409 from the exception handler
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public List<Product> products() {
        return products.findAll();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@Valid @RequestBody ProductRequest req) {
        return products.save(apply(new Product(), req));
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
        return products.save(apply(p, req));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        products.deleteById(id); // a product still in use fails with a 409 from the exception handler
        return ResponseEntity.noContent().build();
    }

    private Product apply(Product p, ProductRequest req) {
        p.category = categories.findById(req.categoryId()).orElseThrow(() -> ApiException.notFound("Category not found"));
        p.productName = req.productName().trim();
        p.productType = req.productType();
        p.description = req.description();
        p.unit = req.unit();
        return p;
    }
}
