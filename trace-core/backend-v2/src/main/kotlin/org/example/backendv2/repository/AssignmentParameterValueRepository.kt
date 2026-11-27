package org.example.backendv2.repository

import org.example.backendv2.entity.AssignmentParameterValue
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AssignmentParameterValueRepository : JpaRepository<AssignmentParameterValue, UUID> {
    @EntityGraph(attributePaths = ["parameter"])
    fun findAllByAssignmentIdOrderByParameterNameAsc(
        assignmentId: UUID,
    ): List<AssignmentParameterValue>
}