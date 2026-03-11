package me.bechberger.jstall.util;

public class JsonValueUtils {

    private JsonValueUtils() {
    }

    public static String asString(Object value) {
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalStateException("Expected JSON string but got " +
            (value == null ? "null" : value.getClass().getSimpleName()));
    }

    public static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Expected JSON number but got " +
            (value == null ? "null" : value.getClass().getSimpleName()));
    }
}