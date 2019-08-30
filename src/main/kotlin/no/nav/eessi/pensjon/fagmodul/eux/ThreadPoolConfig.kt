package no.nav.eessi.pensjon.fagmodul.eux

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.ThreadPoolExecutor
import org.springframework.web.context.request.RequestContextListener
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean



@Configuration
@EnableAsync
class ThreadPoolConfig {

    @Bean("threadPoolTaskExecutor")
    fun getAsyncExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.maxPoolSize = 100
        executor.corePoolSize = 25
        executor.keepAliveSeconds = 160
        executor.setQueueCapacity(500)
        executor.setThreadNamePrefix("Async-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

}