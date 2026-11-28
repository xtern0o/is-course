package org.example.backendv2.dto.team.response

import java.time.Instant
import java.util.UUID

data class TeamResponse(
    val id: UUID,
    val projectId: UUID,
    val name: String,
    val description: String?,
    val recruitmentOpen: Boolean,
    val createdAt: Instant,
)