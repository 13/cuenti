package com.cuenti.homebanking.api.dto;

import com.cuenti.homebanking.model.Account;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDTO {
    private Long id;
    private String accountName;
    private String accountNumber;
    private Account.AccountType accountType;
    private String accountGroup;
    private String institution;
    private String currency;
    private BigDecimal startBalance;
    private BigDecimal balance;
    private Integer sortOrder;
    private boolean excludeFromSummary;
    private boolean excludeFromReports;
}
