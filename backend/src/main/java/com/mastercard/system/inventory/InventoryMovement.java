package com.mastercard.system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class InventoryMovement {
    public enum Type { ENTRADA, SALIDA, AJUSTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    @Enumerated(EnumType.STRING)
    private Type type;

    /** Con signo: positivo suma stock, negativo resta. */
    private int quantity;

    private BigDecimal unitCost = BigDecimal.ZERO;
    private String reference;
    private String note;
    private LocalDateTime createdAt = LocalDateTime.now();
}
