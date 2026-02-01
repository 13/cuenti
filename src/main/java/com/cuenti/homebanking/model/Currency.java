package com.cuenti.homebanking.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Currency entity for managing different currencies and their formatting.
 */
@Entity
@Table(name = "currencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // e.g., EUR

    @Column(nullable = false)
    private String name; // e.g., Euro

    @Column(nullable = false)
    private String symbol; // e.g., €

    @Builder.Default
    private String decimalChar = ",";

    @Builder.Default
    private int fracDigits = 2;

    @Builder.Default
    private String groupingChar = ".";
}
