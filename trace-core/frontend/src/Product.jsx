import React, {useState} from "react";
import {
    request,
    useLoad,
    LoadState,
    ActionForm,
    Field,
    Panel,
    statuses,
} from "./ui";

export const roles = {
    admin: "Администратор",
    teacher: "Преподаватель",
    student: "Студент",
};

export function Dashboard({session}) {
    const {value, error, reload} = useLoad(
        () =>
            Promise.all([
                request("me/inbox", session),
                request("me/pending-applications", session),
            ]),
        [],
    );
    const [filter, setFilter] = useState("all");
    if (error || !value) return <LoadState error={error}/>;
    const [tasks, applications] = value;
    const revisions = tasks.filter(
        (t) => t.status === "revision_required",
    ).length;
    const review = tasks.filter((t) => t.status === "submitted").length;
    const categories = [
        ["all", "Все задания", tasks.length],
        ["revision", "На доработке", revisions],
        ["review", "На проверке", review],
    ];
    const shown = tasks.filter(
        (t) =>
            filter === "all" ||
            (filter === "revision"
                ? t.status === "revision_required"
                : t.status === "submitted"),
    );
    return (
        <>
            <div className="heading">
                <div>
                    <small>{roles[session.user.role]}</small>
                    <h1>Рабочий стол</h1>
                </div>
                <button onClick={reload}>Обновить</button>
            </div>
            <div className="metrics">
                {categories.map(([k, title, count]) => (
                    <button
                        key={k}
                        aria-pressed={k === filter}
                        onClick={() => setFilter(k)}
                    >
                        <small>{title}</small>
                        <strong>{count}</strong>
                    </button>
                ))}
            </div>
            <section className="card">
                <h2>
                    {filter === "all"
                        ? "Ближайшие задачи"
                        : categories.find((c) => c[0] === filter)[1]}
                </h2>
                {shown.map((t) => (
                    <a
                        className="row"
                        key={t.id}
                        href={"#assignment/" + t.id + "/" + t.project_id}
                    >
                        <div>
                            <small>
                                {t.project} · {t.team}
                            </small>
                            <h3>{t.title}</h3>
                            {t.status === "revision_required" && <p>{t.feedback}</p>}
                            {t.deadline_at && (
                                <small>
                                    Срок · {new Date(t.deadline_at).toLocaleString("ru-RU")}
                                </small>
                            )}
                        </div>
                        <span className="chip">
              {t.review ? "Проверить работу" : statuses[t.status]}
            </span>
                    </a>
                ))}
                {!shown.length && (
                    <div className="empty">
                        <h3>
                            {tasks.length
                                ? "В этой категории пока пусто"
                                : "Заданий для вас пока нет"}
                        </h3>
                        <a href="#projects">Перейти к проектам →</a>
                    </div>
                )}
            </section>
            {!!applications.length && (
                <section className="card">
                    <h2>Заявки на вступление · {applications.length}</h2>
                    {applications.map((a) => (
                        <a
                            className="row"
                            key={a.id}
                            href={"#project/" + a.project_id + "/teams"}
                        >
                            <div>
                                <strong>{a.display_name}</strong>
                                <p>
                                    {a.project} · {a.team}
                                </p>
                            </div>
                            <span>Рассмотреть →</span>
                        </a>
                    ))}
                </section>
            )}
        </>
    );
}

export function Accounts({session}) {
    const [q, search] = useState(""),
        [page, setPage] = useState(0),
        [editing, edit] = useState(null),
        [notice, tell] = useState("");
    const {value, error, reload} = useLoad(
        () =>
            request(
                "admin/accounts?q=" + encodeURIComponent(q) + "&page=" + page,
                session,
            ),
        [q, page],
    );
    const done = (message) => {
        edit(null);
        tell(message);
        reload();
    };
    return (
        <>
            <div className="heading">
                <h1>Пользователи</h1>
                <button className="primary" onClick={() => edit("new")}>
                    + Создать пользователя
                </button>
            </div>
            {notice && <p role="status">{notice}</p>}
            {editing && (
                <section className="card">
                    <h2>
                        {editing === "new" ? "Новый пользователь" : editing.display_name}
                    </h2>
                    {editing === "new" ? (
                        <ActionForm
                            label="Создать аккаунт"
                            run={(f) =>
                                request(
                                    "admin/accounts",
                                    session,
                                    "POST",
                                    Object.fromEntries(f),
                                )
                            }
                            onDone={() =>
                                done(
                                    "Аккаунт студента создан. Передайте пользователю логин и временный пароль.",
                                )
                            }
                        >
                            <Field name="firstName" label="Имя" required/>
                            <Field name="lastName" label="Фамилия" required/>
                            <Field
                                name="email"
                                type="email"
                                label="Электронная почта"
                                required
                            />
                            <Field
                                name="username"
                                label="Логин (3–80 латинских букв, цифр, точек, дефисов или подчёркиваний)"
                                pattern="[a-zA-Z0-9._\-]{3,80}"
                                required
                            />
                            <Field
                                name="password"
                                type="password"
                                label="Временный пароль (от 8 символов)"
                                minLength={8}
                                autoComplete="new-password"
                                required
                            />
                        </ActionForm>
                    ) : (
                        <ActionForm
                            key={editing.id}
                            label="Сохранить доступ"
                            run={(f) =>
                                request("admin/accounts/" + editing.id, session, "PATCH", {
                                    role: f.get("role"),
                                    enabled: f.get("access") === "active",
                                })
                            }
                            onDone={() => done("Права доступа обновлены.")}
                        >
                            <label>
                                Роль
                                <select name="role" defaultValue={editing.role}>
                                    {Object.entries(roles).map(([k, v]) => (
                                        <option value={k} key={k}>
                                            {v}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                Доступ к TRACE
                                <select
                                    name="access"
                                    defaultValue={editing.enabled ? "active" : "blocked"}
                                >
                                    <option value="active">Открыт</option>
                                    <option value="blocked">Приостановлен</option>
                                </select>
                            </label>
                        </ActionForm>
                    )}
                    <button onClick={() => edit(null)}>Отмена</button>
                </section>
            )}
            <section className="card">
                <Field
                    type="search"
                    label="Поиск по имени, логину или почте"
                    value={q}
                    onChange={(e) => {
                        search(e.target.value);
                        setPage(0);
                    }}
                />
                {error || !value ? (
                    <LoadState error={error}/>
                ) : (
                    <>
                        {value.items.map((u) => (
                            <div className="row" key={u.id}>
                                <div>
                                    <h3>{u.display_name}</h3>
                                    <small>
                                        {u.username} · {u.email}
                                    </small>
                                </div>
                                <span>
                  {roles[u.role]} · {u.enabled ? "Активен" : "Приостановлен"}
                </span>
                                {u.id === session.user.id ? (
                                    <small>Это вы</small>
                                ) : (
                                    <button
                                        onClick={() => {
                                            edit(u);
                                            window.scrollTo(0, 0);
                                        }}
                                    >
                                        Изменить
                                    </button>
                                )}
                            </div>
                        ))}
                        {!value.items.length && <p>Совпадений нет.</p>}
                        {(page > 0 || value.next) && (
                            <nav>
                                <button disabled={!page} onClick={() => setPage(page - 1)}>
                                    Назад
                                </button>
                                {" "}
                                {page + 1}{" "}
                                <button
                                    disabled={!value.next}
                                    onClick={() => setPage(page + 1)}
                                >
                                    Далее
                                </button>
                            </nav>
                        )}
                    </>
                )}
            </section>
        </>
    );
}
