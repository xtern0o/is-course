package org.example.backendv2.dto.project.response

import org.example.backendv2.entity.enums.ProjectStatus
import org.example.backendv2.entity.enums.ProjectType
import java.time.Instant
import java.util.UUID

data class ProjectSummaryResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val type: ProjectType,
    val status: ProjectStatus,
    val createdAt: Instant,
)