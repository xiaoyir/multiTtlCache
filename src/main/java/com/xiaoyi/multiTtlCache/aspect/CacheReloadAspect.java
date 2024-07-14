package com.xiaoyi.multiTtlCache.aspect;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.xiaoyi.multiTtlCache.annotation.CustomizedCacheable;
import com.xiaoyi.multiTtlCache.bean.CachedInvocation;
import com.xiaoyi.multiTtlCache.config.MyCaffeineCache;
import com.xiaoyi.multiTtlCache.constant.CacheConstant;
import com.xiaoyi.multiTtlCache.utils.CacheHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Aspect
@Slf4j
@Order(1)
public class CacheReloadAspect {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    private ReentrantLock lock = new ReentrantLock();

    @SneakyThrows
    @Around(value = "@annotation(com.xiaoyi.multiTtlCache.annotation.CustomizedCacheable)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint){
        //方法入参对象数组
        Object[] args = proceedingJoinPoint.getArgs();
        //方法实体
        Method method = MethodSignature.class.cast(proceedingJoinPoint.getSignature()).getMethod();
        //自定义注解
        CustomizedCacheable cacheable = method.getAnnotation(CustomizedCacheable.class);
        String cacheEnabled = cacheable.cacheEnabled();
        //根据配置判断是否开启缓存
        String property = environment.getProperty(cacheEnabled);
        if (!ObjectUtil.isEmpty(property)) {
            return proceedingJoinPoint.proceed();
        }
        //解析上下文
        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext();
        //参数名称
        String[] parameterNames = new LocalVariableTableParameterNameDiscoverer().getParameterNames(method);
        for (int i = 0; i < parameterNames.length; i++) {
            standardEvaluationContext.setVariable(parameterNames[i], args[i]);
        }
        //解析SPEL表达式的key，获取真正存入缓存中的key值
        String key = parseSPELKey(cacheable, standardEvaluationContext);
        Object result = null;
        String cacheType = cacheable.cacheType();
        switch (cacheType) {
            case CacheConstant.LOCAL_CACHE:
                result = useCaffeineCache(key, cacheable, proceedingJoinPoint);
            case CacheConstant.REDIS_CACHE:
                result = useRedisCache(key, cacheable, proceedingJoinPoint);
            case CacheConstant.BOTH_CACHE:
                result = useBothCache(key, cacheable, proceedingJoinPoint);
            default:
                result = null;
        }
        return result;

    }

    @SneakyThrows
    private Object useBothCache(String key, CustomizedCacheable cacheable, ProceedingJoinPoint proceedingJoinPoint) {
        long expiredInterval = cacheable.expiredInterval();
        MyCaffeineCache myCaffeineCache = (MyCaffeineCache) SpringUtil.getBean(MyCaffeineCache.class);
        RedisTemplate redisTemplate = (RedisTemplate) SpringUtil.getBean("cacheRedisTemplate");
        Object o = myCaffeineCache.get(key);
        if (o != null) {
            Long ttl = myCaffeineCache.getTtl(key);
            if(ObjectUtil.isNotEmpty(ttl) && ttl <= cacheable.preLoadTimeSecond()){
                log.info(">>>>>>>>>>> cacheKey：{}, ttl: {},preLoadTimeSecond: {}",key,ttl,cacheable.preLoadTimeSecond());
                ThreadUtil.execute(()->{
                    lock.lock();
                    try{
                        CachedInvocation cachedInvocation = buildCachedInvocation(proceedingJoinPoint, cacheable);
                        Object o1 = CacheHelper.exeInvocation(cachedInvocation);
                        myCaffeineCache.set(key, o1, cacheable.expiredTimeSecond());
                        redisTemplate.opsForValue().set(key, o1, cacheable.expiredTimeSecond() + expiredInterval, TimeUnit.SECONDS);
                    }catch (Exception e){
                        log.error("{}",e.getMessage(),e);
                    }finally {
                        lock.unlock();
                    }
                });
            }
            return o;
        } else {
            Object o1 = redisTemplate.opsForValue().get(key);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if(o1 != null){
                myCaffeineCache.set(key, o1, ttl);
                return o1;
            }
        }
        Object result = proceedingJoinPoint.proceed();
        myCaffeineCache.set(key, result, cacheable.expiredTimeSecond());
        redisTemplate.opsForValue().set(key, result, cacheable.expiredTimeSecond() + expiredInterval, TimeUnit.SECONDS);
        return result;
    }

