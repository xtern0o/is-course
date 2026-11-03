\set ON_ERROR_STOP on

BEGIN;

CREATE OR REPLACE FUNCTION is_parameter_value_valid(
    checked_type VARCHAR,
    checked_value TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF checked_value IS NULL OR checked_type = 'text' THEN
        RETURN TRUE;
    END IF;

    CASE checked_type
        WHEN 'integer' THEN PERFORM checked_value::BIGINT;
        WHEN 'decimal' THEN PERFORM checked_value::NUMERIC;
        WHEN 'boolean' THEN PERFORM checked_value::BOOLEAN;
        WHEN 'date' THEN PERFORM checked_value::DATE;
        ELSE RETURN FALSE;
    END CASE;

    RETURN TRUE;
EXCEPTION
    WHEN invalid_text_representation
      OR numeric_value_out_of_range
      OR datetime_field_overflow THEN
        RETURN FALSE;
END;
$$;

CREATE OR REPLACE FUNCTION validate_default_value_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT is_parameter_value_valid(
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
BEFORE INSERT OR UPDATE OF data_type, default_value
ON task_parameter
FOR EACH ROW
EXECUTE FUNCTION validate_default_value_type();

CREATE OR REPLACE FUNCTION validate_team_task_project()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    team_project_id UUID;
    task_project_id UUID;
BEGIN
    SELECT project_id INTO team_project_id
    FROM team
    WHERE id = NEW.team_id;

    SELECT project_id INTO task_project_id
    FROM task
    WHERE id = NEW.task_id;

    IF team_project_id IS DISTINCT FROM task_project_id THEN
        RAISE EXCEPTION
            'Task and team must belong to the same project';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_team_task_project
BEFORE INSERT OR UPDATE OF team_id, task_id
ON team_task
FOR EACH ROW
EXECUTE FUNCTION validate_team_task_project();

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
        RAISE EXCEPTION
            'Parameter does not belong to the task template';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_team_task_parameter
BEFORE INSERT OR UPDATE OF team_task_id, parameter_id
ON team_task_parameter_value
FOR EACH ROW
EXECUTE FUNCTION validate_team_task_parameter();

CREATE OR REPLACE FUNCTION validate_parameter_value()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    parameter_type VARCHAR(30);
BEGIN
    SELECT data_type INTO parameter_type
    FROM task_parameter
    WHERE id = NEW.parameter_id;

    IF NOT is_parameter_value_valid(
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
BEFORE INSERT OR UPDATE OF parameter_id, value
ON team_task_parameter_value
FOR EACH ROW
EXECUTE FUNCTION validate_parameter_value();

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
          AND tt.status <> 'completed'
    ) THEN
        RAISE EXCEPTION
            'Submission author must be a team member and task must be open';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_submission_author
BEFORE INSERT OR UPDATE OF team_task_id, submitted_by
ON submission_attempt
FOR EACH ROW
EXECUTE FUNCTION validate_submission_author();

CREATE OR REPLACE FUNCTION mark_team_task_submitted()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE team_task
    SET status = 'submitted',
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
        RAISE EXCEPTION
            'Only the project manager may review a submission';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_reviewer
BEFORE INSERT OR UPDATE OF submission_id, reviewer_id
ON review
FOR EACH ROW
EXECUTE FUNCTION validate_reviewer();

CREATE OR REPLACE FUNCTION update_team_task_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE team_task tt
    SET status = CASE
            WHEN NEW.decision = 'accepted' THEN 'completed'
            ELSE 'revision_required'
        END,
        completed_at = CASE
            WHEN NEW.decision = 'accepted' THEN NEW.reviewed_at
            ELSE NULL
        END
    FROM submission_attempt sa
    WHERE sa.id = NEW.submission_id
      AND tt.id = sa.team_task_id;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_update_team_task_status
AFTER INSERT OR UPDATE OF decision
ON review
FOR EACH ROW
EXECUTE FUNCTION update_team_task_status();

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
        RAISE EXCEPTION
            'Submission does not belong to the selected team task';
    END IF;

    IF NEW.review_id IS NOT NULL AND NOT EXISTS (
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
BEFORE INSERT OR UPDATE OF team_task_id, submission_id, review_id
ON discussion_message
FOR EACH ROW
EXECUTE FUNCTION validate_message_scope();

COMMIT;

