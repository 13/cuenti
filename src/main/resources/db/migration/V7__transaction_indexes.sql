-- PostgreSQL does not index foreign keys automatically. The transaction grid,
-- running balances, budgets and statistics all filter on these columns.
create index idx_transactions_from_account on transactions (from_account_id);
create index idx_transactions_to_account on transactions (to_account_id);
create index idx_transactions_date on transactions (transaction_date);
create index idx_transactions_category on transactions (category_id);
create index idx_transaction_splits_transaction on transaction_splits (transaction_id);
