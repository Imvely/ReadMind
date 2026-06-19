package com.readmind

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ReadmindApplication

fun main(args: Array<String>) {
    runApplication<ReadmindApplication>(*args)
}
