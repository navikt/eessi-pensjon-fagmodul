package no.nav.eessi.pensjon.api.geo

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.util.concurrent.Callable

/**
 * En [Cache]-dekorator som logger cache-treff/-bom og innsettinger, slik at man i loggene kan
 * se om f.eks. land- og valutakodekallene faktisk blir cachet, og ikke slår mot Rina hver gang.
 */
class LoggingCache(private val delegate: Cache) : Cache {

    private val logger = LoggerFactory.getLogger(LoggingCache::class.java)

    override fun getName(): String = delegate.name

    override fun getNativeCache(): Any = delegate.nativeCache

    override fun get(key: Any): Cache.ValueWrapper? {
        val value = delegate.get(key)
        if (value != null) {
            logger.debug("Cache HIT: cache='${delegate.name}' key='$key'")
        } else {
            logger.debug("Cache MISS: cache='${delegate.name}' key='$key'")
        }
        return value
    }

    override fun <T : Any> get(key: Any, type: Class<T>?): T? {
        val value = delegate.get(key, type)
        logger.debug("Cache ${if (value != null) "HIT" else "MISS"}: cache='${delegate.name}' key='$key'")
        return value
    }

    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
        var wasCached = true
        val value = delegate.get(key) {
            wasCached = false
            valueLoader.call()
        }
        logger.debug("Cache ${if (wasCached) "HIT" else "MISS"}: cache='${delegate.name}' key='$key'")
        return value
    }

    override fun put(key: Any, value: Any?) {
        logger.debug("Cache PUT: cache='${delegate.name}' key='$key'")
        delegate.put(key, value)
    }

    override fun evict(key: Any) {
        logger.debug("Cache EVICT: cache='${delegate.name}' key='$key'")
        delegate.evict(key)
    }

    override fun clear() {
        logger.info("Cache CLEAR: cache='${delegate.name}'")
        delegate.clear()
    }
}

/**
 * [CacheManager] som pakker inn cachene fra [delegate] i [LoggingCache] for synlig hit/miss-logging.
 */
class LoggingCacheManager(private val delegate: CacheManager) : CacheManager {

    override fun getCache(name: String): Cache? {
        val cache = delegate.getCache(name) ?: return null
        return LoggingCache(cache)
    }

    override fun getCacheNames(): Collection<String> = delegate.cacheNames
}
