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
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.example.backendv2.entity.enums.ReviewDecision
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "review")
class Review(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotNull
    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "submission_id", nullable = false, unique = true)
    var submission: SubmissionAttempt,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "reviewer_id", nullable = false)
    var reviewer: User,

    @field:NotNull
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 30)
    var decision: ReviewDecision,

    @field:Column(columnDefinition = "text")
    var comment: String? = null,

    @field:CreationTimestamp
    @field:Column(name = "reviewed_at", nullable = false, updatable = false)
    var reviewedAt: Instant? = null,
)
