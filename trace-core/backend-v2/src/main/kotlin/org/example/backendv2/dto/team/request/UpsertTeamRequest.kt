package org.example.backendv2.dto.team.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpsertTeamRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    val description: String? = null,
)
