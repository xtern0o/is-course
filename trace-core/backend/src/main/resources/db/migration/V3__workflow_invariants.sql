ALTER TABLE discussion_message
    ADD COLUMN system_event boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uq_system_submission ON discussion_message (submission_id) WHERE system_event AND submission_id IS NOT NULL;
CREATE UNIQUE INDEX uq_system_review ON discussion_message (review_id) WHERE system_event AND review_id IS NOT NULL;

CREATE FUNCTION guard_submission_state() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
current_state text;
BEGIN
SELECT status
INTO current_state
FROM team_task
WHERE id = NEW.team_task_id FOR UPDATE;
IF
current_state NOT IN ('assigned','in_progress','revision_required') THEN
        RAISE EXCEPTION 'Submission is unavailable in this state';
END IF;
RETURN NEW;
END;
$$;
CREATE TRIGGER guard_submission
    BEFORE INSERT
    ON submission_attempt
    FOR EACH ROW EXECUTE FUNCTION guard_submission_state();

CREATE FUNCTION guard_review_state() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
assignment uuid;
DECLARE
attempt integer;
DECLARE
current_state text;
BEGIN
SELECT team_task_id, attempt_number
INTO assignment, attempt
FROM submission_attempt
WHERE id = NEW.submission_id;
SELECT status
INTO current_state
FROM team_task
WHERE id = assignment FOR UPDATE;
IF
current_state <> 'submitted' OR attempt <> (SELECT max(attempt_number) FROM submission_attempt WHERE team_task_id=assignment) THEN
        RAISE EXCEPTION 'Only the pending current attempt may be reviewed';
END IF;
RETURN NEW;
END;
$$;
CREATE TRIGGER guard_review
    BEFORE INSERT
    ON review
    FOR EACH ROW EXECUTE FUNCTION guard_review_state();

CREATE FUNCTION log_submission() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
INSERT INTO discussion_message(team_task_id, author_id, submission_id, system_event, content)
VALUES (NEW.team_task_id, NEW.submitted_by, NEW.id, true, 'Работа отправлена на проверку');
RETURN NEW;
END;
$$;
CREATE TRIGGER log_submission
    AFTER INSERT
    ON submission_attempt
    FOR EACH ROW EXECUTE FUNCTION log_submission();
CREATE FUNCTION log_review() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
INSERT INTO discussion_message(team_task_id, author_id, review_id, system_event, content)
SELECT team_task_id,
       NEW.reviewer_id,
       NEW.id,
       true,
       CASE WHEN NEW.decision = 'accepted' THEN 'Работа принята' ELSE 'Работа возвращена на доработку' END
FROM submission_attempt
WHERE id = NEW.submission_id;
RETURN NEW;
END;
$$;
CREATE TRIGGER log_review
    AFTER INSERT
    ON review
    FOR EACH ROW EXECUTE FUNCTION log_review();
