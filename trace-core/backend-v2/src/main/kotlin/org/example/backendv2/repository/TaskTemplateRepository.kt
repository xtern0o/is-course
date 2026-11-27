package org.example.backendv2.repository

import org.example.backendv2.entity.TaskTemplate
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TaskTemplateRepository : JpaRepository<TaskTemplate, UUID> {
    fun findAllByCreatedByIdOrderByCreatedAtDesc(
        creatorId: UUID,
        pageable: Pageable,
    ): Slice<TaskTemplate>
}