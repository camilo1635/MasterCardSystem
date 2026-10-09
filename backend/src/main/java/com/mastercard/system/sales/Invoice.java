package com.mastercard.system.sales;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long number;
    private Long customerId;
    private LocalDateTime date = LocalDateTime.now();
    private String paymentType; // CONTADO, CREDITO
    private String status = "EMITIDA"; // EMITIDA, ANULADA
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal total;
    private String notes;

    /** Derivado (no persistido): NINGUNA, PARCIAL o TOTAL según las unidades devueltas. */
    @Transient
    private String returnStatus = "NINGUNA";

    /** Derivados (no persistidos), solo para facturas a crédito: abonado y saldo pendiente de la factura. */
    @Transient
    private BigDecimal paid = BigDecimal.ZERO;
    @Transient
    private BigDecimal pending = BigDecimal.ZERO;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();
}
