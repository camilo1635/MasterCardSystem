package com.mastercard.system.accounting;

import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingService {

    public static final String CAJA = "1105";
    public static final String BANCOS = "1110";
    public static final String CLIENTES = "1305";
    public static final String INVENTARIO = "1435";
    public static final String PROVEEDORES = "2205";
    public static final String IVA = "2408";
    public static final String INGRESOS = "4135";
    public static final String COSTO_VENTAS = "6135";
    public static final String GASTOS_DIVERSOS = "5195";

    public record Line(String accountCode, BigDecimal debit, BigDecimal credit) {
        public static Line debit(String code, BigDecimal amount) {
            return new Line(code, amount, BigDecimal.ZERO);
        }

        public static Line credit(String code, BigDecimal amount) {
            return new Line(code, BigDecimal.ZERO, amount);
        }
    }

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    /** Registra un asiento de partida doble. Las líneas en cero se omiten; debe = haber es obligatorio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(LocalDate date, String description, String source, Long sourceId, List<Line> lines) {
        List<Line> effective = lines.stream()
                .filter(l -> l.debit().signum() != 0 || l.credit().signum() != 0)
                .toList();
        BigDecimal debit = effective.stream().map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = effective.stream().map(Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (effective.isEmpty() || debit.compareTo(credit) != 0) {
            throw new BusinessException("Asiento descuadrado: debe " + debit + " <> haber " + credit);
        }
        JournalEntry e = new JournalEntry();
        e.setDate(date);
        e.setDescription(description);
        e.setSource(source);
        e.setSourceId(sourceId);
        // Una sola consulta para todas las cuentas del asiento.
        Map<String, Account> byCode = accounts.findByCodeIn(effective.stream().map(Line::accountCode).distinct().toList())
                .stream().collect(Collectors.toMap(Account::getCode, Function.identity()));
        for (Line l : effective) {
            Account account = byCode.get(l.accountCode());
            if (account == null) {
                throw new NotFoundException("Cuenta", l.accountCode());
            }
            JournalLine jl = new JournalLine();
            jl.setEntry(e);
            jl.setAccount(account);
            jl.setDebit(l.debit());
            jl.setCredit(l.credit());
            e.getLines().add(jl);
        }
        return entries.save(e);
    }

    /** Asiento inverso (debe/haber intercambiados) del asiento original identificado por source/sourceId. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(String originalSource, Long sourceId, String newSource, String description) {
        JournalEntry original = entries.findBySource(originalSource, sourceId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Asiento " + originalSource, sourceId));
        List<Line> inverse = new ArrayList<>();
        for (JournalLine l : original.getLines()) {
            inverse.add(new Line(l.getAccount().getCode(), l.getCredit(), l.getDebit()));
        }
        post(LocalDate.now(), description, newSource, sourceId, inverse);
    }

    // ===== Reportes =====

    public record TrialBalanceRow(String code, String name, String type, BigDecimal debit, BigDecimal credit,
                                  BigDecimal balance) {}

    public record IncomeStatement(BigDecimal income, BigDecimal costOfSales, BigDecimal grossProfit,
                                  BigDecimal expenses, BigDecimal netProfit) {}

    @Transactional(readOnly = true)
    public List<TrialBalanceRow> trialBalance(LocalDate from, LocalDate to) {
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (Object[] r : entries.totalsByAccount(from, to)) {
            BigDecimal d = (BigDecimal) r[3];
            BigDecimal c = (BigDecimal) r[4];
            String type = (String) r[2];
            boolean debitNature = type.equals("ACTIVO") || type.equals("COSTO") || type.equals("GASTO");
            rows.add(new TrialBalanceRow((String) r[0], (String) r[1], type, d, c,
                    debitNature ? d.subtract(c) : c.subtract(d)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public IncomeStatement incomeStatement(LocalDate from, LocalDate to) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (TrialBalanceRow r : trialBalance(from, to)) {
            switch (r.type()) {
                case "INGRESO" -> income = income.add(r.balance());
                case "COSTO" -> cost = cost.add(r.balance());
                case "GASTO" -> expenses = expenses.add(r.balance());
                default -> { }
            }
        }
        BigDecimal gross = income.subtract(cost);
        return new IncomeStatement(income, cost, gross, expenses, gross.subtract(expenses));
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> journal(LocalDate from, LocalDate to) {
        return entries.findBetween(from, to);
    }
}
