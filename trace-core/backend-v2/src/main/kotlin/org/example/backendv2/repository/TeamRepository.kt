package org.example.backendv2.repository

import org.example.backendv2.entity.Team
import org.example.backendv2.repository.projection.TeamSummaryProjection
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TeamRepository : JpaRepository<Team, UUID> {

    @EntityGraph(attributePaths = ["project", "project.manager"])
    @Query(
        """
        select team
        from Team team
        where team.id = :id
        """,
    )
    fun findDetailedById(
        @Param("id") id: UUID,
    ): Team?

    @EntityGraph(attributePaths = ["project", "project.manager"])
    fun findByIdAndProjectManagerSsoSubject(
        id: UUID,
        ssoSubject: String,
    ): Team?

    @Query(
        """
        select
            team.id as id,
            team.project.id as projectId,
            team.name as name,
            team.description as description,
            team.recruitmentOpen as recruitmentOpen
        from Team team
        where team.project.id = :projectId
        order by team.createdAt
        """,
    )
    fun findSummariesByProjectId(
        @Param("projectId") projectId: UUID,
    ): List<TeamSummaryProjection>


}