package ru.itmo.trace

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TraceApplication

fun main(args: Array<String>) {
    runApplication<TraceApplication>(*args)
}
