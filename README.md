# Invoice Vault & Warranty Tracker

An offline-first Android application designed to track personal purchases, archive receipts and tax invoices, automatically extract billing data using on-device OCR, and manage warranty lifecycles. Built with **Kotlin**, **Jetpack Compose (Material 3)**, **Google ML Kit Text Recognition**, and **Room Database**.

This is a collaborative 3-member major project structured around a unified relational database schema.

---

## Team Division & Modules

```
                    ┌─────────────────────────┐
                    │   InvoiceVaultDatabase  │
                    │       (Room v3)         │
                    └────────────┬────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
   ┌───────────┐           ┌───────────┐           ┌───────────┐
   │ Member 1  │           │ Member 2  │           │ Member 3  │
   │  Product  │           │  Invoice  │           │ Warranty  │
   │  Catalog  │           │   & OCR   │           │  Tracker  │
   └───────────┘           └───────────┘           └───────────┘
```

### Member 1: Product Catalog & Home Dashboard
- Core `Product` entity and `ProductDao`.
- `ProductRepository` with asynchronous Flow/Coroutine data streams.
- Live `HomeDashboard` with product metric cards, product listing, and CRUD dialogs.
- Baseline Room database setup and schema versioning (`MIGRATION_1_2`).

### Member 2: Invoice Management, Document Storage & OCR (Current Scope)
- **Database & Relations**:
  - `Invoice` entity referencing `products(productId)` with `CASCADE` delete.
  - 1-to-many relationship models (`ProductWithInvoices` and `InvoiceWithProduct`).
  - `InvoiceDao` and `InvoiceRepository` with full CRUD, search, and reactive Flow queries.
- **Local Document Storage (`InvoiceStorageManager`)**:
  - Secure sandboxed storage in internal app directory (`files/invoices/`) with UUID identifiers.
  - Native camera capture (`ActivityResultContracts.TakePicture()`) and multi-format document picker (`ActivityResultContracts.OpenDocument()`).
  - `FileProvider` setup for secure sharing and external viewing.
- **Dual-Format Document Preview (`InvoicePreviewHelper`)**:
  - In-app PDF first-page rendering via Android's native `PdfRenderer`.
  - Memory-safe downsampled bitmap decoding for images (`JPEG`, `PNG`, `WEBP`).
  - Full-screen external viewer launcher (`ACTION_VIEW`).
- **On-Device OCR & Data Extraction (`InvoiceOcrProcessor`)**:
  - Bundled **Google ML Kit Text Recognition** (`com.google.mlkit:text-recognition`) running 100% offline.
  - High-resolution (2048px) white-canvas rendering pipeline for PDF invoices prior to OCR analysis.
  - Automated extraction of:
    - **Invoice / Bill Number**: Regex pattern matchers for `INV-`, `Invoice #`, `Bill No`, `Order ID`.
    - **Purchase Date**: Multi-format date parsing (`dd/MM/yyyy`, `yyyy-MM-dd`, `MMM dd, yyyy`), normalized to ISO-8601 (`yyyy-MM-dd`).
    - **Seller / Merchant Name**: Header line analysis, vendor labels (`Sold by:`, `Merchant:`), and known retailer matching.
    - **Product Name**: Identification of electronics/consumer goods keywords and table line items.
    - **Total Amount**: Keyword and currency symbol parsing (`$`, `€`, `£`, `₹`, `Rs.`, `Total`, `Grand Total`, `Net Amount`).
    - **Warranty Information**: Automated detection of warranty durations (e.g. `1 Year Warranty`, `24 Months Guarantee`) converted into months.
- **Verification & Review Screen (`OcrReviewDialog`)**:
  - Displays color-coded confidence badges:
    - 🟢 **Verified by OCR** (High confidence keyword + pattern match)
    - 🟡 **Inferred** (Positional or heuristic match needing review)
    - 🔴 **Missing / Required** (Prompting user for manual input)
  - Inline document preview to easily cross-reference values with the physical paper bill.
  - Product linking: attach invoice to an existing product or 1-tap **"Create new product from this invoice"**.
  - Expandable **Raw OCR Text Inspector** to examine raw recognized text blocks.

### Member 3: Warranty Tracking & Notifications (Upcoming)
- Warranty lifecycle calculation using `Product.purchaseDate`, `warrantyDuration`, and `warrantyUnit`.
- Push notifications / alarm reminders for upcoming warranty expirations.

---

## Database Schema & Shared Contracts

