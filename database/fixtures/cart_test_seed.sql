-- Cart integration fixture for smart_fashion_shop_cart_test only.
-- Fail closed: a wrong database raises an error and every INSERT also requires that database.
-- Idempotent: existing usernames, slugs, and SKUs are left unchanged.
-- Does not delete rows. Does not select or modify any other database.
-- Apply with the target database already selected:
--   mysql ... smart_fashion_shop_cart_test < database/fixtures/cart_test_seed.sql

SET @cart_test_seed_ok = IF(DATABASE() = 'smart_fashion_shop_cart_test', 1, 0);
SET @cart_test_seed_guard = IF(
  @cart_test_seed_ok = 1,
  'DO 0',
  'SELECT `cart_test_seed_refused_wrong_database`.`id` FROM `cart_test_seed_refused_wrong_database`'
);
PREPARE cart_test_seed_guard FROM @cart_test_seed_guard;
EXECUTE cart_test_seed_guard;
DEALLOCATE PREPARE cart_test_seed_guard;

INSERT INTO users (created_at, updated_at, username, email, password_hash, full_name, phone_number, status)
SELECT NOW(6), NOW(6), 'carttest_customer_a', 'carttest_customer_a@example.com', '$2a$10$JSlK2brOfMlHBDJPhm7BGuSZIT0yYNwXukyaHtXLyw7rZtYI3N6g2', 'Cart Test A', NULL, 'ACTIVE'
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'carttest_customer_a' OR email = 'carttest_customer_a@example.com');

INSERT INTO users (created_at, updated_at, username, email, password_hash, full_name, phone_number, status)
SELECT NOW(6), NOW(6), 'carttest_customer_b', 'carttest_customer_b@example.com', '$2a$10$JSlK2brOfMlHBDJPhm7BGuSZIT0yYNwXukyaHtXLyw7rZtYI3N6g2', 'Cart Test B', NULL, 'ACTIVE'
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'carttest_customer_b' OR email = 'carttest_customer_b@example.com');

INSERT INTO users (created_at, updated_at, username, email, password_hash, full_name, phone_number, status)
SELECT NOW(6), NOW(6), 'carttest_staff', 'carttest_staff@example.com', '$2a$10$JSlK2brOfMlHBDJPhm7BGuSZIT0yYNwXukyaHtXLyw7rZtYI3N6g2', 'Cart Test Staff', NULL, 'ACTIVE'
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'carttest_staff' OR email = 'carttest_staff@example.com');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'CUSTOMER'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND u.username IN ('carttest_customer_a', 'carttest_customer_b')
  AND NOT EXISTS (
    SELECT 1 FROM user_roles existing WHERE existing.user_id = u.id AND existing.role_id = r.id
  );

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'STAFF'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND u.username = 'carttest_staff'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles existing WHERE existing.user_id = u.id AND existing.role_id = r.id
  );

INSERT INTO categories (created_at, updated_at, name, slug, description, status, parent_id)
SELECT NOW(6), NOW(6), 'Cart Test Tees', 'carttest-tees', NULL, 'ACTIVE', NULL
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'carttest-tees');

INSERT INTO brands (created_at, updated_at, name, slug, description, logo_url, status)
SELECT NOW(6), NOW(6), 'Cart Test Brand', 'carttest-brand', NULL, NULL, 'ACTIVE'
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM brands WHERE slug = 'carttest-brand' OR name = 'Cart Test Brand');

INSERT INTO sizes (name, display_order, active)
SELECT 'CT-M', 1, 1
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM sizes WHERE name = 'CT-M');

INSERT INTO sizes (name, display_order, active)
SELECT 'CT-L', 2, 1
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM sizes WHERE name = 'CT-L');

INSERT INTO colors (name, hex_code, active)
SELECT 'CT-Black', '#111111', 1
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM colors WHERE name = 'CT-Black');

