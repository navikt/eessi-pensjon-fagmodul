package no.nav.eessi.pensjon.fagmodul.eux

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
@ActiveProfiles("test")
class ThreadPoolConfig {

    @Bean("threadPoolTaskExecutor")
    fun getAsyncExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 2
        executor.setQueueCapacity(1)
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setThreadNamePrefix("AsyncTest-")
        executor.initialize()
        return executor
    }

}