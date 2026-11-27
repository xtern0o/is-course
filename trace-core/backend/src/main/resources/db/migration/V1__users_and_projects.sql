CREATE TABLE app_user
(
    id           UUID PRIMARY KEY      DEFAULT uuidv7(),
    sso_subject  VARCHAR(255) NOT NULL UNIQUE,
    username     VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL UNIQUE,
    role         VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_user_role
        CHECK (role IN ('student', 'teacher', 'admin'))
);

CREATE TABLE project
(
    id           UUID PRIMARY KEY      DEFAULT uuidv7(),
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    goal         TEXT,
    type         VARCHAR(30)  NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'draft',
    manager_id   UUID         NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,

    CONSTRAINT chk_project_type
        CHECK (type IN ('educational', 'development')),

    CONSTRAINT chk_project_status
        CHECK (status IN ('draft', 'active', 'completed', 'archived')),

    CONSTRAINT chk_project_completion
        CHECK (completed_at IS NULL OR completed_at >= created_at)
);

CREATE INDEX idx_project_manager
    ON project (manager_id);
