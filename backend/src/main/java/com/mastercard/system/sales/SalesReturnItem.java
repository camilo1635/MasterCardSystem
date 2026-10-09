package com.mastercard.system.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SalesReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id")
    private SalesReturn salesReturn;

    private Long invoiceItemId;
    private Long productId;
    private String description;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;
    private BigDecimal ivaRate;
}
