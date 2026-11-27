package ru.itmo.trace.identity

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class ProfileChanges(
    @field:NotBlank @field:Size(max = 255) val displayName: String,
    @field:Size(max = 120) val specialty: String,
    @field:Size(max = 1200) val bio: String,
    val shareIntro: Boolean,
    @field:PositiveOrZero val version: Long
)

@Service
class ProfileService(private val db: JdbcTemplate) {
    fun read(id: UUID) = db.queryForMap(
        """
        SELECT display_name AS "displayName", username, email, specialty, bio,
               share_intro AS "shareIntro", profile_version AS version
        FROM app_user WHERE id = ?
    """, id
    )

    @Transactional
    fun update(id: UUID, changes: ProfileChanges): Map<String, Any?> {
        val changed = db.update(
            """
            UPDATE app_user SET display_name=?, specialty=?, bio=?, share_intro=?,
                profile_version=profile_version+1 WHERE id=? AND profile_version=?
        """, changes.displayName.trim(), changes.specialty.trim(), changes.bio.trim(),
            changes.shareIntro, id, changes.version
        )
        if (changed != 1) throw ResponseStatusException(CONFLICT)
        return read(id)
    }
}

@RestController
@RequestMapping("/api/me/profile")
class ProfileController(
    private val profiles: ProfileService,
    @org.springframework.beans.factory.annotation.Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}") issuer: String
) {
    private val account = mapOf("accountUrl" to (issuer.trimEnd('/') + "/account/"))
    @GetMapping
    fun get(actor: Actor) = profiles.read(actor.id) + account
    @PutMapping
    fun save(actor: Actor, @Valid @RequestBody changes: ProfileChanges) =
        profiles.update(actor.id, changes) + account
}
