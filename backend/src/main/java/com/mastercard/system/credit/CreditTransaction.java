package com.mastercard.system.credit;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class CreditTransaction {
    public enum Type { CARGO, ABONO, REVERSO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    private Type type;

    /** Con signo: CARGO suma a la deuda, ABONO/REVERSO resta. */
    private BigDecimal amount;

    /** Saldo adeudado por el cliente tras este movimiento. */
    private BigDecimal balance;

    private Long invoiceId;
    private String method;
    private String note;
    private LocalDateTime createdAt = LocalDateTime.now();
}
