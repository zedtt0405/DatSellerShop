# DatSellerShop official database package

This folder is the reproducible MySQL baseline for the backend.

A new developer can create an empty MySQL 8 database, run these scripts, configure local credentials, and start Spring Boot without Hibernate inventing the initial schema.

## Requirements

- MySQL 8.x (developed against 8.4)
- Character set `utf8mb4`
- Collation `utf8mb4_unicode_ci`
- InnoDB

Do not put MySQL passwords, JWT secrets, or other credentials in these SQL files.

## Execution order

1. Install MySQL 8.x.
2. Execute `00_create_database.sql` as a MySQL administrator. This creates `smart_fashion_shop` if it does not already exist and selects it.
3. Execute `01_schema.sql` against that **empty** database. It creates the 20 active tables.
4. Execute `02_seed_roles.sql`. It inserts `CUSTOMER`, `STAFF`, and `ADMIN` if they are missing.
5. Execute `03_verify.sql` and confirm 20 application tables plus the three roles.
6. Configure local Spring Boot credentials in `backend/DatShop/application-local.properties` (gitignored). Set at least:
   - `spring.datasource.url`
   - `spring.datasource.username`
   - `spring.datasource.password`
   - `app.jwt.secret`
7. Start the backend from `backend/DatShop`.

Example (adjust the MySQL client invocation for your machine; do not put a password on the command line if you can avoid it):

```bash
mysql -u root -p < 00_create_database.sql
mysql -u root -p smart_fashion_shop < 01_schema.sql
mysql -u root -p smart_fashion_shop < 02_seed_roles.sql
mysql -u root -p smart_fashion_shop < 03_verify.sql
```

`01_schema.sql` does not contain `USE smart_fashion_shop`, so the same file can be applied to a temporary verification database.

## Hibernate relationship

Current local development still uses:

```properties
spring.jpa.hibernate.ddl-auto=update
```

That setting was used while the working database grew from JPA entities. It is **not** the source of truth for a new clone.

For a database built from this package, Hibernate should validate the existing schema rather than change it:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

This package does not introduce Flyway or Liquibase. Those can be added later if deployment needs versioned migrations.

Do not rely on `ddl-auto=update` to invent missing columns after running `01_schema.sql`. If validation fails, fix the SQL.

`RoleDataInitializer` also inserts the three roles on application startup if they are absent. `02_seed_roles.sql` is still required so a database built from SQL is complete before the first boot.

## Active tables (20)

| Table | Purpose |
| --- | --- |
| `roles` | System roles: CUSTOMER, STAFF, ADMIN |
| `users` | Accounts; stores BCrypt `password_hash`, never plaintext |
| `user_roles` | User ↔ Role assignment |
| `addresses` | Customer shipping addresses |
| `categories` | Catalog tree with optional parent |
| `brands` | Product brands |
| `sizes` | Variant sizes |
| `colors` | Variant colors |
| `products` | Catalog products; price lives here, stock does not |
| `product_images` | Product image URLs (no BLOB) |
| `product_variants` | SKU + stock + optional size/color |
| `carts` | One cart per user |
| `cart_items` | Cart lines against a variant |
| `wishlists` | One wishlist per user |
| `wishlist_items` | Wishlist lines against a product |
| `vouchers` | Discount codes |
| `orders` | Historical order documents, including shipping snapshots |
| `order_items` | Historical line snapshots |
| `payments` | One payment per order |
| `product_reviews` | One review per user per product |

## Legacy tables

The current working database `smart_fashion_shop` may still contain:

- `user`
- `customer`
- `category`

They are leftover names from earlier iterations. They are **not** in `01_schema.sql`. This package does not drop them from the existing working database.

## Role seed

`02_seed_roles.sql` inserts only:

- `CUSTOMER`
- `STAFF`
- `ADMIN`

It is idempotent. It does not create admin/admin, customer accounts, products, orders, payments, or reviews.

## Foreign keys

All foreign keys use InnoDB default `NO ACTION` / `RESTRICT` behavior. There is no `ON DELETE CASCADE`.

Historical rows (`orders`, `order_items`, `payments`) must not disappear because catalog or account data later changes.

`order_items.product_id` and `order_items.variant_id` are snapshot integers, not catalog foreign keys.

`orders` stores shipping recipient/address snapshots. It does not reference `addresses`.

## Application-level invariants

These rules are enforced in services / bean validation, not fully as database constraints:

- **One default address per user.** `addresses.is_default` exists, but the database does not require exactly one default row per user.
- **One primary product image per product.** `product_images.is_primary` exists, but uniqueness of the primary flag is application-managed.
- **Category trees cannot contain cycles.** `categories.parent_id` may be null; cycle checks stay in the application.
- **Variant uniqueness for nullable size/color.** There is no `UNIQUE(product_id, size_id, color_id)` because MySQL `NULL` does not treat two `(product, NULL, NULL)` rows as duplicates. Semantic uniqueness is enforced in the service.
- **`sale_price <= price`.** Both columns are `DECIMAL(15,2)`; the comparison is application-managed.
- **Voucher validity.** Active flag, date window, usage limit, min order amount, and percent/fixed rules are checked in voucher/checkout services. There is no `voucher_usages` table.
- **Stock checkout.** `product_variants.stock_quantity >= 0` is a CHECK constraint. Decrementing stock, locking order, and preventing oversell are transactional application rules.
- **Cart totals are not persisted.** Subtotal, discount, and shipping are computed, not stored on `carts` / `cart_items`.
- **One default-style “one review per purchased product” business rule** may be stricter than the database unique key `(user_id, product_id)`, which only prevents duplicate review rows.

Database CHECKs that *are* present:

- `product_variants.stock_quantity >= 0`
- `cart_items.quantity >= 1`
- `order_items.quantity >= 1`
- `product_reviews.rating` between 1 and 5

## Money columns

All money fields are `DECIMAL(15,2)`:

- `products.price`, `products.sale_price`
- `vouchers.discount_value`, `vouchers.min_order_amount`, `vouchers.max_discount_amount`
- `orders.subtotal`, `orders.discount_amount`, `orders.shipping_fee`, `orders.total_amount`, `orders.voucher_discount_value`
- `order_items.unit_price`, `order_items.line_total`
- `payments.amount`

Do not use `FLOAT` or `DOUBLE` for money.

## Existing working database

Do not drop `smart_fashion_shop` to apply this package. That database already contains working application data.

Use these scripts on:

- a new empty clone database, or
- a clearly named temporary database such as `smart_fashion_shop_schema_test`

## Collation note

This package uses `utf8mb4_unicode_ci`. A database that Hibernate created incrementally may currently use MySQL 8 default `utf8mb4_0900_ai_ci`. Column types and constraints are the same; only the database/table collation may differ.
