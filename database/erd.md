# DatSellerShop Entity-Relationship Diagram

Official schema: 20 active tables. Legacy tables `user`, `customer`, and `category` are not part of this model.

## Relationship summary

| Relationship | Cardinality | Notes |
| --- | --- | --- |
| User ↔ Role | M:N | Join table `user_roles` with composite primary key `(user_id, role_id)` |
| User → Address | 1:N | One default address per user is application-managed |
| Category → Category | 1:N | Optional `parent_id`. Cycle prevention is application-managed |
| Category → Product | 1:N | Required |
| Brand → Product | 1:N | Required |
| Product → ProductVariant | 1:N | Stock lives on the variant, not the product |
| Product → ProductImage | 1:N | One primary image per product is application-managed |
| Size → ProductVariant | 1:N | `size_id` nullable |
| Color → ProductVariant | 1:N | `color_id` nullable |
| User → Cart | 1:1 | Unique `carts.user_id` |
| Cart → CartItem | 1:N | Unique `(cart_id, product_variant_id)` |
| ProductVariant → CartItem | 1:N | Live catalog FK |
| User → Wishlist | 1:1 | Unique `wishlists.user_id` |
| Wishlist → WishlistItem | 1:N | Unique `(wishlist_id, product_id)` |
| Product → WishlistItem | 1:N | Wishlist stores Product, not ProductVariant |
| User → Order | 1:N | Historical; FK is conservative (`NO ACTION`) |
| Order → OrderItem | 1:N | Line items are snapshots |
| Voucher → Order | 1:N | `orders.voucher_id` nullable; voucher fields are also snapshotted |
| Order → Payment | 1:0..1 | Unique `payments.order_id` |
| User → ProductReview | 1:N | Unique `(user_id, product_id)` |
| Product → ProductReview | 1:N | |

`OrderItem.product_id` and `OrderItem.variant_id` are historical scalars. They are not catalog foreign keys, so an order remains readable after catalog changes.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : assigned
    users ||--o{ addresses : has
    users ||--o| carts : owns
    users ||--o| wishlists : owns
    users ||--o{ orders : places
    users ||--o{ product_reviews : writes

    categories ||--o{ categories : parent
    categories ||--o{ products : contains
    brands ||--o{ products : owns
    products ||--o{ product_images : has
    products ||--o{ product_variants : has
    products ||--o{ wishlist_items : saved_in
    products ||--o{ product_reviews : receives
    sizes ||--o{ product_variants : optional
    colors ||--o{ product_variants : optional

    carts ||--o{ cart_items : contains
    product_variants ||--o{ cart_items : added_as
    wishlists ||--o{ wishlist_items : contains

    vouchers ||--o{ orders : applied_to
    orders ||--|{ order_items : contains
    orders ||--o| payments : paid_by

    users {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone_number
        enum status
        datetime created_at
        datetime updated_at
    }

    roles {
        bigint id PK
        enum name UK
    }

    user_roles {
        bigint user_id PK,FK
        bigint role_id PK,FK
    }

    addresses {
        bigint id PK
        bigint user_id FK
        varchar recipient_name
        varchar phone_number
        varchar province
        varchar district
        varchar ward
        varchar address_line
        bit is_default
    }

    categories {
        bigint id PK
        varchar name
        varchar slug UK
        bigint parent_id FK
        enum status
    }

    brands {
        bigint id PK
        varchar name UK
        varchar slug UK
        enum status
    }

    sizes {
        bigint id PK
        varchar name UK
        int display_order
        bit active
    }

    colors {
        bigint id PK
        varchar name UK
        varchar hex_code
        bit active
    }

    products {
        bigint id PK
        varchar name
        varchar slug UK
        decimal price
        decimal sale_price
        bigint category_id FK
        bigint brand_id FK
        enum status
        bit featured
    }

    product_images {
        bigint id PK
        bigint product_id FK
        varchar image_url
        varchar alt_text
        bit is_primary
        int sort_order
    }

    product_variants {
        bigint id PK
        bigint product_id FK
        bigint size_id FK
        bigint color_id FK
        varchar sku UK
        int stock_quantity
        bit active
    }

    carts {
        bigint id PK
        bigint user_id UK,FK
    }

    cart_items {
        bigint id PK
        bigint cart_id FK
        bigint product_variant_id FK
        int quantity
    }

    wishlists {
        bigint id PK
        bigint user_id UK,FK
    }

    wishlist_items {
        bigint id PK
        bigint wishlist_id FK
        bigint product_id FK
    }

    vouchers {
        bigint id PK
        varchar code UK
        enum type
        decimal discount_value
        decimal min_order_amount
        decimal max_discount_amount
        int usage_limit
        int used_count
        datetime start_at
        datetime end_at
        bit active
    }

    orders {
        bigint id PK
        varchar order_number UK
        bigint user_id FK
        enum status
        decimal subtotal
        decimal discount_amount
        decimal shipping_fee
        decimal total_amount
        bigint voucher_id FK
        varchar recipient_name
        varchar shipping_address_line
    }

    order_items {
        bigint id PK
        bigint order_id FK
        bigint product_id
        bigint variant_id
        varchar product_name
        varchar sku
        decimal unit_price
        int quantity
        decimal line_total
    }

    payments {
        bigint id PK
        varchar payment_reference UK
        bigint order_id UK,FK
        enum method
        enum status
        decimal amount
        datetime paid_at
    }

    product_reviews {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        int rating
        varchar comment
        enum status
    }
```
