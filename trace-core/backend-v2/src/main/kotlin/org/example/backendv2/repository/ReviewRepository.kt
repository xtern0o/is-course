package org.example.backendv2.repository

import org.example.backendv2.entity.Review
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReviewRepository : JpaRepository<Review, UUID> {

    @EntityGraph(attributePaths = ["reviewer"])
    fun findBySubmissionId(submissionId: UUID, ): Review?

    fun existsBySubmissionId(submissionId: UUID): Boolean
}