package ru.itmo.trace.projects

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus.*
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.itmo.trace.identity.Actor
import java.util.UUID

data class NewProject(
    @field:NotBlank @field:Size(max = 255) val name: String,
    val type: String, val goal: String? = null, val description: String? = null
)

data class ProjectPage(val items: List<ProjectView>, val page: Int, val totalPages: Int)

@Service
class ProjectService(private val projects: ProjectRepository, private val jdbc: JdbcClient) {
    fun catalog(page: Int, size: Int): ProjectPage {
        if (page < 0 || size !in 1..100) throw ResponseStatusException(BAD_REQUEST)
        val result = projects.catalog(PageRequest.of(page, size))
        return ProjectPage(result.content, page, result.totalPages)
    }

    fun card(id: UUID) = projects.card(id) ?: throw ResponseStatusException(NOT_FOUND)

    fun requireManager(id: UUID, userId: UUID) {
        if (card(id).managerId != userId) throw ResponseStatusException(FORBIDDEN)
    }

    @Transactional
    fun create(actor: Actor, data: NewProject): ProjectView {
        if (data.type !in setOf("educational", "development")) throw ResponseStatusException(BAD_REQUEST)
        val role = if (data.type == "educational") "teacher" else "student"
        if (actor.role != role) throw ResponseStatusException(FORBIDDEN)
        val id = jdbc.sql(
            """
            INSERT INTO project(name, type, goal, description, manager_id)
            VALUES (:name,:type,:goal,:description,:manager) RETURNING id
        """
        ).param("name", data.name.trim()).param("type", data.type)
            .param("goal", data.goal).param("description", data.description)
            .param("manager", actor.id).query(UUID::class.java).single()
        return card(id)
    }

    @Transactional
    fun edit(id: UUID, actor: Actor, changes: Map<String, String?>): ProjectView {
        requireManager(id, actor.id)
        if (changes.isEmpty() || changes.keys.any { it !in setOf("name", "goal", "description") })
            throw ResponseStatusException(BAD_REQUEST)
        val p = projects.findById(id).orElseThrow()
        if ("name" in changes) {
            val name = changes["name"]?.trim().orEmpty()
            if (name.isBlank() || name.length > 255) throw ResponseStatusException(BAD_REQUEST)
            p.name = name
        }
        if ("goal" in changes) p.goal = changes["goal"]
        if ("description" in changes) p.description = changes["description"]
        projects.flush()
        return card(id)
    }
}
