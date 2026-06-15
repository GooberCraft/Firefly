-- Firefly locator-bar preferences.
--
-- The plugin creates this automatically on startup (CREATE TABLE IF NOT EXISTS) for the H2 and
-- MySQL backends. Run it yourself first if the database user is not allowed to CREATE tables
-- (least-privilege setups). Portable DDL: valid on MySQL and on H2 in MySQL-compatibility mode.
--
-- Columns:
--   uuid   - player UUID (primary key)
--   hidden - the player hid their own locator-bar dot
--   color  - chosen dot color as 0xRRGGBB (NULL = vanilla)
--   bypass - admin see-all choice (NULL = unset/use config default, TRUE/FALSE = explicit)
--
-- Indexing: every access is either a primary-key lookup (uuid, on upsert/delete) or a full read of
-- the whole table at startup. The PRIMARY KEY on uuid is therefore the only index needed; no column
-- is ever filtered on, so secondary indexes would only add write cost with no read benefit.

CREATE TABLE IF NOT EXISTS firefly_players (
    uuid   VARCHAR(36) PRIMARY KEY,
    hidden BOOLEAN     NOT NULL DEFAULT FALSE,
    color  INT         NULL,
    bypass BOOLEAN     NULL
);