package com.xiaoyi.multiTtlCache.bean;

import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
public class CacheObject<T> {
    T data;
    long expire;
    long createDate;//创建时间
    public CacheObject(T data, long second, long createDate) {
        this.data = data;
        this.expire = TimeUnit.SECONDS.toNanos(second);
        this.createDate = createDate;
    }
}
