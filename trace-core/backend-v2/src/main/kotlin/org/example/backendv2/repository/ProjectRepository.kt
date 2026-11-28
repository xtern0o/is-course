package org.example.backendv2.repository

import org.example.backendv2.entity.Project
import org.example.backendv2.entity.enums.ProjectStatus
import org.example.backendv2.repository.projection.ProjectSummaryProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectRepository : JpaRepository<Project, UUID> {

    @EntityGraph(attributePaths = ["manager"])
    @Query(
        """
        select project
        from Project project
        where project.id = :id
        """,
    )
    fun findDetailedById(
        @Param("id") id: UUID,
    ): Project?

    /**
     * Найти проект и проверить, что у пользователя есть права на упраление им
     */
    @EntityGraph(attributePaths = ["manager"])
    fun findByIdAndManagerSsoSubject(
        id: UUID,
        ssoSubject: String,
    ): Project?

    fun findAllProjectedByStatusOrderByCreatedAtDesc(
        status: ProjectStatus,
        pageable: Pageable,
    ): Slice<ProjectSummaryProjection>

    fun findAllProjectedByManagerSsoSubjectOrderByCreatedAtDesc(
        ssoSubject: String,
        pageable: Pageable,
    ): Slice<ProjectSummaryProjection>
}