-- +migrate Up
-- Accounts table with metadata and status
CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT UNSIGNED AUTO_INCREMENT,
    account_name VARCHAR(256) NOT NULL,
    email VARCHAR(256),  -- Optional: for password recovery
    account_status TINYINT NOT NULL DEFAULT 1 COMMENT '0=disabled, 1=active, 2=locked',
    failed_login_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    last_failed_login_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (account_name),
    UNIQUE (email),
    INDEX idx_account_status (account_status),
    INDEX idx_created_at (created_at)
);

-- Password storage with history tracking
CREATE TABLE IF NOT EXISTS account_passwords (
    of_account_id BIGINT UNSIGNED NOT NULL,
    hash VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (of_account_id),
    FOREIGN KEY (of_account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- JWT signing keys (for rotation)
CREATE TABLE IF NOT EXISTS token_public_keys (
    id BIGINT UNSIGNED AUTO_INCREMENT,
    public_key TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME,
    PRIMARY KEY (id),
    INDEX idx_active (is_active)
);

-- Active sessions for logout and token tracking
CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT UNSIGNED AUTO_INCREMENT,
    of_account_id BIGINT UNSIGNED NOT NULL,
    access_token_jti VARCHAR(64) NOT NULL COMMENT 'JWT ID for access token',
    refresh_token_jti VARCHAR(64) COMMENT 'JWT ID for refresh token',
    token_public_key_id BIGINT UNSIGNED NOT NULL,
    ip_address VARCHAR(45),  -- Supports IPv6
    user_agent TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    access_token_expires_at DATETIME NOT NULL,
    refresh_token_expires_at DATETIME,
    revoked_at DATETIME,  -- Set when user logs out
    PRIMARY KEY (id),
    UNIQUE (access_token_jti),
    UNIQUE (refresh_token_jti),
    FOREIGN KEY (of_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (token_public_key_id) REFERENCES token_public_keys(id),
    INDEX idx_account_active_sessions (of_account_id, revoked_at),
    INDEX idx_access_token_lookup (access_token_jti, revoked_at),
    INDEX idx_refresh_token_lookup (refresh_token_jti, revoked_at),
    INDEX idx_expired_sessions (access_token_expires_at)
);

-- Password reset tokens for "forgot password" functionality
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT,
    of_account_id BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    used_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE (token_hash),
    FOREIGN KEY (of_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    INDEX idx_token_lookup (token_hash, expires_at, used_at)
);

-- Password history to prevent reuse
CREATE TABLE IF NOT EXISTS password_history (
    id BIGINT UNSIGNED AUTO_INCREMENT,
    of_account_id BIGINT UNSIGNED NOT NULL,
    hash VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (of_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    INDEX idx_account_history (of_account_id, created_at)
);

-- Download tasks
CREATE TABLE IF NOT EXISTS download_tasks (
    id BIGINT UNSIGNED AUTO_INCREMENT,
    of_account_id BIGINT UNSIGNED NOT NULL,
    download_type SMALLINT NOT NULL,
    url TEXT NOT NULL,
    download_status SMALLINT NOT NULL,
    metadata TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME,
    PRIMARY KEY (id),
    FOREIGN KEY (of_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    INDEX idx_account_tasks (of_account_id, download_status),
    INDEX idx_status (download_status),
    INDEX idx_created_at (created_at)
);

-- +migrate Down
DROP TABLE IF EXISTS download_tasks;

DROP TABLE IF EXISTS password_history;

DROP TABLE IF EXISTS password_reset_tokens;

DROP TABLE IF EXISTS sessions;

DROP TABLE IF EXISTS token_public_keys;

DROP TABLE IF EXISTS account_passwords;

DROP TABLE IF EXISTS accounts;