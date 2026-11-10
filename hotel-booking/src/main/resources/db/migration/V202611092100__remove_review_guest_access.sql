-- Retire every review-only grant and challenge. Booking/payment access tokens are unaffected.
-- Keep earlier migrations unchanged for databases that have already applied them.
DROP TABLE IF EXISTS review_access_challenges;
