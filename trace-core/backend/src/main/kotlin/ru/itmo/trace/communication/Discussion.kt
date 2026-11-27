package ru.itmo.trace.communication

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.util.UUID
import org.springframework.http.HttpStatus.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.itmo.trace.assignments.AssignmentAccess
import ru.itmo.trace.identity.Actor

data class Message(
    @field:NotBlank val content: String,
    val submissionId: UUID? = null,
    val reviewId: UUID? = null,
)

data class Notice(@field:NotBlank val title: String, @field:NotBlank val content: String)

@RestController
@RequestMapping("/api/assignments/{id}")
class Discussion(private val service: DiscussionService) {
    @GetMapping("/discussion")
    fun history(@PathVariable id: UUID, actor: Actor) = service.history(id, actor)

    @PostMapping("/messages")
    fun post(@PathVariable id: UUID, actor: Actor, @Valid @RequestBody message: Message) =
        service.post(id, actor, message)

    @PostMapping("/notices")
    fun announce(@PathVariable id: UUID, actor: Actor, @Valid @RequestBody notice: Notice) =
        service.announce(id, actor, notice)
}

@org.springframework.stereotype.Service
class DiscussionService(private val access: AssignmentAccess, private val db: JdbcTemplate) {
    fun history(id: UUID, actor: Actor): Map<String, Any> {
        val scope = access.read(id, actor)
        return mapOf(
            "messages" to
                    db.queryForList(
                        """SELECT m.*,u.display_name FROM discussion_message m
            JOIN app_user u ON u.id=m.author_id WHERE team_task_id=? ORDER BY m.created_at,m.id""",
                        id,
                    ),
            "notices" to
                    db.queryForList(
                        "SELECT title,content,created_at FROM announcement WHERE task_id=? ORDER BY created_at DESC,id",
                        scope.taskId,
                    ),
        )
    }

    fun post(id: UUID, actor: Actor, message: Message) {
        access.read(id, actor)
        db.update(
            """INSERT INTO discussion_message(team_task_id,author_id,content,submission_id,review_id)
            VALUES (?,?,?,?,?)""",
            id,
            actor.id,
            message.content.trim(),
            message.submissionId,
            message.reviewId,
        )
    }

    fun announce(id: UUID, actor: Actor, notice: Notice) {
        val scope = access.read(id, actor)
        if (!scope.manager && scope.author != actor.id) throw ResponseStatusException(FORBIDDEN)
        db.update(
            "INSERT INTO announcement(task_id,author_id,title,content) VALUES (?,?,?,?)",
            scope.taskId,
            actor.id,
            notice.title.trim(),
            notice.content.trim(),
        )
    }
}
