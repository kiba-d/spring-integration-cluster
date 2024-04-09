package com.jobandtalent.spring.integration.cluster

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.TaskExecutor
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.integration.channel.QueueChannel
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.core.MessageSource
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.Pollers
import org.springframework.integration.handler.LoggingHandler
import org.springframework.integration.handler.LoggingHandler.Level.INFO
import org.springframework.integration.redis.store.RedisMessageStore
import org.springframework.integration.store.MessageGroupQueue
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import java.io.Serializable
import java.util.UUID

@Configuration
@EnableIntegration
class IntegrationFlowConfig {
    @Bean
    fun integrationTaskExecutor(): TaskExecutor {
        val taskExecutor = ThreadPoolTaskExecutor()
        taskExecutor.corePoolSize = 4 // Set the number of concurrent consumers
        taskExecutor.setThreadNamePrefix("wrk-consumer-")
        taskExecutor.initialize()
        return taskExecutor
    }

    @Bean
    fun redisMessageStore(redisConnectionFactory: RedisConnectionFactory): RedisMessageStore {
        return RedisMessageStore(redisConnectionFactory)
    }

    @Bean
    fun sourceChannel(messageGroupQueue: MessageGroupQueue) =
        QueueChannel(
            messageGroupQueue,
        )

    @Bean
    fun messageGroupQueue(redisMessageStore: RedisMessageStore) = MessageGroupQueue(redisMessageStore, "WORKERS_GROUP")

    @Bean
    @Profile("consumer:single-thread")
    fun singleThreadIntegrationFlow(sourceChannel: MessageChannel): IntegrationFlow {
        return IntegrationFlow.from(sourceChannel)
            .handle(LoggingHandler(INFO))
            .get()
    }

    @Bean
    @Profile("consumer")
    fun parallelIntegrationFlow(
        messageGroupQueue: MessageGroupQueue,
        integrationTaskExecutor: TaskExecutor,
    ): IntegrationFlow {
        val messageSource = MessageSource { messageGroupQueue.poll() }
        return IntegrationFlow.from(messageSource) { e ->
            e.poller(
                Pollers.fixedDelay(500).taskExecutor(integrationTaskExecutor),
            )
        }
            .handle(LoggingHandler(INFO))
            .get()
    }
}

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
class SchedulingConfig {
    @Bean
    fun lockProvider(
        connectionFactory: RedisConnectionFactory,
        @Value("\${spring.profiles.active}") lockNamespace: String,
    ): LockProvider {
        return RedisLockProvider(connectionFactory, lockNamespace)
    }
}

@Service
@Profile("producer")
class MessageProduce(
    private val sourceChannel: MessageChannel,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 100)
    @SchedulerLock(
        name = "send_worker_message",
        lockAtMostFor = "1m",
    )
    fun sendMessage() {
        logger.info("Sending message")
        val worker = Worker() // replace with your complex object
        val headers =
            mapOf(
                "action" to "CREATED",
                "createdAt" to System.currentTimeMillis(),
            )

        val message = MessageBuilder.withPayload(worker).copyHeaders(headers).build()

        sourceChannel.send(message)
    }
}

data class Worker(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "John Doe",
    val age: Int = 22,
    val married: Boolean = true,
    val address: Address = Address(),
    val createdAt: Long = System.currentTimeMillis(),
) : Serializable

data class Address(
    val street: String = "Main Street",
    val number: Int = 123,
    val city: String = "Springfield",
    val country: String = "USA",
) : Serializable
