-- Income posted from a schedule kept its account in from_account_id. The ledger credits
-- income to to_account_id, so those rows showed no account and never reached the balance.
-- Credit the missing amounts first, then move the account to the income side.
update accounts a
set balance = a.balance + s.total
from (select from_account_id as account_id, sum(amount) as total
      from transactions
      where type = 'INCOME' and to_account_id is null and from_account_id is not null
      group by from_account_id) s
where a.id = s.account_id;

update transactions
set to_account_id = from_account_id, from_account_id = null
where type = 'INCOME' and to_account_id is null and from_account_id is not null;

update scheduled_transactions
set to_account_id = from_account_id, from_account_id = null
where type = 'INCOME' and to_account_id is null and from_account_id is not null;

-- Duplicate tag rows (same name ignoring case) made the same tag land twice in a
-- tag string. Keep the oldest row per name and forbid new duplicates.
delete from tags t
using tags keep
where keep.user_id = t.user_id
  and lower(trim(keep.name)) = lower(trim(t.name))
  and keep.id < t.id;

update tags set name = trim(name) where name <> trim(name);

create unique index uq_tags_user_name on tags (user_id, lower(name));

-- Remove blanks and case-insensitive duplicates from stored tag strings (first spelling wins).
with parts as (
    select t.id, trim(u.tag) as name, u.ord
    from transactions t, unnest(string_to_array(t.tags, ',')) with ordinality as u(tag, ord)
    where t.tags is not null
), firsts as (
    select distinct on (id, lower(name)) id, name, ord
    from parts where name <> ''
    order by id, lower(name), ord
), cleaned as (
    select t.id, (select string_agg(f.name, ',' order by f.ord) from firsts f where f.id = t.id) as clean
    from transactions t where t.tags is not null
)
update transactions t set tags = c.clean
from cleaned c
where c.id = t.id and t.tags is distinct from c.clean;

with parts as (
    select s.id, trim(u.tag) as name, u.ord
    from scheduled_transactions s, unnest(string_to_array(s.tags, ',')) with ordinality as u(tag, ord)
    where s.tags is not null
), firsts as (
    select distinct on (id, lower(name)) id, name, ord
    from parts where name <> ''
    order by id, lower(name), ord
), cleaned as (
    select s.id, (select string_agg(f.name, ',' order by f.ord) from firsts f where f.id = s.id) as clean
    from scheduled_transactions s where s.tags is not null
)
update scheduled_transactions s set tags = c.clean
from cleaned c
where c.id = s.id and s.tags is distinct from c.clean;

with parts as (
    select p.id, trim(u.tag) as name, u.ord
    from payees p, unnest(string_to_array(p.default_tags, ',')) with ordinality as u(tag, ord)
    where p.default_tags is not null
), firsts as (
    select distinct on (id, lower(name)) id, name, ord
    from parts where name <> ''
    order by id, lower(name), ord
), cleaned as (
    select p.id, (select string_agg(f.name, ',' order by f.ord) from firsts f where f.id = p.id) as clean
    from payees p where p.default_tags is not null
)
update payees p set default_tags = c.clean
from cleaned c
where c.id = p.id and p.default_tags is distinct from c.clean;
