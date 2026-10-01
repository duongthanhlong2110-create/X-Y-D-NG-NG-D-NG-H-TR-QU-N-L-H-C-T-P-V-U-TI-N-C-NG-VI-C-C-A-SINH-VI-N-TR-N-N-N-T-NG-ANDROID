package vn.edu.studymate.logic;

import java.time.*;
import vn.edu.studymate.model.Entry;

public final class ReminderRules {
  /** Next unhandled occurrence, including a bounded catch-up window. Zero means no reminder. */
  public static long next(Entry e, long now) {
    if (!e.remindEnabled || e.minutesBefore < 0 || SmartPlanner.finished(e)) return 0;
    if (e.kind.equals("task") || e.kind.equals("exam"))
      return e.dueAt > 0 && e.handledAt != e.dueAt && e.dueAt >= now - 86400000L ? e.dueAt : 0;
    if (!e.kind.equals("schedule")) return 0;
    LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.of(e.zoneId)).toLocalDate();
    LocalDate first = today.minusDays(1);
    LocalDate valid = LocalDate.parse(e.validFrom);
    if (first.isBefore(valid)) first = valid;
    first = first.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(e.day - 1)));
    for (int i = 0; i < 2; i++) {
      LocalDate date = first.plusWeeks(i);
      if (!ScheduleRules.occurs(e, date)) continue;
      long at = ScheduleRules.at(e, date, e.startMinute);
      if (at != e.handledAt && ScheduleRules.at(e, date, e.endMinute) > now) return at;
    }
    return 0;
  }

  public static long trigger(Entry e, long occurrence) {
    return occurrence - e.minutesBefore * 60000L;
  }
}
