--liquibase formatted sql

--changeset trace:002-parameter-value-validator splitStatements:false
CREATE OR REPLACE FUNCTION is_parameter_value_valid(
    checked_type VARCHAR,
    checked_value TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF checked_value IS NULL OR checked_type = 'TEXT' THEN
        RETURN TRUE;
    END IF;

    CASE checked_type
        WHEN 'INTEGER' THEN PERFORM checked_value::BIGINT;
        WHEN 'DECIMAL' THEN PERFORM checked_value::NUMERIC;
        WHEN 'BOOLEAN' THEN PERFORM checked_value::BOOLEAN;
        WHEN 'DATE' THEN PERFORM checked_value::DATE;
        ELSE RETURN FALSE;
    END CASE;

    RETURN TRUE;
EXCEPTION
    WHEN invalid_text_representation
      OR invalid_datetime_format
      OR numeric_value_out_of_range
      OR datetime_field_overflow THEN
        RETURN FALSE;
END;
$$;

--changeset trace:003-default-value-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_default_value_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT is_parameter_value_valid(NEW.data_type, NEW.default_value) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Default value does not match parameter type';
    END IF;

    RETURN NEW;
END;
$$;

--changeset trace:004-assignment-project-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_team_task_project()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    team_project_id UUID;
    task_project_id UUID;
BEGIN
    SELECT project_id
    INTO team_project_id
    FROM team
    WHERE id = NEW.team_id;

    SELECT project_id
    INTO task_project_id
    FROM task
    WHERE id = NEW.task_id;

    IF team_project_id IS DISTINCT FROM task_project_id THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Task and team must belong to the same project';
    END IF;

    RETURN NEW;
END;
$$;

--changeset trace:005-assignment-parameter-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_team_task_parameter()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM team_task tt
        JOIN task t ON t.id = tt.task_id
        JOIN task_parameter tp
            ON tp.id = NEW.parameter_id
           AND tp.template_id = t.template_id
        WHERE tt.id = NEW.team_task_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Parameter does not belong to the task template';
    END IF;

    RETURN NEW;
END;
$$;

--changeset trace:006-parameter-value-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_parameter_value()
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

    IF NOT is_parameter_value_valid(parameter_type, NEW.value) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Parameter value does not match declared type';
    END IF;

    RETURN NEW;
END;
$$;

--changeset trace:007-submission-author-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_submission_author()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM team_task tt
        JOIN team_member tm ON tm.team_id = tt.team_id
        WHERE tt.id = NEW.team_task_id
          AND tm.user_id = NEW.submitted_by
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Submission author must be a member of the assigned team';
    END IF;

    RETURN NEW;
END;
$$;

--changeset trace:008-mark-assignment-submitted-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION mark_team_task_submitted()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE team_task
    SET status = 'SUBMITTED',
        completed_at = NULL
    WHERE id = NEW.team_task_id;

    RETURN NEW;
END;
$$;

--changeset trace:009-reviewer-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_reviewer()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM submission_attempt sa
        JOIN team_task tt ON tt.id = sa.team_task_id
        JOIN task t ON t.id = tt.task_id
        JOIN project p ON p.id = t.project_id
        WHERE sa.id = NEW.submission_id
          AND p.manager_id = NEW.reviewer_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Only the project manager may review a submission';
    END IF;

    RETURN NEW;
END;
$$;

--changeset trace:010-update-assignment-status-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION update_team_task_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE team_task tt
    SET status = CASE
            WHEN NEW.decision = 'ACCEPTED' THEN 'COMPLETED'
            ELSE 'REVISION_REQUIRED'
        END,
        completed_at = CASE
            WHEN NEW.decision = 'ACCEPTED' THEN NEW.reviewed_at
            ELSE NULL
        END
    FROM submission_attempt sa
    WHERE sa.id = NEW.submission_id
      AND tt.id = sa.team_task_id;

    RETURN NEW;
END;
$$;

--changeset trace:011-message-scope-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION validate_message_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.submission_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM submission_attempt sa
        WHERE sa.id = NEW.submission_id
          AND sa.team_task_id = NEW.team_task_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Submission does not belong to the selected assignment';
    END IF;

    IF NEW.review_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM review r
        JOIN submission_attempt sa ON sa.id = r.submission_id
        WHERE r.id = NEW.review_id
          AND sa.team_task_id = NEW.team_task_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Review does not belong to the selected assignment';
    END IF;

    RETURN NEW;
END;
$$;

