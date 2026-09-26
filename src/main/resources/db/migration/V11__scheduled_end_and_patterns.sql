-- Optional end of a schedule: a last date, or a number of occurrences still to post.
alter table scheduled_transactions add column end_date date;
alter table scheduled_transactions add column remaining_occurrences integer;

-- Legacy patterns become "weekly" with an interval, which the form can express.
-- Align the next date to the weekday first so weekly keeps landing on it
-- (isodow: Friday = 5, Saturday = 6).
update scheduled_transactions
set next_occurrence = next_occurrence + (((5 - extract(isodow from next_occurrence))::int + 7) % 7) * interval '1 day',
    recurrence_pattern = 'WEEKLY', recurrence_value = 1
where recurrence_pattern = 'EVERY_FRIDAY';

update scheduled_transactions
set next_occurrence = next_occurrence + (((6 - extract(isodow from next_occurrence))::int + 7) % 7) * interval '1 day',
    recurrence_pattern = 'WEEKLY', recurrence_value = 1
where recurrence_pattern = 'EVERY_SATURDAY';

update scheduled_transactions
set recurrence_pattern = 'WEEKLY', recurrence_value = 2
where recurrence_pattern = 'BI_WEEKLY';
