package ru.itmo.trace.assignments

import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.http.*
import org.springframework.http.HttpStatus.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.itmo.trace.identity.Actor

data class Scope(val manager: Boolean, val member: Boolean, val taskId: UUID, val author: UUID)

@Service
class AssignmentAccess(private val db: JdbcTemplate) {
    fun read(id: UUID, actor: Actor): Scope {
        val access =
            db.query(
                """SELECT p.manager_id,t.id task_id,t.created_by,
            EXISTS(SELECT 1 FROM team_member m WHERE m.team_id=a.team_id AND m.user_id=?) AS member
            FROM team_task a JOIN task t ON t.id=a.task_id JOIN project p ON p.id=t.project_id WHERE a.id=?""",
                { r, _ ->
                    Scope(
                        r.getObject("manager_id") == actor.id,
                        r.getBoolean("member"),
                        r.getObject("task_id", UUID::class.java),
                        r.getObject("created_by", UUID::class.java),
                    )
                },
                actor.id,
                id,
            )
                .singleOrNull() ?: throw ResponseStatusException(NOT_FOUND)
        if (!access.manager && !access.member) throw ResponseStatusException(FORBIDDEN)
        return access
    }
}

data class Review(val decision: String, val comment: String? = null)

@Service
class ExecutionService(
    private val db: JdbcTemplate,
    private val access: AssignmentAccess,
    private val storage: AttachmentStore,
) {
    fun detail(id: UUID, actor: Actor): Map<String, Any?> {
        val scope = access.read(id, actor)
        val task =
            db.queryForMap(
                """SELECT t.title,t.instructions,t.deadline_at,a.status,tm.name team,
            tpl.description,tpl.expected_result FROM team_task a JOIN task t ON t.id=a.task_id
            JOIN team tm ON tm.id=a.team_id JOIN task_template tpl ON tpl.id=t.template_id WHERE a.id=?""",
                id,
            )
        val files =
            db.queryForList(
                """SELECT f.id,f.original_name,f.size_bytes,f.submission_id FROM submission_attachment f
            JOIN submission_attempt s ON s.id=f.submission_id WHERE s.team_task_id=? ORDER BY f.id""",
                id,
            )
                .groupBy { it["submission_id"] }
        val attempts =
            db.queryForList(
                """SELECT s.id,s.attempt_number,s.content,s.submitted_at,u.display_name,
            r.id review_id,r.decision,r.comment,r.reviewed_at FROM submission_attempt s
            JOIN app_user u ON u.id=s.submitted_by LEFT JOIN review r ON r.submission_id=s.id
            WHERE s.team_task_id=? ORDER BY s.attempt_number DESC""",
                id,
            )
                .map { it + ("files" to files[it["id"]].orEmpty()) }
        val parameters =
            db.queryForList(
                """SELECT p.name,v.value FROM team_task_parameter_value v
            JOIN task_parameter p ON p.id=v.parameter_id WHERE v.team_task_id=? ORDER BY p.name""",
                id,
            )
        return task + mapOf("scope" to scope, "attempts" to attempts, "parameters" to parameters)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun submit(id: UUID, actor: Actor, content: String, files: List<MultipartFile>): UUID {
        if (!access.read(id, actor).member) throw ResponseStatusException(FORBIDDEN)
        if ((content.isBlank() && files.isEmpty()) || files.any { it.isEmpty })
            throw ResponseStatusException(BAD_REQUEST)
        val submission =
            db.queryForObject(
                "SELECT submit_task(?,?,?)",
                UUID::class.java,
                id,
                actor.id,
                content,
            )!!
        for (file in files) {
            val name =
                file.originalFilename
                    .orEmpty()
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .filter { !it.isISOControl() }
                    .take(255)
                    .ifBlank { "file" }
            val key = file.inputStream.use(storage::put)
            db.update(
                """INSERT INTO submission_attachment(submission_id,object_key,original_name,mime_type,size_bytes)
                VALUES (?,?,?,?,?)""",
                submission,
                key.toString(),
                name,
                file.contentType ?: "application/octet-stream",
                file.size,
            )
        }
        return submission
    }

    @Transactional
    fun review(id: UUID, attempt: UUID, actor: Actor, input: Review): UUID {
        if (!access.read(id, actor).manager) throw ResponseStatusException(FORBIDDEN)
        if (
            input.decision !in setOf("accepted", "revision_required") ||
            (input.decision == "revision_required" && input.comment.isNullOrBlank())
        )
            throw ResponseStatusException(BAD_REQUEST)
        return db.queryForList(
            """SELECT review_submission(s.id,?,?,?) FROM submission_attempt s
            WHERE s.id=? AND s.team_task_id=?""",
            UUID::class.java,
            actor.id,
            input.decision,
            input.comment,
            attempt,
            id,
        )
            .singleOrNull() ?: throw ResponseStatusException(NOT_FOUND)
    }

    fun download(id: UUID, file: UUID, actor: Actor): ResponseEntity<ByteArray> {
        access.read(id, actor)
        return db.query(
            """SELECT f.original_name,f.object_key FROM submission_attachment f
            JOIN submission_attempt s ON s.id=f.submission_id
            WHERE f.id=? AND s.team_task_id=?""",
            { r, _ ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                            .filename(r.getString(1), StandardCharsets.UTF_8)
                            .build()
                            .toString(),
                    )
                    .body(storage.read(UUID.fromString(r.getString(2))))
            },
            file,
            id,
        )
            .singleOrNull() ?: throw ResponseStatusException(NOT_FOUND)
    }
}

@RestController
@RequestMapping("/api/assignments/{id}")
class Execution(private val service: ExecutionService) {
    @GetMapping
    fun detail(@PathVariable id: UUID, actor: Actor) = service.detail(id, actor)

    @PostMapping("/attempts")
    fun submit(
        @PathVariable id: UUID,
        actor: Actor,
        @RequestParam(defaultValue = "") content: String,
        @RequestParam(required = false) files: List<MultipartFile>?,
    ) = service.submit(id, actor, content, files.orEmpty())

    @PostMapping("/attempts/{attempt}/review")
    fun review(
        @PathVariable id: UUID,
        @PathVariable attempt: UUID,
        actor: Actor,
        @RequestBody input: Review,
    ) = service.review(id, attempt, actor, input)

    @GetMapping("/files/{file}")
    fun download(@PathVariable id: UUID, @PathVariable file: UUID, actor: Actor) =
        service.download(id, file, actor)
}
