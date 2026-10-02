package com.oracul.app.chatgpt;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

/** Holder of a credential value: prints as [REDACTED] and can never be serialised into a response. */
@JsonSerialize(using = Secret.Refusing.class)
public final class Secret {

    private final String value;

    public Secret(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }

    static final class Refusing extends ValueSerializer<Secret> {
        @Override
        public void serialize(Secret secret, JsonGenerator gen, SerializationContext ctx) {
            throw DatabindException.from(gen, "a Secret must never be serialised");
        }
    }
}
