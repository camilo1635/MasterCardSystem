package com.mastercard.system.inventory;

import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.inventory.PurchaseService.PurchaseRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService service;
    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;

    @GetMapping("/suppliers")
    public List<Supplier> suppliers() {
        return suppliers.findAll();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier createSupplier(@Valid @RequestBody Supplier s) {
        s.setId(null);
        return suppliers.save(s);
    }

    @PutMapping("/suppliers/{id}")
    public Supplier updateSupplier(@PathVariable Long id, @Valid @RequestBody Supplier s) {
        suppliers.findById(id).orElseThrow(() -> new NotFoundException("Proveedor", id));
        s.setId(id);
        return suppliers.save(s);
    }

    @GetMapping("/purchases")
    @Transactional(readOnly = true)
    public List<Purchase> purchases() {
        return purchases.findAllWithItems();
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public Purchase create(@Valid @RequestBody PurchaseRequest r) {
        return service.create(r);
    }
}
