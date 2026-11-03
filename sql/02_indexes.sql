\set ON_ERROR_STOP on

BEGIN;

CREATE INDEX idx_project_manager
    ON project (manager_id);

CREATE INDEX idx_team_open_recruitment
    ON team (project_id)
    WHERE recruitment_open = true;

CREATE INDEX idx_team_member_user
    ON team_member (user_id);

CREATE INDEX idx_team_application_queue
    ON team_application (team_id, status, created_at);

CREATE UNIQUE INDEX uq_team_application_pending
    ON team_application (team_id, applicant_id)
    WHERE status = 'pending';

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

COMMIT;

