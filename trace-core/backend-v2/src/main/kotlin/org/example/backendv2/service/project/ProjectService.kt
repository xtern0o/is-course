package org.example.backendv2.service.project

import org.example.backendv2.dto.project.request.CreateProjectRequest
import org.example.backendv2.dto.project.request.UpdateProjectRequest
import org.example.backendv2.dto.project.response.ProjectResponse
import org.example.backendv2.dto.project.response.ProjectSummaryResponse
import org.example.backendv2.entity.Project
import org.example.backendv2.entity.User
import org.example.backendv2.entity.enums.ProjectStatus
import org.example.backendv2.entity.enums.ProjectType
import org.example.backendv2.entity.enums.UserRole
import org.example.backendv2.entity.toResponse
import org.example.backendv2.repository.ProjectRepository
import org.example.backendv2.repository.projection.toResponse
import org.example.backendv2.service.jwt.JwtService
import org.example.backendv2.service.user.UserService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID


class ProjectService(
    private val projectRepository: ProjectRepository,
    private val userService: UserService,
    private val jwtService: JwtService,
) {
    @Transactional
    fun create(
        jwt: Jwt,
        request: CreateProjectRequest,
    ): ProjectResponse {
        val manager = userService.requireCurrentUser(jwt)
        checkCreationPermission(manager, request.type)

        val project = Project(
            name = request.name.trim(),
            description = request.description?.trim(),
            goal = request.goal?.trim(),
            type = request.type,
            manager = manager,
        )

        return projectRepository.save(project).toResponse()
    }

    @Transactional(readOnly = true)
    fun getById(
        id: UUID,
        jwt: Jwt,
    ): ProjectResponse {
        val project = requireById(id)

        if (
            project.status == ProjectStatus.DRAFT &&
            project.manager.ssoSubject != jwtService.getSubject(jwt)
        ) {
            throw AccessDeniedException(
                "черновик доступен только управляющему проекта",
            )
        }

        return project.toResponse()
    }

    /**
     * Получить список активных проектов
     */
    @Transactional(readOnly = true)
    fun getAvailable(pageable: Pageable): Slice<ProjectSummaryResponse> =
        projectRepository
            .findAllProjectedByStatusOrderByCreatedAtDesc(
                status = ProjectStatus.ACTIVE,
                pageable = pageable,
            )
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getManagedByCurrentUser(jwt: Jwt, pageable: Pageable): Slice<ProjectSummaryResponse> =
        projectRepository
            .findAllProjectedByManagerSsoSubjectOrderByCreatedAtDesc(
                ssoSubject = jwtService.getSubject(jwt),
                pageable = pageable,
            )
            .map { it.toResponse() }

    /**
     * Обновить информацию о проекте, если это возможно
     */
    @Transactional
    fun update(id: UUID, jwt: Jwt, request: UpdateProjectRequest): ProjectResponse {
        val project = requireManagedProject(
            id = id,
            ssoSubject = jwtService.getSubject(jwt),
        )

        checkEditable(project)

        return project.apply {
            name = request.name.trim()
            description = request.description?.trim()
            goal = request.goal?.trim()
        }.toResponse()
    }

    /**
     * Активировать проект, находящийся в состоянии черновика или архива
     */
    @Transactional
    fun activate(id: UUID, jwt: Jwt): ProjectResponse {
        val project = requireManagedProject(
            id = id,
            ssoSubject = jwtService.getSubject(jwt),
        )

        check(project.status == ProjectStatus.DRAFT || project.status == ProjectStatus.ARCHIVED) {
            "активировать можно только черновик проекта или тот, что заархивирован"
        }

        return project.apply {
            status = ProjectStatus.ACTIVE
            completedAt = null
        }.toResponse()
    }

    /**
     * Пометить активный проект завершенным
     */
    @Transactional
    fun complete(id: UUID, jwt: Jwt): ProjectResponse {
        val project = requireManagedProject(
            id = id,
            ssoSubject = jwtService.getSubject(jwt),
        )

        check(project.status == ProjectStatus.ACTIVE) {
            "завершить можно только активный проект"
        }

        project.status = ProjectStatus.COMPLETED
        project.completedAt = Instant.now()

        return project.toResponse()
    }

    /**
     * Заархивировать проект, если это вохможно
     */
    @Transactional
    fun archive(id: UUID, jwt: Jwt): ProjectResponse {
        val project = requireManagedProject(
            id = id,
            ssoSubject = jwtService.getSubject(jwt),
        )

        check(project.status != ProjectStatus.ARCHIVED) {
            "проект уже находится в архиве"
        }

        project.status = ProjectStatus.ARCHIVED
        return project.toResponse()
    }

    private fun checkCreationPermission(manager: User, type: ProjectType) {
        val allowed = when (type) {
            ProjectType.DEVELOPMENT -> true

            ProjectType.EDUCATIONAL ->
                manager.role == UserRole.TEACHER ||
                        manager.role == UserRole.ADMIN
        }

        if (!allowed) {
            throw AccessDeniedException(
                "только преподаватель может создать учебный проект",
            )
        }
    }

    @Transactional(readOnly = true)
    fun requireById(id: UUID): Project =
        projectRepository.findDetailedById(id)
            ?: throw NoSuchElementException(
                "проект с id=$id не найден",
            )

    @Transactional(readOnly = true)
    fun requireManagedProject(id: UUID, ssoSubject: String): Project =
        projectRepository.findByIdAndManagerSsoSubject(
            id = id,
            ssoSubject = ssoSubject,
        ) ?: throw AccessDeniedException(
            "проект не найден или пользователь не является его управляющим",
        )

    private fun checkEditable(project: Project) {
        check(
            project.status == ProjectStatus.DRAFT ||
                    project.status == ProjectStatus.ACTIVE,
        ) {
            "завершённый или архивный проект нельзя редактировать"
        }
    }

}