    @SneakyThrows
    private Object useRedisCache(String key, CustomizedCacheable cacheable, ProceedingJoinPoint proceedingJoinPoint) {
        RedisTemplate redisTemplate = (RedisTemplate) SpringUtil.getBean("cacheRedisTemplate");
        Object o = redisTemplate.opsForValue().get(key);
        if (o != null) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if(ObjectUtil.isNotEmpty(ttl) && ttl <= cacheable.preLoadTimeSecond()){
                log.info(">>>>>>>>>>> cacheKey：{}, ttl: {},preLoadTimeSecond: {}", key, ttl, cacheable.preLoadTimeSecond());
                ThreadUtil.execute(()->{
                    lock.lock();
                    try{
                        CachedInvocation cachedInvocation = buildCachedInvocation(proceedingJoinPoint, cacheable);
                        Object o1 = CacheHelper.exeInvocation(cachedInvocation);
                        redisTemplate.opsForValue().set(key, o1, cacheable.expiredTimeSecond(), TimeUnit.SECONDS);
                    }catch (Exception e){
                        log.error("{}",e.getMessage(),e);
                    }finally {
                        lock.unlock();
                    }
                });
            }
            return o;
        }
        Object result = proceedingJoinPoint.proceed();
        redisTemplate.opsForValue().set(key, result, cacheable.expiredTimeSecond(), TimeUnit.SECONDS);
        return result;
    }

    @SneakyThrows
    private Object useCaffeineCache(String key, CustomizedCacheable cacheable, ProceedingJoinPoint proceedingJoinPoint) {
        MyCaffeineCache myCaffeineCache = (MyCaffeineCache) SpringUtil.getBean(MyCaffeineCache.class);
        Object o = myCaffeineCache.get(key);
        if (o != null) {
            Long ttl = myCaffeineCache.getTtl(key);
            if(ObjectUtil.isNotEmpty(ttl) && ttl <= cacheable.preLoadTimeSecond()){
                log.info(">>>>>>>>>>> cacheKey：{}, ttl: {},preLoadTimeSecond: {}",key,ttl,cacheable.preLoadTimeSecond());
                ThreadUtil.execute(()->{
                    lock.lock();
                    try{
                        CachedInvocation cachedInvocation = buildCachedInvocation(proceedingJoinPoint, cacheable);
                        Object o1 = CacheHelper.exeInvocation(cachedInvocation);
                        myCaffeineCache.set(key, o1, cacheable.expiredTimeSecond());
                    }catch (Exception e){
                        log.error("{}",e.getMessage(),e);
                    }finally {
                        lock.unlock();
                    }
                });
            }
            return o;
        }
        Object result = proceedingJoinPoint.proceed();
        myCaffeineCache.set(key, result, cacheable.expiredTimeSecond());
        return result;
    }


    private CachedInvocation buildCachedInvocation(ProceedingJoinPoint proceedingJoinPoint,CustomizedCacheable customizedCacheable){
        Method method = this.getSpecificmethod(proceedingJoinPoint);
        String[] cacheNames = customizedCacheable.cacheNames();
        Object targetBean = proceedingJoinPoint.getTarget();
        Object[] arguments = proceedingJoinPoint.getArgs();
        Object key = customizedCacheable.key();
        CachedInvocation cachedInvocation = CachedInvocation.builder()
                .arguments(arguments)
                .targetBean(targetBean)
                .targetMethod(method)
                .cacheNames(cacheNames)
                .key(key)
                .expiredTimeSecond(customizedCacheable.expiredTimeSecond())
                .preLoadTimeSecond(customizedCacheable.preLoadTimeSecond())
                .build();
        return cachedInvocation;
    }

    private Method getSpecificmethod(ProceedingJoinPoint pjp) {
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method method = methodSignature.getMethod();
        // The method may be on an interface, but we need attributes from the
        // target class. If the target class is null, the method will be
        // unchanged.
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(pjp.getTarget());
        if (targetClass == null && pjp.getTarget() != null) {
            targetClass = pjp.getTarget().getClass();
        }
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
        // If we are dealing with method with generic parameters, find the
        // original method.
        specificMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);
        return specificMethod;
    }

    private String parseSPELKey(CustomizedCacheable cacheable, StandardEvaluationContext context) {
        String keySpel = cacheable.key();
        Expression expression = new SpelExpressionParser().parseExpression(keySpel);
        String key = expression.getValue(context, String.class);
        return key;
    }

}