### 1. `products` Table (Member 1)
| Column | Type | Description |
|---|---|---|
| `productId` | `INTEGER PRIMARY KEY AUTOINCREMENT` | Cross-module shared foreign key |
| `productName` | `TEXT` | Name of the product (indexed) |
| `brand` | `TEXT` | Brand / manufacturer |
| `category` | `TEXT` | Product category |
| `purchaseDate` | `TEXT` | ISO-8601 date (`yyyy-MM-dd`) |
| `purchasePrice` | `REAL` | Purchase amount |
| `sellerName` | `TEXT` | Retailer or merchant |
| `productDescription` | `TEXT` | Optional product notes |
| `productImageUri` | `TEXT` | Optional content URI |
| `warrantyDuration` | `INTEGER` | Duration count |
| `warrantyUnit` | `TEXT` | `DAYS`, `MONTHS`, or `YEARS` |

### 2. `invoices` Table (Member 2)
| Column | Type | Description |
|---|---|---|
| `invoiceId` | `INTEGER PRIMARY KEY AUTOINCREMENT` | Invoice record primary key |
| `productId` | `INTEGER NOT NULL` | Foreign key referencing `products(productId)` (ON DELETE CASCADE, indexed) |
| `invoiceNumber` | `TEXT NOT NULL` | Invoice / receipt identifier (indexed) |
| `purchaseDate` | `TEXT NOT NULL` | ISO-8601 date (`yyyy-MM-dd`) |
| `sellerName` | `TEXT NOT NULL` | Merchant or seller name |
| `totalAmount` | `REAL NOT NULL` | Total billed amount |
| `invoiceFileUri` | `TEXT NOT NULL` | Absolute local path to internal stored file |
| `invoiceType` | `TEXT NOT NULL` | `TAX_INVOICE`, `RECEIPT`, `WARRANTY_SLIP`, `REPAIR_BILL`, `OTHER` |
| `createdAt` | `INTEGER NOT NULL` | Epoch timestamp (milliseconds) |

### Non-Destructive Room Migrations
- **`MIGRATION_1_2`**: Added index on `products(productName)`.
- **`MIGRATION_2_3`**: Added `invoices` table, foreign key constraint to `products(productId)`, and indices on `productId` and `invoiceNumber`.

---

## Tech Stack & Dependencies

- **OS / Target**: Android 15 (API 35), Min SDK 26 (Android 8.0)
- **Language / Runtime**: Kotlin 2.0.21, Java 17
- **UI Framework**: Jetpack Compose with Material 3 and Compose Navigation
- **Architecture**: MVVM with Repository Pattern and Kotlin Coroutines / Flow
- **OCR Engine**: Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`)
- **Local Database**: AndroidX Room 2.6.1 + KSP code generation
- **Document Rendering**: Built-in Android `PdfRenderer` + `BitmapFactory`
- **Security**: AndroidX `FileProvider` for secure camera capture and external file opening

---

## Build, Run & Deployment Guide

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35 with build-tools
- JDK 17 configured in Android Studio (`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JDK`)

### Build & Run via Android Studio
1. Open the project in Android Studio.
2. Let Gradle sync and download dependencies.
3. Select an emulator (e.g. **Pixel 10 - API 35 / emulator-5554**) or a connected physical phone.
4. Click **Run** (`Shift + F10`).

---

## Testing OCR & Invoice Upload on Emulator / Phone

1. **Upload or Capture Invoice**:
   - In the app dashboard, tap **"Upload invoice"** (or tap **"Invoices"** on any existing product card $\rightarrow$ **"+ Add Invoice"**).
   - Tap **"📷 Camera"** to capture a picture of a receipt, or tap **"📁 Gallery / PDF"** to pick a receipt image or PDF file.
2. **Trigger On-Device OCR**:
   - Once the document is selected, the in-app preview card appears.
   - Tap **"✨ Auto-Extract with OCR"**.
   - Google ML Kit processes the image or renders the PDF in the background.
3. **Verify in the OCR Review Screen**:
   - The **"Verify OCR Extraction"** screen opens showing:
     - The document preview (tap **"Open Full View 🔍"** to zoom into the document).
     - Confidence status tag for each field:
       - 🟢 **Verified by OCR**: High confidence detection.
       - 🟡 **Inferred**: Positional match (verify details).
       - 🔴 **Not detected**: Enter the value manually.
     - Extracted values for **Product Name**, **Invoice Number**, **Purchase Date**, **Seller Name**, **Total Amount**, and **Warranty Duration**.
   - Tap **"▼ View Raw Extracted OCR Text"** to review the full text recognized by ML Kit.
4. **Save & Associate**:
   - Link the invoice to an existing registered product or check **"Create New Product"** to register a new product record simultaneously.
   - Tap **"Confirm & Save Invoice"**.
   - Verify that the invoice is saved in Room database, the document is safely archived in private storage, and the dashboard metrics update!
