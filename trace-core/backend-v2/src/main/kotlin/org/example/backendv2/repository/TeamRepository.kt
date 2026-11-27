package org.example.backendv2.repository

import org.example.backendv2.entity.Team
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TeamRepository : JpaRepository<Team, UUID> {
    fun findAllByProjectIdOrderByCreatedAtAsc(projectId: UUID): List<Team>
    fun findByIdAndProjectId(id: UUID, projectId: UUID): Team?
}