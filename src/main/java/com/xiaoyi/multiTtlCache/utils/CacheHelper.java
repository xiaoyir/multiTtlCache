package com.xiaoyi.multiTtlCache.utils;


import com.xiaoyi.multiTtlCache.bean.CachedInvocation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CacheHelper {



    public static Object exeInvocation(CachedInvocation cachedInvocation) {
        boolean invocationSuccess;
        Object computed = null;
        try {
            computed = cachedInvocation.invoke();
            invocationSuccess = true;
        } catch (Exception ex) {
            invocationSuccess = false;
            log.error(">>>>>>>>>>>>>>>>> refresh redis fail",ex.getMessage(),ex);
        }

        if (invocationSuccess) {
            return computed;
        }
        return null;
    }


}
