package com.b1progame.adminmod.util;

public final class DurationParser {
    private DurationParser() {
    }

    public static long parseToMillis(String text) {
        ParseResult result = parse(text);
        return result.valid() ? result.millis() : -1L;
    }

    public static ParseResult parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParseResult(false, -1L, "Duration is empty.");
        }
        String raw = text.trim().toLowerCase();
        raw = raw
                .replace("seconds", "s")
                .replace("second", "s")
                .replace("secs", "s")
                .replace("sec", "s")
                .replace("minutes", "m")
                .replace("minute", "m")
                .replace("mins", "m")
                .replace("min", "m")
                .replace("hours", "h")
                .replace("hour", "h")
                .replace("hrs", "h")
                .replace("hr", "h")
                .replace("days", "d")
                .replace("day", "d")
                .replaceAll("\\s+", "");
        long total = 0L;
        int index = 0;
        while (index < raw.length()) {
            int start = index;
            while (index < raw.length() && Character.isDigit(raw.charAt(index))) {
                index++;
            }
            if (start == index) {
                return new ParseResult(false, -1L, "Invalid duration format.");
            }
            if (index >= raw.length()) {
                return new ParseResult(false, -1L, "Missing unit in duration.");
            }
            long value;
            try {
                value = Long.parseLong(raw.substring(start, index));
            } catch (NumberFormatException exception) {
                return new ParseResult(false, -1L, "Invalid number in duration.");
            }
            if (value <= 0L) {
                return new ParseResult(false, -1L, "Duration values must be positive.");
            }
            char unit = raw.charAt(index++);
            long multiplier = switch (unit) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> -1L;
            };
            if (multiplier < 0L) {
                return new ParseResult(false, -1L, "Unsupported unit '" + unit + "'. Use s/m/h/d.");
            }
            long add = value * multiplier;
            if (Long.MAX_VALUE - total < add) {
                return new ParseResult(false, -1L, "Duration is too large.");
            }
            total += add;
        }
        if (total <= 0L) {
            return new ParseResult(false, -1L, "Duration must be greater than zero.");
        }
        return new ParseResult(true, total, "");
    }

    public static String formatMillis(long millis) {
        if (millis <= 0L) {
            return "0s";
        }
        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder out = new StringBuilder();
        if (days > 0L) out.append(days).append("d");
        if (hours > 0L) out.append(hours).append("h");
        if (minutes > 0L) out.append(minutes).append("m");
        if (seconds > 0L || out.isEmpty()) out.append(seconds).append("s");
        return out.toString();
    }

    public record ParseResult(boolean valid, long millis, String error) {
    }
}
