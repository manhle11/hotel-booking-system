ALTER TABLE payment_refunds
    ADD COLUMN transaction_id UUID UNIQUE REFERENCES payment_transactions(id);

-- Completed historical refunds are linked only when the bank reference identifies
-- exactly one refund and one transaction. Ambiguous records need reconciliation.
WITH matches AS (
    SELECT r.id AS refund_id, t.id AS transaction_id,
           count(*) OVER (PARTITION BY r.id) AS refund_matches,
           count(*) OVER (PARTITION BY t.id) AS transaction_matches
    FROM payment_refunds r
    JOIN payment_transactions t
      ON t.payment_id = r.payment_id AND t.amount = r.amount
     AND t.currency = r.currency AND t.type = 'REFUND' AND t.status = 'SUCCESS'
     AND t.transaction_ref = r.provider_refund_id
    WHERE r.status = 'SUCCESS' AND r.provider_refund_id IS NOT NULL
)
UPDATE payment_refunds r SET transaction_id = m.transaction_id
FROM matches m
WHERE r.id = m.refund_id AND m.refund_matches = 1 AND m.transaction_matches = 1;

-- Existing pending requests had no explicit link. Pair them deterministically
-- only when both sides contain the same number of matching pending entries.
WITH refunds AS (
    SELECT id, payment_id, amount, currency,
           row_number() OVER (PARTITION BY payment_id, amount, currency ORDER BY requested_at, id) AS position,
           count(*) OVER (PARTITION BY payment_id, amount, currency) AS entries
    FROM payment_refunds WHERE status = 'PENDING' AND transaction_id IS NULL
), transactions AS (
    SELECT t.id, t.payment_id, t.amount, t.currency,
           row_number() OVER (PARTITION BY t.payment_id, t.amount, t.currency ORDER BY t.received_at, t.id) AS position,
           count(*) OVER (PARTITION BY t.payment_id, t.amount, t.currency) AS entries
    FROM payment_transactions t
    WHERE t.type = 'REFUND' AND t.status = 'PENDING'
      AND NOT EXISTS (SELECT 1 FROM payment_refunds r WHERE r.transaction_id = t.id)
)
UPDATE payment_refunds r SET transaction_id = t.id
FROM refunds f JOIN transactions t
  ON t.payment_id = f.payment_id AND t.amount = f.amount AND t.currency = f.currency
 AND t.position = f.position AND t.entries = f.entries
WHERE r.id = f.id;
