--liquibase formatted sql

--changeset trace:012-create-triggers
CREATE TRIGGER trg_validate_default_value_type
BEFORE INSERT OR UPDATE OF data_type, default_value
ON task_parameter
FOR EACH ROW
EXECUTE FUNCTION validate_default_value_type();

CREATE TRIGGER trg_validate_team_task_project
BEFORE INSERT OR UPDATE OF team_id, task_id
ON team_task
FOR EACH ROW
EXECUTE FUNCTION validate_team_task_project();

CREATE TRIGGER trg_validate_team_task_parameter
BEFORE INSERT OR UPDATE OF team_task_id, parameter_id
ON team_task_parameter_value
FOR EACH ROW
EXECUTE FUNCTION validate_team_task_parameter();

CREATE TRIGGER trg_validate_parameter_value
BEFORE INSERT OR UPDATE OF parameter_id, value
ON team_task_parameter_value
FOR EACH ROW
EXECUTE FUNCTION validate_parameter_value();

CREATE TRIGGER trg_validate_submission_author
BEFORE INSERT OR UPDATE OF team_task_id, submitted_by
ON submission_attempt
FOR EACH ROW
EXECUTE FUNCTION validate_submission_author();

CREATE TRIGGER trg_mark_team_task_submitted
AFTER INSERT
ON submission_attempt
FOR EACH ROW
EXECUTE FUNCTION mark_team_task_submitted();

CREATE TRIGGER trg_validate_reviewer
BEFORE INSERT OR UPDATE OF submission_id, reviewer_id
ON review
FOR EACH ROW
EXECUTE FUNCTION validate_reviewer();

CREATE TRIGGER trg_update_team_task_status
AFTER INSERT OR UPDATE OF decision
ON review
FOR EACH ROW
EXECUTE FUNCTION update_team_task_status();

CREATE TRIGGER trg_validate_message_scope
BEFORE INSERT OR UPDATE OF team_task_id, submission_id, review_id
ON discussion_message
FOR EACH ROW
EXECUTE FUNCTION validate_message_scope();

