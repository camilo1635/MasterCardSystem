package com.mastercard.system.sales;

import com.mastercard.system.accounting.AccountingService;
import com.mastercard.system.accounting.AccountingService.Line;
import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.Money;
import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.credit.CreditService;
import com.mastercard.system.inventory.InventoryMovement.Type;
import com.mastercard.system.inventory.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesReturnService {

    public record ReturnItemRequest(@NotNull Long invoiceItemId, @Positive int quantity) {}

    public record ReturnRequest(@NotNull Long invoiceId, String reason,
                                @NotEmpty @Valid List<ReturnItemRequest> items) {}

    /** Cantidad pendiente de devolver por ítem de factura. */
    public record Returnable(Long invoiceItemId, Long productId, String description, int sold, int returned,
                             int available, BigDecimal unitPrice, BigDecimal ivaRate) {}

    private final InvoiceRepository invoices;
    private final SalesReturnRepository returns;
    private final InventoryService inventory;
    private final CreditService credit;
    private final AccountingService accounting;

    /**
     * Registra la devolución en una sola transacción: reingresa el stock al costo vendido, descuenta el
     * crédito del cliente (o reembolsa en efectivo) y genera el asiento inverso de la parte devuelta.
     */
    @Transactional
    public SalesReturn create(ReturnRequest r) {
        Invoice inv = invoices.findByIdForUpdate(r.invoiceId())
                .orElseThrow(() -> new NotFoundException("Factura", r.invoiceId()));
        if (inv.getStatus().equals("ANULADA")) {
            throw new BusinessException("No se puede devolver sobre una factura anulada");
        }
        Map<Long, InvoiceItem> itemsById = new HashMap<>();
        for (InvoiceItem it : invoices.findWithItems(inv.getId()).orElseThrow().getItems()) {
            itemsById.put(it.getId(), it);
        }
        Map<Long, Integer> alreadyReturned = returnedMap(inv.getId());

        SalesReturn ret = new SalesReturn();
        ret.setNumber(returns.nextNumber());
        ret.setInvoiceId(inv.getId());
        ret.setReason(r.reason());

        Set<Long> seen = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;

        for (ReturnItemRequest i : r.items()) {
            if (!seen.add(i.invoiceItemId())) {
                throw new BusinessException("El ítem " + i.invoiceItemId() + " está repetido en la devolución");
            }
            InvoiceItem src = itemsById.get(i.invoiceItemId());
            if (src == null) {
                throw new BusinessException("El ítem " + i.invoiceItemId() + " no pertenece a la factura " + inv.getNumber());
            }
            int available = src.getQuantity() - alreadyReturned.getOrDefault(src.getId(), 0);
            if (i.quantity() > available) {
                throw new BusinessException("'" + src.getDescription() + "': se pueden devolver máximo " + available
                        + " unidades (vendidas " + src.getQuantity() + ")");
            }
            SalesReturnItem it = new SalesReturnItem();
            it.setSalesReturn(ret);
            it.setInvoiceItemId(src.getId());
            it.setProductId(src.getProductId());
            it.setDescription(src.getDescription());
            it.setQuantity(i.quantity());
            it.setUnitPrice(src.getUnitPrice());
            it.setUnitCost(src.getUnitCost());
            it.setIvaRate(src.getIvaRate());
            ret.getItems().add(it);

            BigDecimal line = Money.round(src.getUnitPrice().multiply(BigDecimal.valueOf(i.quantity())));
            subtotal = subtotal.add(line);
            iva = iva.add(Money.percent(line, src.getIvaRate()));
            cogs = cogs.add(Money.round(src.getUnitCost().multiply(BigDecimal.valueOf(i.quantity()))));
        }

        // Entrada de stock al costo con que se vendió; bloqueo en orden de producto (evita deadlocks).
        List<SalesReturnItem> byProduct = new ArrayList<>(ret.getItems());
        byProduct.sort(Comparator.comparing(SalesReturnItem::getProductId));
        for (SalesReturnItem it : byProduct) {
            inventory.move(it.getProductId(), Type.ENTRADA, it.getQuantity(), it.getUnitCost(),
                    "DEV-" + ret.getNumber(), "Devolución factura " + inv.getNumber());
        }

        BigDecimal total = subtotal.add(iva);
        BigDecimal creditApplied = BigDecimal.ZERO;
        if (inv.getPaymentType().equals("CREDITO")) {
            creditApplied = credit.applyReturn(inv.getCustomerId(), total, inv.getId(),
                    "Devolución " + ret.getNumber() + " factura " + inv.getNumber());
        }
        ret.setSubtotal(subtotal);
        ret.setIva(iva);
        ret.setTotal(total);
        ret.setCreditApplied(creditApplied);
        ret.setCashRefund(total.subtract(creditApplied));
        ret = returns.save(ret);

        accounting.post(LocalDate.now(), "Devolución " + ret.getNumber() + " factura " + inv.getNumber(),
                "DEVOLUCION", ret.getId(), List.of(
                        Line.debit(AccountingService.INGRESOS, subtotal),
                        Line.debit(AccountingService.IVA, iva),
                        Line.credit(AccountingService.CLIENTES, creditApplied),
                        Line.credit(AccountingService.CAJA, ret.getCashRefund()),
                        Line.debit(AccountingService.INVENTARIO, cogs),
                        Line.credit(AccountingService.COSTO_VENTAS, cogs)));
        return ret;
    }

    /** Unidades vendidas, devueltas y aún devolvibles de cada ítem de la factura. */
    @Transactional(readOnly = true)
    public List<Returnable> returnable(Long invoiceId) {
        Invoice inv = invoices.findWithItems(invoiceId).orElseThrow(() -> new NotFoundException("Factura", invoiceId));
        Map<Long, Integer> done = returnedMap(invoiceId);
        List<Returnable> out = new ArrayList<>();
        for (InvoiceItem it : inv.getItems()) {
            int returned = done.getOrDefault(it.getId(), 0);
            out.add(new Returnable(it.getId(), it.getProductId(), it.getDescription(), it.getQuantity(), returned,
                    inv.getStatus().equals("ANULADA") ? 0 : it.getQuantity() - returned,
                    it.getUnitPrice(), it.getIvaRate()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SalesReturn get(Long id) {
        return returns.findWithItems(id).orElseThrow(() -> new NotFoundException("Devolución", id));
    }

    private Map<Long, Integer> returnedMap(Long invoiceId) {
        Map<Long, Integer> m = new HashMap<>();
        for (Object[] row : returns.returnedQuantities(invoiceId)) {
            m.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return m;
    }
}
