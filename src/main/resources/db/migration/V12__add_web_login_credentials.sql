ALTER TABLE app_user
    ADD COLUMN web_username VARCHAR(64) NULL AFTER avatar_url,
    ADD COLUMN web_password_hash VARCHAR(100) NULL AFTER web_username,
    ADD UNIQUE KEY uk_app_user_web_username (web_username);
