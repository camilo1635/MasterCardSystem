package com.mastercard.system.credit;

import com.mastercard.system.accounting.AccountingService;
import com.mastercard.system.accounting.AccountingService.Line;
import com.mastercard.system.common.BusinessException;
import com.mastercard.system.common.NotFoundException;
import com.mastercard.system.credit.CreditTransaction.Type;
import com.mastercard.system.customer.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditTransactionRepository txs;
    private final CustomerRepository customers;
    private final AccountingService accounting;

    public BigDecimal balanceOf(Long customerId) {
        return txs.balanceOf(customerId);
    }

    /** Cargo por factura a crédito (con bloqueo del cliente para serializar su saldo). Sin cupo máximo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void charge(Long customerId, BigDecimal amount, Long invoiceId, String note) {
        customers.findByIdForUpdate(customerId).orElseThrow(() -> new NotFoundException("Cliente", customerId));
        BigDecimal newBalance = txs.balanceOf(customerId).add(amount);
        save(customerId, Type.CARGO, amount, newBalance, invoiceId, null, note);
    }

    /** Anulación de una factura a crédito: resta el cargo original del saldo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseCharge(Long customerId, BigDecimal amount, Long invoiceId, String note) {
        customers.findByIdForUpdate(customerId).orElseThrow(() -> new NotFoundException("Cliente", customerId));
        BigDecimal newBalance = txs.balanceOf(customerId).subtract(amount);
        save(customerId, Type.REVERSO, amount.negate(), newBalance, invoiceId, null, note);
    }

    /**
     * Devolución de una factura a crédito: descuenta del saldo hasta donde alcance (nunca lo deja negativo).
     * Devuelve el monto aplicado al saldo; el resto debe reembolsarse en efectivo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal applyReturn(Long customerId, BigDecimal amount, Long invoiceId, String note) {
        customers.findByIdForUpdate(customerId).orElseThrow(() -> new NotFoundException("Cliente", customerId));
        BigDecimal balance = txs.balanceOf(customerId);
        BigDecimal applied = amount.min(balance.max(BigDecimal.ZERO));
        if (applied.signum() > 0) {
            save(customerId, Type.REVERSO, applied.negate(), balance.subtract(applied), invoiceId, null, note);
        }
        return applied;
    }

    /** Abono del cliente: Dr Caja/Bancos, Cr Clientes. No puede superar el saldo adeudado. */
    @Transactional
    public CreditTransaction pay(Long customerId, BigDecimal amount, String method, String note) {
        return pay(customerId, amount, method, note, null);
    }

    /** Abono aplicado a una factura específica (invoiceId) o a la cuenta general del cliente (null). */
    @Transactional
    public CreditTransaction pay(Long customerId, BigDecimal amount, String method, String note, Long invoiceId) {
        customers.findByIdForUpdate(customerId).orElseThrow(() -> new NotFoundException("Cliente", customerId));
        BigDecimal balance = txs.balanceOf(customerId);
        if (amount.signum() <= 0) {
            throw new BusinessException("El abono debe ser mayor a cero");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BusinessException("El abono (" + amount + ") supera el saldo adeudado (" + balance + ")");
        }
        CreditTransaction t = save(customerId, Type.ABONO, amount.negate(), balance.subtract(amount), invoiceId, method, note);
        String cash = "EFECTIVO".equals(method) ? AccountingService.CAJA : AccountingService.BANCOS;
        accounting.post(LocalDate.now(), "Abono cliente #" + customerId, "ABONO", t.getId(), List.of(
                Line.debit(cash, amount),
                Line.credit(AccountingService.CLIENTES, amount)));
        return t;
    }

    /** Historial de crédito del cliente: sus abonos, el más reciente primero. */
    @Transactional(readOnly = true)
    public List<CreditTransaction> history(Long customerId) {
        return txs.findByCustomerIdAndTypeOrderByIdDesc(customerId, Type.ABONO);
    }

    private CreditTransaction save(Long customerId, Type type, BigDecimal signedAmount, BigDecimal balance,
                                   Long invoiceId, String method, String note) {
        CreditTransaction t = new CreditTransaction();
        t.setCustomerId(customerId);
        t.setType(type);
        t.setAmount(signedAmount);
        t.setBalance(balance);
        t.setInvoiceId(invoiceId);
        t.setMethod(method);
        t.setNote(note);
        return txs.save(t);
    }
}
