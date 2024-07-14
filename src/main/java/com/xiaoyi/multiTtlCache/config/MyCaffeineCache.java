package com.xiaoyi.multiTtlCache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.xiaoyi.multiTtlCache.bean.CacheObject;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Objects;

//@Component
public class MyCaffeineCache {

    private Cache<String, CacheObject> CAFFEINE;

    public static final long NOT_EXPIRED = Long.MAX_VALUE;

    //@PostConstruct
    public void init() {
        CAFFEINE = Caffeine.newBuilder()
                .expireAfter(new Expiry<String, CacheObject<?>>() {
                    @Override
                    public long expireAfterCreate(@NonNull String s, @NonNull CacheObject<?> cacheObject, long l) {
                        return cacheObject.getExpire();
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
    }

    public <T> void set(String key, T value, long expire) {
        CacheObject<T> tCacheObject = new CacheObject<>(value, expire, System.currentTimeMillis());
        CAFFEINE.put(key, tCacheObject);
    }

    public <T> T get(String key) {
        CacheObject<?> ifPresent = CAFFEINE.getIfPresent(key);
        if (Objects.isNull(ifPresent)) {
            return null;
        }
        return (T) ifPresent.getData();
    }

    public void delete(String key) {
        CAFFEINE.invalidate(key);
    }

    /**
     * 获取key的剩余过期时间，单位秒
     * @param key
     * @return
     */
    public Long getTtl(String key) {
        CacheObject o = (CacheObject)CAFFEINE.getIfPresent(key);
        if (Objects.isNull(o)) {
            return null;
        }
        Long flat = ((o.getCreateDate() + o.getExpire()/1000000) - System.currentTimeMillis())/1000;
        return flat;
    }

}
