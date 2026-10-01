package vn.edu.studymate;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.junit.Test;
import vn.edu.studymate.logic.*;
import vn.edu.studymate.model.Entry;

public class ReportRulesTest {
  private final long now =
      ZonedDateTime.of(2026, 9, 15, 8, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
          .toInstant()
          .toEpochMilli();

  private Entry task(long due, int priority) {
    Entry e = new Entry();
    e.kind = "task";
    e.title = "Task";
    e.dueAt = due;
    e.priority = priority;
    return e;
  }

  private List<Entry> fixture() {
    long[] times = {
      now - 3600000,
      now,
      now + 86400000,
      now + 86400001,
      now + 259200000,
      now + 259200001,
      0,
      now - 3600000
    };
    List<Entry> tasks = new ArrayList<>();
    for (int i = 0; i < times.length; i++) {
      Entry e = task(times[i], 2);
      e.id = i + 1;
      tasks.add(e);
    }
    tasks.get(2).status = "Đang làm";
    tasks.get(7).status = "Hoàn thành";
    return tasks;
  }

  @Test
  public void NV01_reportBoundaryLabels() {
    String[] labels = {
      "Quá hạn",
      "Rất gần hạn",
      "Rất gần hạn",
      "Sắp đến hạn",
      "Sắp đến hạn",
      "Còn thời gian",
      "Chưa đặt deadline",
      "Đã hoàn thành"
    };
    List<Entry> all = fixture();
    for (int i = 0; i < 8; i++) assertEquals(labels[i], SmartPlanner.label(all.get(i), now));
    assertEquals(0, SmartPlanner.group(now - 1, now));
  }

  @Test
  public void NV02_stateBonus() {
    Entry e = task(now, 2);
    int before = SmartPlanner.score(e, now);
    e.status = "Đang làm";
    assertEquals(before + 5, SmartPlanner.score(e, now));
  }

  @Test
  public void NV03_completedRemoved() {
    List<Entry> all = fixture();
    assertEquals(7, SmartPlanner.ranked(all, now).size());
    all.get(2).status = "Hoàn thành";
    assertEquals(6, SmartPlanner.ranked(all, now).size());
    assertEquals(3, SmartPlanner.stats(all, now).near);
  }

  @Test
  public void NV04_scoreDeadlineIdTies() {
    Entry a = task(now + 10, 2), b = task(now + 20, 2);
    a.id = 8;
    b.id = 1;
    assertSame(a, SmartPlanner.ranked(Arrays.asList(b, a), now).get(0));
    b.dueAt = a.dueAt;
    a.createdAt = 1;
    b.createdAt = 9;
    assertSame(b, SmartPlanner.ranked(Arrays.asList(a, b), now).get(0));
  }

  @Test
  public void NV05_missingDeadlineLastOnEqualScore() {
    Entry a = task(0, 2), b = task(now + 259200001, 1);
    assertEquals(SmartPlanner.score(a, now), SmartPlanner.score(b, now));
    assertSame(b, SmartPlanner.ranked(Arrays.asList(a, b), now).get(0));
  }

  @Test
  public void NV06_reportScoresAndOrder() {
    List<Entry> all = fixture();
    int[] scores = {120, 90, 95, 60, 60, 30, 20, 0};
    for (int i = 0; i < 8; i++) assertEquals(scores[i], SmartPlanner.score(all.get(i), now));
    long[] ids = {1, 3, 2, 4, 5, 6, 7};
    List<Entry> ranked = SmartPlanner.ranked(all, now);
    for (int i = 0; i < 7; i++) assertEquals(ids[i], ranked.get(i).id);
    assertTrue(SmartPlanner.reason(all.get(2), now).contains("(+5)"));
    assertFalse(SmartPlanner.reason(all.get(6), now).contains("Quá hạn"));
  }

  @Test
  public void NV07_reportDashboard() {
    SmartPlanner.Stats s = SmartPlanner.stats(fixture(), now);
    assertEquals(8, s.total);
    assertEquals(1, s.completed);
    assertEquals(4, s.near);
    assertEquals(1, s.overdue);
    assertEquals(12.5, s.percent(), 0.001);
    assertEquals(0, SmartPlanner.stats(Collections.emptyList(), now).percent(), 0);
  }

  @Test
  public void NV11_changeDeadlineChangesTrigger() {
    Entry e = task(now + 7200000, 2);
    e.minutesBefore = 60;
    assertEquals(now + 3600000, ReminderRules.trigger(e, e.dueAt));
    e.dueAt += 7200000;
    assertEquals(now + 10800000, ReminderRules.trigger(e, e.dueAt));
  }

  @Test
  public void NV12_absentOrDisabledNeverReminds() {
    Entry e = task(0, 2);
    assertEquals(0, ReminderRules.next(e, now));
    e.dueAt = now;
    e.remindEnabled = false;
    assertEquals(0, ReminderRules.next(e, now));
    e.remindEnabled = true;
    e.status = "Hoàn thành";
    assertEquals(0, ReminderRules.next(e, now));
  }

  @Test
  public void DL07_invalidDataRejected() {
    Entry e = task(now, 2);
    e.priority = 4;
    assertThrows(IllegalArgumentException.class, () -> Validation.validate(e));
    e.priority = 2;
    e.status = "invalid";
    assertThrows(IllegalArgumentException.class, () -> Validation.validate(e));
    e.status = "Chưa làm";
    e.dueAt = -1;
    assertThrows(IllegalArgumentException.class, () -> Validation.validate(e));
    e.dueAt = now;
    e.minutesBefore = 15;
    assertThrows(IllegalArgumentException.class, () -> Validation.validate(e));
  }

  @Test
  public void reminderOptionsMatchWord() {
    assertArrayEquals(new int[] {-1, 0, 30, 60, 360, 1440, 4320}, ReminderOptions.MINUTES);
    for (int m : ReminderOptions.MINUTES) {
      Entry e = task(now, 2);
      e.remindEnabled = m >= 0;
      e.minutesBefore = Math.max(0, m);
      Validation.validate(e);
    }
  }

  @Test
  public void allOverdueOutrankNonOverdue() {
    Entry a = task(now - 1, 1), b = task(now, 3);
    b.status = "Đang làm";
    assertTrue(SmartPlanner.score(a, now) > SmartPlanner.score(b, now));
  }
}
