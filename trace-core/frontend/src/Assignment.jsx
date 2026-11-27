import React from "react";
import {
    request,
    useLoad,
    LoadState,
    Field,
    Panel,
    ActionForm,
    statuses,
} from "./ui";

const date = (v) => new Date(v).toLocaleString("ru-RU");
export default function Assignment({id, session, back, refreshProject}) {
    const path = "assignments/" + id;
    const {value, error, reload} = useLoad(
        () =>
            Promise.all([
                request(path, session),
                request(path + "/discussion", session),
            ]),
        [id],
    );
    if (error || !value) return <LoadState error={error}/>;
    const [task, discussion] = value;
    const send = (part, data) =>
        request(path + "/" + part, session, "POST", data);
    const changed = () => {
        reload();
        refreshProject();
    };
    const comment = (f, extra = {}) =>
        send("messages", {content: f.get("content"), ...extra});
    return (
        <section>
            <button onClick={back}>← К проекту</button>
            <h1>{task.title}</h1>
            <p>
                {task.team} · {statuses[task.status]}
            </p>
            <p>{task.description}</p>
            <h3>Что нужно сделать</h3>
            <p>{task.instructions}</p>
            {task.expected_result && (
                <p>Ожидаемый результат · {task.expected_result}</p>
            )}
            {task.deadline_at && <p>Сдать до {date(task.deadline_at)}</p>}
            {task.parameters.map((p) => (
                <p key={p.name}>
                    {p.name} · {p.value}
                </p>
            ))}
            {task.status === "revision_required" && (
                <div className="feedback">
                    <h3>Что исправить</h3>
                    <p>{task.attempts[0]?.comment}</p>
                </div>
            )}
            {task.scope.member &&
                ["assigned", "in_progress", "revision_required"].includes(
                    task.status,
                ) && (
                    <Panel
                        context={task.title}
                        success="Работа отправлена на проверку"
                        title={
                            task.status === "revision_required"
                                ? "Отправить исправление"
                                : "Отправить работу"
                        }
                    >
                        <ActionForm
                            label="Сдать на проверку"
                            onDone={changed}
                            run={(f) => {
                                const upload = new FormData();
                                upload.set("content", f.get("content"));
                                for (const file of f.getAll("files"))
                                    if (file.size) upload.append("files", file);
                                return send("attempts", upload);
                            }}
                        >
                            <Field name="content" label="Результат" area/>
                            <Field
                                name="files"
                                label="Вложения (до 10 МиБ каждый, 20 МиБ на запрос)"
                                type="file"
                                multiple
                            />
                        </ActionForm>
                    </Panel>
                )}
            <h3>История сдачи</h3>
            {task.attempts.map((a, index) => (
                <article key={a.id}>
                    <b>Попытка {a.attempt_number}</b>
                    <p>
                        {a.display_name} · {date(a.submitted_at)}
                    </p>
                    <p>{a.content}</p>
                    {a.files.map((f) => (
                        <p key={f.id}>
                            <a href={"/api/" + path + "/files/" + f.id}>{f.original_name}</a>
                        </p>
                    ))}
                    {a.decision && (
                        <p>
                            {statuses[a.decision]} · {a.comment}
                        </p>
                    )}
                    <Panel
                        title="Добавить комментарий"
                        context={"Попытка " + a.attempt_number}
                        success="Комментарий добавлен"
                    >
                        <ActionForm
                            label="Добавить комментарий"
                            run={(f) => comment(f, {submissionId: a.id})}
                            onDone={reload}
                        >
                            <Field label="Комментарий" name="content" area required/>
                        </ActionForm>
                    </Panel>
                    {task.scope.manager && index === 0 && task.status === "submitted" && (
                        <ActionForm
                            label="Сохранить проверку"
                            onDone={changed}
                            run={(f) =>
                                send("attempts/" + a.id + "/review", Object.fromEntries(f))
                            }
                        >
                            <label>
                                Решение
                                <select name="decision">
                                    <option value="accepted">Принять</option>
                                    <option value="revision_required">
                                        Вернуть на доработку
                                    </option>
                                </select>
                            </label>
                            <Field
                                label="Замечания (обязательны при доработке)"
                                name="comment"
                                area
                            />
                        </ActionForm>
                    )}
                </article>
            ))}
            <h3>Объявления задания</h3>
            {discussion.notices.map((n, i) => (
                <article key={i}>
                    <b>{n.title}</b>
                    <p>{n.content}</p>
                </article>
            ))}
            {(task.scope.manager || task.scope.author === session.user.id) && (
                <Panel
                    title="Опубликовать объявление"
                    context="Увидят все назначенные команды"
                    success="Объявление опубликовано"
                >
                    <ActionForm
                        label="Опубликовать"
                        onDone={reload}
                        run={(f) => send("notices", Object.fromEntries(f))}
                    >
                        <Field name="title" label="Заголовок" required/>
                        <Field name="content" label="Объявление" area required/>
                    </ActionForm>
                </Panel>
            )}
            <h3>Обсуждение команды</h3>
            {discussion.messages.map((m) => (
                <article key={m.id}>
                    <small>
                        {m.system_event ? "Система" : m.display_name} · {date(m.created_at)}{" "}
                        {m.submission_id &&
                            "· попытка " +
                            task.attempts.find((a) => a.id === m.submission_id)
                                ?.attempt_number}
                    </small>
                    <p>{m.content}</p>
                </article>
            ))}
            <ActionForm
                label="Отправить сообщение"
                run={(f) => comment(f)}
                onDone={reload}
            >
                <Field label="Сообщение" name="content" required area/>
            </ActionForm>
            <button onClick={reload}>Обновить историю</button>
        </section>
    );
}
