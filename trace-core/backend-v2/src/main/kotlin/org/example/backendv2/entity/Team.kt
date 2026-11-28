package org.example.backendv2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.example.backendv2.dto.team.response.TeamResponse
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "team",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_team_project_name",
            columnNames = ["project_id", "name"],
        ),
    ],
)
class Team(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @field:NotBlank
    @field:Size(max = 255)
    @field:Column(nullable = false, length = 255)
    var name: String,

    @field:Column(columnDefinition = "text")
    var description: String? = null,

    @field:Column(name = "recruitment_open", nullable = false)
    var recruitmentOpen: Boolean = true,

    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

fun Team.toResponse(): TeamResponse =
    TeamResponse(
        id = requireNotNull(id),
        projectId = requireNotNull(project.id),
        name = name,
        description = description,
        recruitmentOpen = recruitmentOpen,
        createdAt = requireNotNull(createdAt),
    )