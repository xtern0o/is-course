package org.example.backendv2.repository

import org.example.backendv2.entity.User
import org.example.backendv2.repository.projection.UserSummaryProjection
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findBySsoSubject(ssoSubject: String): User?
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?

    fun findAllProjectedByIdIn(ids: Collection<UUID>): List<UserSummaryProjection>


    @Query(
        """
    select
        user.id as id,
        user.username as username,
        user.displayName as displayName,
        user.role as role
    from User user
    order by user.displayName, user.id
    """,
    )
    fun findAllSummaries(pageable: Pageable, ): Slice<UserSummaryProjection>

    /**
     * Поиск пользователей по подстроке без учета регистра сразу
     * по двум полям username и displayName
     */
    @Query(
        """
    select
        user.id as id,
        user.username as username,
        user.displayName as displayName,
        user.role as role
    from User user
    where lower(user.username) like lower(concat('%', :query, '%'))
       or lower(user.displayName) like lower(concat('%', :query, '%'))
    order by user.displayName, user.id
    """,
    )
    fun searchSummaries(
        @Param("query") query: String,
        pageable: Pageable,
    ): Slice<UserSummaryProjection>
}