-- Keep the role used by the existing default-login priority for legacy accounts.
-- Customers continue to have no staff-role row.
CREATE TEMPORARY TABLE accounts_with_multiple_roles ON COMMIT DROP AS
SELECT user_id
FROM user_roles
GROUP BY user_id
HAVING COUNT(*) > 1;

WITH ranked_roles AS (
    SELECT ur.user_id, ur.role_id,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_id
               ORDER BY CASE r.code
                   WHEN 'ADMIN' THEN 1
                   WHEN 'MANAGER' THEN 2
                   WHEN 'RECEPTIONIST' THEN 3
                   WHEN 'HOUSEKEEPER' THEN 4
                   ELSE 5
               END, ur.role_id
           ) AS position
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
)
DELETE FROM user_roles ur
USING ranked_roles ranked
WHERE ur.user_id = ranked.user_id
  AND ur.role_id = ranked.role_id
  AND ranked.position > 1;

UPDATE refresh_tokens
SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL
  AND user_id IN (SELECT user_id FROM accounts_with_multiple_roles);

ALTER TABLE user_roles
    ADD CONSTRAINT uq_user_roles_user_id UNIQUE (user_id);
