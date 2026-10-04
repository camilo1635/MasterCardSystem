package com.mastercard.system.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String sku;

    @NotBlank
    private String name;

    private String brand;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull
    @DecimalMin("0")
    private BigDecimal cost = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0")
    private BigDecimal price;

    @NotNull
    private BigDecimal ivaRate = BigDecimal.valueOf(19);

    /** Solo lectura desde la API: cambia únicamente mediante movimientos de inventario. */
    private int stock;

    private int minStock;

    private boolean active = true;
}
