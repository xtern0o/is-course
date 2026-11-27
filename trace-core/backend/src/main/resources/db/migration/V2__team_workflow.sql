CREATE TABLE team
(
    id               UUID PRIMARY KEY      DEFAULT uuidv7(),
    project_id       UUID         NOT NULL
        REFERENCES project (id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    recruitment_open BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_team_name
        UNIQUE (project_id, name)
);

CREATE TABLE team_member
(
    team_id   UUID        NOT NULL
        REFERENCES team (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL
        REFERENCES app_user (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE team_application
(
    id           UUID PRIMARY KEY     DEFAULT uuidv7(),
    team_id      UUID        NOT NULL
        REFERENCES team (id) ON DELETE CASCADE,
    applicant_id UUID        NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    status       VARCHAR(30) NOT NULL DEFAULT 'pending',
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at  TIMESTAMPTZ,
    reviewed_by  UUID
        REFERENCES app_user (id) ON DELETE RESTRICT,

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

CREATE TABLE task_template
(
    id              UUID PRIMARY KEY      DEFAULT uuidv7(),
    created_by      UUID         NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    expected_result TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_template_title
        CHECK (btrim(title) <> '')
);

CREATE TABLE task_parameter
(
    id            UUID PRIMARY KEY      DEFAULT uuidv7(),
    template_id   UUID         NOT NULL
        REFERENCES task_template (id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    description   TEXT,
    data_type     VARCHAR(30)  NOT NULL,
    default_value TEXT,
    required      BOOLEAN      NOT NULL DEFAULT false,

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

CREATE TABLE task
(
    id           UUID PRIMARY KEY      DEFAULT uuidv7(),
    project_id   UUID         NOT NULL
        REFERENCES project (id) ON DELETE CASCADE,
    template_id  UUID         NOT NULL
        REFERENCES task_template (id) ON DELETE RESTRICT,
    created_by   UUID         NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    title        VARCHAR(255) NOT NULL,
    instructions TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deadline_at  TIMESTAMPTZ,

    CONSTRAINT chk_task_title
        CHECK (btrim(title) <> ''),

    CONSTRAINT chk_task_deadline
        CHECK (deadline_at > created_at)
);

CREATE TABLE team_task
(
    id           UUID PRIMARY KEY     DEFAULT uuidv7(),
    team_id      UUID        NOT NULL
        REFERENCES team (id) ON DELETE CASCADE,
    task_id      UUID        NOT NULL
        REFERENCES task (id) ON DELETE CASCADE,
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

CREATE TABLE team_task_parameter_value
(
    team_task_id UUID NOT NULL
        REFERENCES team_task (id) ON DELETE CASCADE,
    parameter_id UUID NOT NULL
        REFERENCES task_parameter (id) ON DELETE CASCADE,
    value        TEXT NOT NULL,

    PRIMARY KEY (team_task_id, parameter_id)
);

CREATE TABLE submission_attempt
(
    id             UUID PRIMARY KEY     DEFAULT uuidv7(),
    team_task_id   UUID        NOT NULL
        REFERENCES team_task (id) ON DELETE CASCADE,
    submitted_by   UUID        NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    attempt_number INTEGER     NOT NULL,
    content        TEXT,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_submission_attempt
        UNIQUE (team_task_id, attempt_number),

    CONSTRAINT chk_attempt_number
        CHECK (attempt_number > 0)
);

CREATE TABLE submission_attachment
(
    id            UUID PRIMARY KEY       DEFAULT uuidv7(),
    submission_id UUID          NOT NULL
        REFERENCES submission_attempt (id) ON DELETE CASCADE,
    object_key    VARCHAR(1024) NOT NULL UNIQUE,
    original_name VARCHAR(255)  NOT NULL,
    mime_type     VARCHAR(255)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    uploaded_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_attachment_key
        CHECK (btrim(object_key) <> ''),

    CONSTRAINT chk_attachment_name
        CHECK (btrim(original_name) <> ''),

    CONSTRAINT chk_attachment_size
        CHECK (size_bytes > 0)
);

CREATE TABLE review
(
    id            UUID PRIMARY KEY     DEFAULT uuidv7(),
    submission_id UUID        NOT NULL UNIQUE
        REFERENCES submission_attempt (id) ON DELETE CASCADE,
    reviewer_id   UUID        NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
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

CREATE TABLE announcement
(
    id         UUID PRIMARY KEY      DEFAULT uuidv7(),
    task_id    UUID         NOT NULL
        REFERENCES task (id) ON DELETE CASCADE,
    author_id  UUID         NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_announcement_title
        CHECK (btrim(title) <> ''),

    CONSTRAINT chk_announcement_content
        CHECK (btrim(content) <> '')
);

CREATE TABLE discussion_message
(
    id            UUID PRIMARY KEY     DEFAULT uuidv7(),
    team_task_id  UUID        NOT NULL
        REFERENCES team_task (id) ON DELETE CASCADE,
    author_id     UUID        NOT NULL
        REFERENCES app_user (id) ON DELETE RESTRICT,
    submission_id UUID
                              REFERENCES submission_attempt (id) ON DELETE SET NULL,
    review_id     UUID
                              REFERENCES review (id) ON DELETE SET NULL,
    content       TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_message_target
        CHECK (num_nonnulls(submission_id, review_id) <= 1),

    CONSTRAINT chk_message_content
        CHECK (btrim(content) <> '')
);
CREATE INDEX idx_team_open_recruitment
    ON team (project_id) WHERE recruitment_open = true;

CREATE INDEX idx_team_member_user
    ON team_member (user_id);

CREATE INDEX idx_team_application_queue
    ON team_application (team_id, status, created_at);

CREATE UNIQUE INDEX uq_team_application_pending
    ON team_application (team_id, applicant_id) WHERE status = 'pending';

CREATE INDEX idx_task_template_creator
    ON task_template (created_by);

CREATE INDEX idx_task_project_deadline
    ON task (project_id, deadline_at);

CREATE INDEX idx_team_task_task
    ON team_task (task_id);

CREATE INDEX idx_submission_attachment_submission
    ON submission_attachment (submission_id);

CREATE INDEX idx_announcement_task_created
    ON announcement (task_id, created_at DESC);

CREATE INDEX idx_discussion_message_team_task_created
    ON discussion_message (team_task_id, created_at);
CREATE
OR REPLACE FUNCTION is_parameter_value_valid(
    checked_type VARCHAR,
    checked_value TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF
checked_value IS NULL OR checked_type = 'text' THEN
        RETURN TRUE;
END IF;

CASE checked_type
        WHEN 'integer' THEN PERFORM checked_value::BIGINT;
WHEN 'decimal' THEN PERFORM checked_value::NUMERIC;
WHEN 'boolean' THEN PERFORM checked_value::BOOLEAN;
WHEN 'date' THEN PERFORM checked_value::DATE;
ELSE RETURN FALSE;
END
CASE;

RETURN TRUE;
EXCEPTION
    WHEN invalid_text_representation
      OR numeric_value_out_of_range
      OR datetime_field_overflow THEN
        RETURN FALSE;
END;
$$;

CREATE
OR REPLACE FUNCTION validate_default_value_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
NOT is_parameter_value_valid(
        NEW.data_type,
        NEW.default_value
    ) THEN
        RAISE EXCEPTION
            'Default value does not match parameter type';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_default_value_type
    BEFORE INSERT OR
UPDATE OF data_type, default_value
ON task_parameter
    FOR EACH ROW
    EXECUTE FUNCTION validate_default_value_type();

CREATE
OR REPLACE FUNCTION validate_team_task_project()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
team_project_id UUID;
    task_project_id
UUID;
BEGIN
SELECT project_id
INTO team_project_id
FROM team
WHERE id = NEW.team_id;

SELECT project_id
INTO task_project_id
FROM task
WHERE id = NEW.task_id;

IF
team_project_id IS DISTINCT FROM task_project_id THEN
        RAISE EXCEPTION
            'Task and team must belong to the same project';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_team_task_project
    BEFORE INSERT OR
UPDATE OF team_id, task_id
ON team_task
    FOR EACH ROW
    EXECUTE FUNCTION validate_team_task_project();

CREATE
OR REPLACE FUNCTION validate_team_task_parameter()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM team_task tt
        JOIN task t ON t.id = tt.task_id
        JOIN task_parameter tp
            ON tp.id = NEW.parameter_id
           AND tp.template_id = t.template_id
        WHERE tt.id = NEW.team_task_id
    ) THEN
        RAISE EXCEPTION
            'Parameter does not belong to the task template';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_team_task_parameter
    BEFORE INSERT OR
UPDATE OF team_task_id, parameter_id
ON team_task_parameter_value
    FOR EACH ROW
    EXECUTE FUNCTION validate_team_task_parameter();

CREATE
OR REPLACE FUNCTION validate_parameter_value()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
parameter_type VARCHAR(30);
BEGIN
SELECT data_type
INTO parameter_type
FROM task_parameter
WHERE id = NEW.parameter_id;

IF
NOT is_parameter_value_valid(
        parameter_type,
        NEW.value
    ) THEN
        RAISE EXCEPTION
            'Parameter value does not match declared type';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_parameter_value
    BEFORE INSERT OR
UPDATE OF parameter_id, value
ON team_task_parameter_value
    FOR EACH ROW
    EXECUTE FUNCTION validate_parameter_value();

CREATE
OR REPLACE FUNCTION validate_submission_author()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM team_task tt
        JOIN team_member tm ON tm.team_id = tt.team_id
        WHERE tt.id = NEW.team_task_id
          AND tm.user_id = NEW.submitted_by
          AND tt.status <> 'completed'
    ) THEN
        RAISE EXCEPTION
            'Submission author must be a team member and task must be open';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_submission_author
    BEFORE INSERT OR
UPDATE OF team_task_id, submitted_by
ON submission_attempt
    FOR EACH ROW
    EXECUTE FUNCTION validate_submission_author();

CREATE
OR REPLACE FUNCTION mark_team_task_submitted()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
UPDATE team_task
SET status       = 'submitted',
    completed_at = NULL
WHERE id = NEW.team_task_id;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_mark_team_task_submitted
    AFTER INSERT
    ON submission_attempt
    FOR EACH ROW
    EXECUTE FUNCTION mark_team_task_submitted();

CREATE
OR REPLACE FUNCTION validate_reviewer()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM submission_attempt sa
        JOIN team_task tt ON tt.id = sa.team_task_id
        JOIN task t ON t.id = tt.task_id
        JOIN project p ON p.id = t.project_id
        WHERE sa.id = NEW.submission_id
          AND p.manager_id = NEW.reviewer_id
    ) THEN
        RAISE EXCEPTION
            'Only the project manager may review a submission';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_reviewer
    BEFORE INSERT OR
UPDATE OF submission_id, reviewer_id
ON review
    FOR EACH ROW
    EXECUTE FUNCTION validate_reviewer();

CREATE
OR REPLACE FUNCTION update_team_task_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
UPDATE team_task tt
SET status       = CASE
                       WHEN NEW.decision = 'accepted' THEN 'completed'
                       ELSE 'revision_required'
    END,
    completed_at = CASE
                       WHEN NEW.decision = 'accepted' THEN NEW.reviewed_at
                       ELSE NULL
        END FROM submission_attempt sa
WHERE sa.id = NEW.submission_id
  AND tt.id = sa.team_task_id;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_update_team_task_status
    AFTER INSERT OR
UPDATE OF decision
ON review
    FOR EACH ROW
    EXECUTE FUNCTION update_team_task_status();

CREATE
OR REPLACE FUNCTION validate_message_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
NEW.submission_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM submission_attempt sa
        WHERE sa.id = NEW.submission_id
          AND sa.team_task_id = NEW.team_task_id
    ) THEN
        RAISE EXCEPTION
            'Submission does not belong to the selected team task';
END IF;

    IF
NEW.review_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM review r
        JOIN submission_attempt sa ON sa.id = r.submission_id
        WHERE r.id = NEW.review_id
          AND sa.team_task_id = NEW.team_task_id
    ) THEN
        RAISE EXCEPTION
            'Review does not belong to the selected team task';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_message_scope
    BEFORE INSERT OR
UPDATE OF team_task_id, submission_id, review_id
ON discussion_message
    FOR EACH ROW
    EXECUTE FUNCTION validate_message_scope();
CREATE
OR REPLACE FUNCTION apply_to_team(
    p_team_id UUID,
    p_applicant_id UUID,
    p_comment TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
application_id UUID;
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM team
        WHERE id = p_team_id
          AND recruitment_open = true
    ) THEN
        RAISE EXCEPTION
            'Team does not exist or recruitment is closed';
END IF;

    IF
EXISTS (
        SELECT 1
        FROM team_member
        WHERE team_id = p_team_id
          AND user_id = p_applicant_id
    ) THEN
        RAISE EXCEPTION
            'User is already a member of this team';
END IF;

INSERT INTO team_application (team_id,
                              applicant_id,
                              comment)
VALUES (p_team_id,
        p_applicant_id,
        p_comment) RETURNING id
INTO application_id;

RETURN application_id;
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION
            'User already has a pending application for this team';
END;
$$;

CREATE
OR REPLACE PROCEDURE review_team_application(
    p_application_id UUID,
    p_reviewer_id UUID,
    p_decision VARCHAR(30),
    p_comment TEXT DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
application_team_id UUID;
    application_user_id
UUID;
    application_status
VARCHAR(30);
    project_manager_id
UUID;
BEGIN
    IF
p_decision NOT IN ('accepted', 'rejected') THEN
        RAISE EXCEPTION
            'Decision must be accepted or rejected';
END IF;

SELECT ta.team_id,
       ta.applicant_id,
       ta.status,
       p.manager_id
INTO
    application_team_id,
    application_user_id,
    application_status,
    project_manager_id
FROM team_application ta
         JOIN team t ON t.id = ta.team_id
         JOIN project p ON p.id = t.project_id
WHERE ta.id = p_application_id
    FOR UPDATE OF ta;

IF
NOT FOUND THEN
        RAISE EXCEPTION 'Application not found';
END IF;

    IF
application_status <> 'pending' THEN
        RAISE EXCEPTION 'Application is already reviewed';
END IF;

    IF
project_manager_id <> p_reviewer_id THEN
        RAISE EXCEPTION
            'Only the project manager may review this application';
END IF;

UPDATE team_application
SET status      = p_decision,
    comment     = COALESCE(p_comment, comment),
    reviewed_at = now(),
    reviewed_by = p_reviewer_id
WHERE id = p_application_id;

IF
p_decision = 'accepted' THEN
        INSERT INTO team_member (team_id, user_id)
        VALUES (application_team_id, application_user_id)
        ON CONFLICT DO NOTHING;
END IF;
END;
$$;

CREATE
OR REPLACE FUNCTION submit_task(
    p_team_task_id UUID,
    p_submitted_by UUID,
    p_content TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
next_attempt INTEGER;
    submission_id
UUID;
BEGIN
    PERFORM
1
    FROM team_task
    WHERE id = p_team_task_id
    FOR
UPDATE;

IF
NOT FOUND THEN
        RAISE EXCEPTION 'Team task not found';
END IF;

SELECT COALESCE(MAX(attempt_number), 0) + 1
INTO next_attempt
FROM submission_attempt
WHERE team_task_id = p_team_task_id;

INSERT INTO submission_attempt (team_task_id,
                                submitted_by,
                                attempt_number,
                                content)
VALUES (p_team_task_id,
        p_submitted_by,
        next_attempt,
        p_content) RETURNING id
INTO submission_id;

RETURN submission_id;
END;
$$;

CREATE
OR REPLACE FUNCTION review_submission(
    p_submission_id UUID,
    p_reviewer_id UUID,
    p_decision VARCHAR(30),
    p_comment TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
review_id UUID;
BEGIN
    IF
p_decision NOT IN ('accepted', 'revision_required') THEN
        RAISE EXCEPTION
            'Decision must be accepted or revision_required';
END IF;

    PERFORM
1
    FROM submission_attempt
    WHERE id = p_submission_id
    FOR
UPDATE;

IF
NOT FOUND THEN
        RAISE EXCEPTION 'Submission not found';
END IF;

INSERT INTO review (submission_id,
                    reviewer_id,
                    decision,
                    comment)
VALUES (p_submission_id,
        p_reviewer_id,
        p_decision,
        p_comment) RETURNING id
INTO review_id;

RETURN review_id;
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION
            'Submission has already been reviewed';
END;
$$;
