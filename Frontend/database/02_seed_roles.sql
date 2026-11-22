-- Essential system roles only.
-- Idempotent: safe to run more than once. Does not create users or passwords.

INSERT INTO roles (name)
SELECT seed.name
FROM (
    SELECT 'CUSTOMER' AS name
    UNION ALL
    SELECT 'STAFF'
    UNION ALL
    SELECT 'ADMIN'
) AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM roles existing
    WHERE existing.name = seed.name
);
