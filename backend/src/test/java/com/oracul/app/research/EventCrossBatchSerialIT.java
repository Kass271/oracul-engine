package com.oracul.app.research;

import org.springframework.test.context.TestPropertySource;

/** Rows 6, 7, 27 with {@code oracul.events.normalization-concurrency=1}: every test of {@link EventCrossBatchIT} must hold sequentially, too. */
// @trace FR-14
@TestPropertySource(properties = "oracul.events.normalization-concurrency=1")
class EventCrossBatchSerialIT extends EventCrossBatchIT {
}
