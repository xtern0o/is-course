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
import jakarta.validation.constraints.Positive
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "submission_attempt",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_submission_assignment_attempt",
            columnNames = ["team_task_id", "attempt_number"],
        ),
    ],
)
class SubmissionAttempt(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "team_task_id", nullable = false)
    var assignment: Assignment,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "submitted_by", nullable = false)
    var submittedBy: User,

    @field:Positive
    @field:Column(name = "attempt_number", nullable = false)
    var attemptNumber: Int,

    @field:Column(columnDefinition = "text")
    var content: String? = null,

    @field:CreationTimestamp
    @field:Column(name = "submitted_at", nullable = false, updatable = false)
    var submittedAt: Instant? = null,
)
