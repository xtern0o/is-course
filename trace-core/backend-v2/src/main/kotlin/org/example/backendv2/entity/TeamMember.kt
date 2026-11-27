package org.example.backendv2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "team_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_team_member_team_user",
            columnNames = ["team_id", "user_id"],
        ),
    ],
)
class TeamMember(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "team_id", nullable = false)
    var team: Team,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @field:CreationTimestamp
    @field:Column(name = "joined_at", nullable = false, updatable = false)
    var joinedAt: Instant? = null,
)
