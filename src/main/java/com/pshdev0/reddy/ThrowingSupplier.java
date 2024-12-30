package com.pshdev0.reddy;

@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws Exception;
}
