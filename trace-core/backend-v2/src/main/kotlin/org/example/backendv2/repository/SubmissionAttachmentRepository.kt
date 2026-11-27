package org.example.backendv2.repository

import org.example.backendv2.entity.SubmissionAttachment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SubmissionAttachmentRepository : JpaRepository<SubmissionAttachment, UUID> {

    fun findAllBySubmissionIdOrderByUploadedAtAsc(submissionId: UUID, ): List<SubmissionAttachment>

    fun findByObjectKey(objectKey: String): SubmissionAttachment?
}