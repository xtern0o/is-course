package org.example.backendv2.repository.projection

import org.example.backendv2.dto.project.response.ProjectSummaryResponse
import org.example.backendv2.entity.enums.ProjectStatus
import org.example.backendv2.entity.enums.ProjectType
import java.time.Instant
import java.util.UUID

interface ProjectSummaryProjection {
    val id: UUID
    val name: String
    val description: String?
    val type: ProjectType
    val status: ProjectStatus
    val createdAt: Instant
}

fun ProjectSummaryProjection.toResponse(): ProjectSummaryResponse =
    ProjectSummaryResponse(
        id = id,
        name = name,
        description = description,
        type = type,
        status = status,
        createdAt = createdAt,
    )