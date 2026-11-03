\set ON_ERROR_STOP on

BEGIN;

DO $$
DECLARE
    v_manager UUID := uuidv7();
    v_alice UUID := uuidv7();
    v_bob UUID := uuidv7();
    v_carol UUID := uuidv7();
    v_admin UUID := uuidv7();

    v_project UUID := uuidv7();
    v_team_alpha UUID := uuidv7();
    v_team_beta UUID := uuidv7();

    v_template UUID := uuidv7();
    v_parameter_topic UUID := uuidv7();
    v_parameter_pages UUID := uuidv7();
    v_parameter_due_date UUID := uuidv7();

    v_task_research UUID := uuidv7();
    v_task_presentation UUID := uuidv7();

    v_alpha_research UUID := uuidv7();
    v_beta_research UUID := uuidv7();
    v_alpha_presentation UUID := uuidv7();

    v_application UUID;
    v_submission_1 UUID;
    v_submission_2 UUID;
    v_submission_3 UUID;
    v_review_1 UUID;
    v_review_2 UUID;
BEGIN
    INSERT INTO app_user (
        id,
        sso_subject,
        username,
        display_name,
        email,
        role
    )
    VALUES
        (
            v_manager,
            'sso-teacher-trace',
            'teacher.trace',
            'Анна Преподаватель',
            'teacher@trace.local',
            'teacher'
        ),
        (
            v_alice,
            'sso-alice-trace',
            'alice.trace',
            'Алиса Смирнова',
            'alice@trace.local',
            'student'
        ),
        (
            v_bob,
            'sso-bob-trace',
            'bob.trace',
            'Борис Иванов',
            'bob@trace.local',
            'student'
        ),
        (
            v_carol,
            'sso-carol-trace',
            'carol.trace',
            'Карина Петрова',
            'carol@trace.local',
            'student'
        ),
        (
            v_admin,
            'sso-admin-trace',
            'admin.trace',
            'Администратор TRACE',
            'admin@trace.local',
            'admin'
        );

    INSERT INTO project (
        id,
        name,
        description,
        goal,
        type,
        status,
        manager_id
    )
    VALUES (
        v_project,
        'Проектирование информационной системы',
        'Учебный проект для проверки основных сценариев TRACE.',
        'Подготовить проект информационной системы.',
        'educational',
        'active',
        v_manager
    );

    INSERT INTO team (
        id,
        project_id,
        name,
        description,
        recruitment_open
    )
    VALUES
        (
            v_team_alpha,
            v_project,
            'Команда Альфа',
            'Основная тестовая команда.',
            true
        ),
        (
            v_team_beta,
            v_project,
            'Команда Бета',
            'Вторая тестовая команда.',
            true
        );

    INSERT INTO team_member (team_id, user_id)
    VALUES
        (v_team_alpha, v_alice),
        (v_team_beta, v_carol);

    v_application := apply_to_team(
        v_team_alpha,
        v_bob,
        'Хочу работать над аналитикой.'
    );

    CALL review_team_application(
        v_application,
        v_manager,
        'accepted',
        'Заявка принята.'
    );

    PERFORM apply_to_team(
        v_team_alpha,
        v_carol,
        'Хочу присоединиться к команде Альфа.'
    );

    v_application := apply_to_team(
        v_team_beta,
        v_alice,
        'Хочу перейти в команду Бета.'
    );

    CALL review_team_application(
        v_application,
        v_manager,
        'rejected',
        'Команда уже укомплектована.'
    );

    INSERT INTO task_template (
        id,
        created_by,
        title,
        description,
        expected_result
    )
    VALUES (
        v_template,
        v_manager,
        'Исследовательское задание',
        'Шаблон для подготовки аналитического материала.',
        'Документ с результатами исследования.'
    );

    INSERT INTO task_parameter (
        id,
        template_id,
        name,
        description,
        data_type,
        default_value,
        required
    )
    VALUES
        (
            v_parameter_topic,
            v_template,
            'Тема',
            'Тема исследования.',
            'text',
            NULL,
            true
        ),
        (
            v_parameter_pages,
            v_template,
            'Максимум страниц',
            'Ограничение объема работы.',
            'integer',
            '20',
            false
        ),
        (
            v_parameter_due_date,
            v_template,
            'Дата защиты',
            'Плановая дата защиты.',
            'date',
            '2026-12-20',
            false
        );

    INSERT INTO task (
        id,
        project_id,
        template_id,
        created_by,
        title,
        instructions,
        deadline_at
    )
    VALUES
        (
            v_task_research,
            v_project,
            v_template,
            v_manager,
            'Исследование предметной области',
            'Подготовить описание предметной области и основных процессов.',
            now() + INTERVAL '30 days'
        ),
        (
            v_task_presentation,
            v_project,
            v_template,
            v_manager,
            'Итоговая презентация',
            'Подготовить краткую презентацию результатов проекта.',
            now() + INTERVAL '45 days'
        );

    INSERT INTO team_task (
        id,
        team_id,
        task_id
    )
    VALUES
        (
            v_alpha_research,
            v_team_alpha,
            v_task_research
        ),
        (
            v_beta_research,
            v_team_beta,
            v_task_research
        ),
        (
            v_alpha_presentation,
            v_team_alpha,
            v_task_presentation
        );

    INSERT INTO team_task_parameter_value (
        team_task_id,
        parameter_id,
        value
    )
    VALUES
        (
            v_alpha_research,
            v_parameter_topic,
            'Цифровизация учебных проектов'
        ),
        (
            v_alpha_research,
            v_parameter_pages,
            '15'
        ),
        (
            v_alpha_research,
            v_parameter_due_date,
            '2026-12-18'
        ),
        (
            v_beta_research,
            v_parameter_topic,
            'Командная работа студентов'
        ),
        (
            v_beta_research,
            v_parameter_pages,
            '12'
        ),
        (
            v_alpha_presentation,
            v_parameter_topic,
            'Результаты проекта TRACE'
        );

    INSERT INTO announcement (
        task_id,
        author_id,
        title,
        content
    )
    VALUES (
        v_task_research,
        v_manager,
        'Требования к исследованию',
        'Добавьте описание аудитории и не менее трех основных процессов.'
    );

    v_submission_1 := submit_task(
        v_alpha_research,
        v_alice,
        'Первая версия исследования.'
    );

    INSERT INTO submission_attachment (
        submission_id,
        object_key,
        original_name,
        mime_type,
        size_bytes
    )
    VALUES (
        v_submission_1,
        'submissions/alpha/research/attempt-1/report.pdf',
        'research-v1.pdf',
        'application/pdf',
        245760
    );

    v_review_1 := review_submission(
        v_submission_1,
        v_manager,
        'revision_required',
        'Нужно подробнее описать целевую аудиторию.'
    );

    INSERT INTO discussion_message (
        team_task_id,
        author_id,
        submission_id,
        content
    )
    VALUES (
        v_alpha_research,
        v_alice,
        v_submission_1,
        'Подскажите, достаточно ли добавить две персоны?'
    );

    INSERT INTO discussion_message (
        team_task_id,
        author_id,
        review_id,
        content
    )
    VALUES (
        v_alpha_research,
        v_manager,
        v_review_1,
        'Да, двух основных персон будет достаточно.'
    );

    v_submission_2 := submit_task(
        v_alpha_research,
        v_bob,
        'Исправленная версия с описанием аудитории.'
    );

    INSERT INTO submission_attachment (
        submission_id,
        object_key,
        original_name,
        mime_type,
        size_bytes
    )
    VALUES (
        v_submission_2,
        'submissions/alpha/research/attempt-2/report.pdf',
        'research-v2.pdf',
        'application/pdf',
        286720
    );

    v_review_2 := review_submission(
        v_submission_2,
        v_manager,
        'accepted',
        'Работа принята.'
    );

    INSERT INTO discussion_message (
        team_task_id,
        author_id,
        review_id,
        content
    )
    VALUES (
        v_alpha_research,
        v_alice,
        v_review_2,
        'Спасибо, замечания учтены.'
    );

    v_submission_3 := submit_task(
        v_alpha_presentation,
        v_alice,
        'Черновик итоговой презентации.'
    );

    INSERT INTO submission_attachment (
        submission_id,
        object_key,
        original_name,
        mime_type,
        size_bytes
    )
    VALUES (
        v_submission_3,
        'submissions/alpha/presentation/attempt-1/slides.pdf',
        'trace-slides.pdf',
        'application/pdf',
        524288
    );

    INSERT INTO discussion_message (
        team_task_id,
        author_id,
        content
    )
    VALUES (
        v_alpha_presentation,
        v_bob,
        'Черновик готов, ждем проверку.'
    );
END;
$$;

COMMIT;

