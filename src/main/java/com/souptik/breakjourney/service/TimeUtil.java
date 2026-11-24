package com.souptik.breakjourney.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    public static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeUtil() {
    }

    public static OffsetDateTime ist(OffsetDateTime t) {
        return t == null ? null : t.withOffsetSameInstant(IST);
    }

    public static OffsetDateTime nowIst() {
        return OffsetDateTime.now(IST);
    }

    /** Whole minutes from a to b, rounded down (negative when b is before a). */
    public static long minutesBetween(OffsetDateTime a, OffsetDateTime b) {
        return Math.floorDiv(Duration.between(a, b).getSeconds(), 60L);
    }

    public static String hhmm(OffsetDateTime t) {
        return t == null ? "-" : ist(t).format(HHMM);
    }
}
