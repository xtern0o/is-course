package ru.itmo.trace.projects

import jakarta.persistence.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "project")
class Project(
    @Id val id: UUID,
    var name: String,
    var description: String?,
    var goal: String?,
    val type: String,
    val status: String,
    val managerId: UUID,
    val createdAt: Instant
)

interface ProjectView {
    val id: UUID
    val name: String
    val description: String?
    val goal: String?
    val type: String
    val status: String
    val managerId: UUID
    val managerName: String
}

private const val CARD = """
    SELECT p.id, p.name, p.description, p.goal, p.type, p.status,
           p.manager_id AS "managerId", u.display_name AS "managerName"
    FROM project p JOIN app_user u ON u.id=p.manager_id
"""

interface ProjectRepository : JpaRepository<Project, UUID> {
    @Query(
        value = CARD + " ORDER BY p.created_at DESC, p.id DESC",
        countQuery = "SELECT count(*) FROM project", nativeQuery = true
    )
    fun catalog(pageable: Pageable): Page<ProjectView>

    @Query(value = CARD + " WHERE p.id=:id", nativeQuery = true)
    fun card(id: UUID): ProjectView?
}
