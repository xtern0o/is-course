import React, {
    createContext,
    useContext,
    useEffect,
    useId,
    useRef,
    useState,
} from "react";

export async function request(path, session, method = "GET", data) {
    const upload = data instanceof FormData;
    const headers = {};
    if (method !== "GET") headers[session.csrfHeader] = session.csrfToken;
    if (data !== undefined && !upload)
        headers["Content-Type"] = "application/json";
    const res = await fetch("/api/" + path, {
        method,
        headers,
        body: upload ? data : JSON.stringify(data),
    });
    if (res.status === 401) location.assign("/oauth2/authorization/trace");
    if (!res.ok) {
        const info = await res.json().catch(() => ({}));
        const error = Error(
            info.message ||
            {
                400: "Проверьте поля формы",
                403: "Недостаточно прав",
                404: "Объект не найден",
                409: "Обновите данные и проверьте состояние",
                413: "Файл слишком большой",
            }[res.status] ||
            "Ошибка запроса",
        );
        error.status = res.status;
        throw error;
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

export function useLoad(load, deps) {
    const [value, setValue] = useState(null),
        [error, setError] = useState("");
    const [version, reload] = useState(0);
    useEffect(() => {
        let active = true;
        setError("");
        load()
            .then((v) => {
                if (active) setValue(v);
            })
            .catch((e) => {
                if (active) setError(e.message);
            });
        return () => {
            active = false;
        };
    }, [...deps, version]);
    return {value, error, reload: () => reload((v) => v + 1)};
}

export function ActionForm({children, label, run, onDone}) {
    const completed = useContext(ActionContext);
    const [busy, setBusy] = useState(false),
        [error, setError] = useState("");

    async function submit(e) {
        e.preventDefault();
        const form = e.currentTarget;
        setBusy(true);
        setError("");
        try {
            const result = await run(new FormData(form));
            form.reset();
            completed?.();
            onDone?.(result);
        } catch (err) {
            setError(err.message);
        } finally {
            setBusy(false);
        }
    }

    return (
        <form onSubmit={submit} className="action">
            <fieldset disabled={busy}>
                {children}
                <button>{busy ? "Сохранение…" : label}</button>
            </fieldset>
            {error && <p role="alert">{error}</p>}
        </form>
    );
}

export function Field({label, name, area = false, ...props}) {
    return (
        <label>
            {label}
            {area ? (
                <textarea name={name} {...props} />
            ) : (
                <input name={name} {...props} />
            )}
        </label>
    );
}

const ActionContext = createContext(null);
export const FeedbackContext = createContext(() => {
});

export function Disclosure({title, count, children, initialOpen = false}) {
    const [open, setOpen] = useState(initialOpen);
    const id = useId();
    return (
        <div className="disclosure">
            <button
                type="button"
                className="disclose"
                aria-expanded={open}
                aria-controls={id}
                onClick={() => setOpen(!open)}
            >
        <span>
          {title} {count != null && <b className="count">{count}</b>}
        </span>
                <i aria-hidden="true"/>
            </button>
            <div id={id} hidden={!open} className="disclosed">
                {open && children}
            </div>
        </div>
    );
}

export function Panel({title, context, success = "Сохранено", children}) {
    const ref = useRef(null);
    const [opened, setOpened] = useState(false);
    const id = useId();
    const notify = useContext(FeedbackContext);
    const complete = () => {
        ref.current?.close();
        setOpened(false);
        notify(success);
    };
    return (
        <div className="action-entry">
            <button
                type="button"
                className="launch"
                aria-haspopup="dialog"
                onClick={() => {
                    setOpened(true);
                    ref.current.showModal();
                }}
            >
                {title}
                <span aria-hidden="true">↗</span>
            </button>
            <dialog ref={ref} aria-labelledby={id} className="task-dialog">
                <div className="dialog-head">
                    <div>
                        {context && <small>{context}</small>}
                        <h2 id={id}>{title}</h2>
                    </div>
                    <button
                        type="button"
                        className="dismiss"
                        aria-label="Закрыть окно"
                        onClick={() => ref.current.close()}
                    >
                        ×
                    </button>
                </div>
                <div className="dialog-body">
                    <ActionContext.Provider value={complete}>
                        {opened && children}
                    </ActionContext.Provider>
                </div>
            </dialog>
        </div>
    );
}

export function LoadState({error}) {
    return error ? <p role="alert">{error}</p> : <p>Загрузка…</p>;
}

export const statuses = {
    assigned: "Назначено",
    in_progress: "В работе",
    submitted: "На проверке",
    revision_required: "На доработке",
    completed: "Принято",
    pending: "Ожидает решения",
    accepted: "Принято",
    rejected: "Отклонено",
};
