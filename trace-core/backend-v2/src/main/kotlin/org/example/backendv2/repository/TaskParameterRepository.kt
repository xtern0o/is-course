package org.example.backendv2.repository

import org.example.backendv2.entity.TaskParameter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TaskParameterRepository : JpaRepository<TaskParameter, UUID> {
    fun findAllByTemplateIdOrderByNameAsc(templateId: UUID): List<TaskParameter>
}