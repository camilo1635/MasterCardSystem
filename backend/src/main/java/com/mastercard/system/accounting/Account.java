package com.mastercard.system.accounting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private String type; // ACTIVO, PASIVO, PATRIMONIO, INGRESO, COSTO, GASTO
}
