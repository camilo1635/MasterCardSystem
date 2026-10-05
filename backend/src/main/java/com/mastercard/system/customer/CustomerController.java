package com.mastercard.system.customer;

import org.springframework.security.access.prepost.PreAuthorize;
import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.credit.CreditService;
import com.mastercard.system.credit.CreditTransaction;
import com.mastercard.system.credit.CreditTransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final CreditTransactionRepository creditTxs;

    public record PaymentRequest(@NotNull @Positive BigDecimal amount, String method, String note) {}

    public record CustomerCredit(Customer customer, BigDecimal balance, BigDecimal available) {}

    public record Receivable(Long customerId, String name, String document, String phone, BigDecimal balance) {}

    @GetMapping
    public List<Customer> list(@RequestParam(required = false) String q) {
        return (q == null || q.isBlank()) ? customers.findAll() : customers.search(q.trim());
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@Valid @RequestBody Customer c) {
        c.setId(null);
        return customers.save(c);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    public Customer update(@PathVariable Long id, @Valid @RequestBody Customer c) {
        get(id);
        c.setId(id);
        return customers.save(c);
    }

    /** Saldo y cupo disponible del cliente. */
    @GetMapping("/{id}/credit")
    public CustomerCredit creditSummary(@PathVariable Long id) {
        Customer c = get(id);
        BigDecimal balance = credit.balanceOf(id);
        return new CustomerCredit(c, balance, c.getCreditLimit().subtract(balance));
    }

    /** Historial completo (cargos, abonos, reversos), más reciente primero. */
    @GetMapping("/{id}/credit-history")
    public List<CreditTransaction> history(@PathVariable Long id) {
        get(id);
        return credit.history(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditTransaction pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest r, Authentication auth) {
        CreditTransaction t = credit.pay(id, r.amount(), r.method() == null ? "EFECTIVO" : r.method(), r.note());
        log.info("Abono de {} al cliente {} registrado por {}", r.amount(), id, auth.getName());
        return t;
    }

    /** Cartera: clientes con saldo pendiente. */
    @GetMapping("/receivables")
    public List<Receivable> receivables() {
        List<Object[]> rows = creditTxs.balancesByCustomer();
        Map<Long, Customer> byId = customers.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return rows.stream().map(row -> {
            Customer c = byId.get((Long) row[0]);
            return new Receivable(c.getId(), c.getName(), c.getDocument(), c.getPhone(), (BigDecimal) row[1]);
        }).toList();
    }
}
