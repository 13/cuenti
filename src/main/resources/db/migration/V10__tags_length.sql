-- 255 characters ran out for transactions with many tags; saving then failed.
alter table transactions alter column tags type varchar(2000);
alter table scheduled_transactions alter column tags type varchar(2000);
alter table payees alter column default_tags type varchar(2000);
