package org.example.backendv2.repository

import jakarta.persistence.LockModeType
import org.example.backendv2.entity.TeamApplication
import org.example.backendv2.entity.enums.ApplicationStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TeamApplicationRepository : JpaRepository<TeamApplication, UUID> {

    @EntityGraph(attributePaths = ["applicant"])
    fun findAllByTeamIdAndStatusOrderByCreatedAtAsc(
        teamId: UUID,
        status: ApplicationStatus,
        pageable: Pageable,
    ): Slice<TeamApplication>

    fun existsByTeamIdAndApplicantIdAndStatus(
        teamId: UUID,
        applicantId: UUID,
        status: ApplicationStatus,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findOneById(id: UUID): TeamApplication?
}