package com.mastercard.system.inventory;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.mastercard.system.product.Product;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService service;
    private final InventoryMovementRepository movements;

    public record AdjustRequest(@NotNull Long productId, int quantity, String note) {}

    @GetMapping("/movements")
    public List<InventoryMovement> movements(@RequestParam(required = false) Long productId) {
        return productId == null ? movements.findTop200ByOrderByIdDesc()
                : movements.findByProductIdOrderByIdDesc(productId);
    }

    /** quantity con signo: +5 suma, -2 resta. */
    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public Product adjust(@Valid @RequestBody AdjustRequest r) {
        return service.adjust(r.productId(), r.quantity(), r.note());
    }
}
