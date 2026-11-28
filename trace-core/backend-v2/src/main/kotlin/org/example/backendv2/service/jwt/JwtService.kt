package org.example.backendv2.service.jwt


import org.example.backendv2.entity.enums.UserRole
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class JwtService {

    fun getUserClaims(jwt: Jwt): AuthenticatedUserClaims {
        val username = getRequiredStringClaim(jwt, "preferred_username")

        return AuthenticatedUserClaims(
            subject = getSubject(jwt),
            username = username,
            displayName = jwt.getClaimAsString("name")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: username,
            email = getRequiredStringClaim(jwt, "email")
                .lowercase(Locale.ROOT),
            role = getUserRole(jwt),
        )
    }

    fun getSubject(jwt: Jwt): String =
        jwt.subject
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("JWT не содержит subject")


    fun getUserRoles(jwt: Jwt): Set<UserRole> {
        val realmRoles = jwt.getClaimAsMap("realm_access")
            ?.get("roles")
            .let { it as? Collection<*> }
            .orEmpty()
            .filterIsInstance<String>()
            .map { it.uppercase(Locale.ROOT) }
            .toSet()

        return UserRole.entries
            .filterTo(mutableSetOf()) { it.name in realmRoles }
    }

    fun getUserRole(jwt: Jwt): UserRole =
        getUserRoles(jwt).singleOrNull()
            ?: throw IllegalArgumentException(
                "пользователю должна быть назначена ровно одна системная роль",
            )

    private fun getRequiredStringClaim(jwt: Jwt, name: String): String =
        jwt.getClaimAsString(name)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException(
                "JWT не содержит claim '$name'",
            )
}