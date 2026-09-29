-- Bumped on password change, account disable or "sign out all devices";
-- API tokens are only accepted with the version they were issued for.
alter table users add column token_version integer not null default 0;
