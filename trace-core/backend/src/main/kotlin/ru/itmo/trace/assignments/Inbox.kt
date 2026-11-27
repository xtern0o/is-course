package ru.itmo.trace.assignments

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import ru.itmo.trace.identity.Actor

@RestController
class Inbox(private val db: JdbcTemplate) {
    @GetMapping("/api/me/inbox")
    fun inbox(actor: Actor) =
        db.queryForList(
            """SELECT a.id,p.id project_id,p.name project,t.title,
        tm.name team,a.status,t.deadline_at,(p.manager_id=? AND a.status='submitted') review,
        (SELECT r.comment FROM review r JOIN submission_attempt s ON s.id=r.submission_id
         WHERE s.team_task_id=a.id ORDER BY s.attempt_number DESC LIMIT 1) feedback
        FROM team_task a JOIN task t ON a.task_id=t.id JOIN project p ON p.id=t.project_id JOIN team tm ON tm.id=a.team_id
        WHERE (p.manager_id=? AND a.status='submitted') OR (a.status IN ('assigned','in_progress','revision_required','submitted') AND EXISTS
        (SELECT 1 FROM team_member m WHERE m.team_id=a.team_id AND m.user_id=?))
        ORDER BY CASE WHEN a.status='revision_required' THEN 0 WHEN a.status='submitted' THEN 1 ELSE 2 END,t.deadline_at NULLS LAST,a.id""",
            actor.id,
            actor.id,
            actor.id,
        )

    @GetMapping("/api/me/pending-applications")
    fun applications(actor: Actor) =
        db.queryForList(
            """SELECT a.id,p.id project_id,p.name project,t.name team,u.display_name
        FROM team_application a JOIN team t ON t.id=a.team_id JOIN project p ON p.id=t.project_id
        JOIN app_user u ON u.id=a.applicant_id WHERE p.manager_id=? AND a.status='pending' ORDER BY a.created_at""",
            actor.id,
        )
}
