package org.example.backendv2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.example.backendv2.dto.user.response.UserResponse
import org.example.backendv2.dto.user.response.UserSummaryResponse
import org.example.backendv2.entity.enums.UserRole
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_user")
class User(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotBlank
    @field:Size(max = 255)
    @field:Column(name = "sso_subject", nullable = false, unique = true, length = 255)
    var ssoSubject: String,

    @field:NotBlank
    @field:Size(max = 100)
    @field:Column(nullable = false, unique = true, length = 100)
    var username: String,

    @field:NotBlank
    @field:Size(max = 255)
    @field:Column(name = "display_name", nullable = false, length = 255)
    var displayName: String,

    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    @field:Column(nullable = false, unique = true, length = 255)
    var email: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 30)
    var role: UserRole = UserRole.STUDENT,

    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

fun User.toResponse(): UserResponse =
    UserResponse(
        id = requireNotNull(id),
        username = username,
        displayName = displayName,
        email = email,
        role = role,
        createdAt = requireNotNull(createdAt),
    )

fun User.toSummaryResponse(): UserSummaryResponse =
    UserSummaryResponse(
        id = requireNotNull(id),
        username = username,
        displayName = displayName,
        role = role,
    )