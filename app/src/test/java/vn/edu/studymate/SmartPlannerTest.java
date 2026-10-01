package vn.edu.studymate;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import vn.edu.studymate.auth.PasswordHasher;
import vn.edu.studymate.logic.*;
import vn.edu.studymate.model.Entry;

public class SmartPlannerTest {
  private Entry task(long due, int priority) {
    Entry e = new Entry();
    e.kind = "task";
    e.title = "Task";
    e.dueAt = due;
    e.priority = priority;
    return e;
  }

  @Test
  public void exactDeadlineAndBoundaries() {
    long n = 1000000000L;
    assertEquals(0, SmartPlanner.group(n - 1, n));
    assertEquals(1, SmartPlanner.group(n, n));
    assertEquals(1, SmartPlanner.group(n + 86400000L, n));
    assertEquals(2, SmartPlanner.group(n + 86400001L, n));
    assertEquals(2, SmartPlanner.group(n + 3 * 86400000L, n));
    assertEquals(3, SmartPlanner.group(n + 3 * 86400000L + 1, n));
    assertEquals(3, SmartPlanner.group(n + 7 * 86400000L, n));
    assertEquals(3, SmartPlanner.group(n + 7 * 86400000L + 1, n));
    assertEquals(4, SmartPlanner.group(0, n));
  }

  @Test
  public void groupPrecedesImportance() {
    Entry overdue = task(1, 1), soon = task(100, 3);
    assertSame(overdue, SmartPlanner.ranked(Arrays.asList(soon, overdue), 10).get(0));
  }

  @Test
  public void importancePrecedesDeadlineWithinGroup() {
    Entry early = task(100, 1), late = task(200, 3);
    assertSame(late, SmartPlanner.ranked(Arrays.asList(early, late), 1).get(0));
  }

  @Test
  public void stableTies() {
    Entry a = task(100, 2), b = task(100, 2);
    a.createdAt = 3;
    b.createdAt = 2;
    a.id = 4;
    b.id = 2;
    assertSame(b, SmartPlanner.ranked(Arrays.asList(a, b), 1).get(0));
    a.createdAt = 2;
    a.id = 4;
    b.id = 2;
    assertSame(b, SmartPlanner.ranked(Arrays.asList(a, b), 1).get(0));
  }

  @Test
  public void completedExcluded() {
    Entry a = task(1, 3);
    a.status = "Hoàn thành";
    assertTrue(SmartPlanner.ranked(Arrays.asList(a), 10).isEmpty());
  }

  @Test
  public void progressAddsFivePoints() {
    Entry a = task(100, 2), b = task(100, 2);
    a.id = 1;
    b.id = 2;
    b.status = "Đang làm";
    assertEquals(5, SmartPlanner.score(b, 1) - SmartPlanner.score(a, 1));
    assertSame(b, SmartPlanner.ranked(Arrays.asList(b, a), 1).get(0));
  }

  @Test
  public void remaining() {
    assertEquals("Đến hạn ngay", SmartPlanner.remaining(1, 1));
    assertEquals("Còn 1 phút", SmartPlanner.remaining(2, 1));
    assertTrue(SmartPlanner.remaining(1, 2).startsWith("Quá hạn"));
  }

  @Test
  public void weightedGrades() {
    Entry a = new Entry(), b = new Entry();
    a.score = 8;
    a.weight = 30;
    b.score = 9;
    b.weight = 70;
    assertEquals(8.7, SmartPlanner.average(Arrays.asList(a, b)), 0.0001);
  }

  @Test
  public void passwordHash() {
    String s = PasswordHasher.salt(), h = PasswordHasher.hash("Demo123!", s);
    assertTrue(PasswordHasher.verify("Demo123!", s, h));
    assertFalse(PasswordHasher.verify("bad", s, h));
  }
}
