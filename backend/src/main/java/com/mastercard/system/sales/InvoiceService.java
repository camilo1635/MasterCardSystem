package com.mastercard.system.sales;

import com.mastercard.system.accounting.AccountingService;
import com.mastercard.system.accounting.AccountingService.Line;
import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.Money;
import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.credit.CreditService;
import com.mastercard.system.credit.CreditTransactionRepository;
import com.mastercard.system.customer.CustomerRepository;
import com.mastercard.system.inventory.InventoryMovement.Type;
import com.mastercard.system.inventory.InventoryService;
import com.mastercard.system.product.Product;
import com.mastercard.system.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    public record ItemRequest(@NotNull Long productId, @Positive int quantity, BigDecimal unitPrice) {}

    public record InvoiceRequest(Long customerId, @NotNull String paymentType, String notes,
                                 @NotEmpty @Valid List<ItemRequest> items) {}

    private final InvoiceRepository invoices;
    private final SalesReturnRepository returns;
    private final CreditTransactionRepository creditTxs;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final InventoryService inventory;
    private final CreditService credit;
    private final AccountingService accounting;

    /**
     * Crea la factura en una sola transacción: descuenta inventario, registra cargo a crédito (si aplica)
     * y genera el asiento contable. Cualquier fallo (stock, cupo) revierte todo.
     */
    @Transactional
    public Invoice create(InvoiceRequest r) {
        boolean onCredit = r.paymentType().equals("CREDITO");
        if (!onCredit && !r.paymentType().equals("CONTADO")) {
            throw new BusinessException("Tipo de pago inválido: " + r.paymentType());
        }
        if (onCredit && r.customerId() == null) {
            throw new BusinessException("Una venta a crédito requiere cliente");
        }
        if (r.customerId() != null && !customers.existsById(r.customerId())) {
            throw new NotFoundException("Cliente", r.customerId());
        }

        Invoice inv = new Invoice();
        inv.setNumber(invoices.nextNumber());
        inv.setCustomerId(r.customerId());
        inv.setPaymentType(r.paymentType());
        inv.setNotes(r.notes());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;

        // Orden estable por producto evita deadlocks entre ventas concurrentes.
        List<ItemRequest> ordered = new ArrayList<>(r.items());
        ordered.sort(Comparator.comparing(ItemRequest::productId));

        // Una sola consulta para todos los productos de la factura.
        Map<Long, Product> catalog = products.findAllById(ordered.stream().map(ItemRequest::productId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        for (ItemRequest i : ordered) {
            Product p = catalog.get(i.productId());
            if (p == null) {
                throw new NotFoundException("Producto", i.productId());
            }
            InvoiceItem it = new InvoiceItem();
            it.setInvoice(inv);
            it.setProductId(p.getId());
            it.setDescription(p.getName());
            it.setQuantity(i.quantity());
            it.setUnitPrice(i.unitPrice() != null ? i.unitPrice() : p.getPrice());
            it.setIvaRate(p.getIvaRate());
            inv.getItems().add(it);
        }

        for (InvoiceItem it : inv.getItems()) {
            // Salida de stock: valida disponibilidad y devuelve el producto con su costo vigente.
            Product p = inventory.move(it.getProductId(), Type.SALIDA, -it.getQuantity(), BigDecimal.ZERO,
                    "FAC-" + inv.getNumber(), null);
            it.setUnitCost(p.getCost()); // costo real bloqueado al descontar stock
            BigDecimal line = Money.round(it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
            subtotal = subtotal.add(line);
            iva = iva.add(Money.percent(line, it.getIvaRate()));
            cogs = cogs.add(Money.round(p.getCost().multiply(BigDecimal.valueOf(it.getQuantity()))));
        }
        BigDecimal total = subtotal.add(iva);
        inv.setSubtotal(subtotal);
        inv.setIva(iva);
        inv.setTotal(total);
        inv = invoices.save(inv); // único guardado: cabecera + ítems por cascada

        if (onCredit) {
            credit.charge(inv.getCustomerId(), total, inv.getId(), "Factura " + inv.getNumber());
        }
        accounting.post(LocalDate.now(), "Factura " + inv.getNumber(), "FACTURA", inv.getId(), List.of(
                Line.debit(onCredit ? AccountingService.CLIENTES : AccountingService.CAJA, total),
                Line.credit(AccountingService.INGRESOS, subtotal),
                Line.credit(AccountingService.IVA, iva),
                Line.debit(AccountingService.COSTO_VENTAS, cogs),
                Line.credit(AccountingService.INVENTARIO, cogs)));
        return inv;
    }

    /** Anula la factura: devuelve stock al costo vendido, revierte el cargo a crédito y el asiento. */
    @Transactional
    public Invoice cancel(Long id) {
        invoices.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Factura", id));
        Invoice inv = invoices.findWithItems(id).orElseThrow(() -> new NotFoundException("Factura", id));
        if (inv.getStatus().equals("ANULADA")) {
            throw new BusinessException("La factura ya está anulada");
        }
        if (returns.existsByInvoiceId(id)) {
            throw new BusinessException("La factura tiene devoluciones registradas y no se puede anular");
        }
        inv.setStatus("ANULADA");
        // Bloqueo en orden ascendente de producto (evita deadlocks con ventas/compras concurrentes).
        List<InvoiceItem> byProduct = new ArrayList<>(inv.getItems());
        byProduct.sort(Comparator.comparing(InvoiceItem::getProductId));
        for (InvoiceItem it : byProduct) {
            inventory.move(it.getProductId(), Type.ENTRADA, it.getQuantity(), it.getUnitCost(),
                    "ANUL-FAC-" + inv.getNumber(), "Anulación de factura");
        }
        if (inv.getPaymentType().equals("CREDITO")) {
            credit.reverseCharge(inv.getCustomerId(), inv.getTotal(), inv.getId(), "Anulación factura " + inv.getNumber());
        }
        accounting.reverse("FACTURA", inv.getId(), "ANULACION", "Anulación factura " + inv.getNumber());
        return inv; // entidad gestionada: el commit persiste el estado
    }

    /**
     * Abono a una factura a crédito, hasta completar su saldo. Descuenta del saldo del cliente y
     * genera el asiento (Dr Caja/Bancos, Cr Clientes) igual que un abono general.
     */
    @Transactional
    public Invoice payInvoice(Long id, BigDecimal amount, String method, String note) {
        Invoice inv = invoices.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Factura", id));
        if (!inv.getPaymentType().equals("CREDITO")) {
            throw new BusinessException("Solo las facturas a crédito reciben abonos");
        }
        if (inv.getStatus().equals("ANULADA")) {
            throw new BusinessException("La factura está anulada");
        }
        BigDecimal value = Money.round(amount);
        if (value.signum() <= 0) {
            throw new BusinessException("El abono debe ser mayor a cero");
        }
        BigDecimal pending = enrich(List.of(inv)).get(0).getPending();
        if (value.compareTo(pending) > 0) {
            throw new BusinessException("El abono (" + value + ") supera el saldo de la factura (" + pending + ")");
        }
        credit.pay(inv.getCustomerId(), value, method == null ? "EFECTIVO" : method,
                note != null && !note.isBlank() ? note : "Abono factura " + inv.getNumber(), inv.getId());
        return enrich(List.of(get(id))).get(0);
    }

    /**
     * Abono del cliente desde su ficha: se aplica a sus facturas a crédito pendientes, de la más antigua a la
     * más reciente, para que el saldo por factura y el del cliente siempre coincidan.
     */
    @Transactional
    public PaymentResult payCustomer(Long customerId, BigDecimal amount, String method, String note) {
        if (!customers.existsById(customerId)) {
            throw new NotFoundException("Cliente", customerId);
        }
        BigDecimal value = Money.round(amount);
        if (value.signum() <= 0) {
            throw new BusinessException("El abono debe ser mayor a cero");
        }
        BigDecimal balance = credit.balanceOf(customerId);
        if (value.compareTo(balance) > 0) {
            throw new BusinessException("El abono (" + value + ") supera el saldo adeudado (" + balance + ")");
        }
        String m = method == null ? "EFECTIVO" : method;
        BigDecimal left = value;
        int touched = 0;
        for (Invoice inv : enrich(invoices.findOpenCreditByCustomer(customerId))) {
            if (left.signum() == 0) {
                break;
            }
            if (inv.getPending().signum() <= 0) {
                continue;
            }
            BigDecimal part = left.min(inv.getPending());
            payInvoice(inv.getId(), part, m, note);
            left = left.subtract(part);
            touched++;
        }
        if (left.signum() > 0) { // saldo heredado sin factura asociada
            credit.pay(customerId, left, m, note, null);
        }
        return new PaymentResult(credit.balanceOf(customerId), touched);
    }

    public record PaymentResult(BigDecimal balance, int invoices) {}

    public record Receivable(Long customerId, String name, String document, String phone, BigDecimal balance,
                             int invoices) {}

    /** Cartera por cobrar: clientes con facturas a crédito pendientes de pago, con su saldo y número de facturas. */
    @Transactional(readOnly = true)
    public List<Receivable> receivables() {
        Map<Long, List<Invoice>> byCustomer = enrich(invoices.findOpenCredit()).stream()
                .filter(i -> i.getCustomerId() != null && i.getPending().signum() > 0)
                .collect(Collectors.groupingBy(Invoice::getCustomerId));
        Map<Long, com.mastercard.system.customer.Customer> people = customers.findAllById(byCustomer.keySet()).stream()
                .collect(Collectors.toMap(com.mastercard.system.customer.Customer::getId, Function.identity()));
        return byCustomer.entrySet().stream().map(e -> {
            var c = people.get(e.getKey());
            BigDecimal sum = e.getValue().stream().map(Invoice::getPending).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Receivable(c.getId(), c.getName(), c.getDocument(), c.getPhone(), sum, e.getValue().size());
        }).sorted(Comparator.comparing(Receivable::balance).reversed()).toList();
    }

    /**
     * Completa los campos derivados de cada factura con pocas consultas: estado de devolución
     * (NINGUNA/PARCIAL/TOTAL) y, en facturas a crédito, lo abonado y el saldo pendiente
     * (total - abonos aplicados a la factura - deuda descontada por devoluciones).
     */
    @Transactional(readOnly = true)
    public List<Invoice> enrich(List<Invoice> list) {
        if (list.isEmpty()) {
            return list;
        }
        computeLinked(list);
        applyUnlinkedPayments(list);
        return list;
    }

    /**
     * Abonos antiguos sin factura asociada (hechos antes de que los abonos se aplicaran por factura): se imputan
     * a las facturas pendientes del cliente, de la más antigua a la más reciente, para que el saldo por factura
     * coincida con el saldo del cliente.
     */
    private void applyUnlinkedPayments(List<Invoice> list) {
        Set<Long> customerIds = list.stream()
                .filter(i -> i.getCustomerId() != null && i.getPaymentType().equals("CREDITO") && i.getPending().signum() > 0)
                .map(Invoice::getCustomerId).collect(Collectors.toSet());
        if (customerIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> take = new HashMap<>();
        for (Object[] row : creditTxs.unlinkedPaymentsByCustomer(customerIds)) {
            BigDecimal left = (BigDecimal) row[1];
            List<Invoice> open = invoices.findOpenCreditByCustomer((Long) row[0]);
            computeLinked(open);
            for (Invoice inv : open) {
                if (left.signum() <= 0) {
                    break;
                }
                BigDecimal part = left.min(inv.getPending());
                if (part.signum() > 0) {
                    take.put(inv.getId(), part);
                    left = left.subtract(part);
                }
            }
        }
        for (Invoice inv : list) {
            BigDecimal part = take.get(inv.getId());
            if (part != null) {
                inv.setPaid(inv.getPaid().add(part));
                inv.setPending(inv.getPending().subtract(part));
            }
        }
    }

    /** Estado de devolución y, en crédito, abonado/saldo considerando solo abonos aplicados a cada factura. */
    private void computeLinked(List<Invoice> list) {
        List<Long> ids = list.stream().map(Invoice::getId).toList();
        Map<Long, Long> returned = new HashMap<>();
        for (Object[] row : returns.returnedByInvoice(ids)) {
            returned.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] row : creditTxs.paidByInvoice(ids)) {
            paid.put((Long) row[0], (BigDecimal) row[1]);
        }
        Map<Long, BigDecimal> returnCredit = new HashMap<>();
        for (Object[] row : returns.creditAppliedByInvoice(ids)) {
            returnCredit.put((Long) row[0], (BigDecimal) row[1]);
        }
        for (Invoice inv : list) {
            long done = returned.getOrDefault(inv.getId(), 0L);
            long sold = inv.getItems().stream().mapToLong(InvoiceItem::getQuantity).sum();
            inv.setReturnStatus(done == 0 ? "NINGUNA" : done >= sold ? "TOTAL" : "PARCIAL");
            if (inv.getPaymentType().equals("CREDITO")) {
                BigDecimal p = paid.getOrDefault(inv.getId(), BigDecimal.ZERO);
                inv.setPaid(p);
                inv.setPending(inv.getStatus().equals("ANULADA") ? BigDecimal.ZERO
                        : inv.getTotal().subtract(p).subtract(returnCredit.getOrDefault(inv.getId(), BigDecimal.ZERO))
                                .max(BigDecimal.ZERO));
            }
        }
    }

    @Transactional(readOnly = true)
    public Invoice get(Long id) {
        return invoices.findWithItems(id).orElseThrow(() -> new NotFoundException("Factura", id));
    }
}
