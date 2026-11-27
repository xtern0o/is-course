import React, {useState} from "react";
import {useLoad, LoadState, request, statuses} from "./ui";
import {TeamPanel} from "./Teams";
import {Allocation} from "./Allocation";

export default function Workspace({project, session, tab = "overview"}) {
    const manager = project.managerId === session.user.id;
    const canCreateTemplate = manager || session.user.role === "teacher";
    const setAssignment = (id) => {
        location.hash = "assignment/" + id + "/" + project.id;
    };
    const [pendingOnly, setPendingOnly] = useState(false);
    const get = (s) => request("projects/" + project.id + "/" + s, session);
    const {value, error, reload} = useLoad(
        () =>
            Promise.all([
                get("teams"),
                get("applications"),
                get("assignments"),
                canCreateTemplate ? get("templates") : [],
                manager &&
                session.user.role === "teacher" &&
                project.type === "educational"
                    ? get("students")
                    : [],
                manager ? get("tasks") : [],
            ]),
        [project.id],
    );
    if (error || !value) return <LoadState error={error}/>;
    const [teams, applications, tasks, templates, students, existingTasks] =
        value;
    return (
        <>
            <nav className="tabs" aria-label="Разделы проекта">
                {Object.entries({
                    overview: "Обзор",
                    teams: "Команды",
                    tasks: "Задания",
                }).map(([k, v]) => (
                    <a
                        key={k}
                        aria-current={k === tab ? "page" : undefined}
                        href={"#project/" + project.id + "/" + k}
                    >
                        {v}
                    </a>
                ))}
            </nav>
            {tab === "overview" && (
                <div className="overview">
                    <section className="card">
                        <h2>О проекте</h2>
                        <h3>Цель</h3>
                        <p>{project.goal || "Цель ещё не указана"}</p>
                        <h3>Описание</h3>
                        <p>{project.description || "Описание ещё не добавлено"}</p>
                    </section>
                    <section className="card">
                        <h2>Участие</h2>
                        <p>Управляющий · {project.managerName}</p>
                        <a href={"#project/" + project.id + "/teams"}>
                            Команды · {teams.length} →
                        </a>
                        <p>
                            <a href={"#project/" + project.id + "/tasks"}>
                                Доступные назначения · {tasks.length} →
                            </a>
                        </p>
                    </section>
                </div>
            )}
            {tab === "teams" && (
                <TeamPanel
                    {...{teams, applications, students, project, session, reload}}
                />
            )}
            {tab === "tasks" && (
                <section>
                    <h2>Назначенные задания</h2>
                    {canCreateTemplate && (
                        <Allocation
                            manager={manager}
                            assignments={tasks}
                            {...{teams, templates, existingTasks, project, session, reload}}
                        />
                    )}
                    {manager && (
                        <label>
                            <input
                                type="checkbox"
                                checked={pendingOnly}
                                onChange={(e) => setPendingOnly(e.target.checked)}
                            />
                            Только ожидающие проверки
                        </label>
                    )}
                    {tasks
                        .filter((a) => !pendingOnly || a.status === "submitted")
                        .map((a) => (
                            <article key={a.id}>
                                <button className="link" onClick={() => setAssignment(a.id)}>
                                    {a.title}
                                </button>
                                <p>
                                    {a.team} · {statuses[a.status]}{" "}
                                    {a.deadline_at &&
                                        "· до " + new Date(a.deadline_at).toLocaleString("ru-RU")}
                                </p>
                            </article>
                        ))}
                    {!tasks.length && <p>Доступных назначений пока нет.</p>}
                </section>
            )}
            <button onClick={reload}>Обновить проект</button>
        </>
    );
}

export function MyTeams({session, open}) {
    const {value, error} = useLoad(
        () =>
            Promise.all([
                request("me/teams", session),
                request("me/applications", session),
            ]),
        [],
    );
    if (!value || error) return <LoadState error={error}/>;
    return (
        <>
            <section className="card">
                <h2>Мои команды</h2>
                {value[0].length ? (
                    value[0].map((t) => (
                        <p key={t.id}>
                            <button onClick={() => open(t.project_id)}>
                                {t.project_name} · {t.name}
                            </button>
                        </p>
                    ))
                ) : (
                    <p>
                        Вы пока не состоите в командах.{" "}
                        <a href="#projects">Найти проект →</a>
                    </p>
                )}
            </section>
            <section className="card">
                <h2>Заявки на вступление</h2>
                {value[1].length ? (
                    value[1].map((a) => (
                        <p key={a.id}>
                            <button onClick={() => open(a.project_id)}>
                                {a.project_name} · {a.team}
                            </button>
                            {" "}
                            {statuses[a.status]} ·{" "}
                            {new Date(a.created_at).toLocaleDateString("ru-RU")}
                        </p>
                    ))
                ) : (
                    <p>Заявок пока нет.</p>
                )}
            </section>
        </>
    );
}
