package io.github.reactivecircus.cache4k

import kotlin.time.Duration
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/**
 * An in-memory key-value cache with support for time-based (expiration) and size-based evictions.
 */
public interface Cache<in Key : Any, Value : Any> {

    /**
     * Returns the value associated with [key] in this cache, or null if there is no
     * cached value for [key].
     */
    public fun get(key: Key): Value?

    /**
     * Returns the value associated with [key] in this cache if exists,
     * otherwise gets the value by invoking [loader], associates the value with [key] in the cache,
     * and returns the cached value.
     *
     * Note that [loader] is executed on the caller's thread. When called from multiple threads
     * concurrently, if an unexpired value for the [key] is present by the time the [loader] returns
     * the new value, the existing value won't be replaced by the new value.
     * Instead the existing value will be returned.
     *
     * Any exceptions thrown by the [loader] will be propagated to the caller of this function.
     */
    public fun get(key: Key, loader: suspend () -> Value): Value

    /**
     * Associates [value] with [key] in this cache. If the cache previously contained a
     * value associated with [key], the old value is replaced by [value].
     */
    public fun put(key: Key, value: Value)

    /**
     * Discards any cached value for key [key].
     */
    public fun invalidate(key: Key)

    /**
     * Discards all entries in the cache.
     */
    public fun invalidateAll()

    /**
     * Returns a defensive copy of cache entries as [Map].
     */
    public fun asMap(): Map<in Key, Value>

    /**
     * Main entry point for creating a [Cache].
     */
    public interface Builder {

        /**
         * Specifies that each entry should be automatically removed from the cache once a fixed duration
         * has elapsed after the entry's creation or the most recent replacement of its value.
         *
         * When [duration] is zero, the cache's max size will be set to 0
         * meaning no values will be cached.
         */
        public fun expireAfterWrite(duration: Duration): Builder

        /**
         * Specifies that each entry should be automatically removed from the cache once a fixed duration
         * has elapsed after the entry's creation, the most recent replacement of its value, or its last
         * access.
         *
         * When [duration] is zero, the cache's max size will be set to 0
         * meaning no values will be cached.
         */
        public fun expireAfterAccess(duration: Duration): Builder

        /**
         * Specifies the maximum number of entries the cache may contain.
         * Cache eviction policy is based on LRU - i.e. least recently accessed entries get evicted first.
         *
         * When [size] is 0, entries will be discarded immediately and no values will be cached.
         *
         * If not set, cache size will be unlimited.
         */
        public fun maximumCacheSize(size: Long): Builder

        /**
         * Specifies [TimeSource] for this cache.
         *
         * This is useful for programmatically updating the reading of a [TimeSource] in tests
         * by specifying [TestTimeSource] as the time source.
         *
         * A [TimeSource.Monotonic] will be used if not specified.
         */
        public fun timeSource(timeSource: TimeSource): Builder

        /**
         * Builds a new instance of [Cache] with the specified configurations.
         */
        public fun <K : Any, V : Any> build(): Cache<K, V>

        public companion object {

            /**
             * Returns a new [Cache.Builder] instance.
             */
            public fun newBuilder(): Builder = CacheBuilderImpl()
        }
    }
}

/**
 * A default implementation of [Cache.Builder].
 */
internal class CacheBuilderImpl : Cache.Builder {

    private var expireAfterWriteDuration = Duration.INFINITE

    private var expireAfterAccessDuration = Duration.INFINITE
    private var maxSize = UNSET_LONG
    private var timeSource: TimeSource? = null

    override fun expireAfterWrite(duration: Duration): CacheBuilderImpl = apply {
        require(duration.isPositive()) {
            "expireAfterWrite duration must be positive"
        }
        this.expireAfterWriteDuration = duration
    }

    override fun expireAfterAccess(duration: Duration): CacheBuilderImpl = apply {
        require(duration.isPositive()) {
            "expireAfterAccess duration must be positive"
        }
        this.expireAfterAccessDuration = duration
    }

    override fun maximumCacheSize(size: Long): CacheBuilderImpl = apply {
        require(size >= 0) {
            "maximum size must not be negative"
        }
        this.maxSize = size
    }

    override fun timeSource(timeSource: TimeSource): CacheBuilderImpl = apply {
        this.timeSource = timeSource
    }

    override fun <K : Any, V : Any> build(): Cache<K, V> {
        return RealCache(
            expireAfterWriteDuration,
            expireAfterAccessDuration,
            maxSize,
            timeSource ?: TimeSource.Monotonic
        )
    }

    companion object {
        internal const val UNSET_LONG: Long = -1
    }
}
