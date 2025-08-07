package no.nav.eessi.pensjon.fagmodul.config

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit

internal const val INSTITUTION_CACHE = "institutions"

@Configuration
@EnableCaching
@EnableScheduling
class FagmodulCacheConfig {
    private val logger = LoggerFactory.getLogger(FagmodulCacheConfig::class.java)

    @Primary
    @Bean("fagmodulCacheManager")
    fun cacheManager(): CacheManager {
        return ConcurrentMapCacheManager(INSTITUTION_CACHE)
    }

    @CacheEvict(cacheNames = [INSTITUTION_CACHE])
    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    fun reportCacheEvict() {
        logger.info("Flushing cache: $INSTITUTION_CACHE")
    }

}