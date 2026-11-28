package org.example.backendv2.dto.project.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateProjectRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    val description: String? = null,

    val goal: String? = null,
)