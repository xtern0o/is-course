package org.example.backendv2.repository

import org.example.backendv2.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findBySsoSubject(ssoSubject: String): User?
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
}