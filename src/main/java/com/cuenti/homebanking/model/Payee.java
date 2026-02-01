package com.cuenti.homebanking.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Payee entity for managing frequent transaction partners.
 */
@Entity
@Table(name = "payees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_category_id")
    private Category defaultCategory;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Transaction.PaymentMethod defaultPaymentMethod = Transaction.PaymentMethod.NONE;
}
