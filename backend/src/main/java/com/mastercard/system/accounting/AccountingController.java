package com.mastercard.system.accounting;

import org.springframework.security.access.prepost.PreAuthorize;
import com.mastercard.system.accounting.AccountingService.IncomeStatement;
import com.mastercard.system.accounting.AccountingService.TrialBalanceRow;
import com.mastercard.system.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService service;
    private final AccountRepository accounts;
    private final ExpenseRepository expenses;

    public record LineDto(String accountCode, String accountName, BigDecimal debit, BigDecimal credit) {}

    public record EntryDto(Long id, LocalDate date, String description, String source, Long sourceId,
                           List<LineDto> lines) {}

    public record ExpenseRequest(@NotNull LocalDate date, @NotNull Long accountId, @NotBlank String description,
                                 @NotNull @Positive BigDecimal amount) {}

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public List<Account> accounts() {
        return accounts.findAllByOrderByCode();
    }

    @GetMapping("/journal")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    @Transactional(readOnly = true)
    public List<EntryDto> journal(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.journal(from, to).stream()
                .map(e -> new EntryDto(e.getId(), e.getDate(), e.getDescription(), e.getSource(), e.getSourceId(),
                        e.getLines().stream()
                                .map(l -> new LineDto(l.getAccount().getCode(), l.getAccount().getName(),
                                        l.getDebit(), l.getCredit()))
                                .toList()))
                .toList();
    }

    @GetMapping("/reports/trial-balance")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public List<TrialBalanceRow> trialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.trialBalance(from, to);
    }

    @GetMapping("/reports/income-statement")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public IncomeStatement incomeStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.incomeStatement(from, to);
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public List<Expense> expenses(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.findByDateBetweenOrderByDateDesc(from, to);
    }

    /** Gasto pagado en efectivo: Dr cuenta de gasto / Cr Caja. */
    @PostMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Expense createExpense(@Valid @RequestBody ExpenseRequest r) {
        Account acc = accounts.findById(r.accountId())
                .orElseThrow(() -> new NotFoundException("Cuenta", r.accountId()));
        Expense e = new Expense();
        e.setDate(r.date());
        e.setAccount(acc);
        e.setDescription(r.description());
        e.setAmount(r.amount());
        e = expenses.save(e);
        service.post(r.date(), "Gasto: " + r.description(), "GASTO", e.getId(), List.of(
                AccountingService.Line.debit(acc.getCode(), r.amount()),
                AccountingService.Line.credit(AccountingService.CAJA, r.amount())));
        return e;
    }
}
