package org.example.backendv2.repository

import org.example.backendv2.entity.SubmissionAttempt
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SubmissionAttemptRepository : JpaRepository<SubmissionAttempt, UUID> {

    @EntityGraph(attributePaths = ["submittedBy"])
    fun findAllByAssignmentIdOrderByAttemptNumberDesc(
        assignmentId: UUID,
        pageable: Pageable,
    ): Slice<SubmissionAttempt>

    fun findFirstByAssignmentIdOrderByAttemptNumberDesc(
        assignmentId: UUID,
    ): SubmissionAttempt?
}