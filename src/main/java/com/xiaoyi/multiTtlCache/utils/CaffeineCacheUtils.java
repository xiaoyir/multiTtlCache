package com.xiaoyi.multiTtlCache.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CaffeineCacheUtils {
    public static final long NOT_EXPIRED = Long.MAX_VALUE;
    private static class CacheObject<T> {
        T data;
        long expire;
        long createDate;
        public CacheObject(T data, long second, long createDate) {
            this.data = data;
            this.expire = TimeUnit.SECONDS.toNanos(second);
            this.createDate = createDate;
        }
    }

    public static class CaffeineExpiry implements Expiry<String, Object> {
        @Override
        public long expireAfterCreate(@NonNull String key, @NonNull Object value, long currentTime) {
            return 0;
        }

        @Override
        public long expireAfterUpdate(@NonNull String key, @NonNull Object value, long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(@NonNull String key, @NonNull Object value, long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }
    }

    public static final Cache<String, Object> caffeineNew = Caffeine.newBuilder()
            .expireAfter(new CaffeineExpiry())
            .initialCapacity(100)
                .maximumSize(1000)
                .build();


    public static final Cache<String, CacheObject<?>> CAFFEINE = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, CacheObject<?>>() {
                @Override
                public long expireAfterCreate(@NonNull String s, @NonNull CacheObject<?> cacheObject, long l) {
                    return cacheObject.expire;
                }

                @Override
                public long expireAfterUpdate(@NonNull String s, @NonNull CacheObject<?> cacheObject, long l, @NonNegative long l1) {
                    return l1;
                }

                @Override
                public long expireAfterRead(@NonNull String s, @NonNull CacheObject<?> cacheObject, long l, @NonNegative long l1) {
                    return l1;
                }
            })
            .initialCapacity(100)
            .maximumSize(1024)
            .build();

    public static <T> void set(String key, T value, long expire) {
        CacheObject<T> tCacheObject = new CacheObject<>(value, expire, System.currentTimeMillis());
        CAFFEINE.put(key, tCacheObject);
    }

    public static void set(String key, Object value) {
        set(key, value, NOT_EXPIRED);
    }

    public static <T> T get(String key) {
        CacheObject<?> ifPresent = CAFFEINE.getIfPresent(key);
        if (Objects.isNull(ifPresent)) {
            return null;
        }
        return (T) ifPresent.data;
    }

    public static void delete(String key) {
        CAFFEINE.invalidate(key);
    }

    public static void cdelete(Collection<String> keys) {
        CAFFEINE.invalidateAll(keys);
    }

    /**
     * 单位秒
     * @param key
     * @return
     */
    public static Long getTtl(String key) {
        CacheObject o = (CacheObject)CAFFEINE.getIfPresent(key);
        if (Objects.isNull(o)) {
            return null;
        }
        Long flat = ((o.createDate + o.expire/1000000) - System.currentTimeMillis())/1000;
        return flat;
    }


}
