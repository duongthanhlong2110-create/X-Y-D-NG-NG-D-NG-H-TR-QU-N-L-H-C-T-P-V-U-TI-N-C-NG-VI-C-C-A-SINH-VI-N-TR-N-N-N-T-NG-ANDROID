package vn.edu.studymate;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.logic.ReminderRules;
import vn.edu.studymate.model.Entry;

public class ReportDatabaseTest {
  private Context c;
  private DatabaseHelper db;
  private long u, v;

  @Before
  public void setup() {
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("report_test.db");
    db = new DatabaseHelper(c, "report_test.db");
    u = db.register("U01", "sv01@example.test", "Test123!");
    v = db.register("U02", "sv02@example.test", "Test123!");
  }

  @After
  public void cleanup() {
    db.close();
    c.deleteDatabase("report_test.db");
  }

  private Entry entry(String kind) {
    Entry e = new Entry();
    e.kind = kind;
    e.title = " Dữ liệu thử ";
    return e;
  }

  @Test
  public void DL02_rejectBlankAndInvalidAccount() {
    assertThrows(IllegalArgumentException.class, () -> db.register(" ", "x@y.vn", "123456"));
    assertThrows(IllegalArgumentException.class, () -> db.register("X", "not-email", "123456"));
    Entry e = entry("task");
    e.title = "   ";
    assertThrows(IllegalArgumentException.class, () -> db.save(u, e));
  }

  @Test
  public void DL03_persistSubjectAndUniqueCode() {
    Entry e = entry("subject");
    e.code = "CS01";
    db.save(u, e);
    assertEquals("Dữ liệu thử", db.get(u, e.id).title);
    e.title = "Đã sửa";
    db.save(u, e);
    db.close();
    db = new DatabaseHelper(c, "report_test.db");
    assertEquals(e.title, db.get(u, e.id).title);
    Entry duplicate = entry("subject");
    duplicate.code = "cs01";
    assertThrows(
        android.database.sqlite.SQLiteConstraintException.class, () -> db.save(u, duplicate));
  }

  @Test
  public void DL05_editDeleteAndKindIsolation() {
    Entry e = entry("task");
    db.save(v, e);
    assertNull(db.get(u, e.id));
    assertThrows(IllegalArgumentException.class, () -> db.save(u, e));
    db.delete(u, e.id);
    assertNotNull(db.get(v, e.id));
    e.kind = "note";
    assertThrows(IllegalArgumentException.class, () -> db.save(v, e));
  }

  @Test
  public void DL06_nonexistentSubject() {
    Entry e = entry("task");
    e.subjectId = 999999;
    assertThrows(IllegalArgumentException.class, () -> db.save(u, e));
  }

  @Test
  public void DL08_blockSubjectWithSchedule() {
    Entry subject = entry("subject");
    db.save(u, subject);
    Entry schedule = entry("schedule");
    schedule.subjectId = subject.id;
    db.save(u, schedule);
    assertThrows(IllegalArgumentException.class, () -> db.delete(u, subject.id));
    assertEquals(subject.id, db.get(u, schedule.id).subjectId);
  }

  @Test
  public void NV12_noDeadlineAndNoReminderPersist() {
    Entry e = entry("task");
    e.dueAt = 0;
    e.remindEnabled = false;
    db.save(u, e);
    assertEquals(0, db.get(u, e.id).dueAt);
    assertEquals(0, ReminderRules.next(db.get(u, e.id), System.currentTimeMillis()));
    try (Cursor cursor =
        db.getReadableDatabase()
            .rawQuery(
                "SELECT reminder_before_min FROM entries WHERE id=?", new String[] {"" + e.id})) {
      assertTrue(cursor.moveToFirst());
      assertEquals(-1, cursor.getInt(0));
    }
  }

  @Test
  public void NV13_14_resetOnlyForNewReminder() {
    Entry e = entry("task");
    e.dueAt = System.currentTimeMillis() + 100000;
    db.save(u, e);
    db.markHandled(u, e.id, e.dueAt);
    db.save(u, e);
    assertEquals(e.dueAt, db.get(u, e.id).handledAt);
    e.status = "Hoàn thành";
    db.save(u, e);
    assertEquals(0, ReminderRules.next(db.get(u, e.id), System.currentTimeMillis()));
    e.status = "Chưa làm";
    db.save(u, e);
    assertEquals(0, db.get(u, e.id).handledAt);
    assertEquals(e.dueAt, ReminderRules.next(db.get(u, e.id), System.currentTimeMillis()));
  }

  @Test
  public void reconciliationRevisionSurvivesReopen() {
    long before = db.reminderRevision(u);
    Entry e = entry("task");
    db.save(u, e);
    long updated = db.reminderRevision(u);
    assertTrue(updated > before);
    assertFalse(db.acknowledgeReminders(u, before));
    assertTrue(db.acknowledgeReminders(u, updated));
    db.close();
    db = new DatabaseHelper(c, "report_test.db");
    assertEquals(updated, db.reminderRevision(u));
    db.delete(u, e.id);
    assertTrue(db.reminderRevision(u) > updated);
  }
}
