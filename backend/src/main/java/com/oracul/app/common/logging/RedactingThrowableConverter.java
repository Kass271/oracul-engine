package com.oracul.app.common.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class RedactingThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretRedactor.redact(super.convert(event));
    }
}
