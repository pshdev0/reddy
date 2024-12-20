package com.pshdev0.reddy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Logging {
    default Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    default void sout(String info) {
        getLogger().info(info);
    }
    default void serr(String error) {
        getLogger().error(error);
    }
}
