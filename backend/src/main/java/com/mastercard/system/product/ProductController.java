package com.mastercard.system.product;

import com.mastercard.system.common.NotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository products;

    @GetMapping("/products")
    public List<Product> list(@RequestParam(required = false) String q) {
        return (q == null || q.isBlank()) ? products.findAll() : products.search(q.trim());
    }

    @GetMapping("/products/low-stock")
    public List<Product> lowStock() {
        return products.findLowStock();
    }

    @GetMapping("/products/{id}")
    public Product get(@PathVariable Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Producto", id));
    }

    /** El stock inicial se carga con un movimiento de inventario (o compra), no desde aquí. */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody Product p) {
        p.setId(null);
        p.setStock(0);
        return products.save(p);
    }

    @PutMapping("/products/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody Product in) {
        Product p = get(id);
        p.setSku(in.getSku());
        p.setName(in.getName());
        p.setBrand(in.getBrand());
        p.setCost(in.getCost());
        p.setPrice(in.getPrice());
        p.setIvaRate(in.getIvaRate());
        p.setMinStock(in.getMinStock());
        return products.save(p);
    }
}
