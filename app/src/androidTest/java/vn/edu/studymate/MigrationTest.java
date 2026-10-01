package vn.edu.studymate;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.model.Entry;

public class MigrationTest {
  @Test
  public void upgradesWithoutLosingAccountsOrTasks() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name = "migration_test.db";
    c.deleteDatabase(name);
    long user, id;
    try (LegacyDatabaseHelper old = new LegacyDatabaseHelper(c, name)) {
      user = old.register("Test", "migration@test.vn", "Test123!");
      Entry e = new Entry();
      e.kind = "task";
      e.title = "Giữ dữ liệu cũ";
      e.dueAt = System.currentTimeMillis() + 86400000L;
      id = old.save(user, e);
    }
    try (DatabaseHelper db = new DatabaseHelper(c, name)) {
      assertEquals(user, db.login("migration@test.vn", "Test123!"));
      Entry e = db.get(user, id);
      assertEquals("Giữ dữ liệu cũ", e.title);
      assertTrue(e.remindEnabled);
      assertEquals(60, e.minutesBefore);
      assertEquals(5, db.getReadableDatabase().getVersion());
      e.minutesBefore = 30;
      db.save(user, e);
      assertEquals(30, db.get(user, id).minutesBefore);
    } finally {
      c.deleteDatabase(name);
    }
  }

  @Test
  public void upgradesEarlierVersionTwoWithDifferentColumns() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name = "alternate_v2_test.db";
    c.deleteDatabase(name);
    long user, id;
    try (LegacyDatabaseHelper old = new LegacyDatabaseHelper(c, name)) {
      user = old.register("Test", "alternate@test.vn", "Test123!");
      Entry e = new Entry();
      e.kind = "task";
      e.title = "Existing task";
      e.dueAt = System.currentTimeMillis() + 86400000L;
      id = old.save(user, e);
      old.getWritableDatabase()
          .execSQL(
              "ALTER TABLE entries ADD COLUMN reminder_before_min INTEGER NOT NULL DEFAULT 30");
      old.getWritableDatabase().setVersion(2);
    }
    try (DatabaseHelper db = new DatabaseHelper(c, name)) {
      assertEquals(user, db.login("alternate@test.vn", "Test123!"));
      Entry e = db.get(user, id);
      assertEquals("Existing task", e.title);
      assertEquals(30, e.minutesBefore);
      assertEquals("", e.code);
      assertEquals(5, db.getReadableDatabase().getVersion());
      e.detail = "Can update after migration";
      db.save(user, e);
      assertEquals(e.detail, db.get(user, id).detail);
    } finally {
      c.deleteDatabase(name);
    }
  }

  @Test
  public void upgradesCompleteVersionTwoWithoutResettingValues() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name = "complete_v2_test.db";
    c.deleteDatabase(name);
    long user, id, created;
    try (VersionFourHelper db = new VersionFourHelper(c, name)) {
      user = db.register("Test", "complete@test.vn", "Test123!");
      Entry e = new Entry();
      e.kind = "exam";
      e.title = "Existing exam";
      e.dueAt = System.currentTimeMillis() + 86400000L;
      e.endAt = e.dueAt + 7200000;
      e.minutesBefore = 60;
      id = db.save(user, e);
      created = db.get(user, id).createdAt;
      db.getWritableDatabase().execSQL("UPDATE entries SET minutes_before=15 WHERE id=" + id);
      db.getWritableDatabase().setVersion(2);
    }
    try (DatabaseHelper db = new DatabaseHelper(c, name)) {
      Entry e = db.get(user, id);
      assertEquals(7200000L, e.endAt - e.dueAt);
      assertEquals(created, e.createdAt);
      assertEquals(15, e.minutesBefore);
      assertEquals(5, db.getReadableDatabase().getVersion());
    } finally {
      c.deleteDatabase(name);
    }
  }
}
