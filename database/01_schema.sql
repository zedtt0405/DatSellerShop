-- DatSellerShop official schema for the 20 active application tables.
-- Derived from current JPA entities and verified against the live MySQL schema.
-- Apply to an empty database (after 00_create_database.sql, or a temporary clone database).
-- Does not DROP objects. Does not create legacy tables: user, customer, category.

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name ENUM('CUSTOMER', 'STAFF', 'ADMIN') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 2. users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(150) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_email (email),
    KEY idx_users_phone_number (phone_number),
    KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. user_roles
-- ---------------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. addresses
-- ---------------------------------------------------------------------------
CREATE TABLE addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    is_default BIT(1) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_addresses_user_id (user_id),
    CONSTRAINT fk_addresses_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. categories
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL,
    parent_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_slug (slug),
    KEY idx_categories_parent_id (parent_id),
    CONSTRAINT fk_categories_parent_id FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. brands
-- ---------------------------------------------------------------------------
CREATE TABLE brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    logo_url VARCHAR(500) NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_name (name),
    UNIQUE KEY uk_brands_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. sizes
-- ---------------------------------------------------------------------------
CREATE TABLE sizes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    display_order INT NOT NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sizes_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. colors
-- ---------------------------------------------------------------------------
CREATE TABLE colors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    hex_code VARCHAR(7) NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_colors_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 9. products
-- ---------------------------------------------------------------------------
CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description VARCHAR(5000) NULL,
    price DECIMAL(15,2) NOT NULL,
    sale_price DECIMAL(15,2) NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'DRAFT') NOT NULL,
    featured BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_slug (slug),
    KEY idx_products_category_id (category_id),
    KEY idx_products_brand_id (brand_id),
    CONSTRAINT fk_products_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand_id FOREIGN KEY (brand_id) REFERENCES brands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 10. product_images
-- ---------------------------------------------------------------------------
CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(200) NULL,
    is_primary BIT(1) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_images_product_id (product_id),
    CONSTRAINT fk_product_images_product_id FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 11. product_variants
-- ---------------------------------------------------------------------------
CREATE TABLE product_variants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT NOT NULL,
    size_id BIGINT NULL,
    color_id BIGINT NULL,
    sku VARCHAR(64) NOT NULL,
    stock_quantity INT NOT NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_variants_sku (sku),
    KEY idx_product_variants_product_id (product_id),
    KEY idx_product_variants_size_id (size_id),
    KEY idx_product_variants_color_id (color_id),
    CONSTRAINT fk_product_variants_product_id FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_variants_size_id FOREIGN KEY (size_id) REFERENCES sizes (id),
    CONSTRAINT fk_product_variants_color_id FOREIGN KEY (color_id) REFERENCES colors (id),
    CONSTRAINT chk_product_variants_stock_quantity CHECK (stock_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 12. carts
-- ---------------------------------------------------------------------------
CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_user_id (user_id),
    CONSTRAINT fk_carts_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 13. cart_items
-- ---------------------------------------------------------------------------
CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    cart_id BIGINT NOT NULL,
    product_variant_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_cart_id_product_variant_id (cart_id, product_variant_id),
    KEY idx_cart_items_cart_id (cart_id),
    KEY idx_cart_items_product_variant_id (product_variant_id),
    CONSTRAINT fk_cart_items_cart_id FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_product_variant_id FOREIGN KEY (product_variant_id) REFERENCES product_variants (id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 14. wishlists
-- ---------------------------------------------------------------------------
CREATE TABLE wishlists (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlists_user_id (user_id),
    CONSTRAINT fk_wishlists_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 15. wishlist_items
-- ---------------------------------------------------------------------------
CREATE TABLE wishlist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    wishlist_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlist_items_wishlist_id_product_id (wishlist_id, product_id),
    KEY idx_wishlist_items_wishlist_id (wishlist_id),
    KEY idx_wishlist_items_product_id (product_id),
    CONSTRAINT fk_wishlist_items_wishlist_id FOREIGN KEY (wishlist_id) REFERENCES wishlists (id),
    CONSTRAINT fk_wishlist_items_product_id FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 16. vouchers
-- ---------------------------------------------------------------------------
CREATE TABLE vouchers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    code VARCHAR(40) NOT NULL,
    type ENUM('PERCENT', 'FIXED') NOT NULL,
    discount_value DECIMAL(15,2) NOT NULL,
    min_order_amount DECIMAL(15,2) NULL,
    max_discount_amount DECIMAL(15,2) NULL,
    usage_limit INT NULL,
    used_count INT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vouchers_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 17. orders
-- Historical document. Shipping fields are snapshots, not an Address FK.
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED') NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    discount_amount DECIMAL(15,2) NOT NULL,
    shipping_fee DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    voucher_id BIGINT NULL,
    voucher_code VARCHAR(40) NULL,
    voucher_type ENUM('PERCENT', 'FIXED') NULL,
    voucher_discount_value DECIMAL(15,2) NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_province VARCHAR(100) NOT NULL,
    shipping_district VARCHAR(100) NOT NULL,
    shipping_ward VARCHAR(100) NOT NULL,
    shipping_address_line VARCHAR(255) NOT NULL,
    customer_note VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_created_at (created_at),
    KEY idx_orders_voucher_id (voucher_id),
    CONSTRAINT fk_orders_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_voucher_id FOREIGN KEY (voucher_id) REFERENCES vouchers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 18. order_items
-- Historical line snapshots. product_id / variant_id are scalars, not catalog FKs.
-- ---------------------------------------------------------------------------
CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_slug VARCHAR(220) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    size_name VARCHAR(30) NULL,
    color_name VARCHAR(50) NULL,
    product_image_url VARCHAR(500) NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order_id (order_id),
    CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 19. payments
-- One payment row per order.
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    payment_reference VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    method ENUM('COD', 'BANK_QR') NOT NULL,
    status ENUM('PENDING', 'PAID', 'FAILED', 'CANCELLED') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    qr_content VARCHAR(500) NULL,
    provider_reference VARCHAR(100) NULL,
    paid_at DATETIME(6) NULL,
    failure_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_payment_reference (payment_reference),
    UNIQUE KEY uk_payments_order_id (order_id),
    CONSTRAINT fk_payments_order_id FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 20. product_reviews
-- ---------------------------------------------------------------------------
CREATE TABLE product_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000) NULL,
    status ENUM('PUBLISHED', 'HIDDEN') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_reviews_user_id_product_id (user_id, product_id),
    KEY idx_product_reviews_product_id_status (product_id, status),
    CONSTRAINT fk_product_reviews_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_product_reviews_product_id FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_reviews_rating CHECK (rating >= 1 AND rating <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
