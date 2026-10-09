package com.mastercard.system.sales;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.Formula;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SalesReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long number;
    private Long invoiceId;

    /** Cliente de la factura (solo lectura, derivado). */
    @Formula("(select inv.customer_id from invoice inv where inv.id = invoice_id)")
    private Long customerId;

    private LocalDateTime date = LocalDateTime.now();
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal total;
    /** Parte del total descontada del saldo adeudado (facturas a crédito). */
    private BigDecimal creditApplied = BigDecimal.ZERO;
    /** Parte del total devuelta en efectivo al cliente. */
    private BigDecimal cashRefund = BigDecimal.ZERO;
    private String reason;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesReturnItem> items = new ArrayList<>();
}
