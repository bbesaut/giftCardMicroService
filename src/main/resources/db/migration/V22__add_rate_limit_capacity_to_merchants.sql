-- Per-merchant override for the redeem/lookup/reserve rate limit (requests/minute), which is now
-- keyed by merchant identity instead of client IP (see RateLimitFilter). NULL means "use the
-- application-wide default" (app.rate-limit.merchant-capacity) - most merchants don't need a
-- custom value, only high-volume ones do.
ALTER TABLE merchants ADD COLUMN rate_limit_capacity INTEGER;
