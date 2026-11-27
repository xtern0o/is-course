package org.example.backendv2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotNull
import org.example.backendv2.entity.enums.AssignmentStatus
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "team_task",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_assignment_team_task",
            columnNames = ["team_id", "task_id"],
        ),
    ],
)
class Assignment(
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
    @field:JoinColumn(name = "task_id", nullable = false)
    var task: Task,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 30)
    var status: AssignmentStatus = AssignmentStatus.ASSIGNED,

    @field:CreationTimestamp
    @field:Column(name = "assigned_at", nullable = false, updatable = false)
    var assignedAt: Instant? = null,

    @field:Column(name = "completed_at")
    var completedAt: Instant? = null,
)
