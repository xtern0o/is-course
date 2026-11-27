package ru.itmo.trace.admin

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus.*
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import ru.itmo.trace.identity.Actor

data class NewAccount(
    @field:Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") val username: String,
    @field:Email @field:NotBlank @field:Size(max = 254) val email: String,
    @field:NotBlank @field:Size(max = 100) val firstName: String,
    @field:NotBlank @field:Size(max = 100) val lastName: String,
    @field:Size(min = 8, max = 128) val password: String,
)

data class ChangeAccount(val role: String? = null, val enabled: Boolean? = null)

@Service
class AccountProvisioner(
    @Value("\${spring.security.oauth2.provider-url:http://localhost:8180}") base: String,
    @Value("\${trace.provision-secret:local-provision-secret}") private val secret: String,
) {
    private val client = RestClient.create(base)

    fun create(input: NewAccount): String {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
                add("client_id", "trace-provision")
                add("client_secret", secret)
            }
        try {
            val token =
                client
                    .post()
                    .uri("/realms/trace/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map::class.java)
                    ?.get("access_token") as? String ?: throw ResponseStatusException(BAD_GATEWAY)
            val response =
                client
                    .post()
                    .uri("/admin/realms/trace/users")
                    .headers { it.setBearerAuth(token) }
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        mapOf(
                            "username" to input.username,
                            "email" to input.email,
                            "firstName" to input.firstName,
                            "lastName" to input.lastName,
                            "enabled" to true,
                            "credentials" to
                                    listOf(
                                        mapOf(
                                            "type" to "password",
                                            "value" to input.password,
                                            "temporary" to true,
                                        )
                                    ),
                        )
                    )
                    .retrieve()
                    .toBodilessEntity()
            return response.headers.location?.path?.substringAfterLast('/')
                ?: throw ResponseStatusException(BAD_GATEWAY)
        } catch (e: RestClientResponseException) {
            throw ResponseStatusException(
                when (e.statusCode.value()) {
                    409 -> CONFLICT
                    400 -> BAD_REQUEST
                    else -> BAD_GATEWAY
                }
            )
        }
    }
}

@RestController
@RequestMapping("/api/admin/accounts")
class Accounts(private val service: AccountService) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
    ) = service.list(actor, q, page)

    @PostMapping
    @ResponseStatus(CREATED)
    fun create(actor: Actor, @Valid @RequestBody input: NewAccount) = service.create(actor, input)

    @PatchMapping("/{id}")
    fun update(actor: Actor, @PathVariable id: UUID, @RequestBody edit: ChangeAccount) =
        service.update(actor, id, edit)
}

@Service
class AccountService(private val db: JdbcTemplate, private val provisioner: AccountProvisioner) {
    private fun requireAdmin(actor: Actor) {
        if (actor.role != "admin") throw ResponseStatusException(FORBIDDEN)
    }

    fun list(actor: Actor, q: String, page: Int): Map<String, Any> {
        requireAdmin(actor)
        if (page !in 0..1000000) throw ResponseStatusException(BAD_REQUEST)
        val pattern = "%" + q.trim() + "%"
        val rows =
            db.queryForList(
                """SELECT id,username,display_name,email,role,enabled FROM app_user
            WHERE concat_ws(' ',username,display_name,email) ILIKE ? ORDER BY display_name,id LIMIT 26 OFFSET ?""",
                pattern,
                page * 25,
            )
        return mapOf("items" to rows.take(25), "next" to (rows.size > 25))
    }

    fun create(actor: Actor, input: NewAccount) {
        requireAdmin(actor)
        val sub = provisioner.create(input)
        db.update(
            """INSERT INTO app_user(sso_subject,username,display_name,email,role) VALUES (?,?,?,?,'student')
            ON CONFLICT (sso_subject) DO NOTHING""",
            sub,
            input.username.lowercase(),
            input.firstName + " " + input.lastName,
            input.email.lowercase(),
        )
    }

    @Transactional
    fun update(actor: Actor, id: UUID, edit: ChangeAccount) {
        requireAdmin(actor)
        // Serialize privilege changes and recheck the caller under the lock.
        db.execute("LOCK TABLE app_user IN SHARE ROW EXCLUSIVE MODE")
        if (
            db.queryForObject(
                "SELECT enabled AND role='admin' FROM app_user WHERE id=?",
                Boolean::class.java,
                actor.id,
            ) != true || id == actor.id
        )
            throw ResponseStatusException(FORBIDDEN)
        if (edit.role != null && edit.role !in setOf("admin", "teacher", "student"))
            throw ResponseStatusException(BAD_REQUEST)
        if (
            db.update(
                "UPDATE app_user SET role=coalesce(?,role),enabled=coalesce(?,enabled) WHERE id=?",
                edit.role,
                edit.enabled,
                id,
            ) == 0
        )
            throw ResponseStatusException(NOT_FOUND)
    }
}
