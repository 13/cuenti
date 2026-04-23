package com.cuenti.homebanking.api.dto;

import com.cuenti.homebanking.model.*;

/**
 * Utility class for converting between entities and DTOs.
 */
public final class DtoMapper {

    private DtoMapper() {}

    public static AccountDTO toAccountDTO(Account a) {
        return AccountDTO.builder()
                .id(a.getId())
                .accountName(a.getAccountName())
                .accountNumber(a.getAccountNumber())
                .accountType(a.getAccountType())
                .accountGroup(a.getAccountGroup())
                .institution(a.getInstitution())
                .currency(a.getCurrency())
                .startBalance(a.getStartBalance())
                .balance(a.getBalance())
                .sortOrder(a.getSortOrder())
                .excludeFromSummary(a.isExcludeFromSummary())
                .excludeFromReports(a.isExcludeFromReports())
                .build();
    }

    public static TransactionDTO toTransactionDTO(Transaction t) {
        return TransactionDTO.builder()
                .id(t.getId())
                .type(t.getType())
                .fromAccountId(t.getFromAccount() != null ? t.getFromAccount().getId() : null)
                .fromAccountName(t.getFromAccount() != null ? t.getFromAccount().getAccountName() : null)
                .toAccountId(t.getToAccount() != null ? t.getToAccount().getId() : null)
                .toAccountName(t.getToAccount() != null ? t.getToAccount().getAccountName() : null)
                .amount(t.getAmount())
                .transactionDate(t.getTransactionDate())
                .status(t.getStatus())
                .payee(t.getPayee())
                .categoryId(t.getCategory() != null ? t.getCategory().getId() : null)
                .categoryName(t.getCategory() != null ? t.getCategory().getFullName() : null)
                .memo(t.getMemo())
                .tags(t.getTags())
                .number(t.getNumber())
                .paymentMethod(t.getPaymentMethod())
                .assetId(t.getAsset() != null ? t.getAsset().getId() : null)
                .assetName(t.getAsset() != null ? t.getAsset().getName() : null)
                .units(t.getUnits())
                .sortOrder(t.getSortOrder())
                .build();
    }

    public static CategoryDTO toCategoryDTO(Category c) {
        return CategoryDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .fullName(c.getFullName())
                .type(c.getType())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .parentName(c.getParent() != null ? c.getParent().getName() : null)
                .build();
    }

    public static PayeeDTO toPayeeDTO(Payee p) {
        return PayeeDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .notes(p.getNotes())
                .defaultCategoryId(p.getDefaultCategory() != null ? p.getDefaultCategory().getId() : null)
                .defaultCategoryName(p.getDefaultCategory() != null ? p.getDefaultCategory().getFullName() : null)
                .defaultPaymentMethod(p.getDefaultPaymentMethod())
                .build();
    }

    public static TagDTO toTagDTO(Tag t) {
        return TagDTO.builder()
                .id(t.getId())
                .name(t.getName())
                .build();
    }

    public static AssetDTO toAssetDTO(Asset a) {
        return AssetDTO.builder()
                .id(a.getId())
                .symbol(a.getSymbol())
                .name(a.getName())
                .type(a.getType())
                .currentPrice(a.getCurrentPrice())
                .currency(a.getCurrency())
                .lastUpdate(a.getLastUpdate())
                .build();
    }

    public static CurrencyDTO toCurrencyDTO(Currency c) {
        return CurrencyDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .symbol(c.getSymbol())
                .decimalChar(c.getDecimalChar())
                .fracDigits(c.getFracDigits())
                .groupingChar(c.getGroupingChar())
                .build();
    }

    public static ScheduledTransactionDTO toScheduledTransactionDTO(ScheduledTransaction s) {
        return ScheduledTransactionDTO.builder()
                .id(s.getId())
                .type(s.getType())
                .fromAccountId(s.getFromAccount() != null ? s.getFromAccount().getId() : null)
                .fromAccountName(s.getFromAccount() != null ? s.getFromAccount().getAccountName() : null)
                .toAccountId(s.getToAccount() != null ? s.getToAccount().getId() : null)
                .toAccountName(s.getToAccount() != null ? s.getToAccount().getAccountName() : null)
                .amount(s.getAmount())
                .payee(s.getPayee())
                .categoryId(s.getCategory() != null ? s.getCategory().getId() : null)
                .categoryName(s.getCategory() != null ? s.getCategory().getFullName() : null)
                .memo(s.getMemo())
                .tags(s.getTags())
                .number(s.getNumber())
                .paymentMethod(s.getPaymentMethod())
                .assetId(s.getAsset() != null ? s.getAsset().getId() : null)
                .assetName(s.getAsset() != null ? s.getAsset().getName() : null)
                .units(s.getUnits())
                .recurrencePattern(s.getRecurrencePattern())
                .recurrenceValue(s.getRecurrenceValue())
                .nextOccurrence(s.getNextOccurrence())
                .enabled(s.isEnabled())
                .build();
    }

    public static UserProfileDTO toUserProfileDTO(User u) {
        return UserProfileDTO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .defaultCurrency(u.getDefaultCurrency())
                .darkMode(u.isDarkMode())
                .locale(u.getLocale())
                .apiEnabled(u.isApiEnabled())
                .roles(u.getRoles())
                .build();
    }
}
