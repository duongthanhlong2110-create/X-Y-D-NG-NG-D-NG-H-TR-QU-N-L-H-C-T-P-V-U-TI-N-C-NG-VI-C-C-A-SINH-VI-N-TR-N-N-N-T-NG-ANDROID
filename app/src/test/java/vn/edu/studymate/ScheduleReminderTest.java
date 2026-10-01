package vn.edu.studymate;

import static org.junit.Assert.*;

import java.time.*;
import org.junit.Test;
import vn.edu.studymate.logic.*;
import vn.edu.studymate.model.Entry;

public class ScheduleReminderTest {
  private Entry weekly() {
    Entry e = new Entry();
    e.kind = "schedule";
    e.title = "Class";
    e.zoneId = "Asia/Ho_Chi_Minh";
    e.day = 2;
    e.validFrom = "2026-09-01";
    e.validTo = "2026-09-30";
    return e;
  }

  @Test
  public void identicalAndPartialOverlap() {
    Entry a = weekly(), b = weekly();
    assertTrue(ScheduleRules.conflict(a, b));
    b.startMinute = 500;
    b.endMinute = 560;
    assertTrue(ScheduleRules.conflict(a, b));
  }

  @Test
  public void touchingDoesNotOverlap() {
    Entry a = weekly(), b = weekly();
    b.startMinute = a.endMinute;
    b.endMinute = 600;
    assertFalse(ScheduleRules.conflict(a, b));
  }

  @Test
  public void containment() {
    Entry a = weekly(), b = weekly();
    b.startMinute = 490;
    b.endMinute = 500;
    assertTrue(ScheduleRules.conflict(a, b));
  }

  @Test
  public void distinctDates() {
    Entry a = weekly(), b = weekly();
    b.validFrom = "2026-10-01";
    b.validTo = "2026-10-31";
    assertFalse(ScheduleRules.conflict(a, b));
  }

  @Test
  public void noCommonWeekday() {
    Entry a = weekly(), b = weekly();
    b.validFrom = "2026-09-08";
    b.validTo = "2026-09-09";
    assertFalse(ScheduleRules.conflict(a, b));
  }

  @Test
  public void examVersusClass() {
    Entry a = weekly(), b = new Entry();
    b.kind = "exam";
    b.dueAt = ScheduleRules.at(a, LocalDate.parse("2026-09-07"), 500);
    b.endAt = b.dueAt + 60000;
    assertTrue(ScheduleRules.conflict(a, b));
    b.dueAt = ScheduleRules.at(a, LocalDate.parse("2026-09-08"), 500);
    b.endAt = b.dueAt + 60000;
    assertFalse(ScheduleRules.conflict(a, b));
  }

  @Test
  public void recurringReminderAndHandledOccurrence() {
    Entry e = weekly();
    long event = ScheduleRules.at(e, LocalDate.parse("2026-09-07"), 480);
    long now = event - 1800000;
    assertEquals(event, ReminderRules.next(e, now));
    assertEquals(event - 3600000, ReminderRules.trigger(e, event));
    e.handledAt = event;
    assertEquals(event + 7 * 86400000L, ReminderRules.next(e, now));
  }

  @Test
  public void disabledAndCompleted() {
    Entry e = weekly();
    e.remindEnabled = false;
    assertEquals(0, ReminderRules.next(e, 1));
    e.remindEnabled = true;
    e.status = "Hoàn thành";
    assertEquals(0, ReminderRules.next(e, 1));
  }

  @Test
  public void taskCatchupWindow() {
    Entry e = new Entry();
    e.kind = "task";
    e.dueAt = 1000000000L;
    assertEquals(e.dueAt, ReminderRules.next(e, e.dueAt + 86400000L));
    assertEquals(0, ReminderRules.next(e, e.dueAt + 86400001L));
    e.handledAt = e.dueAt;
    assertEquals(0, ReminderRules.next(e, e.dueAt));
  }

  @Test
  public void invalidRangeRejected() {
    Entry e = weekly();
    e.validTo = "2026-08-01";
    assertThrows(IllegalArgumentException.class, () -> Validation.validate(e));
  }
}
