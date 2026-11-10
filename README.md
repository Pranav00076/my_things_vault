# Invoice Vault & Warranty Tracker

Android starter project for the three-member major project. The repository initially contained only a placeholder README, so this commit establishes the Kotlin + Jetpack Compose + Room foundation.

## Member 1 modules in this starter

- Room-backed product catalog: `app/src/main/java/com/example/invoicevault/data/`
- Reusable `ProductRepository` with coroutine/Flow APIs
- Home dashboard with live product count and recent purchases
- Room migration registration with exported schema snapshots enabled

## Shared product contract

`Product.productId` is an auto-generated `Long` primary key and is the cross-module identifier. Invoice, warranty and service-history tables should store a `Long productId` foreign key to `products.productId`; do not create a second product identifier. The product entity currently contains:

| Field | Kotlin type | Storage/meaning |
|---|---|---|
| `productId` | `Long` | Primary key, auto-generated; use as the shared FK |
| `productName` | `String` | Product name |
| `brand` | `String` | Brand |
| `category` | `String` | Category |
| `purchaseDate` | `String` | ISO date (`yyyy-MM-dd`) |
| `purchasePrice` | `Double` | Purchase amount |
| `sellerName` | `String` | Seller |
| `productDescription` | `String` | Optional description (empty by default) |
| `productImageUri` | `String?` | Optional persisted content URI |
| `warrantyDuration` | `Int` | Duration count |
| `warrantyUnit` | `WarrantyUnit` | `DAYS`, `MONTHS` or `YEARS` |

The project owner confirmed that Members 2 and 3 have no objections to this contract. Use it consistently in the invoice, warranty and service-history modules. Keep a single Room database and add each module's entities/DAOs to it to preserve relational integrity. Before adding child tables, agree with those members on deletion behavior (cascade, restrict, or soft delete) so removing a product cannot unexpectedly remove records they need.

## Database changes

Room schema export is configured under `app/schemas`. Database version changes must include an explicit migration in `InvoiceVaultMigrations` and should never use destructive migration fallback. The included 1→2 migration demonstrates the additive index migration; retain older migration steps as the project evolves.

## Build setup

Open this directory in Android Studio and let Gradle sync. The app uses Kotlin, Jetpack Compose, Material 3, Room, KSP and Navigation Compose. Android SDK 35 and JDK 17 are expected. A Gradle wrapper was not present in the source repository.
