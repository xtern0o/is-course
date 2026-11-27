--liquibase formatted sql

--changeset trace:001-initial-schema
CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    sso_subject VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'STUDENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_user_sso_subject UNIQUE (sso_subject),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT chk_app_user_sso_subject CHECK (btrim(sso_subject) <> ''),
    CONSTRAINT chk_app_user_username CHECK (btrim(username) <> ''),
    CONSTRAINT chk_app_user_display_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT chk_app_user_email CHECK (btrim(email) <> ''),
    CONSTRAINT chk_app_user_role CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN'))
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    goal TEXT,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    manager_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_project_manager
        FOREIGN KEY (manager_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_project_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_project_type
        CHECK (type IN ('EDUCATIONAL', 'DEVELOPMENT')),
    CONSTRAINT chk_project_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT chk_project_completion
        CHECK (completed_at IS NULL OR completed_at >= created_at)
);

CREATE INDEX idx_project_manager ON project(manager_id);

CREATE TABLE team (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    recruitment_open BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_team_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_project_name UNIQUE (project_id, name),
    CONSTRAINT chk_team_name CHECK (btrim(name) <> '')
);

CREATE INDEX idx_team_project ON team(project_id);
CREATE INDEX idx_team_open_recruitment
    ON team(project_id)
    WHERE recruitment_open;

CREATE TABLE team_member (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_team_member_team
        FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_member_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_member_team_user UNIQUE (team_id, user_id)
);

CREATE INDEX idx_team_member_user ON team_member(user_id);

