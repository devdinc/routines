package com.devdinc.routines;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @deprecated This class is deprecated and will be removed in a future release.
 *             Use the new implementation in {@link github.devdinc.routines.FluentRoutine} instead.
 */
@Deprecated
public final class CronRoutineDelegate {

	/**
	 * Parses a cron expression and returns an IncompleteRoutine that will run at the next scheduled time.
	 * <p>
	 * The cron expression is made up of five fields, separated by spaces:
	 * - minute (0-59): the minute of the hour when the routine will run
	 * - hour (0-23): the hour of the day when the routine will run
	 * - day of month (1-31): the day of the month when the routine will run
	 * - month (1-12): the month of the year when the routine will run
	 * - day of week (0-6): the day of the week when the routine will run (0 = Sunday, 1 = Monday, ..., 6 = Saturday)
	 * <p>
	 * Accepts lists of values(?,?), ranges(0-5 including *), and steps(?/n).
	 *
	 * @param cronExpression the cron expression to parse
	 * @return an IncompleteRoutine that will run at the next scheduled time
	 */
	public static IncompleteRoutine<Void> unix(String cronExpression) {
		String[] parts = cronExpression.trim().split("\\s+");
		if (parts.length != 5)
			throw new IllegalArgumentException("Invalid cron: " + cronExpression);

		String minutePart = parts[0];
		String hourPart = parts[1];
		String dayOfMonthPart = parts[2];
		String monthPart = parts[3];
		String dayOfWeekPart = parts[4];

		Map<String, List<Integer>> fields = new LinkedHashMap<>();
		fields.put("minute", expand(minutePart, 0, 59));
		fields.put("hour", expand(hourPart, 0, 23));
		fields.put("dayOfMonth", expand(dayOfMonthPart, 1, 31));
		fields.put("month", expand(monthPart, 1, 12));
		fields.put("dayOfWeek", expand(dayOfWeekPart, 0, 6));

		LocalDate now = LocalDate.now();
		LocalDateTime firstTime = LocalDateTime.of(
				now.getYear(),
				fields.get("month").getFirst(),
				Math.min(fields.get("dayOfMonth").getFirst(), now.lengthOfMonth()),
				fields.get("hour").getFirst(),
				fields.get("minute").getFirst()
		);

		// Determine smallest repeating unit
		ChronoUnit unit;
		int interval = 1;
		if (hasRepetition(fields.get("minute"))) unit = ChronoUnit.MINUTES;
		else if (hasRepetition(fields.get("hour"))) unit = ChronoUnit.HOURS;
		else if (hasRepetition(fields.get("dayOfMonth"))) unit = ChronoUnit.DAYS;
		else if (hasRepetition(fields.get("month"))) unit = ChronoUnit.MONTHS;
		else unit = ChronoUnit.YEARS;

		IncompleteRoutine<Void> routine = Routine.at(firstTime).every(unit.getDuration().multipliedBy(interval));

		// Composite conditional
		Predicate<Void> composite = buildCompositeCondition(fields);
		if (composite != null)
			routine.conditional(composite);

		return routine;
	}

	/**
	 * Expand a cron field to explicit integer list.
	 */
	private static List<Integer> expand(String field, int min, int max) {
		List<Integer> result = new ArrayList<>();
		if (field.equals("*")) {
			for (int i = min; i <= max; i++) result.add(i);
			return result;
		}

		for (String part : field.split(",")) {
			if (part.contains("/")) { // step
				String[] stepParts = part.split("/");
				int step = Integer.parseInt(stepParts[1]);
				List<Integer> base = expand(stepParts[0], min, max);
				for (int i = 0; i < base.size(); i += step)
					result.add(base.get(i));
			} else if (part.contains("-")) { // range
				String[] range = part.split("-");
				int start = Integer.parseInt(range[0]);
				int end = Integer.parseInt(range[1]);
				for (int i = start; i <= end; i++) result.add(i);
			} else {
				result.add(Integer.parseInt(part));
			}
		}
		return result;
	}

	private static boolean hasRepetition(List<Integer> list) {
		return list.size() > 1;
	}

	/**
	 * Build single composite condition
	 */
	private static Predicate<Void> buildCompositeCondition(Map<String, List<Integer>> fields) {
		List<Predicate<Void>> matchers = new ArrayList<>();

		if (hasRepetition(fields.get("month")))
			matchers.add(v -> matchMonth(fields.get("month"), LocalDate.now().getMonthValue()));
		if (hasRepetition(fields.get("dayOfMonth")))
			matchers.add(v -> matchDay(fields.get("dayOfMonth"), LocalDate.now().getDayOfMonth()));
		if (hasRepetition(fields.get("hour")))
			matchers.add(v -> matchHour(fields.get("hour"), LocalDateTime.now().getHour()));
		if (hasRepetition(fields.get("minute")))
			matchers.add(v -> matchMinute(fields.get("minute"), LocalDateTime.now().getMinute()));
		if (hasRepetition(fields.get("dayOfWeek")))
			matchers.add(v -> matchDayOfWeek(fields.get("dayOfWeek"), LocalDate.now().getDayOfWeek().getValue()));

		if (matchers.isEmpty()) return null;
		return v -> matchers.stream().allMatch(f -> f.test(v));
	}

	// --- Match helpers ---
	public static boolean matchMinute(List<Integer> valid, int minute) {
		return valid.contains(minute);
	}

	public static boolean matchHour(List<Integer> valid, int hour) {
		return valid.contains(hour);
	}

	public static boolean matchDay(List<Integer> valid, int day) {
		return valid.contains(day);
	}

	public static boolean matchMonth(List<Integer> valid, int month) {
		return valid.contains(month);
	}

	public static boolean matchDayOfWeek(List<Integer> valid, int dayOfWeek) {
		// Sunday fix for 0-based vs 7-based week
		int normalized = (dayOfWeek == 7) ? 0 : dayOfWeek;
		return valid.contains(normalized);
	}
}
