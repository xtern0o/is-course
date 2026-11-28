package org.example.backendv2.dto.user.response

import org.example.backendv2.entity.enums.UserRole
import java.util.UUID

data class UserSummaryResponse(
    val id: UUID,
    val username: String,
    val displayName: String,
    val role: UserRole,
)