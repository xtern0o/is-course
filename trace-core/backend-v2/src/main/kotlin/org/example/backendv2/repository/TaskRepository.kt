package org.example.backendv2.repository

import org.example.backendv2.entity.Task
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TaskRepository : JpaRepository<Task, UUID> {
    fun findAllByProjectIdOrderByCreatedAtDesc(
        projectId: UUID,
        pageable: Pageable,
    ): Slice<Task>

    fun findByIdAndProjectId(id: UUID, projectId: UUID): Task?
}