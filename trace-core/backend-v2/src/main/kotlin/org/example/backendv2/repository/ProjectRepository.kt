package org.example.backendv2.repository

import org.example.backendv2.entity.Project
import org.example.backendv2.entity.enums.ProjectStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findAllByManagerIdOrderByCreatedAtDesc(
        managerId: UUID,
        pageable: Pageable,
    ): Slice<Project>

    fun findAllByStatusOrderByCreatedAtDesc(
        status: ProjectStatus,
        pageable: Pageable,
    ): Slice<Project>

    fun existsByIdAndManagerId(
        id: UUID,
        managerId: UUID,
    ): Boolean
}