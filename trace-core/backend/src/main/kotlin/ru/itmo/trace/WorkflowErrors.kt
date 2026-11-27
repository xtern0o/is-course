package ru.itmo.trace

import java.sql.SQLException
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestControllerAdvice
class WorkflowErrors {
    @ExceptionHandler(DataAccessException::class)
    fun database(error: DataAccessException): ResponseEntity<Map<String, String>> {
        val state = (error.mostSpecificCause as? SQLException)?.sqlState.orEmpty()
        val status =
            when {
                state == "P0001" || state == "23505" -> 409
                state.startsWith("22") || state.startsWith("23") -> 400
                else -> throw error
            }
        val message =
            if (status == 409) "Операция недоступна в текущем состоянии или запись уже существует"
            else "Проверьте значения полей и принадлежность объектов"
        return ResponseEntity.status(status).body(mapOf("message" to message))
    }
}
