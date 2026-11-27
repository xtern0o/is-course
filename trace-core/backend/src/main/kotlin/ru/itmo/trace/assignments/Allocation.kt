package ru.itmo.trace.assignments

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpStatus.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.itmo.trace.identity.Actor
import ru.itmo.trace.projects.ProjectService

data class Parameter(
    @field:NotBlank @field:Size(max = 100) val name: String,
    val type: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val description: String? = null,
)

data class Template(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val description: String? = null,
    val expectedResult: String? = null,
    @field:Valid val parameters: List<Parameter> = emptyList(),
)

data class Target(val teamId: UUID, val values: Map<UUID, String> = emptyMap())

data class Task(
    val templateId: UUID,
    @field:NotBlank @field:Size(max = 255) val title: String,
    @field:NotBlank val instructions: String,
    val deadline: OffsetDateTime? = null,
    val teams: List<Target> = emptyList(),
)

data class Distribution(@field:NotEmpty val teams: List<Target>)

@Service
class AllocationService(private val db: JdbcTemplate, private val projects: ProjectService) {
    private fun requireTemplateAuthor(project: UUID, actor: Actor) {
        if (actor.role == "teacher") projects.card(project)
        else projects.requireManager(project, actor.id)
    }

    fun templates(project: UUID, actor: Actor): List<Map<String, Any?>> {
        requireTemplateAuthor(project, actor)
        val params =
            db.queryForList(
                """SELECT p.* FROM task_parameter p JOIN task_template t ON t.id=p.template_id
            WHERE t.created_by=? ORDER BY p.name""",
                actor.id,
            )
                .groupBy { it["template_id"] }
        return db.queryForList(
            "SELECT * FROM task_template WHERE created_by=? ORDER BY title,id",
            actor.id,
        )
            .map { it + ("parameters" to params[it["id"]].orEmpty()) }
    }

    @Transactional
    fun template(project: UUID, actor: Actor, t: Template): UUID {
        requireTemplateAuthor(project, actor)
        val id =
            db.queryForObject(
                """INSERT INTO task_template(created_by,title,description,expected_result)
            VALUES (?,?,?,?) RETURNING id""",
                UUID::class.java,
                actor.id,
                t.title,
                t.description,
                t.expectedResult,
            )!!
        for (p in t.parameters) {
            db.update(
                """INSERT INTO task_parameter(template_id,name,data_type,required,default_value,description)
                VALUES (?,?,?,?,?,?)""",
                id,
                p.name.trim(),
                p.type,
                p.required,
                p.defaultValue,
                p.description,
            )
        }
        return id
    }

    @Transactional
    fun assign(project: UUID, actor: Actor, task: Task): UUID {
        projects.requireManager(project, actor.id)
        if (
            db.queryForObject(
                "SELECT count(*) FROM task_template WHERE id=? AND created_by=?",
                Int::class.java,
                task.templateId,
                actor.id,
            ) != 1
        )
            throw ResponseStatusException(BAD_REQUEST)
        val id =
            db.queryForObject(
                """INSERT INTO task(project_id,created_by,template_id,title,instructions,deadline_at)
            VALUES (?,?,?,?,?,?) RETURNING id""",
                UUID::class.java,
                project,
                actor.id,
                task.templateId,
                task.title,
                task.instructions,
                task.deadline,
            )!!
        allocate(id, task.templateId, task.teams)
        return id
    }

    fun tasks(project: UUID, actor: Actor): List<Map<String, Any?>> {
        projects.requireManager(project, actor.id)
        return db.queryForList(
            "SELECT id,title,template_id FROM task WHERE project_id=? ORDER BY created_at DESC,id",
            project,
        )
    }

    @Transactional
    fun distribute(project: UUID, task: UUID, actor: Actor, targets: List<Target>) {
        projects.requireManager(project, actor.id)
        val template =
            db.queryForList(
                "SELECT template_id FROM task WHERE project_id=? AND id=?",
                UUID::class.java,
                project,
                task,
            )
                .singleOrNull() ?: throw ResponseStatusException(NOT_FOUND)
        allocate(task, template, targets)
    }

    private fun allocate(id: UUID, template: UUID, targets: List<Target>) {
        val params = db.queryForList("SELECT * FROM task_parameter WHERE template_id=?", template)
        val keys = params.map { it["id"] }.toSet()
        for (target in targets) {
            if (!keys.containsAll(target.values.keys)) throw ResponseStatusException(BAD_REQUEST)
            val assignment =
                db.queryForObject(
                    "INSERT INTO team_task(team_id,task_id) VALUES (?,?) RETURNING id",
                    UUID::class.java,
                    target.teamId,
                    id,
                )!!
            for (p in params) {
                val value =
                    target.values[p["id"]]?.takeIf { it.isNotBlank() }
                        ?: p["default_value"] as String?
                if (p["required"] == true && value.isNullOrBlank())
                    throw ResponseStatusException(BAD_REQUEST)
                if (value != null)
                    db.update(
                        """INSERT INTO team_task_parameter_value(team_task_id,parameter_id,value)
                    VALUES (?,?,?)""",
                        assignment,
                        p["id"],
                        value,
                    )
            }
        }
    }

    fun list(project: UUID, actor: Actor) =
        db.queryForList(
            """
        SELECT tt.id,tt.team_id,tt.status,t.title,t.instructions,t.deadline_at,t.id task_id,tm.name team
        FROM team_task tt JOIN task t ON t.id=tt.task_id JOIN project p ON p.id=t.project_id
        JOIN team tm ON tm.id=tt.team_id WHERE p.id=? AND (p.manager_id=? OR EXISTS
        (SELECT 1 FROM team_member m WHERE m.team_id=tm.id AND m.user_id=?))
        ORDER BY tt.assigned_at DESC,tt.id
    """,
            project,
            actor.id,
            actor.id,
        )
}

@RestController
@RequestMapping("/api/projects/{id}")
class Allocation(private val service: AllocationService) {
    @GetMapping("/templates")
    fun templates(@PathVariable id: UUID, actor: Actor) = service.templates(id, actor)

    @PostMapping("/templates")
    fun create(@PathVariable id: UUID, actor: Actor, @RequestBody @Valid t: Template) =
        service.template(id, actor, t)

    @PostMapping("/tasks")
    fun assign(@PathVariable id: UUID, actor: Actor, @RequestBody @Valid t: Task) =
        service.assign(id, actor, t)

    @GetMapping("/tasks")
    fun tasks(@PathVariable id: UUID, actor: Actor) = service.tasks(id, actor)

    @PostMapping("/tasks/{task}/assignments")
    fun distribute(
        @PathVariable id: UUID,
        @PathVariable task: UUID,
        actor: Actor,
        @Valid @RequestBody input: Distribution,
    ) = service.distribute(id, task, actor, input.teams)

    @GetMapping("/assignments")
    fun list(@PathVariable id: UUID, actor: Actor) = service.list(id, actor)
}
