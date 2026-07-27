package no.nav.eessi.pensjon.api.geo

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit

internal const val LANDKODE_CACHE = "landkoder"
internal const val LAND_OG_VALUTAKODE_CACHE = "landOgValutakoder"

/**
 * Land- og valutakoder hentes fra Rina og endrer seg svært sjelden, så de caches
 * i minnet for å unngå unødvendige/trege kall for hvert oppslag.
 */
@Configuration
@EnableCaching
@EnableScheduling
class GeoCacheConfig {
    private val logger = LoggerFactory.getLogger(GeoCacheConfig::class.java)

    @Bean("geoCacheManager")
    fun cacheManager(): CacheManager {
        return LoggingCacheManager(ConcurrentMapCacheManager(LANDKODE_CACHE, LAND_OG_VALUTAKODE_CACHE))
    }

    @CacheEvict(cacheNames = [LANDKODE_CACHE, LAND_OG_VALUTAKODE_CACHE], cacheManager = "geoCacheManager", allEntries = true)
    @Scheduled(fixedDelay = 7, timeUnit = TimeUnit.DAYS)
    fun reportCacheEvict() {
        logger.info("Flushing cache: $LANDKODE_CACHE, $LAND_OG_VALUTAKODE_CACHE")
    }

}
