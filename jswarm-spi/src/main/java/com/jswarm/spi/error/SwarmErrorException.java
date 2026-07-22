// 携带 SwarmError 的运行时异常
package com.jswarm.spi.error;

import com.jswarm.core.SwarmException;

public final class SwarmErrorException extends SwarmException {

    private final SwarmError error;

    public SwarmErrorException(SwarmError error) {
        super(error.publicMessage(), error.cause());
        this.error = error;
    }

    public SwarmError error() {
        return error;
    }

    public SwarmErrorCode code() {
        return error.code();
    }

    public boolean retryable() {
        return error.retryable();
    }
}
