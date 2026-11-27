package ru.itmo.trace.identity

import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class Actor(val id: UUID, val name: String, val role: String)

data class SessionInfo(val user: Actor, val csrfToken: String, val csrfHeader: String)

@Service
class Identity(private val jdbc: JdbcClient) {
    fun current(user: OidcUser): Actor {
        val claims = user.getClaimAsStringList("trace_roles").orEmpty()
        val initialRole =
            when {
                "admin" in claims -> "admin"
                "teacher" in claims -> "teacher"
                else -> "student"
            }
        val name = user.fullName ?: user.preferredUsername
        fun actor(row: java.sql.ResultSet): Actor {
            if (!row.getBoolean("enabled"))
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ приостановлен")
            return Actor(
                row.getObject("id", UUID::class.java),
                row.getString("display_name"),
                row.getString("role"),
            )
        }

        val existing =
            jdbc
                .sql(
                    """SELECT id,display_name,role,enabled FROM app_user
            WHERE sso_subject=:sub AND username=:login AND (profile_version>0 OR display_name=:name) AND email=:email"""
                )
                .param("sub", user.subject)
                .param("login", user.preferredUsername)
                .param("name", name)
                .param("email", user.email)
                .query { rs, _ -> actor(rs) }
                .optional()
        if (existing.isPresent) return existing.get()
        return jdbc
            .sql(
                """INSERT INTO app_user(sso_subject,username,display_name,email,role)
            VALUES (:sub,:login,:name,:email,:role) ON CONFLICT (sso_subject) DO UPDATE
            SET username=excluded.username, display_name=CASE WHEN app_user.profile_version=0 THEN excluded.display_name ELSE app_user.display_name END,email=excluded.email
            RETURNING id,display_name,role,enabled"""
            )
            .param("sub", user.subject)
            .param("login", user.preferredUsername)
            .param("name", name)
            .param("email", user.email)
            .param("role", initialRole)
            .query { rs, _ -> actor(rs) }
            .single()
    }
}

@RestController
class SessionController {
    @GetMapping("/api/me")
    fun me(actor: Actor, csrf: CsrfToken) = SessionInfo(actor, csrf.token, csrf.headerName)
}
