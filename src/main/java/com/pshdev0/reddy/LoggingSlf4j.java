package com.pshdev0.reddy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface LoggingSlf4j {

    default Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    default void info(String info) {
        getLogger().info(info);
    }

    default void warn(String warn) {
        getLogger().warn(warn);
    }

    default void error(String error) {
        getLogger().error(error);
    }
}
