package vn.edu.studymate.logic;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import vn.edu.studymate.model.Entry;

public final class ScheduleRules {
  public static boolean occurs(Entry e, LocalDate date) {
    return e.kind.equals("schedule")
        && date.getDayOfWeek().getValue() == e.day - 1
        && !date.isBefore(LocalDate.parse(e.validFrom))
        && !date.isAfter(LocalDate.parse(e.validTo));
  }

  public static long at(Entry e, LocalDate d, int minute) {
    return d.atStartOfDay()
        .plusMinutes(minute)
        .atZone(ZoneId.of(e.zoneId))
        .toInstant()
        .toEpochMilli();
  }

  public static boolean overlap(long a, long b, long c, long d) {
    return a < d && c < b;
  }

  public static boolean conflict(Entry a, Entry b) {
    if (a.id > 0 && a.id == b.id) return false;
    if (a.kind.equals("exam") && b.kind.equals("exam"))
      return overlap(a.dueAt, a.endAt, b.dueAt, b.endAt);
    if (a.kind.equals("exam")) return conflict(b, a);
    if (!a.kind.equals("schedule")) return false;
    if (b.kind.equals("exam")) {
      LocalDate from = Instant.ofEpochMilli(b.dueAt).atZone(ZoneId.of(a.zoneId)).toLocalDate(),
          to = Instant.ofEpochMilli(b.endAt).atZone(ZoneId.of(a.zoneId)).toLocalDate();
      LocalDate first = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(a.day - 1)));
      for (LocalDate d = first; !d.isAfter(to); d = d.plusWeeks(1))
        if (occurs(a, d)
            && overlap(at(a, d, a.startMinute), at(a, d, a.endMinute), b.dueAt, b.endAt))
          return true;
      return false;
    }
    if (!b.kind.equals("schedule")) return false;
    LocalDate from = LocalDate.parse(a.validFrom), to = LocalDate.parse(a.validTo);
    LocalDate bf = LocalDate.parse(b.validFrom).minusDays(1),
        bt = LocalDate.parse(b.validTo).plusDays(1);
    if (from.isBefore(bf)) from = bf;
    if (to.isAfter(bt)) to = bt;
    for (LocalDate d = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(a.day - 1)));
        !d.isAfter(to);
        d = d.plusWeeks(1)) {
      long start = at(a, d, a.startMinute), end = at(a, d, a.endMinute);
      LocalDate bd = Instant.ofEpochMilli(start).atZone(ZoneId.of(b.zoneId)).toLocalDate();
      for (int offset = -1; offset <= 1; offset++) {
        LocalDate day = bd.plusDays(offset);
        if (occurs(b, day)
            && overlap(start, end, at(b, day, b.startMinute), at(b, day, b.endMinute))) return true;
      }
    }
    return false;
  }
}
