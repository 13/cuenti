package com.cuenti.homebanking.api.dto;

import com.cuenti.homebanking.model.Asset;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDTO {
    private Long id;
    private String symbol;
    private String name;
    private Asset.AssetType type;
    private BigDecimal currentPrice;
    private String currency;
    private LocalDateTime lastUpdate;
}
