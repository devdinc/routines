package github.devdinc.routines.cron;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Cron implements java.io.Serializable {

    private final Set<Integer> years; // nullable
    private final Set<Integer> months;
    private final Set<Integer> days;
    private final Set<Integer> hours;
    private final Set<Integer> minutes;
    private final Set<Integer> seconds; // nullable
    private final Set<Integer> weekdays; // nullable

    Cron(
            Set<Integer> years,
            Set<Integer> months,
            Set<Integer> days,
            Set<Integer> hours,
            Set<Integer> minutes,
            Set<Integer> seconds,
            Set<Integer> weekdays) {
        this.years = years;
        this.months = months;
        this.days = days;
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds; // null means “ignore seconds”
        this.weekdays = weekdays; // null means "ignore weekday restriction"
    }

    public List<Instant> next(int count, Instant after, ZoneId zone) {
        Instant end = after.plus(365 * 5, ChronoUnit.DAYS); // fallback
        return occurrences(after, end, count, false, zone);
    }

    public List<Instant> last(int count, Instant before, ZoneId zone) {
        Instant start = before.minus(365 * 5, ChronoUnit.DAYS);
        return occurrences(start, before, count, true, zone);
    }

    public List<Instant> in(Instant start, Instant end, ZoneId zone) {
        return occurrences(start, end, null, false, zone);
    }

    private static final Set<Integer> ALL_HOURS = IntStream.rangeClosed(0, 23).boxed().collect(Collectors.toSet());
    private static final Set<Integer> ALL_MINUTES = IntStream.rangeClosed(0, 59).boxed().collect(Collectors.toSet());

    public List<Instant> occurrences(
            Instant start,
            Instant end,
            Integer limit,
            boolean reverse,
            ZoneId zone) {
        ZoneId zonee = zone != null ? zone : ZoneId.systemDefault();

        ZonedDateTime zStart = start.atZone(zonee);
        ZonedDateTime zEnd;
        try {
            zEnd = end.atZone(zonee);
        } catch (Exception e) {
            zEnd = LocalDateTime.MAX.atZone(ZoneId.systemDefault());
        }

        // Full date range
        LocalDate startDate = zStart.toLocalDate();
        LocalDate endDate = zEnd.toLocalDate();

        List<Instant> out = new ArrayList<>();

        List<LocalDate> daysInPeriod = startDate.datesUntil(endDate)
                .toList();

        if (reverse)
            Collections.reverse(daysInPeriod);

        for (LocalDate date : daysInPeriod) {

            int y = date.getYear();
            int m = date.getMonthValue();
            int d = date.getDayOfMonth();

            if (!matches(years, y))
                continue;
            if (!matches(months, m))
                continue;
            if (!matches(days, d))
                continue;

            // Validate weekday
            if (weekdays != null) {
                int wd = date.getDayOfWeek().getValue() % 7;
                if (!weekdays.contains(wd))
                    continue;
            }

            // Hours
            Set<Integer> hrs = hours != null ? hours : ALL_HOURS;
            for (int hour : hrs) {

                // Minutes
                Set<Integer> mins = minutes != null ? minutes : ALL_MINUTES;
                for (int minute : mins) {

                    // Seconds (optional)
                    Set<Integer> secs = seconds != null ? seconds : Set.of(0);
                    for (int sec : secs) {

                        ZonedDateTime zdt = date.atTime(hour, minute, sec).atZone(zonee);
                        Instant inst = zdt.toInstant();

                        // Strict boundaries
                        if (inst.isBefore(start) || inst.isAfter(end))
                            continue;

                        out.add(inst);
                        if (limit != null && out.size() == limit)
                            return out;
                    }
                }
            }
        }

        return out;
    }

    private static boolean matches(Set<Integer> set, int value) {
        return set == null || set.contains(value);
    }
}