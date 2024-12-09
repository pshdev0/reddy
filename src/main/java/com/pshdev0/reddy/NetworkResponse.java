package com.pshdev0.reddy;

public class NetworkResponse<T> {
    private T value;
    private boolean viaRedis;

    public NetworkResponse() {
    }

    public NetworkResponse(T returnValue, boolean viaRedis) {
        this.value = returnValue;
        this.viaRedis = viaRedis;
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
}
