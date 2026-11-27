package org.example.backendv2.repository

import org.example.backendv2.entity.TeamMember
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
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

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean
}