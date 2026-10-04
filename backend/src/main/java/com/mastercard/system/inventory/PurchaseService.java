package com.mastercard.system.inventory;

import com.mastercard.system.accounting.AccountingService;
import com.mastercard.system.accounting.AccountingService.Line;
import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.Money;
import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.inventory.InventoryMovement.Type;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mastercard.system.product.Product;
import com.mastercard.system.product.ProductRepository;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    public record ItemRequest(@NotNull Long productId, @Positive int quantity, @NotNull @PositiveOrZero BigDecimal unitCost) {}

    public record PurchaseRequest(@NotNull Long supplierId, String docNumber, LocalDate date,
                                  @NotNull String paymentType, @NotEmpty @Valid List<ItemRequest> items) {}

    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final AccountingService accounting;

    /** Compra: entrada de inventario + asiento (Dr Inventario, Dr IVA / Cr Caja o Proveedores). */
    @Transactional
    public Purchase create(PurchaseRequest r) {
        if (!r.paymentType().equals("CONTADO") && !r.paymentType().equals("CREDITO")) {
            throw new BusinessException("Tipo de pago inválido: " + r.paymentType());
        }
        Supplier supplier = suppliers.findById(r.supplierId())
                .orElseThrow(() -> new NotFoundException("Proveedor", r.supplierId()));
        LocalDate date = r.date() != null ? r.date() : LocalDate.now();

        Purchase p = new Purchase();
        p.setSupplier(supplier);
        p.setDocNumber(r.docNumber());
        p.setDate(date);
        p.setPaymentType(r.paymentType());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        // Orden estable por producto (evita deadlocks con ventas concurrentes) y una sola consulta de productos.
        List<ItemRequest> ordered = new ArrayList<>(r.items());
        ordered.sort(Comparator.comparing(ItemRequest::productId));
        Map<Long, Product> catalog = products.findAllById(ordered.stream().map(ItemRequest::productId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (ItemRequest i : ordered) {
            Product prod = catalog.get(i.productId());
            if (prod == null) {
                throw new NotFoundException("Producto", i.productId());
            }
            PurchaseItem pi = new PurchaseItem();
            pi.setPurchase(p);
            pi.setProductId(prod.getId());
            pi.setQuantity(i.quantity());
            pi.setUnitCost(i.unitCost());
            pi.setIvaRate(prod.getIvaRate());
            p.getItems().add(pi);
            BigDecimal line = Money.round(i.unitCost().multiply(BigDecimal.valueOf(i.quantity())));
            subtotal = subtotal.add(line);
            iva = iva.add(Money.percent(line, prod.getIvaRate()));
        }
        p.setSubtotal(subtotal);
        p.setIva(iva);
        p.setTotal(subtotal.add(iva));
        p = purchases.save(p);

        for (PurchaseItem pi : p.getItems()) {
            inventory.move(pi.getProductId(), Type.ENTRADA, pi.getQuantity(), pi.getUnitCost(),
                    "COMPRA-" + p.getId(), supplier.getName());
        }
        String payAccount = p.getPaymentType().equals("CONTADO") ? AccountingService.CAJA : AccountingService.PROVEEDORES;
        accounting.post(date, "Compra #" + p.getId() + " - " + supplier.getName(), "COMPRA", p.getId(), List.of(
                Line.debit(AccountingService.INVENTARIO, subtotal),
                Line.debit(AccountingService.IVA, iva),
                Line.credit(payAccount, p.getTotal())));
        return p;
    }
}
