package com.pshdev0.reddy;

public class NetworkResponse<T> {
    private T value;
    private boolean viaRedis;
    private String redisKey;

    public NetworkResponse() {
    }

    public NetworkResponse(T returnValue, boolean viaRedis) {
        this(returnValue, viaRedis, "");
    }

    public NetworkResponse(T returnValue, boolean viaRedis, String redisKey) {
        this.value = returnValue;
        this.viaRedis = viaRedis;
        this.redisKey = redisKey;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T returnValue) {
        this.value = returnValue;
    }

    public boolean isViaRedis() {
        return viaRedis;
    }

    public void setViaRedis(boolean viaRedis) {
        this.viaRedis = viaRedis;
    }

    public RateLimitedProcessor.Action getRlpHint() {
        return viaRedis ? RateLimitedProcessor.Action.SKIP_DELAY : RateLimitedProcessor.Action.WAIT_AND_CONTINUE;
    }

    public String getRedisKey() {
        return redisKey;
    }
}
