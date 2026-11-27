import {
  request,
  Field,
  ActionForm,
  useLoad,
  LoadState,
  FeedbackContext,
} from "./ui";
import Workspace, { MyTeams } from "./Workspace";
import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
import "./product.css";
import { Dashboard, Accounts, roles } from "./Product";
import Profile from "./Profile";
import Assignment from "./Assignment";

const labels = { educational: "Учебный проект", development: "Организация" };
const states = {
  draft: "Черновик",
  active: "Активен",
  completed: "Завершён",
  archived: "В архиве",
};
function ProjectForm({ project, session, onSave, cancel }) {
  const type =
    project?.type ||
    (session.user.role === "teacher" ? "educational" : "development");
  async function save(form) {
    const data = Object.fromEntries(form);
    if (!project) data.type = type;
    return request(
      project ? "projects/" + project.id : "projects",
      session,
      project ? "PATCH" : "POST",
      data,
    );
  }
  return (
    <section>
      <h1>{project ? "Редактирование" : "Создание проекта"}</h1>
      <p>{labels[type]}</p>
      <ActionForm label="Сохранить" run={save} onDone={onSave}>
        <Field
          name="name"
          label="Название"
          defaultValue={project?.name}
          maxLength={255}
          required
        />
        <Field name="goal" label="Цель" area defaultValue={project?.goal} />
        <Field
          name="description"
          label="Описание"
          area
          defaultValue={project?.description}
        />
      </ActionForm>
      <button onClick={cancel}>Отмена</button>
    </section>
  );
}

function Catalog({ session }) {
  const [page, setPage] = useState(0);
  const { value, error } = useLoad(
    () => request("projects?page=" + page, session),
    [page],
  );
  return (
    <>
      <div className="heading">
        <h1>Проекты</h1>
        {session.user.role !== "admin" && (
          <a className="primary" href="#new">
            + Создать проект
          </a>
        )}
      </div>
      {!value || error ? (
        <LoadState error={error} />
      ) : (
        <>
          <div className="grid">
            {value.items.map((p) => (
              <a className="projectcard" key={p.id} href={"#project/" + p.id}>
                <small>{labels[p.type]}</small>
                <h2>{p.name}</h2>
                <p>{p.description || p.goal}</p>
                <footer>
                  {p.managerName}
                  <span>{states[p.status]}</span>
                </footer>
              </a>
            ))}
          </div>
          {!value.items.length && (
            <div className="empty">В каталоге пока нет проектов.</div>
          )}
          {value.totalPages > 1 && (
            <nav>
              <button disabled={!page} onClick={() => setPage(page - 1)}>
                Назад
              </button>{" "}
              {page + 1} / {value.totalPages}{" "}
              <button
                disabled={page + 1 >= value.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Далее
              </button>
            </nav>
          )}
        </>
      )}
    </>
  );
}

function ProjectPage({ session, id, tab }) {
  const {
    value: p,
    error,
    reload,
  } = useLoad(() => request("projects/" + id, session), [id]);
  if (error || !p) return <LoadState error={error} />;
  if (tab === "edit" && session.user.id === p.managerId)
    return (
      <ProjectForm
        project={p}
        session={session}
        cancel={() => (location.hash = "project/" + id)}
        onSave={() => {
          reload();
          location.hash = "project/" + id;
        }}
      />
    );
  return (
    <>
      <a href="#projects">← Все проекты</a>
      <div className="heading">
        <div>
          <small>
            {labels[p.type]} · {states[p.status]}
          </small>
          <h1>{p.name}</h1>
        </div>
        {session.user.id === p.managerId && (
          <a className="secondary" href={"#project/" + id + "/edit"}>
            Редактировать
          </a>
        )}
      </div>
      <Workspace project={p} session={session} tab={tab || "overview"} />
    </>
  );
}

function App() {
  const {
    value: session,
    error,
    reload: refreshSession,
  } = useLoad(() => request("me"), []);
  const [message, notify] = useState(null);
  useEffect(() => {
    if (!message) return;
    const t = setTimeout(() => notify(null), 5000);
    return () => clearTimeout(t);
  }, [message]);
  const [route, setRoute] = useState(location.hash.slice(1));
  useEffect(() => {
    const changed = () => {
      setRoute(location.hash.slice(1));
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  if (!session || error)
    return (
      <main>
        <LoadState error={error} />
        {error && <a href="/logout">Сменить аккаунт</a>}
      </main>
    );
  const admin = session.user.role === "admin";
  const [screen, id, tab] = (route || (admin ? "accounts" : "home")).split("/");
  const menu = [
    ["home", "Рабочий стол"],
    ["projects", "Проекты"],
    ["teams", "Мои команды и заявки"],
    ...(admin ? [["accounts", "Пользователи"]] : []),
  ];
  const current = ["project", "new"].includes(screen)
    ? "projects"
    : screen === "assignment"
      ? "home"
      : screen;
  return (
    <FeedbackContext.Provider value={(text) => notify({ text })}>
      <div className="shell">
        <aside>
          <div className="brand">
            <span>▰</span> TRACE
          </div>
          <nav>
            {menu.map(([k, v]) => (
              <a
                key={k}
                aria-current={current === k ? "page" : undefined}
                href={"#" + k}
              >
                {v}
              </a>
            ))}
          </nav>
          <div className="identity">
            <a
              className="profile-link"
              href="#profile"
              aria-current={current === "profile" ? "page" : undefined}
            >
              <strong>{session.user.name}</strong>
              <span>Мой профиль →</span>
            </a>
            <small>{roles[session.user.role]}</small>
            <form method="post" action="/logout">
              <input type="hidden" name="_csrf" value={session.csrfToken} />
              <button>Выйти</button>
            </form>
          </div>
        </aside>
        <main>
          <div className="crumb">
            TRACE　/　
            {menu.find(([k]) => k === current)?.[1] ||
              (current === "profile" ? "Мой профиль" : "Проект")}
          </div>
          {screen === "profile" ? (
            <Profile session={session} onSaved={refreshSession} />
          ) : screen === "projects" ? (
            <Catalog session={session} />
          ) : screen === "accounts" && admin ? (
            <Accounts session={session} />
          ) : screen === "teams" ? (
            <>
              <h1>Мои команды и заявки</h1>
              <MyTeams
                session={session}
                open={(id) => (location.hash = "project/" + id + "/teams")}
              />
            </>
          ) : screen === "assignment" ? (
            <Assignment
              key={id}
              id={id}
              session={session}
              back={() =>
                (location.hash = tab ? "project/" + tab + "/tasks" : "home")
              }
              refreshProject={() => {}}
            />
          ) : screen === "project" ? (
            <ProjectPage key={id} id={id} tab={tab} session={session} />
          ) : screen === "new" && !admin ? (
            <ProjectForm
              session={session}
              cancel={() => (location.hash = "projects")}
              onSave={(p) => (location.hash = "project/" + p.id)}
            />
          ) : (
            <Dashboard session={session} />
          )}
        </main>
        <div
          className={message ? "feedback-toast" : ""}
          role="status"
          aria-live="polite"
        >
          {message?.text}
        </div>
      </div>
    </FeedbackContext.Provider>
  );
}
createRoot(document.getElementById("root")).render(<App />);
