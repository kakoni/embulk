package org.embulk.deps.timestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Locale;
import org.embulk.spi.time.TimestampFormatterDelegate;
import org.embulk.util.timestamp.TimestampFormatter;

public final class TimestampFormatterDelegateImpl extends TimestampFormatterDelegate {
    public TimestampFormatterDelegateImpl(final String pattern, final String defaultZone, final String defaultDate) {
        if (pattern != null && pattern.startsWith("java:")) {
            this.formatter = new JavaFormatterCompat(
                    pattern.substring("java:".length()),
                    defaultZone,
                    defaultDate);
        } else {
            final TimestampFormatter.Builder builder = TimestampFormatter.builder(pattern, true);
            if (defaultZone != null) {
                builder.setDefaultZoneFromString(defaultZone);
            }
            if (defaultDate != null) {
                builder.setDefaultDateFromString(defaultDate);
            }
            this.formatter = new UtilFormatter(builder.build());
        }
    }

    @Override
    public String format(final Instant format) {
        return this.formatter.format(format);
    }

    @Override
    public Instant parse(final String text) {
        return this.formatter.parse(text);
    }

    private interface Formatter {
        String format(Instant instant);

        Instant parse(String text);
    }

    private static final class UtilFormatter implements Formatter {
        UtilFormatter(final TimestampFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public String format(final Instant instant) {
            return this.formatter.format(instant);
        }

        @Override
        public Instant parse(final String text) {
            return this.formatter.parse(text);
        }

        private final TimestampFormatter formatter;
    }

    private static final class JavaFormatterCompat implements Formatter {
        JavaFormatterCompat(final String pattern, final String defaultZone, final String defaultDate) {
            this.originalPattern = pattern;
            this.defaultZoneId = defaultZone != null ? ZoneId.of(defaultZone) : ZoneOffset.UTC;
            this.defaultDate = defaultDate != null ? LocalDate.parse(defaultDate) : LocalDate.of(1970, 1, 1);

            this.usesClockHour = containsSymbolOutsideLiterals(pattern, 'h');      // CLOCK_HOUR_OF_AMPM (1-12)
            this.usesHourOfAmPm = containsSymbolOutsideLiterals(pattern, 'K');     // HOUR_OF_AMPM (0-11)
            this.uses12HourFormat = this.usesClockHour || this.usesHourOfAmPm;
            this.hasZoneText = containsSymbolOutsideLiterals(pattern, 'z', 'V', 'O');

            // Build formatter
            final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern(pattern);

            if (!this.uses12HourFormat) {
                builder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
            }
            builder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                   .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                   .parseDefaulting(ChronoField.NANO_OF_SECOND, 0);

            // h pattern: LENIENT to handle 12 AM → 00:xx
            // K pattern: STRICT (reject 12, but we handle 12 AM fallback)
            // 24-hour: STRICT to reject invalid dates
            this.formatter = builder
                    .toFormatter(Locale.ENGLISH)
                    .withResolverStyle(this.usesClockHour ? ResolverStyle.LENIENT : ResolverStyle.STRICT);

            // Create lenient formatter for K pattern 12 AM fallback
            if (this.usesHourOfAmPm && !this.usesClockHour) {
                this.lenientFormatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(pattern)
                        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                        .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
                        .toFormatter(Locale.ENGLISH)
                        .withResolverStyle(ResolverStyle.LENIENT);
            } else {
                this.lenientFormatter = null;
            }
        }

        @Override
        public String format(final Instant instant) {
            return this.formatter.format(instant.atZone(this.defaultZoneId));
        }

        @Override
        public Instant parse(final String text) {
            if (this.hasZoneText) {
                throw new DateTimeParseException(
                        "Zone text patterns (z, V, O) are not supported: " + this.originalPattern, text, 0);
            }
            try {
                return doParse(text, this.formatter);
            } catch (DateTimeParseException ex) {
                // K pattern: 12 AM should succeed (using lenient fallback), 12 PM should fail
                if (this.lenientFormatter != null && isHourOfAmPmTwelveAM(text, ex)) {
                    return doParse(text, this.lenientFormatter);
                }
                throw ex;
            }
        }

