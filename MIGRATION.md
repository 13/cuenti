# Cuenti Migration Plan: cuenti → New Project

## Status: ✅ COMPLETE (Backend Core)

The backend core has been successfully ported from `cuenti` to the new project skeleton.

---

## What Was Ported

### Entities (10 total)
| Entity | Status | Notes |
|--------|--------|-------|
| User | ✅ Ported | Enhanced with locale, darkMode, apiEnabled, profilePicture |
| Account | ✅ Ported | Bank accounts with balance tracking |
| Transaction | ✅ Ported | Income, expense, transfer with balance updates |
| Category | ✅ Ported | Hierarchical categories |
| Asset | ✅ Ported | Stocks, ETFs, crypto tracking |
| Payee | ✅ Ported | Transaction partners |
| Tag | ✅ Ported | Flexible labeling |
| Currency | ✅ Ported | Multi-currency support |
| ScheduledTransaction | ✅ Ported | Recurring transactions |
| GlobalSetting | ✅ Ported | System-wide settings |

### Repositories (10 total)
All repositories ported with custom query methods.

### Services (12 total)
| Service | Status | Notes |
|---------|--------|-------|
| UserService | ✅ Ported | Implements UserDetailsService for auth |
| AccountService | ✅ Ported | Account CRUD with balance management |
| TransactionService | ✅ Ported | Transaction CRUD with balance updates |
| CategoryService | ✅ Ported | Category management |
| AssetService | ✅ Ported | Asset management with Yahoo Finance price fetch |
| PayeeService | ✅ Ported | Payee management |
| TagService | ✅ Ported | Tag management |
| CurrencyService | ✅ Ported | Currency management |
| ScheduledTransactionService | ✅ Ported | Recurring transaction processing |
| GlobalSettingService | ✅ Ported | System settings |
| ExchangeRateService | ✅ Ported | Currency conversion via Yahoo Finance |
| SecurityUtil | ✅ Ported | Static auth helper |

### Security
| Component | Status |
|-----------|--------|
| SecurityConfiguration | ✅ Updated with API security |
| SecurityUtils | ✅ Created - Vaadin auth context |
| AuthenticatedUser | ✅ Updated |

### API Controllers
| Endpoint | Status |
|----------|--------|
| TransactionApiController | ✅ Ported |

### Configuration
| Component | Status |
|-----------|--------|
| TranslationProvider | ✅ Created |
| messages.properties | ✅ Created |
| application.properties | ✅ Updated |

### Data Initialization
| Component | Status |
|-----------|--------|
| DataInitializer | ✅ Created with demo data |

---

## Files Removed (Obsolete)
- `SamplePerson.java`
- `SamplePersonRepository.java`
- `SamplePersonService.java`
- `Role.java`
- `AbstractEntity.java`
- `UserDetailsServiceImpl.java`
- `data.sql`

---

## Remaining Work (Views)

The following views need full business logic porting:

| View | Priority | Status |
|------|----------|--------|
| DashboardView | High | Skeleton only |
| TransactionsView | High | Skeleton only |
| RegisterView | High | Not created |
| AccountsView | Medium | Not created |
| CategoriesView | Medium | Not created |
| PayeesView | Medium | Not created |
| TagsView | Low | Not created |
| CurrenciesView | Low | Not created |
| ScheduledView | Medium | Not created |
| SettingsView | Medium | Not created |
| StatisticsView | Low | Not created |

---

## Run Instructions

```bash
# Build the project
./mvnw -DskipTests clean package

# Run in development mode
./mvnw spring-boot:run

# Access the application
open http://localhost:8080

# Demo credentials
# Username: demo
# Password: demo123
```

---

## API Smoke Tests

```bash
# Get transactions (requires Basic Auth)
curl -u demo:demo123 -X GET http://localhost:8080/api/transactions

# Create a transaction
curl -u demo:demo123 -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{"type":"EXPENSE","amount":50.00,"payee":"Test"}'
```

---

## PR Description

### Summary
Port backend core functionality from `cuenti` project to new Vaadin 25/Spring Boot 4 skeleton.

### Changes
- **Entities**: 10 JPA entities for homebanking domain model
- **Repositories**: 10 Spring Data repositories with custom queries
- **Services**: 12 services with business logic and security checks
- **Security**: API security with Basic Auth, Vaadin security integration
- **i18n**: Translation support with German/English
- **Demo Data**: DataInitializer creates sample user and data

### Dependencies Added
- Lombok (annotation processor configured)
- Jackson (JSON processing)
- PostgreSQL driver (production)

### Testing
- Builds successfully with `./mvnw -DskipTests package`
- Application starts and creates demo data
- Demo login: `demo` / `demo123`

### Remaining Work
- Port view business logic (DashboardView, TransactionHistoryView, etc.)
- Add registration view
- Port remaining API controllers (JsonExportImport)
- Add integration tests
