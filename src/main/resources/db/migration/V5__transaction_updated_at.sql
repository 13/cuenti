-- Concurrency token for API clients (If-Match). Null for existing rows,
-- which report version "0" until their next write.
alter table transactions add column updated_at timestamp(6);
