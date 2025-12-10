package github.devdinc.routines.cron;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Basic UNIX cron expression parser that produces a Cron instance.
 * Supports fields: seconds (optional), minutes, hours, day-of-month, month,
 * day-of-week, year (optional).
 * Accepts: "sec min hour dom mon dow [year]"
 * If seconds or year omitted -> corresponding Cron field becomes null.
 */
public final class UnixCronParser {

    private static final Set<Integer> ALL_SECONDS = IntStream.rangeClosed(0, 59).boxed().collect(Collectors.toSet());

    public static Cron parse(String expression) {
        String[] parts = expression.trim().split("\\s+");

        if (parts.length < 5 || parts.length > 7) {
            throw new IllegalArgumentException("Cron must have 5–7 fields: [sec] min hour dom mon dow [year]");
        }

        int idx = 0;
        Set<Integer> seconds = null;
        if (parts.length != 5) {
            // If length is 6 or 7, first is seconds
            seconds = parseField(parts[idx++], 0, 59);
        }

        Set<Integer> minutes = parseField(parts[idx++], 0, 59);
        Set<Integer> hours = parseField(parts[idx++], 0, 23);
        Set<Integer> days = parseField(parts[idx++], 1, 31);
        Set<Integer> months = parseField(parts[idx++], 1, 12);
        Set<Integer> weekdays = parseField(parts[idx++], 0, 6);

        Set<Integer> years = null;
        if (idx < parts.length) {
            years = parseField(parts[idx], 1970, 2099);
        }

        return new Cron(
                years,
                months,
                days,
                hours,
                minutes,
                seconds,
                weekdays);
    }

    private static Set<Integer> parseField(String f, int min, int max) {
        if (f.equals("*"))
            return ALL_SECONDS;

        Set<Integer> out = new TreeSet<>();
        String[] segments = f.split(",");

        for (String seg : segments) {
            if (seg.contains("/")) {
                String[] stepParts = seg.split("/");
                if (stepParts.length != 2)
                    throw new IllegalArgumentException("Invalid step syntax: " + seg);

                String rangePart = stepParts[0];
                int step = Integer.parseInt(stepParts[1]);

                int rStart = min;
                int rEnd = max;

                if (!rangePart.equals("*")) {
                    String[] r = rangePart.split("-");
                    if (r.length != 2)
                        throw new IllegalArgumentException("Invalid range in step: " + seg);
                    rStart = Integer.parseInt(r[0]);
                    rEnd = Integer.parseInt(r[1]);
                }
                for (int v = rStart; v <= rEnd; v += step)
                    out.add(v);
                continue;
            }

            if (seg.contains("-")) {
                String[] r = seg.split("-");
                if (r.length != 2)
                    throw new IllegalArgumentException("Invalid range: " + seg);
                int start = Integer.parseInt(r[0]);
                int end = Integer.parseInt(r[1]);
                if (start > end)
                    throw new IllegalArgumentException("Range start > end: " + seg);
                for (int v = start; v <= end; v++)
                    out.add(v);
            } else {
                int v = Integer.parseInt(seg);
                if (v < min || v > max)
                    throw new IllegalArgumentException("Value out of range: " + v);
                out.add(v);
            }
        }

        return out;
    }
}