INSERT INTO colors (name, hex_code, active)
SELECT 'CT-White', '#FFFFFF', 1
FROM DUAL
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND NOT EXISTS (SELECT 1 FROM colors WHERE name = 'CT-White');

INSERT INTO products (
  created_at, updated_at, name, slug, description, price, sale_price, category_id, brand_id, status, featured
)
SELECT NOW(6), NOW(6), 'Cart Test Tee', 'carttest-tee', 'Public cart test product', 500000.00, 400000.00, c.id, b.id, 'ACTIVE', 0
FROM categories c
JOIN brands b ON b.slug = 'carttest-brand'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND c.slug = 'carttest-tees'
  AND NOT EXISTS (SELECT 1 FROM products WHERE slug = 'carttest-tee');

INSERT INTO products (
  created_at, updated_at, name, slug, description, price, sale_price, category_id, brand_id, status, featured
)
SELECT NOW(6), NOW(6), 'Cart Test Draft', 'carttest-draft', 'Draft cart test product', 100000.00, NULL, c.id, b.id, 'DRAFT', 0
FROM categories c
JOIN brands b ON b.slug = 'carttest-brand'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND c.slug = 'carttest-tees'
  AND NOT EXISTS (SELECT 1 FROM products WHERE slug = 'carttest-draft');

INSERT INTO product_images (product_id, image_url, alt_text, is_primary, sort_order)
SELECT p.id, 'https://example.test/carttest-tee.jpg', 'Cart Test Tee', 1, 1
FROM products p
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND p.slug = 'carttest-tee'
  AND NOT EXISTS (
    SELECT 1 FROM product_images existing WHERE existing.product_id = p.id AND existing.is_primary = 1
  );

INSERT INTO product_variants (created_at, updated_at, product_id, size_id, color_id, sku, stock_quantity, active)
SELECT NOW(6), NOW(6), p.id, s.id, c.id, 'CT-M-BLACK', 10, 1
FROM products p
JOIN sizes s ON s.name = 'CT-M'
JOIN colors c ON c.name = 'CT-Black'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND p.slug = 'carttest-tee'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'CT-M-BLACK');

INSERT INTO product_variants (created_at, updated_at, product_id, size_id, color_id, sku, stock_quantity, active)
SELECT NOW(6), NOW(6), p.id, s.id, c.id, 'CT-L-BLACK', 8, 1
FROM products p
JOIN sizes s ON s.name = 'CT-L'
JOIN colors c ON c.name = 'CT-Black'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND p.slug = 'carttest-tee'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'CT-L-BLACK');

INSERT INTO product_variants (created_at, updated_at, product_id, size_id, color_id, sku, stock_quantity, active)
SELECT NOW(6), NOW(6), p.id, s.id, c.id, 'CT-M-WHITE', 0, 1
FROM products p
JOIN sizes s ON s.name = 'CT-M'
JOIN colors c ON c.name = 'CT-White'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND p.slug = 'carttest-tee'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'CT-M-WHITE');

INSERT INTO product_variants (created_at, updated_at, product_id, size_id, color_id, sku, stock_quantity, active)
SELECT NOW(6), NOW(6), p.id, s.id, c.id, 'CT-INACTIVE', 5, 0
FROM products p
JOIN sizes s ON s.name = 'CT-L'
JOIN colors c ON c.name = 'CT-White'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND p.slug = 'carttest-tee'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'CT-INACTIVE');

INSERT INTO product_variants (created_at, updated_at, product_id, size_id, color_id, sku, stock_quantity, active)
SELECT NOW(6), NOW(6), p.id, s.id, c.id, 'CT-DRAFT', 5, 1
FROM products p
JOIN sizes s ON s.name = 'CT-M'
JOIN colors c ON c.name = 'CT-Black'
WHERE @cart_test_seed_ok = 1
  AND DATABASE() = 'smart_fashion_shop_cart_test'
  AND p.slug = 'carttest-draft'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'CT-DRAFT');
