package com.scripty.api;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps are stored as zone-less {@link LocalDateTime} in server-local time.
 * API responses must carry an explicit UTC offset and whole seconds: Swift's
 * default ISO-8601 date decoding rejects both a missing offset and fractional
 * seconds.
 */
final class ApiDates {

    private ApiDates() {
    }

    static OffsetDateTime toOffset(LocalDateTime local) {
        return local == null
                ? null
                : local.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                        .truncatedTo(ChronoUnit.SECONDS);
    }
}
