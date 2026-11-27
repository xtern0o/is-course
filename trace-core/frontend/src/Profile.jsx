import React, {useState} from "react";
import {request, Field, useLoad, LoadState} from "./ui";
import {roles} from "./Product";

function Editor({initial, session, onSaved, reload}) {
    const [saved, setSaved] = useState(initial);
    const [draft, setDraft] = useState(initial);
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState("");
    const [error, setError] = useState("");
    const [conflict, setConflict] = useState(false);
    const dirty = ["displayName", "specialty", "bio", "shareIntro"].some(
        (k) => draft[k] !== saved[k],
    );

    function update(key, value) {
        setDraft({...draft, [key]: value});
        setNotice("");
        if (!conflict) setError("");
    }

    async function submit(e) {
        e.preventDefault();
        setBusy(true);
        setError("");
        try {
            const {displayName, specialty, bio, shareIntro, version} = draft;
            const result = await request("me/profile", session, "PUT", {
                displayName,
                specialty,
                bio,
                shareIntro,
                version,
            });
            setDraft(result);
            setSaved(result);
            setNotice("Профиль сохранён");
            onSaved();
        } catch (e) {
            setConflict(e.status === 409);
            setError(
                e.status === 409
                    ? "Профиль изменён в другом окне. Скопируйте нужный текст перед загрузкой актуальной версии."
                    : e.message,
            );
        } finally {
            setBusy(false);
        }
    }

    return (
        <>
            <h1>Мой профиль</h1>
            <div className="profile-layout">
                <form className="card profile-editor" onSubmit={submit}>
                    <h2>Личные данные</h2>
                    <fieldset disabled={busy}>
                        <Field
                            label="Имя в TRACE"
                            name="displayName"
                            required
                            maxLength={255}
                            value={draft.displayName}
                            onChange={(e) => update("displayName", e.target.value)}
                        />
                        <Field
                            label="Специализация"
                            name="specialty"
                            placeholder="Например, Kotlin-разработчик"
                            maxLength={120}
                            value={draft.specialty}
                            onChange={(e) => update("specialty", e.target.value)}
                        />
                        <Field
                            label="О себе"
                            name="bio"
                            area
                            rows={5}
                            maxLength={1200}
                            placeholder="Чем вы занимаетесь и с какими задачами можете помочь команде?"
                            value={draft.bio}
                            onChange={(e) => update("bio", e.target.value)}
                        />
                        <div className="counter">{draft.bio.length} / 1200</div>
                        <label className="visibility">
                            <input
                                type="checkbox"
                                checked={draft.shareIntro}
                                onChange={(e) => update("shareIntro", e.target.checked)}
                            />{" "}
                            Показывать специализацию и текст «О себе» в списках участников
                            моих команд
                        </label>
                        <div className="profile-actions">
                            <button
                                type="submit"
                                className="primary"
                                disabled={!dirty || !draft.displayName.trim() || conflict}
                            >
                                {busy ? "Сохранение…" : "Сохранить изменения"}
                            </button>
                            {dirty && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        setDraft(saved);
                                        setNotice("");
                                        if (!conflict) setError("");
                                    }}
                                >
                                    Отменить правки
                                </button>
                            )}
                        </div>
                    </fieldset>
                    {notice && <p role="status">{notice}</p>}
                    {error && <p role="alert">{error}</p>}
                    {conflict && (
                        <button type="button" onClick={reload}>
                            Загрузить сохранённую версию
                        </button>
                    )}
                </form>
                <div>
                    <section className="card preview">
                        <small>Ваша карточка в команде</small>
                        <div className="avatar" aria-hidden="true">
                            {draft.displayName
                                .trim()
                                .split(/\s+/)
                                .slice(0, 2)
                                .map((w) => w[0])
                                .join("")
                                .toUpperCase() || "·"}
                        </div>
                        <h2>{draft.displayName.trim() || "Ваше имя"}</h2>
                        {draft.shareIntro && (
                            <>
                                {draft.specialty && <strong>{draft.specialty}</strong>}
                                {draft.bio && <p>{draft.bio}</p>}
                            </>
                        )}
                    </section>
                    <section className="card">
                        <h2>Учётная запись</h2>
                        <dl>
                            <dt>Логин</dt>
                            <dd>{saved.username}</dd>
                            <dt>Электронная почта</dt>
                            <dd>{saved.email}</dd>
                            <dt>Роль</dt>
                            <dd>{roles[session.user.role]}</dd>
                        </dl>
                        <a href={saved.accountUrl} target="_blank" rel="noreferrer">
                            Настройки аккаунта Keycloak ↗
                        </a>
                    </section>
                </div>
            </div>
        </>
    );
}

export default function Profile({session, onSaved}) {
    const {value, error, reload} = useLoad(
        () => request("me/profile", session),
        [],
    );
    if (!value || error) return <LoadState error={error}/>;
    return (
        <Editor
            key={value.version}
            initial={value}
            session={session}
            onSaved={onSaved}
            reload={reload}
        />
    );
}
