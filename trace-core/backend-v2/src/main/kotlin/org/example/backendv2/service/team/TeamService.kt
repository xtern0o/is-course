package org.example.backendv2.service.team

import org.example.backendv2.dto.team.request.UpsertTeamRequest
import org.example.backendv2.dto.team.response.TeamResponse
import org.example.backendv2.dto.team.response.TeamSummaryResponse
import org.example.backendv2.dto.user.response.UserSummaryResponse
import org.example.backendv2.entity.Team
import org.example.backendv2.entity.TeamMember
import org.example.backendv2.entity.User
import org.example.backendv2.entity.enums.ProjectStatus
import org.example.backendv2.entity.enums.ProjectType
import org.example.backendv2.entity.enums.UserRole
import org.example.backendv2.entity.toResponse
import org.example.backendv2.entity.toSummaryResponse
import org.example.backendv2.repository.TeamMemberRepository
import org.example.backendv2.repository.TeamRepository
import org.example.backendv2.repository.projection.toResponse
import org.example.backendv2.service.jwt.JwtService
import org.example.backendv2.service.project.ProjectService
import org.example.backendv2.service.user.UserService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.AccessDeniedException
import java.util.UUID

class TeamService(
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val projectService: ProjectService,
    private val userService: UserService,
    private val jwtService: JwtService,
) {
    @Transactional
    fun create(
        projectId: UUID,
        jwt: Jwt,
        request: UpsertTeamRequest,
    ): TeamResponse {
        val project = projectService.requireManagedProject(
            id = projectId,
            ssoSubject = jwtService.getSubject(jwt),
        )

        checkProjectEditable(project.status)

        val team = Team(
            project = project,
            name = request.name.trim(),
            description = request.description?.trim(),
        )

        return teamRepository.save(team).toResponse()
    }

    @Transactional(readOnly = true)
    fun getById(
        id: UUID,
        jwt: Jwt,
    ): TeamResponse =
        requireVisibleTeam(id, jwtService.getSubject(jwt))
            .toResponse()

    @Transactional(readOnly = true)
    fun getByProjectId(
        projectId: UUID,
        jwt: Jwt,
    ): List<TeamSummaryResponse> {
        projectService.getById(projectId, jwt)

        return teamRepository.findSummariesByProjectId(projectId)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getCurrentUserTeams(
        jwt: Jwt,
        pageable: Pageable,
    ): Slice<TeamSummaryResponse> =
        teamMemberRepository
            .findTeamSummariesByUserSsoSubject(
                ssoSubject = jwtService.getSubject(jwt),
                pageable = pageable,
            )
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getMembers(
        teamId: UUID,
        jwt: Jwt,
    ): List<UserSummaryResponse> {
        requireVisibleTeam(teamId, jwtService.getSubject(jwt))

        return teamMemberRepository
            .findUserSummariesByTeamId(teamId)
            .map { it.toResponse() }
    }

    @Transactional
    fun update(
        id: UUID,
        jwt: Jwt,
        request: UpsertTeamRequest,
    ): TeamResponse {
        val team = requireManagedTeam(
            id = id,
            ssoSubject = jwtService.getSubject(jwt),
        )

        checkProjectEditable(team.project.status)

        return team.apply {
            name = request.name.trim()
            description = request.description?.trim()
        }.toResponse()
    }

    @Transactional
    fun setRecruitmentOpen(
        id: UUID,
        jwt: Jwt,
        open: Boolean,
    ): TeamResponse {
        val team = requireManagedTeam(
            id = id,
            ssoSubject = jwtService.getSubject(jwt),
        )

        checkProjectEditable(team.project.status)

        team.recruitmentOpen = open
        return team.toResponse()
    }

    /**
     * Добавить участника в команду
     */
    @Transactional
    fun addMember(
        teamId: UUID,
        userId: UUID,
        jwt: Jwt,
    ): UserSummaryResponse {
        val team = requireManagedTeam(
            id = teamId,
            ssoSubject = jwtService.getSubject(jwt),
        )

        checkProjectEditable(team.project.status)

        val user = userService.requireById(userId)
        addMemberToTeam(team, user)

        return user.toSummaryResponse()
    }

    /**
     * Удалить участника из команды
     */
    @Transactional
    fun removeMember(
        teamId: UUID,
        userId: UUID,
        jwt: Jwt,
    ) {
        val team = requireManagedTeam(
            id = teamId,
            ssoSubject = jwtService.getSubject(jwt),
        )

        checkProjectEditable(team.project.status)
        removeMemberFromTeam(teamId, userId)
    }

    /**
     * Один метод для перемещения участнкиа из одной команды в другую
     */
    @Transactional
    fun moveMember(
        sourceTeamId: UUID,
        targetTeamId: UUID,
        userId: UUID,
        jwt: Jwt,
    ): UserSummaryResponse {
        require(sourceTeamId != targetTeamId) {
            "исходная и целевая команды должны различаться"
        }

        val ssoSubject = jwtService.getSubject(jwt)

        val sourceTeam = requireManagedTeam(
            id = sourceTeamId,
            ssoSubject = ssoSubject,
        )

        val targetTeam = requireManagedTeam(
            id = targetTeamId,
            ssoSubject = ssoSubject,
        )

        check(sourceTeam.project.id == targetTeam.project.id) {
            "перемещать пользователя можно только между командами одного проекта"
        }

        checkProjectEditable(sourceTeam.project.status)

        val user = userService.requireById(userId)

        checkMemberCanBeAdded(targetTeam, user)
        removeMemberFromTeam(sourceTeamId, userId)

        teamMemberRepository.save(
            TeamMember(
                team = targetTeam,
                user = user,
            ),
        )

        return user.toSummaryResponse()
    }


    @Transactional(readOnly = true)
    fun requireById(id: UUID): Team =
        teamRepository.findDetailedById(id)
            ?: throw NoSuchElementException(
                "команда с id=$id не найдена",
            )

    @Transactional(readOnly = true)
    fun requireManagedTeam(
        id: UUID,
        ssoSubject: String,
    ): Team =
        teamRepository.findByIdAndProjectManagerSsoSubject(
            id = id,
            ssoSubject = ssoSubject,
        ) ?: throw AccessDeniedException(
            "команда не найдена или пользователь не управляет её проектом",
        )


    private fun requireVisibleTeam(
        id: UUID,
        ssoSubject: String,
    ): Team {
        val team = requireById(id)

        if (
            team.project.status == ProjectStatus.DRAFT &&
            team.project.manager.ssoSubject != ssoSubject
        ) {
            throw AccessDeniedException(
                "команда чернового проекта доступна только управляющему",
            )
        }

        return team
    }

    private fun checkProjectEditable(status: ProjectStatus) {
        check(
            status == ProjectStatus.DRAFT ||
                    status == ProjectStatus.ACTIVE,
        ) {
            "команды завершённого или архивного проекта нельзя изменять"
        }
    }

    private fun addMemberToTeam(
        team: Team,
        user: User,
    ) {
        checkMemberCanBeAdded(team, user)

        teamMemberRepository.save(
            TeamMember(
                team = team,
                user = user,
            ),
        )
    }

    /**
     * Удаление из команды
     */
    private fun removeMemberFromTeam(
        teamId: UUID,
        userId: UUID,
    ) {
        val membership = teamMemberRepository.findByTeamIdAndUserId(
            teamId = teamId,
            userId = userId,
        ) ?: throw NoSuchElementException(
            "пользователь не состоит в указанной команде",
        )

        teamMemberRepository.delete(membership)
    }

    /**
     * Проверяем а можем ли мы вообще переместить пользователя в новую команду
     */
    private fun checkMemberCanBeAdded(
        team: Team,
        user: User,
    ) {
        if (team.project.type == ProjectType.EDUCATIONAL) {
            check(user.role == UserRole.STUDENT) {
                "в команду учебного проекта можно добавить только студента"
            }
        }

        check(
            !teamMemberRepository.existsByTeamIdAndUserId(
                teamId = requireNotNull(team.id),
                userId = requireNotNull(user.id),
            ),
        ) {
            "пользователь уже состоит в целевой команде"
        }
    }

}