        private boolean isHourOfAmPmTwelveAM(final String text, final DateTimeParseException ex) {
            final String upper = text.toUpperCase(Locale.ENGLISH);
            final String msg = ex.getMessage();
            return upper.contains("AM") && !upper.contains("PM")
                    && msg != null && msg.contains("HourOfAmPm") && msg.contains("12");
        }

        private Instant doParse(final String text, final DateTimeFormatter fmt) {
            final TemporalAccessor parsed = fmt.parse(text);

            final LocalDate date = extractDate(parsed);
            final LocalTime time = extractTime(parsed);
            final ZoneOffset offset = parsed.query(TemporalQueries.offset());
            if (offset != null) {
                return OffsetDateTime.of(date, time, offset).toInstant();
            }
            final Instant atLocalInstant = OffsetDateTime.of(date, time, ZoneOffset.UTC).toInstant();
            final ZoneOffset resolvedOffset = this.defaultZoneId.getRules().getOffset(atLocalInstant);
            return OffsetDateTime.of(date, time, resolvedOffset).toInstant();
        }

        private LocalDate extractDate(final TemporalAccessor parsed) {
            try {
                return LocalDate.from(parsed);
            } catch (final java.time.DateTimeException ex) {
                final int year = getField(parsed, ChronoField.YEAR,
                        getField(parsed, ChronoField.YEAR_OF_ERA, this.defaultDate.getYear()));
                final int month = getField(parsed, ChronoField.MONTH_OF_YEAR, this.defaultDate.getMonthValue());
                final int day = getField(parsed, ChronoField.DAY_OF_MONTH, this.defaultDate.getDayOfMonth());
                return LocalDate.of(year, month, day);
            }
        }

        private LocalTime extractTime(final TemporalAccessor parsed) {
            try {
                return LocalTime.from(parsed);
            } catch (final java.time.DateTimeException ex) {
                int hour = 0;
                if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
                    hour = parsed.get(ChronoField.HOUR_OF_DAY);
                } else if (parsed.isSupported(ChronoField.AMPM_OF_DAY)) {
                    final int ampm = parsed.get(ChronoField.AMPM_OF_DAY);
                    int hourOfAmPm = 0;
                    if (parsed.isSupported(ChronoField.HOUR_OF_AMPM)) {
                        hourOfAmPm = parsed.get(ChronoField.HOUR_OF_AMPM);
                    } else if (parsed.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM)) {
                        hourOfAmPm = parsed.get(ChronoField.CLOCK_HOUR_OF_AMPM) % 12;
                    }
                    hour = ampm * 12 + hourOfAmPm;
                }
                final int minute = getField(parsed, ChronoField.MINUTE_OF_HOUR, 0);
                final int second = getField(parsed, ChronoField.SECOND_OF_MINUTE, 0);
                final int nano = getField(parsed, ChronoField.NANO_OF_SECOND, 0);
                return LocalTime.of(hour % 24, minute, second, nano);
            }
        }

        private static int getField(final TemporalAccessor parsed,
                                    final ChronoField field, final int defaultValue) {
            return parsed.isSupported(field) ? parsed.get(field) : defaultValue;
        }

        private static boolean containsSymbolOutsideLiterals(final String pattern, final char... symbols) {
            boolean inLiteral = false;
            for (int i = 0; i < pattern.length(); i++) {
                final char ch = pattern.charAt(i);
                if (ch == '\'') {
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                        i++;  // Escaped quote, skip
                        continue;
                    }
                    inLiteral = !inLiteral;
                    continue;
                }
                if (!inLiteral) {
                    for (final char target : symbols) {
                        if (ch == target) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private final ZoneId defaultZoneId;
        private final LocalDate defaultDate;
        private final DateTimeFormatter formatter;
        private final DateTimeFormatter lenientFormatter;
        private final String originalPattern;
        private final boolean usesClockHour;
        private final boolean usesHourOfAmPm;
        private final boolean uses12HourFormat;
        private final boolean hasZoneText;
    }

    private final Formatter formatter;
}
