\set ON_ERROR_STOP on

BEGIN;

CREATE OR REPLACE FUNCTION apply_to_team(
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
    IF NOT EXISTS (
        SELECT 1
        FROM team
        WHERE id = p_team_id
          AND recruitment_open = true
    ) THEN
        RAISE EXCEPTION
            'Team does not exist or recruitment is closed';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM team_member
        WHERE team_id = p_team_id
          AND user_id = p_applicant_id
    ) THEN
        RAISE EXCEPTION
            'User is already a member of this team';
    END IF;

    INSERT INTO team_application (
        team_id,
        applicant_id,
        comment
    )
    VALUES (
        p_team_id,
        p_applicant_id,
        p_comment
    )
    RETURNING id INTO application_id;

    RETURN application_id;
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION
            'User already has a pending application for this team';
END;
$$;

CREATE OR REPLACE PROCEDURE review_team_application(
    p_application_id UUID,
    p_reviewer_id UUID,
    p_decision VARCHAR(30),
    p_comment TEXT DEFAULT NULL
)
LANGUAGE plpgsql
AS $$
DECLARE
    application_team_id UUID;
    application_user_id UUID;
    application_status VARCHAR(30);
    project_manager_id UUID;
BEGIN
    IF p_decision NOT IN ('accepted', 'rejected') THEN
        RAISE EXCEPTION
            'Decision must be accepted or rejected';
    END IF;

    SELECT
        ta.team_id,
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

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Application not found';
    END IF;

    IF application_status <> 'pending' THEN
        RAISE EXCEPTION 'Application is already reviewed';
    END IF;

    IF project_manager_id <> p_reviewer_id THEN
        RAISE EXCEPTION
            'Only the project manager may review this application';
    END IF;

    UPDATE team_application
    SET status = p_decision,
        comment = COALESCE(p_comment, comment),
        reviewed_at = now(),
        reviewed_by = p_reviewer_id
    WHERE id = p_application_id;

    IF p_decision = 'accepted' THEN
        INSERT INTO team_member (team_id, user_id)
        VALUES (application_team_id, application_user_id)
        ON CONFLICT DO NOTHING;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION submit_task(
    p_team_task_id UUID,
    p_submitted_by UUID,
    p_content TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
    next_attempt INTEGER;
    submission_id UUID;
BEGIN
    PERFORM 1
    FROM team_task
    WHERE id = p_team_task_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Team task not found';
    END IF;

    SELECT COALESCE(MAX(attempt_number), 0) + 1
    INTO next_attempt
    FROM submission_attempt
    WHERE team_task_id = p_team_task_id;

    INSERT INTO submission_attempt (
        team_task_id,
        submitted_by,
        attempt_number,
        content
    )
    VALUES (
        p_team_task_id,
        p_submitted_by,
        next_attempt,
        p_content
    )
    RETURNING id INTO submission_id;

    RETURN submission_id;
END;
$$;

CREATE OR REPLACE FUNCTION review_submission(
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
    IF p_decision NOT IN ('accepted', 'revision_required') THEN
        RAISE EXCEPTION
            'Decision must be accepted or revision_required';
    END IF;

    PERFORM 1
    FROM submission_attempt
    WHERE id = p_submission_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Submission not found';
    END IF;

    INSERT INTO review (
        submission_id,
        reviewer_id,
        decision,
        comment
    )
    VALUES (
        p_submission_id,
        p_reviewer_id,
        p_decision,
        p_comment
    )
    RETURNING id INTO review_id;

    RETURN review_id;
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION
            'Submission has already been reviewed';
END;
$$;

COMMIT;

