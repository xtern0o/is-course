package org.example.backendv2.repository.projection

import org.example.backendv2.dto.team.response.TeamSummaryResponse
import java.util.UUID

interface TeamSummaryProjection {
    val id: UUID
    val projectId: UUID
    val name: String
    val description: String?
    val recruitmentOpen: Boolean
}

fun TeamSummaryProjection.toResponse(): TeamSummaryResponse =
    TeamSummaryResponse(
        id = id,
        projectId = projectId,
        name = name,
        description = description,
        recruitmentOpen = recruitmentOpen,
    )

