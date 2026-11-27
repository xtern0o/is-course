package org.example.backendv2.repository

import jakarta.persistence.LockModeType
import org.example.backendv2.entity.Assignment
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface AssignmentRepository : JpaRepository<Assignment, UUID> {
    @EntityGraph(attributePaths = ["task"])
    fun findAllByTeamIdOrderByAssignedAtDesc(
        teamId: UUID,
        pageable: Pageable,
    ): Slice<Assignment>

    @EntityGraph(attributePaths = ["team"])
    fun findAllByTaskIdOrderByAssignedAtDesc(
        taskId: UUID,
        pageable: Pageable,
    ): Slice<Assignment>

    fun findByTeamIdAndTaskId(
        teamId: UUID,
        taskId: UUID,
    ): Assignment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findOneById(id: UUID): Assignment?
}