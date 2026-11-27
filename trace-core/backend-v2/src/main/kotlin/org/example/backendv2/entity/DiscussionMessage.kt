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
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.example.backendv2.entity.enums.MessageType
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "discussion_message")
class DiscussionMessage(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "team_task_id", nullable = false)
    var assignment: Assignment,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "author_id")
    var author: User? = null,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "submission_id")
    var submission: SubmissionAttempt? = null,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "review_id")
    var review: Review? = null,

    @field:NotNull
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "message_type", nullable = false, length = 30)
    var type: MessageType,

    @field:NotBlank
    @field:Column(nullable = false, columnDefinition = "text")
    var content: String,

    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
