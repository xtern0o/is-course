package org.example.backendv2.dto.user.response

import org.example.backendv2.entity.enums.UserRole
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val username: String,
    val displayName: String,
    val email: String,
    val role: UserRole,
    val createdAt: Instant,
)
