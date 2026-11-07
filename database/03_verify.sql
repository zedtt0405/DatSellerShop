-- Read-only schema verification. Does not insert, update, or delete data.

SELECT DATABASE() AS current_database;

SHOW TABLES;

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

SELECT COUNT(*) AS active_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN (
        'roles', 'users', 'user_roles', 'addresses',
        'categories', 'brands', 'sizes', 'colors',
        'products', 'product_images', 'product_variants',
        'carts', 'cart_items',
        'wishlists', 'wishlist_items',
        'vouchers',
        'orders', 'order_items',
        'payments',
        'product_reviews'
  );

SELECT name, COUNT(*) AS role_count
FROM roles
GROUP BY name
ORDER BY name;

SELECT name
FROM roles
ORDER BY name;

SELECT CONSTRAINT_NAME, TABLE_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_TYPE IN ('UNIQUE', 'FOREIGN KEY', 'CHECK')
ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN (
        'price', 'sale_price',
        'discount_value', 'min_order_amount', 'max_discount_amount',
        'subtotal', 'discount_amount', 'shipping_fee', 'total_amount',
        'voucher_discount_value',
        'unit_price', 'line_total',
        'amount'
  )
ORDER BY TABLE_NAME, COLUMN_NAME;

SELECT INDEX_NAME, TABLE_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN (
        'idx_users_email',
        'idx_users_phone_number',
        'idx_users_status',
        'idx_products_category_id',
        'idx_products_brand_id',
        'idx_product_variants_product_id',
        'idx_cart_items_cart_id',
        'idx_wishlist_items_wishlist_id',
        'idx_orders_user_id',
        'idx_orders_created_at',
        'idx_order_items_order_id',
        'idx_product_reviews_product_id_status',
        'uk_carts_user_id',
        'uk_cart_items_cart_id_product_variant_id',
        'uk_wishlists_user_id',
        'uk_wishlist_items_wishlist_id_product_id',
        'uk_payments_payment_reference',
        'uk_payments_order_id',
        'uk_product_reviews_user_id_product_id'
  )
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;
