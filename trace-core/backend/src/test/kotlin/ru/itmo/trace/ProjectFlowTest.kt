package ru.itmo.trace

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
@AutoConfigureMockMvc
class ProjectFlowTest {
    @Autowired
    lateinit var mvc: MockMvc
    @Autowired
    lateinit var json: ObjectMapper
    @MockBean
    lateinit var clients: ClientRegistrationRepository

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(properties: DynamicPropertyRegistry) {
            properties.add("trace.attachments.directory") { "build/test-attachments" }
            val url = System.getenv("TRACE_TEST_DB_URL")
            if (url == null) {
                val pg = PostgreSQLContainer("postgres:18")
                pg.start()
                properties.add("spring.datasource.url", pg::getJdbcUrl)
                properties.add("spring.datasource.username", pg::getUsername)
                properties.add("spring.datasource.password", pg::getPassword)
            } else {
                properties.add("spring.datasource.url") { url }
                properties.add("spring.datasource.username") { "trace" }
                properties.add("spring.datasource.password") { "trace-local" }
            }
        }
    }

    private fun actor(role: String) =
        oidcLogin().idToken { token ->
            token
                .subject(role)
                .claim("trace_roles", listOf(role))
                .claim("name", role)
                .claim("preferred_username", role)
                .claim("email", "$role@example.org")
        }

    @Test
    fun profileSurvivesLoginAndRejectsStaleEdits() {
        val member = actor("profiletester")
        val path = "/api/me/profile"
        mvc.perform(get(path).with(member)).andExpect(jsonPath("$.shareIntro").value(false))
        fun save(name: String, version: Int) = mvc.perform(
            put(path).with(member).with(csrf())
                .contentType(APPLICATION_JSON).content(
                    json.writeValueAsString(
                        mapOf(
                            "displayName" to name, "specialty" to "Kotlin", "bio" to "Building tools",
                            "shareIntro" to true, "version" to version
                        )
                    )
                )
        )
        save("New name", 0).andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        mvc.perform(get("/api/me").with(member)).andExpect(jsonPath("$.user.name").value("New name"))
        save("Stale", 0).andExpect(status().isConflict)
        save(" ", 1).andExpect(status().isBadRequest)
        mvc.perform(get(path).with(member)).andExpect(jsonPath("$.bio").value("Building tools"))
        mvc.perform(get(path).with(actor("anotherprofile"))).andExpect(jsonPath("$.bio").value(""))
    }

    @Test
    fun accountAccessFollowsAdministrativeDecisions() {
        val admin = actor("admin")
        val member = oidcLogin().idToken {
            it.subject("newmember").claim("preferred_username", "newmember")
                .claim("email", "newmember@example.org").claim("name", "New member")
        }
        val me = json.readTree(
            mvc.perform(get("/api/me").with(member)).andExpect(status().isOk).andReturn().response.contentAsByteArray
        )
        val id = me["user"]["id"].asText()
        mvc.perform(get("/api/admin/accounts").with(member)).andExpect(status().isForbidden)
        fun change(body: String) = mvc.perform(
            patch("/api/admin/accounts/$id").with(admin).with(csrf())
                .contentType(APPLICATION_JSON).content(body)
        ).andExpect(status().isOk)
        change("""{"role":"teacher"}""")
        mvc.perform(get("/api/me").with(member)).andExpect(jsonPath("$.user.role").value("teacher"))
        change("""{"enabled":false}""")
        mvc.perform(get("/api/projects").with(member)).andExpect(status().isForbidden)
        change("""{"enabled":true}""")
        mvc.perform(get("/api/projects").with(member)).andExpect(status().isOk)
    }

    @Test
    fun projectLifecycle() {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized)
        fun create(role: String, type: String) =
            mvc.perform(
                post("/api/projects")
                    .with(actor(role))
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(json.writeValueAsString(mapOf("name" to "Курсовая", "type" to type)))
            )
        create("student", "educational").andExpect(status().isForbidden)
        create("teacher", "educational").andExpect(status().isCreated)
        val created =
            create("student", "development")
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        val path = "/api/projects/" + json.readTree(created)["id"].asText()
        fun editAs(role: String, body: String) =
            mvc.perform(
                patch(path)
                    .with(actor(role))
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(body)
            )
        editAs("student", """{"name":"Updated"}""").andExpect(status().isOk)
        editAs("teacher", """{"name":"Foreign"}""").andExpect(status().isForbidden)
        editAs("student", """{"managerId":"bad"}""").andExpect(status().isBadRequest)
        mvc.perform(get(path).with(actor("teacher"))).andExpect(jsonPath("$.name").value("Updated"))
        mvc.perform(get("/api/projects?size=1").with(actor("student")))
            .andExpect(jsonPath("$.items.length()").value(1))
        mvc.perform(
            get("/api/projects/00000000-0000-0000-0000-000000000000").with(actor("student"))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun workflowAcrossTwoTeams() {
        val manager = actor("teacher")
        val student = actor("student")
        val other =
            oidcLogin().idToken {
                it.subject("other")
                    .claim("trace_roles", listOf("student"))
                    .claim("preferred_username", "other")
                    .claim("name", "Other")
                    .claim("email", "other@example.org")
            }

        fun command(
            path: String,
            body: Any,
            who: org.springframework.test.web.servlet.request.RequestPostProcessor = manager,
            code: Int = 200,
        ): String {
            val response =
                mvc.perform(
                    post("/api/$path")
                        .with(who)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(body))
                )
                    .andExpect(status().`is`(code))
                    .andReturn()
                    .response
            return response.contentAsString.trim('"')
        }

        fun read(
            path: String,
            who: org.springframework.test.web.servlet.request.RequestPostProcessor = manager,
        ) =
            json.readTree(
                mvc.perform(get("/api/$path").with(who))
                    .andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsByteArray
            )

        val project =
            json
                .readTree(
                    command(
                        "projects",
                        mapOf("name" to "Course", "type" to "educational"),
                        code = 201,
                    )
                )["id"]
                .asText()
        val root = "projects/$project"
        val a = command("$root/teams", mapOf("name" to "A"))
        val b = command("$root/teams", mapOf("name" to "B"))
        val application = command("teams/$a/applications", emptyMap<String, String>(), student)
        command("teams/$a/applications", emptyMap<String, String>(), student, 409)
        command("applications/$application/decision", mapOf("decision" to "accepted"))
        val otherId = read("me", other)["user"]["id"].asText()
        command("teams/$b/members", mapOf("userId" to otherId))
        val template =
            command(
                "$root/templates",
                mapOf(
                    "title" to "Report",
                    "parameters" to
                            listOf(mapOf("name" to "variant", "required" to true, "type" to "integer")),
                ),
            )
        val param =
            read("$root/templates")
                .first { it["id"].asText() == template }["parameters"][0]["id"]
                .asText()

        fun assign(second: String) =
            mapOf(
                "title" to "Report",
                "instructions" to "Deliver PDF",
                "templateId" to template,
                "teams" to
                        listOf(
                            mapOf("teamId" to a, "values" to mapOf(param to "1")),
                            mapOf("teamId" to b, "values" to mapOf(param to second)),
                        ),
            )
        command("$root/tasks", assign(""), code = 400)
        org.junit.jupiter.api.Assertions.assertEquals(0, read("$root/assignments").size())
        command("$root/tasks", assign("2"))
        val first = "assignments/" + read("$root/assignments", student).single()["id"].asText()
        val second = "assignments/" + read("$root/assignments", other).single()["id"].asText()
        command("$first/notices", mapOf("title" to "Notice", "content" to "All teams"))
        fun submit(code: Int = 200) =
            mvc.perform(
                multipart("/api/$first/attempts")
                    .file("files", "example".toByteArray())
                    .param("content", "Report")
                    .with(student)
                    .with(csrf())
            )
                .andExpect(status().`is`(code))
                .andReturn()
                .response
                .contentAsString
                .trim('"')

        val attempt = submit()
        submit(409)
        val entry = read(first, student)["attempts"].single()
        val filePath = "/api/$first/files/" + entry["files"].single()["id"].asText()
        mvc.perform(get(filePath).with(student)).andExpect(content().bytes("example".toByteArray()))
        mvc.perform(get(filePath).with(other)).andExpect(status().isForbidden)
        command(
            "$first/messages",
            mapOf("content" to "Comment", "submissionId" to attempt),
            student,
        )
        command(
            "$first/attempts/$attempt/review",
            mapOf("decision" to "revision_required", "comment" to "Fix"),
        )
        val next = submit()
        command("$first/attempts/$attempt/review", mapOf("decision" to "accepted"), manager, 409)
        command("$first/attempts/$next/review", mapOf("decision" to "accepted"))
        submit(409)
        val detail = read(first, student)
        org.junit.jupiter.api.Assertions.assertEquals("completed", detail["status"].asText())
        org.junit.jupiter.api.Assertions.assertEquals(2, detail["attempts"].size())
        org.junit.jupiter.api.Assertions.assertEquals(
            4,
            read("$first/discussion")["messages"].count { it["system_event"].asBoolean() },
        )
        val isolated = read("$second/discussion", other)
        org.junit.jupiter.api.Assertions.assertEquals(1, isolated["notices"].size())
        org.junit.jupiter.api.Assertions.assertEquals(0, isolated["messages"].size())
        org.junit.jupiter.api.Assertions.assertEquals(
            "assigned",
            read(second, other)["status"].asText(),
        )
        val deferred =
            command(
                "$root/tasks",
                mapOf("templateId" to template, "title" to "Later", "instructions" to "Deliver"),
            )
        val targets =
            mapOf("teams" to listOf(mapOf("teamId" to a, "values" to mapOf(param to "7"))))
        command("$root/tasks/$deferred/assignments", targets, student, 403)
        command("$root/tasks/$deferred/assignments", targets)
        command("$root/tasks/$deferred/assignments", targets, code = 409)
        val allocated =
            read("$root/assignments", student).single { it["task_id"].asText() == deferred }
        org.junit.jupiter.api.Assertions.assertEquals(
            "7",
            read("assignments/" + allocated["id"].asText(), student)["parameters"][0]["value"]
                .asText(),
        )
    }
}
