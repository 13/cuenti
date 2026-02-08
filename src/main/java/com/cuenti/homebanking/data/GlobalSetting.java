package com.cuenti.homebanking.data;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity for system-wide configurations.
 */
@Entity
@Table(name = "global_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSetting {

    @Id
    @Column(name = "setting_key")
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;
}
