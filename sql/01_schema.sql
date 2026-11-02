\set ON_ERROR_STOP on

-- постгрес 18+ версии для uuidv7

BEGIN;

CREATE TABLE app_user (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    sso_subject  VARCHAR(255) NOT NULL UNIQUE,
    username     VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL UNIQUE,
    role         VARCHAR(30) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_user_role
        CHECK (role IN ('student', 'teacher', 'admin'))
);

CREATE TABLE project (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    goal         TEXT,
    type         VARCHAR(30) NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'draft',
    manager_id   UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,

    CONSTRAINT chk_project_type
        CHECK (type IN ('educational', 'development')),

    CONSTRAINT chk_project_status
        CHECK (status IN ('draft', 'active', 'completed', 'archived')),

    CONSTRAINT chk_project_completion
        CHECK (completed_at IS NULL OR completed_at >= created_at)
);

CREATE TABLE team (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    project_id       UUID NOT NULL
        REFERENCES project(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    recruitment_open BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_team_name
        UNIQUE (project_id, name)
);

CREATE TABLE team_member (
    team_id   UUID NOT NULL
        REFERENCES team(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL
        REFERENCES app_user(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE team_application (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    team_id      UUID NOT NULL
        REFERENCES team(id) ON DELETE CASCADE,
    applicant_id UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    status       VARCHAR(30) NOT NULL DEFAULT 'pending',
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at  TIMESTAMPTZ,
    reviewed_by  UUID
        REFERENCES app_user(id) ON DELETE RESTRICT,

    CONSTRAINT chk_application_status
        CHECK (status IN ('pending', 'accepted', 'rejected')),

    CONSTRAINT chk_application_review
        CHECK (
            (
                status = 'pending'
                AND reviewed_at IS NULL
                AND reviewed_by IS NULL
            )
            OR
            (
                status IN ('accepted', 'rejected')
                AND reviewed_at IS NOT NULL
                AND reviewed_by IS NOT NULL
            )
        )
);

CREATE TABLE task_template (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    created_by      UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    expected_result TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_template_title
        CHECK (btrim(title) <> '')
);

CREATE TABLE task_parameter (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    template_id   UUID NOT NULL
        REFERENCES task_template(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    description   TEXT,
    data_type     VARCHAR(30) NOT NULL,
    default_value TEXT,
    required      BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT uq_template_parameter
        UNIQUE (template_id, name),

    CONSTRAINT chk_parameter_type
        CHECK (
            data_type IN (
                'text',
                'integer',
                'decimal',
                'boolean',
                'date'
            )
        )
);

CREATE TABLE task (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    project_id   UUID NOT NULL
        REFERENCES project(id) ON DELETE CASCADE,
    template_id  UUID NOT NULL
        REFERENCES task_template(id) ON DELETE RESTRICT,
    created_by   UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    title        VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_task_title
        CHECK (btrim(title) <> ''),

    CONSTRAINT chk_task_deadline
        CHECK (deadline_at > created_at)
);

CREATE TABLE team_task (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    team_id      UUID NOT NULL
        REFERENCES team(id) ON DELETE CASCADE,
    task_id      UUID NOT NULL
        REFERENCES task(id) ON DELETE CASCADE,
    status       VARCHAR(30) NOT NULL DEFAULT 'assigned',
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,

    CONSTRAINT uq_team_task
        UNIQUE (team_id, task_id),

    CONSTRAINT chk_team_task_status
        CHECK (
            status IN (
                'assigned',
                'in_progress',
                'submitted',
                'revision_required',
                'completed'
            )
        ),

    CONSTRAINT chk_team_task_completion
        CHECK (completed_at IS NULL OR completed_at >= assigned_at)
);

CREATE TABLE team_task_parameter_value (
    team_task_id UUID NOT NULL
        REFERENCES team_task(id) ON DELETE CASCADE,
    parameter_id UUID NOT NULL
        REFERENCES task_parameter(id) ON DELETE CASCADE,
    value        TEXT NOT NULL,

    PRIMARY KEY (team_task_id, parameter_id)
);

CREATE TABLE submission_attempt (
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    team_task_id   UUID NOT NULL
        REFERENCES team_task(id) ON DELETE CASCADE,
    submitted_by   UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL,
    content        TEXT,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_submission_attempt
        UNIQUE (team_task_id, attempt_number),

    CONSTRAINT chk_attempt_number
        CHECK (attempt_number > 0)
);

CREATE TABLE submission_attachment (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    submission_id UUID NOT NULL
        REFERENCES submission_attempt(id) ON DELETE CASCADE,
    object_key    VARCHAR(1024) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_attachment_key
        CHECK (btrim(object_key) <> ''),

    CONSTRAINT chk_attachment_name
        CHECK (btrim(original_name) <> ''),

    CONSTRAINT chk_attachment_size
        CHECK (size_bytes > 0)
);

CREATE TABLE review (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    submission_id UUID NOT NULL UNIQUE
        REFERENCES submission_attempt(id) ON DELETE CASCADE,
    reviewer_id   UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    decision      VARCHAR(30) NOT NULL,
    comment       TEXT,
    reviewed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_review_decision
        CHECK (decision IN ('accepted', 'revision_required')),

    CONSTRAINT chk_revision_comment
        CHECK (
            decision = 'accepted'
            OR nullif(btrim(comment), '') IS NOT NULL
        )
);

CREATE TABLE announcement (
    id         UUID PRIMARY KEY DEFAULT uuidv7(),
    task_id    UUID NOT NULL
        REFERENCES task(id) ON DELETE CASCADE,
    author_id  UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    title      VARCHAR(255) NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_announcement_title
        CHECK (btrim(title) <> ''),

    CONSTRAINT chk_announcement_content
        CHECK (btrim(content) <> '')
);

CREATE TABLE discussion_message (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    team_task_id  UUID NOT NULL
        REFERENCES team_task(id) ON DELETE CASCADE,
    author_id     UUID NOT NULL
        REFERENCES app_user(id) ON DELETE RESTRICT,
    submission_id UUID
        REFERENCES submission_attempt(id) ON DELETE SET NULL,
    review_id     UUID
        REFERENCES review(id) ON DELETE SET NULL,
    content       TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_message_target
        CHECK (num_nonnulls(submission_id, review_id) <= 1),

    CONSTRAINT chk_message_content
        CHECK (btrim(content) <> '')
);

COMMIT;

