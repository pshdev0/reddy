package com.pshdev0.reddy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public interface RateLimitedProcessorData<T> {
    AtomicReference<RateLimitedProcessor.Action> action = new AtomicReference<>();
    AtomicInteger index = new AtomicInteger(0);
    AtomicInteger size = new AtomicInteger(0);

    default void setSize(int size) { this.size.set(size); }
    default int getSize() { return size.get(); }

    default void incrementIndex() { this.index.incrementAndGet(); }
    default void setIndex(int index) { this.index.set(index); }
    default int getIndex() { return index.get(); }

    T get();
}
