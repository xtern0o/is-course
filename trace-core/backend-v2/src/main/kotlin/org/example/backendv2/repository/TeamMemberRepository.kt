package org.example.backendv2.repository

import org.example.backendv2.entity.TeamMember
import org.example.backendv2.repository.projection.TeamSummaryProjection
import org.example.backendv2.repository.projection.UserSummaryProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TeamMemberRepository : JpaRepository<TeamMember, UUID> {
    @EntityGraph(attributePaths = ["user"])
    fun findAllByTeamIdOrderByJoinedAtAsc(
        teamId: UUID,
    ): List<TeamMember>

    @EntityGraph(attributePaths = ["team", "team.project"])
    fun findAllByUserIdOrderByJoinedAtDesc(
        userId: UUID,
        pageable: Pageable,
    ): Slice<TeamMember>

    fun findByTeamIdAndUserId(
        teamId: UUID,
        userId: UUID,
    ): TeamMember?

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean

    @Query(
        """
    select
        user.id as id,
        user.username as username,
        user.displayName as displayName,
        user.role as role
    from TeamMember member
    join member.user user
    where member.team.id = :teamId
    order by member.joinedAt
    """,
    )
    fun findUserSummariesByTeamId(
        @Param("teamId") teamId: UUID,
    ): List<UserSummaryProjection>

    @Query(
        """
    select
        team.id as id,
        team.project.id as projectId,
        team.name as name,
        team.description as description,
        team.recruitmentOpen as recruitmentOpen
    from TeamMember member
    join member.team team
    where member.user.ssoSubject = :ssoSubject
    order by member.joinedAt desc
    """,
    )
    fun findTeamSummariesByUserSsoSubject(
        @Param("ssoSubject") ssoSubject: String,
        pageable: Pageable,
    ): Slice<TeamSummaryProjection>
}