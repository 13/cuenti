-- Schedules are date-based: drop time-of-day so "due" and "overdue" agree within a day.
update scheduled_transactions set next_occurrence = date_trunc('day', next_occurrence);

-- Per-user scheduled preferences (null = defaults: badge due-now, list horizon 7 days).
alter table users add column scheduled_badge_days integer;
alter table users add column scheduled_horizon_days integer;

-- Booking history: which schedule a transaction was posted from.
alter table transactions add column scheduled_transaction_id bigint;
create index idx_transactions_scheduled on transactions (scheduled_transaction_id);
