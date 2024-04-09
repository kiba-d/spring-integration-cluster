package com.jobandtalent.spring.integration.cluster

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringIntegrationClusterApplication

fun main(args: Array<String>) {
    runApplication<SpringIntegrationClusterApplication>(*args)
}
