package ru.itmo.trace.teams

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.UUID
import org.springframework.http.HttpStatus.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.itmo.trace.identity.Actor
import ru.itmo.trace.projects.ProjectService

data class NewTeam(
    @field:NotBlank @field:Size(max = 255) val name: String,
    val description: String? = null,
)

data class Recruitment(val open: Boolean)

data class Application(val comment: String? = null)

data class Decision(val decision: String, val comment: String? = null)

data class Member(val userId: UUID)

@Service
class TeamService(private val db: JdbcTemplate, private val projects: ProjectService) {
    fun teams(project: UUID, actor: Actor) =
        db.queryForList(
            """
        SELECT t.id,t.name,t.description,t.recruitment_open,
            EXISTS(SELECT 1 FROM team_member WHERE team_id=t.id AND user_id=?) AS joined,
            (SELECT status FROM team_application WHERE team_id=t.id AND applicant_id=? ORDER BY created_at DESC,id DESC LIMIT 1) AS application,
            (SELECT count(*) FROM team_member WHERE team_id=t.id) AS members
        FROM team t WHERE project_id=? ORDER BY name,id
    """,
            actor.id,
            actor.id,
            project,
        )

    fun roster(team: UUID) =
        db.queryForList(
            """
        SELECT u.id,u.display_name,CASE WHEN u.share_intro THEN u.specialty END AS specialty,
               CASE WHEN u.share_intro THEN u.bio END AS bio FROM team_member m JOIN app_user u ON u.id=m.user_id
        WHERE m.team_id=? ORDER BY u.display_name,u.id
    """,
            team,
        )

    fun mine(actor: Actor) =
        db.queryForList(
            """
        SELECT t.id,t.name,p.id project_id,p.name project_name FROM team_member m
        JOIN team t ON t.id=m.team_id JOIN project p ON p.id=t.project_id WHERE m.user_id=? ORDER BY p.name,t.name
    """,
            actor.id,
        )

    fun applications(project: UUID, actor: Actor) =
        db.queryForList(
            """
        SELECT a.id,a.status,a.comment,a.created_at,t.name team,u.display_name applicant
        FROM team_application a JOIN team t ON t.id=a.team_id JOIN project p ON p.id=t.project_id
        JOIN app_user u ON u.id=a.applicant_id WHERE p.id=? AND (p.manager_id=? OR a.applicant_id=?)
        ORDER BY a.created_at DESC,a.id DESC
    """,
            project,
            actor.id,
            actor.id,
        )

    fun myApplications(actor: Actor) =
        db.queryForList(
            """
        SELECT a.id,a.status,a.created_at,t.name team,p.id project_id,p.name project_name
        FROM team_application a JOIN team t ON t.id=a.team_id JOIN project p ON p.id=t.project_id
        WHERE a.applicant_id=? ORDER BY a.created_at DESC,a.id DESC
    """,
            actor.id,
        )

    @Transactional
    fun create(project: UUID, actor: Actor, input: NewTeam): UUID {
        projects.requireManager(project, actor.id)
        return db.queryForObject(
            "INSERT INTO team(project_id,name,description) VALUES (?,?,?) RETURNING id",
            UUID::class.java,
            project,
            input.name.trim(),
            input.description,
        )!!
    }

    private fun lock(team: UUID): UUID =
        db.queryForList("SELECT project_id FROM team WHERE id=? FOR UPDATE", UUID::class.java, team)
            .singleOrNull() ?: throw ResponseStatusException(NOT_FOUND)

    @Transactional
    fun recruitment(team: UUID, actor: Actor, open: Boolean) {
        projects.requireManager(lock(team), actor.id)
        db.update("UPDATE team SET recruitment_open=? WHERE id=?", open, team)
    }

    @Transactional
    fun apply(team: UUID, actor: Actor, comment: String?): UUID {
        lock(team)
        return db.queryForObject(
            "SELECT apply_to_team(?,?,?)",
            UUID::class.java,
            team,
            actor.id,
            comment,
        )!!
    }

    @Transactional
    fun decide(id: UUID, actor: Actor, input: Decision) {
        val team =
            db.queryForList("SELECT team_id FROM team_application WHERE id=?", UUID::class.java, id)
                .singleOrNull() ?: throw ResponseStatusException(NOT_FOUND)
        projects.requireManager(lock(team), actor.id)
        if (input.decision !in setOf("accepted", "rejected"))
            throw ResponseStatusException(BAD_REQUEST)
        db.update(
            "CALL review_team_application(?,?,?,?)",
            id,
            actor.id,
            input.decision,
            input.comment,
        )
    }

    fun students(project: UUID, actor: Actor): List<Map<String, Any?>> {
        projects.requireManager(project, actor.id)
        if (actor.role != "teacher" || projects.card(project).type != "educational")
            throw ResponseStatusException(FORBIDDEN)
        return db.queryForList(
            "SELECT id,display_name FROM app_user WHERE role='student' AND enabled ORDER BY display_name,id"
        )
    }

    @Transactional
    fun addStudent(team: UUID, actor: Actor, student: UUID) {
        val project = lock(team)
        projects.requireManager(project, actor.id)
        if (actor.role != "teacher" || projects.card(project).type != "educational")
            throw ResponseStatusException(FORBIDDEN)
        if (
            db.update(
                """INSERT INTO team_member(team_id,user_id) SELECT ?,id FROM app_user
            WHERE id=? AND role='student' AND enabled ON CONFLICT DO NOTHING""",
                team,
                student,
            ) != 1
        )
            throw ResponseStatusException(CONFLICT)
        db.update(
            """UPDATE team_application SET status='accepted',reviewed_at=now(),reviewed_by=?
            WHERE team_id=? AND applicant_id=? AND status='pending'""",
            actor.id,
            team,
            student,
        )
    }
}

@RestController
@RequestMapping("/api")
class Teams(private val service: TeamService) {
    @GetMapping("/projects/{id}/teams")
    fun list(@PathVariable id: UUID, actor: Actor) = service.teams(id, actor)

    @GetMapping("/me/applications")
    fun myApplications(actor: Actor) = service.myApplications(actor)

    @GetMapping("/me/teams")
    fun mine(actor: Actor) = service.mine(actor)

    @GetMapping("/teams/{id}/members")
    fun roster(@PathVariable id: UUID) = service.roster(id)

    @GetMapping("/projects/{id}/students")
    fun students(@PathVariable id: UUID, actor: Actor) = service.students(id, actor)

    @GetMapping("/projects/{id}/applications")
    fun applications(@PathVariable id: UUID, actor: Actor) = service.applications(id, actor)

    @PostMapping("/projects/{id}/teams")
    fun create(@PathVariable id: UUID, actor: Actor, @Valid @RequestBody data: NewTeam) =
        service.create(id, actor, data)

    @PatchMapping("/teams/{id}/recruitment")
    fun open(@PathVariable id: UUID, actor: Actor, @RequestBody data: Recruitment) =
        service.recruitment(id, actor, data.open)

    @PostMapping("/teams/{id}/applications")
    fun apply(@PathVariable id: UUID, actor: Actor, @RequestBody data: Application) =
        service.apply(id, actor, data.comment)

    @PostMapping("/applications/{id}/decision")
    fun decide(@PathVariable id: UUID, actor: Actor, @RequestBody data: Decision) =
        service.decide(id, actor, data)

    @PostMapping("/teams/{id}/members")
    fun add(@PathVariable id: UUID, actor: Actor, @RequestBody data: Member) =
        service.addStudent(id, actor, data.userId)
}