CREATE TABLE team_application (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,

    CONSTRAINT fk_team_application_team
        FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_application_applicant
        FOREIGN KEY (applicant_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_application_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_team_application_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_team_application_review
        CHECK (
            (
                status = 'PENDING'
                AND reviewed_at IS NULL
                AND reviewed_by IS NULL
            )
            OR
            (
                status IN ('ACCEPTED', 'REJECTED')
                AND reviewed_at IS NOT NULL
                AND reviewed_by IS NOT NULL
                AND reviewed_at >= created_at
            )
        )
);

CREATE INDEX idx_team_application_team_status_created
    ON team_application(team_id, status, created_at);
CREATE INDEX idx_team_application_applicant
    ON team_application(applicant_id);
CREATE UNIQUE INDEX uq_team_application_pending
    ON team_application(team_id, applicant_id)
    WHERE status = 'PENDING';

CREATE TABLE task_template (
    id UUID PRIMARY KEY,
    created_by UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    expected_result TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_task_template_creator
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_task_template_title CHECK (btrim(title) <> '')
);

CREATE INDEX idx_task_template_creator ON task_template(created_by);

CREATE TABLE task_parameter (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    data_type VARCHAR(30) NOT NULL,
    default_value TEXT,
    required BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT fk_task_parameter_template
        FOREIGN KEY (template_id) REFERENCES task_template(id) ON DELETE CASCADE,
    CONSTRAINT uq_task_parameter_template_name UNIQUE (template_id, name),
    CONSTRAINT chk_task_parameter_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_task_parameter_type
        CHECK (data_type IN ('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE'))
);

CREATE TABLE task (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    template_id UUID NOT NULL,
    created_by UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL,
    deadline_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_task_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_template
        FOREIGN KEY (template_id) REFERENCES task_template(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_creator
        FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_task_title CHECK (btrim(title) <> ''),
    CONSTRAINT chk_task_instructions CHECK (btrim(instructions) <> ''),
    CONSTRAINT chk_task_deadline
        CHECK (deadline_at IS NULL OR deadline_at > created_at)
);

CREATE INDEX idx_task_project_deadline ON task(project_id, deadline_at);
CREATE INDEX idx_task_template ON task(template_id);

CREATE TABLE team_task (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    task_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_team_task_team
        FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_task_task
        FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT uq_assignment_team_task UNIQUE (team_id, task_id),
    CONSTRAINT chk_team_task_status
        CHECK (
            status IN (
                'ASSIGNED',
                'SUBMITTED',
                'REVISION_REQUIRED',
                'COMPLETED'
            )
        ),
    CONSTRAINT chk_team_task_completion
        CHECK (
            (
                status = 'COMPLETED'
                AND completed_at IS NOT NULL
                AND completed_at >= assigned_at
            )
            OR
            (
                status <> 'COMPLETED'
                AND completed_at IS NULL
            )
        )
);

CREATE INDEX idx_team_task_task ON team_task(task_id);
CREATE INDEX idx_team_task_team_status ON team_task(team_id, status);

CREATE TABLE team_task_parameter_value (
    id UUID PRIMARY KEY,
    team_task_id UUID NOT NULL,
    parameter_id UUID NOT NULL,
    value TEXT NOT NULL,

    CONSTRAINT fk_team_task_parameter_value_assignment
        FOREIGN KEY (team_task_id) REFERENCES team_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_task_parameter_value_parameter
        FOREIGN KEY (parameter_id) REFERENCES task_parameter(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_task_parameter_value_assignment_parameter
        UNIQUE (team_task_id, parameter_id),
    CONSTRAINT chk_team_task_parameter_value
        CHECK (btrim(value) <> '')
);

CREATE INDEX idx_team_task_parameter_value_parameter
    ON team_task_parameter_value(parameter_id);

CREATE TABLE submission_attempt (
    id UUID PRIMARY KEY,
    team_task_id UUID NOT NULL,
    submitted_by UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    content TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_submission_attempt_assignment
        FOREIGN KEY (team_task_id) REFERENCES team_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_submission_attempt_author
        FOREIGN KEY (submitted_by) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT uq_submission_assignment_attempt
        UNIQUE (team_task_id, attempt_number),
    CONSTRAINT chk_submission_attempt_number CHECK (attempt_number > 0)
);

CREATE INDEX idx_submission_attempt_author ON submission_attempt(submitted_by);

CREATE TABLE submission_attachment (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_submission_attachment_submission
        FOREIGN KEY (submission_id)
        REFERENCES submission_attempt(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_submission_attachment_object_key UNIQUE (object_key),
    CONSTRAINT chk_submission_attachment_key CHECK (btrim(object_key) <> ''),
    CONSTRAINT chk_submission_attachment_name CHECK (btrim(original_name) <> ''),
    CONSTRAINT chk_submission_attachment_mime_type CHECK (btrim(mime_type) <> ''),
    CONSTRAINT chk_submission_attachment_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_submission_attachment_submission
    ON submission_attachment(submission_id);

CREATE TABLE review (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    decision VARCHAR(30) NOT NULL,
    comment TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_review_submission
        FOREIGN KEY (submission_id)
        REFERENCES submission_attempt(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewer
        FOREIGN KEY (reviewer_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT uq_review_submission UNIQUE (submission_id),
    CONSTRAINT chk_review_decision
        CHECK (decision IN ('ACCEPTED', 'REVISION_REQUIRED')),
    CONSTRAINT chk_review_revision_comment
        CHECK (
            decision = 'ACCEPTED'
            OR nullif(btrim(comment), '') IS NOT NULL
        )
);

CREATE INDEX idx_review_reviewer ON review(reviewer_id);

CREATE TABLE announcement (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_announcement_task
        FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_author
        FOREIGN KEY (author_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT chk_announcement_title CHECK (btrim(title) <> ''),
    CONSTRAINT chk_announcement_content CHECK (btrim(content) <> '')
);

CREATE INDEX idx_announcement_task_created
    ON announcement(task_id, created_at);

CREATE TABLE discussion_message (
    id UUID PRIMARY KEY,
    team_task_id UUID NOT NULL,
    author_id UUID NOT NULL,
    submission_id UUID,
    review_id UUID,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_discussion_message_assignment
        FOREIGN KEY (team_task_id) REFERENCES team_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_message_author
        FOREIGN KEY (author_id) REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_discussion_message_submission
        FOREIGN KEY (submission_id)
        REFERENCES submission_attempt(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_discussion_message_review
        FOREIGN KEY (review_id) REFERENCES review(id) ON DELETE SET NULL,
    CONSTRAINT chk_discussion_message_target
        CHECK (num_nonnulls(submission_id, review_id) <= 1),
    CONSTRAINT chk_discussion_message_content CHECK (btrim(content) <> '')
);

CREATE INDEX idx_discussion_message_assignment_created
    ON discussion_message(team_task_id, created_at);
CREATE INDEX idx_discussion_message_submission
    ON discussion_message(submission_id)
    WHERE submission_id IS NOT NULL;
CREATE INDEX idx_discussion_message_review
    ON discussion_message(review_id)
    WHERE review_id IS NOT NULL;
