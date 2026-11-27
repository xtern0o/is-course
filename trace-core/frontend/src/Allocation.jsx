import React, {useState} from "react";
import {ActionForm, Field, Panel, request} from "./ui";

function ParameterEditor({rows, setRows}) {
    return (
        <>
            <button
                type="button"
                onClick={() => setRows([...rows, crypto.randomUUID()])}
            >
                Добавить параметр
            </button>
            {rows.map((k) => (
                <fieldset key={k}>
                    <Field
                        name={"name-" + k}
                        label="Название параметра"
                        required
                        maxLength={100}
                    />
                    <Field label="Описание параметра" name={"description-" + k}/>
                    <label>
                        Тип
                        <select name={"type-" + k}>
                            {["text", "integer", "decimal", "boolean", "date"].map((t) => (
                                <option key={t}>{t}</option>
                            ))}
                        </select>
                    </label>
                    <Field name={"default-" + k} label="Значение по умолчанию"/>
                    <label>
                        <input type="checkbox" name={"required-" + k}/>
                        Обязательный
                    </label>
                    <button
                        type="button"
                        onClick={() => setRows(rows.filter((r) => r !== k))}
                    >
                        Удалить параметр
                    </button>
                </fieldset>
            ))}
        </>
    );
}

export function Allocation({
                               manager,
                               project,
                               session,
                               templates,
                               teams,
                               existingTasks,
                               assignments,
                               reload,
                           }) {
    const [rows, setRows] = useState([]),
        [selected, setSelected] = useState(""),
        [targets, setTargets] = useState([]);
    const [taskId, setTaskId] = useState("");
    const existing = existingTasks.find((t) => t.id === taskId);
    const template =
        templates.find((t) => t.id === (existing?.template_id || selected)) ||
        templates[0];
    const eligible = teams.filter(
        (team) =>
            !existing ||
            !assignments.some(
                (a) => a.task_id === existing.id && a.team_id === team.id,
            ),
    );
    const send = (suffix, data) =>
        request("projects/" + project.id + "/" + suffix, session, "POST", data);
    return (
        <>
            <Panel
                title="Создать шаблон"
                context={project.name}
                success="Шаблон создан"
            >
                <ActionForm
                    label="Создать шаблон"
                    onDone={() => {
                        setRows([]);
                        reload();
                    }}
                    run={(f) =>
                        send("templates", {
                            title: f.get("title"),
                            description: f.get("description"),
                            expectedResult: f.get("expectedResult"),
                            parameters: rows.map((k) => ({
                                name: f.get("name-" + k),
                                description: f.get("description-" + k),
                                type: f.get("type-" + k),
                                required: f.has("required-" + k),
                                defaultValue: f.get("default-" + k) || null,
                            })),
                        })
                    }
                >
                    <Field label="Название шаблона" name="title" required/>
                    <Field label="Описание" name="description" area/>
                    <Field label="Ожидаемый результат" name="expectedResult" area/>
                    <ParameterEditor rows={rows} setRows={setRows}/>
                </ActionForm>
            </Panel>
            {manager && template && (
                <Panel
                    title="Создать или назначить задание"
                    context={project.name}
                    success={existing ? "Задание назначено" : "Задание создано"}
                >
                    <label>
                        Задание
                        <select
                            value={taskId}
                            onChange={(e) => {
                                setTaskId(e.target.value);
                                setTargets([]);
                            }}
                        >
                            <option value="">Новое задание</option>
                            {existingTasks.map((t) => (
                                <option key={t.id} value={t.id}>
                                    {t.title}
                                </option>
                            ))}
                        </select>
                    </label>
                    <ActionForm
                        key={taskId}
                        label={existing ? "Назначить командам" : "Создать задание"}
                        onDone={() => {
                            setTargets([]);
                            reload();
                        }}
                        run={(f) => {
                            const selectedTeams = targets.map((id) => ({
                                teamId: id,
                                values: Object.fromEntries(
                                    template.parameters.map((p) => [
                                        p.id,
                                        f.get(id + p.id) || "",
                                    ]),
                                ),
                            }));
                            if (existing) {
                                if (!selectedTeams.length) throw Error("Выберите команду");
                                return send("tasks/" + existing.id + "/assignments", {
                                    teams: selectedTeams,
                                });
                            }
                            return send("tasks", {
                                templateId: template.id,
                                title: f.get("title"),
                                instructions: f.get("instructions"),
                                deadline: f.get("deadline")
                                    ? new Date(f.get("deadline")).toISOString()
                                    : null,
                                teams: selectedTeams,
                            });
                        }}
                    >
                        {!existing && (
                            <>
                                <label>
                                    Шаблон
                                    <select
                                        value={template.id}
                                        onChange={(e) => setSelected(e.target.value)}
                                    >
                                        {templates.map((t) => (
                                            <option key={t.id} value={t.id}>
                                                {t.title}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <p>{template.description}</p>
                                <Field name="title" label="Название задания" required/>
                                <Field name="instructions" label="Инструкция" area required/>
                                <Field
                                    name="deadline"
                                    label="Срок (необязательно)"
                                    type="datetime-local"
                                />
                            </>
                        )}
                        <h3>Кому назначить</h3>
                        {targets.length ? (
                            <p>
                                Выбраны команды:{" "}
                                {teams
                                    .filter((t) => targets.includes(t.id))
                                    .map((t) => t.name)
                                    .join(", ")}
                            </p>
                        ) : (
                            <p>
                                {existing
                                    ? "Выберите команду для этого задания."
                                    : "Можно выбрать команды сейчас или распределить задание позже."}
                            </p>
                        )}
                        {eligible.map((t) => (
                            <fieldset key={t.id}>
                                <label>
                                    <input
                                        type="checkbox"
                                        checked={targets.includes(t.id)}
                                        onChange={(e) =>
                                            setTargets(
                                                e.target.checked
                                                    ? [...targets, t.id]
                                                    : targets.filter((i) => i !== t.id),
                                            )
                                        }
                                    />
                                    {t.name}
                                </label>
                                {targets.includes(t.id) &&
                                    template.parameters.map((p) => (
                                        <Field
                                            key={p.id}
                                            label={p.name + " · " + p.data_type}
                                            name={t.id + p.id}
                                            placeholder={p.default_value || ""}
                                            required={p.required && !p.default_value}
                                        />
                                    ))}
                            </fieldset>
                        ))}
                    </ActionForm>
                </Panel>
            )}
        </>
    );
}
