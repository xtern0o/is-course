package org.example.backendv2.dto.project.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.example.backendv2.entity.enums.ProjectType

data class CreateProjectRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    val description: String? = null,

    val goal: String? = null,

    val type: ProjectType,
)