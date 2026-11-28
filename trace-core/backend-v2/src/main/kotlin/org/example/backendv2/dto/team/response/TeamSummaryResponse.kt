package org.example.backendv2.dto.team.response

import java.util.UUID

data class TeamSummaryResponse(
    val id: UUID,
    val projectId: UUID,
    val name: String,
    val description: String?,
    val recruitmentOpen: Boolean,
)