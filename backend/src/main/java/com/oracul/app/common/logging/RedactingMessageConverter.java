package com.oracul.app.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class RedactingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretRedactor.redact(event.getFormattedMessage());
    }
}
