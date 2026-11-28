package org.example.backendv2.repository.projection

import org.example.backendv2.dto.user.response.UserSummaryResponse
import org.example.backendv2.entity.enums.UserRole
import java.util.UUID

interface UserSummaryProjection {
    val id: UUID
    val username: String
    val displayName: String
    val role: UserRole
}

fun UserSummaryProjection.toResponse(): UserSummaryResponse =
    UserSummaryResponse(
        id = id,
        username = username,
        displayName = displayName,
        role = role,
    )