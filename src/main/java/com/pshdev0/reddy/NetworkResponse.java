package com.pshdev0.reddy;

import org.apache.commons.lang3.StringUtils;

public class NetworkResponse<T> {
    private T value;
    private String redisKey;

    public NetworkResponse() {
    }

    public NetworkResponse(T returnValue) {
        this(returnValue, "");
    }

    public NetworkResponse(T returnValue, String redisKey) {
        this.value = returnValue;
        this.redisKey = redisKey;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T returnValue) {
        this.value = returnValue;
    }


    public RateLimitedProcessor.Action getRlpHint() {
        return StringUtils.isBlank(redisKey) ? RateLimitedProcessor.Action.WAIT_AND_CONTINUE : RateLimitedProcessor.Action.SKIP_DELAY;
    }

    public String getRedisKey() {
        return redisKey;
    }
}
