package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ApiDatesTest {

    @Test
    void attachesSystemZoneOffsetWithoutShiftingWallClockTime() {
        LocalDateTime local = LocalDateTime.of(2026, 7, 13, 10, 30, 0);

        OffsetDateTime offset = ApiDates.toOffset(local);

        assertEquals(local, offset.toLocalDateTime());
        assertEquals(ZoneId.systemDefault().getRules().getOffset(local), offset.getOffset());
    }

    @Test
    void truncatesFractionalSecondsForStrictIso8601Parsers() {
        LocalDateTime local = LocalDateTime.of(2026, 7, 13, 10, 30, 0, 760_611_000);

        assertEquals(0, ApiDates.toOffset(local).getNano());
    }

    @Test
    void nullStaysNull() {
        assertNull(ApiDates.toOffset(null));
    }
}
