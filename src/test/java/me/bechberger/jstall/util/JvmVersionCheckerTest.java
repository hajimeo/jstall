package me.bechberger.jstall.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class JvmVersionCheckerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-09T10:00:00Z"), ZoneOffset.UTC);

    // --- parseVersionDate ---

    @Test
    void parseVersionDate_validDate() {
        assertEquals(LocalDate.of(2025, 7, 15), JvmVersionChecker.parseVersionDate("2025-07-15"));
    }

    @Test
    void parseVersionDate_nullReturnsNull() {
        assertNull(JvmVersionChecker.parseVersionDate(null));
    }

    @Test
    void parseVersionDate_blankReturnsNull() {
        assertNull(JvmVersionChecker.parseVersionDate("  "));
    }

    @Test
    void parseVersionDate_garbageReturnsNull() {
        assertNull(JvmVersionChecker.parseVersionDate("not-a-date"));
    }

    @Test
    void parseVersionDate_trimmed() {
        assertEquals(LocalDate.of(2025, 7, 15), JvmVersionChecker.parseVersionDate("  2025-07-15  "));
    }

    // --- isCurrentRelease ---

    @Test
    void isCurrentRelease_withinFourMonths() {
        // 2026-03-09 minus 4 months = 2025-11-09
        assertTrue(JvmVersionChecker.isCurrentRelease("2025-12-01", FIXED_CLOCK));
    }

    @Test
    void isCurrentRelease_exactlyFourMonthsAgo() {
        // Boundary: 2025-11-09 is NOT before 2025-11-09 -> still current
        assertTrue(JvmVersionChecker.isCurrentRelease("2025-11-09", FIXED_CLOCK));
    }

    @Test
    void isCurrentRelease_olderThanFourMonths() {
        assertFalse(JvmVersionChecker.isCurrentRelease("2025-08-01", FIXED_CLOCK));
    }

    @Test
    void isCurrentRelease_nullReturnsFalse() {
        assertFalse(JvmVersionChecker.isCurrentRelease(null, FIXED_CLOCK));
    }

    // --- isOutdated ---

    @Test
    void isOutdated_withinOneYear() {
        assertFalse(JvmVersionChecker.isOutdated("2025-08-01", FIXED_CLOCK));
    }

    @Test
    void isOutdated_olderThanOneYear() {
        assertTrue(JvmVersionChecker.isOutdated("2024-01-01", FIXED_CLOCK));
    }

    @Test
    void isOutdated_exactlyOneYearAgo() {
        // Boundary: 2025-03-09 is NOT before 2025-03-09 -> not outdated
        assertFalse(JvmVersionChecker.isOutdated("2025-03-09", FIXED_CLOCK));
    }

    @Test
    void isOutdated_nullReturnsFalse() {
        assertFalse(JvmVersionChecker.isOutdated(null, FIXED_CLOCK));
    }

    // --- prettyAge ---

    @Test
    void prettyAge_fewDays() {
        assertEquals("5d old",
                JvmVersionChecker.prettyAge(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 9)));
    }

    @Test
    void prettyAge_monthsAndDays() {
        assertEquals("6mo 8d old",
                JvmVersionChecker.prettyAge(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 3, 9)));
    }

    @Test
    void prettyAge_years() {
        assertEquals("2y 2mo 8d old",
                JvmVersionChecker.prettyAge(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 3, 9)));
    }

    @Test
    void prettyAge_futureDate() {
        assertEquals("0d old",
                JvmVersionChecker.prettyAge(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 9)));
    }

    @Test
    void prettyAge_sameDate() {
        assertEquals("0d old",
                JvmVersionChecker.prettyAge(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9)));
    }
}
