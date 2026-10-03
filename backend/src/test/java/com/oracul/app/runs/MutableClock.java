package com.oracul.app.runs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock (UTC) that only moves when told to: starts at {@code Instant.now()}, {@link #advance}, {@link #set}. */
public class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock() {
        this.now = Instant.now();
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
