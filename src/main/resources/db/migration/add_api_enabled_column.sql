-- Migration script to fix users table schema
-- Run this if you get column does not exist or contains null values errors

-- Add missing columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS api_enabled BOOLEAN DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS use_card_layout BOOLEAN DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dark_mode BOOLEAN DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locale VARCHAR(10) DEFAULT 'de-DE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_currency VARCHAR(3) DEFAULT 'EUR';

-- Fix NULL values (required for NOT NULL constraints)
UPDATE users SET api_enabled = false WHERE api_enabled IS NULL;
UPDATE users SET use_card_layout = true WHERE use_card_layout IS NULL;
UPDATE users SET dark_mode = true WHERE dark_mode IS NULL;
UPDATE users SET locale = 'de-DE' WHERE locale IS NULL;
UPDATE users SET default_currency = 'EUR' WHERE default_currency IS NULL;
