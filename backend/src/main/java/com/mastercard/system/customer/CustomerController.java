package com.mastercard.system.customer;

import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.credit.CreditService;
import com.mastercard.system.credit.CreditTransaction;
import com.mastercard.system.sales.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerRepository customers;
    private final CreditService credit;
    private final InvoiceService invoices;

    public record PaymentRequest(@NotNull @Positive BigDecimal amount, String method, String note) {}

    public record CustomerCredit(Customer customer, BigDecimal balance) {}

    @GetMapping
    public List<Customer> list(@RequestParam(required = false) String q) {
        return (q == null || q.isBlank()) ? customers.findAll() : customers.search(q.trim());
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@Valid @RequestBody Customer c) {
        c.setId(null);
        return customers.save(c);
    }

    @PutMapping("/{id}")
    public Customer update(@PathVariable Long id, @Valid @RequestBody Customer c) {
        get(id);
        c.setId(id);
        return customers.save(c);
    }

    /** Cliente y saldo adeudado. */
    @GetMapping("/{id}/credit")
    public CustomerCredit creditSummary(@PathVariable Long id) {
        return new CustomerCredit(get(id), credit.balanceOf(id));
    }

    /** Historial de crédito: los abonos del cliente, el más reciente primero. */
    @GetMapping("/{id}/credit-history")
    public List<CreditTransaction> history(@PathVariable Long id) {
        get(id);
        return credit.history(id);
    }

    /** Abono del cliente: se aplica a sus facturas a crédito pendientes, de la más antigua a la más reciente. */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceService.PaymentResult pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r, Authentication auth) {
        InvoiceService.PaymentResult result = invoices.payCustomer(id, r.amount(), r.method(), r.note());
        log.info("Abono de {} al cliente {} registrado por {}", r.amount(), id, auth.getName());
        return result;
    }

    /** Cartera: clientes con facturas a crédito pendientes de pago. */
    @GetMapping("/receivables")
    public List<InvoiceService.Receivable> receivables() {
        return invoices.receivables();
    }
}
