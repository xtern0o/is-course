package org.example.backendv2.service.jwt

import org.example.backendv2.entity.enums.UserRole

data class AuthenticatedUserClaims(
    val subject: String,
    val username: String,
    val displayName: String,
    val email: String,
    val role: UserRole,
)
