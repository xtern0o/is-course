package ru.itmo.trace.projects

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.itmo.trace.identity.Actor
import java.util.UUID

@RestController
@RequestMapping("/api/projects")
class ProjectController(private val service: ProjectService) {
    @GetMapping
    fun catalog(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) =
        service.catalog(page, size)

    @GetMapping("/{id}")
    fun card(@PathVariable id: UUID) = service.card(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(actor: Actor, @Valid @RequestBody data: NewProject) =
        service.create(actor, data)

    @PatchMapping("/{id}")
    fun edit(
        actor: Actor, @PathVariable id: UUID,
        @RequestBody changes: Map<String, String?>
    ) = service.edit(id, actor, changes)
}
