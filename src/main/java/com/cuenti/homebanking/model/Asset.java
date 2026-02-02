package com.cuenti.homebanking.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Asset entity for managing stocks, ETFs, and crypto.
 */
@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "user")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String symbol; // e.g., VWCE.DE, AMZN, BTC-USD

    @Column(nullable = false)
    private String name; // e.g., Vanguard FTSE All-World, Amazon, Bitcoin

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type;

    @Column(precision = 15, scale = 4)
    private BigDecimal currentPrice;

    private String currency; // Currency of the price (e.g., EUR, USD)

    private LocalDateTime lastUpdate;

    public enum AssetType {
        STOCK,
        ETF,
        CRYPTO
    }
}
