package org.example.backendv2.service.user

import org.example.backendv2.dto.user.response.UserResponse
import org.example.backendv2.dto.user.response.UserSummaryResponse
import org.example.backendv2.entity.User
import org.example.backendv2.entity.enums.UserRole
import org.example.backendv2.entity.toResponse
import org.example.backendv2.repository.UserRepository
import org.example.backendv2.repository.projection.toResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    /**
     * Возвращает бизнес-данные пользователя
     */
    @Transactional
    fun getCurrentUser(jwt: Jwt): UserResponse =
        synchronize(jwt).toResponse()

    /**
     * Используется другими сервисам, которым нужна entity
     */
    @Transactional
    fun requireCurrentUser(jwt: Jwt): User =
        synchronize(jwt)

    /**
     * Получить полную бизнес-информацию о пользователе
     */
    @Transactional(readOnly = true)
    fun getById(id: UUID): UserResponse =
        requireById(id).toResponse()

    /**
     * Потребовать наличие пользователя по id
     */
    @Transactional(readOnly = true)
    fun requireById(id: UUID): User =
        userRepository.findById(id)
            .orElseThrow {
                NoSuchElementException("пользователь с id=$id не найден")
            }

    /**
     * Получение общей информации по нескольким пользователям
     *
     * Использется проекция, так как в дальнейшем при разрастании могут понадобиться далеко не все поля
     */
    @Transactional(readOnly = true)
    fun getByIds(ids: Set<UUID>): List<UserSummaryResponse> {
        if (ids.isEmpty()) return emptyList()
        return userRepository.findAllProjectedByIdIn(ids).map { it.toResponse() }
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Transactional(readOnly = true)
    fun search(query: String?, pageable: Pageable): Slice<UserSummaryResponse> {
        val normalizedQuery = query
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val users = if (normalizedQuery == null) {
            userRepository.findAllSummaries(pageable)
        } else {
            userRepository.searchSummaries(normalizedQuery, pageable)
        }

        return users.map { it.toResponse() }
    }
    private fun synchronize(jwt: Jwt): User {
        val ssoSubject = jwt.subject
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("JWT не содержит subject")

        val username = jwt.requiredStringClaim("preferred_username")
        val email = jwt.requiredStringClaim("email")
            .lowercase(Locale.ROOT)

        val displayName = jwt.getClaimAsString("name")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: username

        val role = jwt.extractUserRole()

        val user = userRepository.findBySsoSubject(ssoSubject) ?: return userRepository.save(
            User(
                ssoSubject = ssoSubject,
                username = username,
                displayName = displayName,
                email = email,
                role = role,
            ),
        )

        return user.apply {
            this.username = username
            this.displayName = displayName
            this.email = email
            this.role = role
        }
    }

    private fun Jwt.requiredStringClaim(name: String): String =
        getClaimAsString(name)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("JWT не содержит claim '$name'")

    private fun Jwt.extractUserRole(): UserRole {
        val realmRoles = getClaimAsMap("realm_access")
            ?.get("roles")
            .let { it as? Collection<*> }
            .orEmpty()
            .filterIsInstance<String>()
            .map { it.uppercase(Locale.ROOT) }
            .toSet()

        val userRoles = UserRole.entries
            .filter { it.name in realmRoles }

        return userRoles.singleOrNull()
            ?: throw IllegalArgumentException(
                "пользователю должна быть назначена ровно одна системная роль",
            )
    }
}