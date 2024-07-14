package com.xiaoyi.multiTtlCache.annotation;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface CustomizedCacheable {


    String[] value() default {};


    String[] cacheNames() default {};


    String key() default "";


    String keyGenerator() default "";


    String cacheResolver() default "";


    String condition() default "";


    String unless() default "";


    boolean sync() default false;

    //String cacheManager() default "redisCacheManager";

    /**
     * 过期时间
     * @return
     */
    long expiredTimeSecond() default 0;

    /**
     * 预刷新时间
     * @return
     */
    long preLoadTimeSecond() default 0;

    /**
     * 缓存级别，1-本地缓存，2-redis缓存，3-本地+redis
     * @return
     */
    String cacheType() default "1";

    /**
     * 一二级缓存之间的缓存过期时间差
     * @return
     */
    long expiredInterval() default 0;

    /**
     * 是否开启缓存
     * @return
     */
    String cacheEnabled() default "1";

    long test() default 1;
}
