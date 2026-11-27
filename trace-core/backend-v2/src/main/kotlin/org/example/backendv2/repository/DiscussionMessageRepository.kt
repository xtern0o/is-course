package org.example.backendv2.repository

import org.example.backendv2.entity.DiscussionMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DiscussionMessageRepository : JpaRepository<DiscussionMessage, UUID> {

    @EntityGraph(
        attributePaths = [
            "author",
            "submission",
            "review",
        ],
    )
    fun findAllByAssignmentIdOrderByCreatedAtDesc(
        assignmentId: UUID,
        pageable: Pageable,
    ): Slice<DiscussionMessage>
}