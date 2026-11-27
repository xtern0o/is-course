package org.example.backendv2.repository

import org.example.backendv2.entity.Announcement
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnnouncementRepository : JpaRepository<Announcement, UUID> {
    @EntityGraph(attributePaths = ["author"])
    fun findAllByTaskIdOrderByCreatedAtDesc(
        taskId: UUID,
        pageable: Pageable
    ): Slice<Announcement>
}