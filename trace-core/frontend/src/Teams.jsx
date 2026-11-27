import React, {useState} from "react";
import {
    ActionForm,
    Field,
    Panel,
    Disclosure,
    useLoad,
    request,
    statuses,
    LoadState,
} from "./ui";

function Roster({id, session, revision}) {
    const {value, error} = useLoad(
        () => request("teams/" + id + "/members", session),
        [id, revision],
    );
    return value ? (
        value.length ? (
            <ul className="people-list">
                {value.map((u) => (
                    <li key={u.id}>
                        <strong>{u.display_name}</strong>
                        {u.specialty && <small>{u.specialty}</small>}
                        {u.bio && <p>{u.bio}</p>}
                    </li>
                ))}
            </ul>
        ) : (
            <p>Участников пока нет.</p>
        )
    ) : (
        <LoadState error={error}/>
    );
}

function StudentPicker({students, team, session}) {
    const [query, setQuery] = useState("");
    const {value: members, error} = useLoad(
        () => request("teams/" + team.id + "/members", session),
        [team.id, team.members],
    );
    if (!members || error)
        return (
            <>
                <LoadState error={error}/>
                <select name="userId" required aria-label="Студент" defaultValue="">
                    <option value="">Выберите студента</option>
                </select>
            </>
        );
    const joined = new Set(members.map((m) => m.id));
    const available = students.filter(
        (u) =>
            !joined.has(u.id) &&
            u.display_name
                .toLocaleLowerCase("ru")
                .includes(query.trim().toLocaleLowerCase("ru")),
    );
    return (
        <>
            <Field
                label="Поиск студента"
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Имя или фамилия"
            />
            <label>
                Студент
                <select name="userId" required key={query} defaultValue="">
                    <option value="">
                        {available.length
                            ? "Выберите студента"
                            : "Подходящих студентов нет"}
                    </option>
                    {available.map((u) => (
                        <option key={u.id} value={u.id}>
                            {u.display_name}
                        </option>
                    ))}
                </select>
            </label>
        </>
    );
}

export function TeamPanel({
                              project,
                              session,
                              teams,
                              applications,
                              students,
                              reload,
                          }) {
    const manager = project.managerId === session.user.id;

    const send = (path, data, method = "POST") =>
        request(path, session, method, data);
    return (
        <section>
            <h2>Команды</h2>
            {manager && (
                <Panel
                    title="Создать команду"
                    context={project.name}
                    success="Команда создана"
                >
                    <ActionForm
                        label="Создать"
                        onDone={reload}
                        run={(f) =>
                            send("projects/" + project.id + "/teams", Object.fromEntries(f))
                        }
                    >
                        <Field
                            label="Название команды"
                            name="name"
                            required
                            maxLength={255}
                        />
                        <Field label="Описание" name="description" area/>
                    </ActionForm>
                </Panel>
            )}
            {teams.map((t) => (
                <article key={t.id}>
                    <h3>{t.name}</h3>
                    <p>{t.description}</p>
                    <p>
                        {t.recruitment_open ? "Набор открыт" : "Набор закрыт"} · {t.members}{" "}
                        участн. {t.joined && "· Вы в команде"}
                    </p>
                    <Disclosure title="Состав команды" count={t.members}>
                        <Roster id={t.id} session={session} revision={t.members}/>
                    </Disclosure>
                    {manager && (
                        <ActionForm
                            label={t.recruitment_open ? "Закрыть набор" : "Открыть набор"}
                            run={() =>
                                send(
                                    "teams/" + t.id + "/recruitment",
                                    {open: !t.recruitment_open},
                                    "PATCH",
                                )
                            }
                            onDone={reload}
                        />
                    )}
                    {!t.joined && t.recruitment_open && t.application !== "pending" && (
                        <Panel
                            title="Подать заявку"
                            context={t.name}
                            success="Заявка отправлена"
                        >
                            <ActionForm
                                label="Подать заявку"
                                run={(f) =>
                                    send("teams/" + t.id + "/applications", Object.fromEntries(f))
                                }
                                onDone={reload}
                            >
                                <Field label="Комментарий" name="comment"/>
                            </ActionForm>
                        </Panel>
                    )}
                    {manager && students.length > 0 && (
                        <Panel
                            title="Добавить студента"
                            context={t.name}
                            success="Студент добавлен в команду"
                        >
                            <ActionForm
                                label="Добавить"
                                run={(f) =>
                                    send("teams/" + t.id + "/members", Object.fromEntries(f))
                                }
                                onDone={reload}
                            >
                                <StudentPicker students={students} team={t} session={session}/>
                            </ActionForm>
                        </Panel>
                    )}
                </article>
            ))}
            {applications.length > 0 && (
                <Disclosure
                    title={manager ? "Заявки в команды" : "Мои заявки"}
                    count={applications.length}
                    initialOpen={applications.some((a) => a.status === "pending")}
                >
                    {applications.map((a) => (
                        <article key={a.id}>
                            <b>
                                {a.applicant} · {a.team}
                            </b>
                            <p>
                                {statuses[a.status]} ·{" "}
                                {new Date(a.created_at).toLocaleDateString("ru-RU")} ·{" "}
                                {a.comment}
                            </p>
                            {manager && a.status === "pending" && (
                                <ActionForm
                                    label="Сохранить решение"
                                    onDone={reload}
                                    run={(f) =>
                                        send(
                                            "applications/" + a.id + "/decision",
                                            Object.fromEntries(f),
                                        )
                                    }
                                >
                                    <label>
                                        Решение
                                        <select name="decision">
                                            <option value="accepted">Принять</option>
                                            <option value="rejected">Отклонить</option>
                                        </select>
                                    </label>
                                    <Field label="Комментарий к решению" name="comment"/>
                                </ActionForm>
                            )}
                        </article>
                    ))}
                </Disclosure>
            )}
        </section>
    );
}
