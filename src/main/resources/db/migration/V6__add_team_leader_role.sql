ALTER TABLE app_user
    DROP CHECK chk_app_user_role,
    ADD CONSTRAINT chk_app_user_role
        CHECK (role_code IN ('ADMIN', 'TEAM_MANAGER', 'TEAM_LEADER', 'VIEWER'));

ALTER TABLE team_manager
    ADD COLUMN role_code VARCHAR(20) NOT NULL DEFAULT 'TEAM_MANAGER' AFTER team_id,
    ADD CONSTRAINT chk_team_manager_role
        CHECK (role_code IN ('TEAM_MANAGER', 'TEAM_LEADER'));