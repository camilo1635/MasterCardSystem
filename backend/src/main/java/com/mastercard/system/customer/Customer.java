package com.mastercard.system.customer;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String document;

    @NotBlank
    private String name;

    private String phone;
    private String email;
    private String address;

    @DecimalMin("0")
    private BigDecimal creditLimit = BigDecimal.ZERO;

    private boolean active = true;
}
