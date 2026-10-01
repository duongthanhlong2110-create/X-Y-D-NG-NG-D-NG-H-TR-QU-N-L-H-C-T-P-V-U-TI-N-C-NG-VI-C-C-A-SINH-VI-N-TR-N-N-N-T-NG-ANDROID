package vn.edu.studymate;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.model.Entry;

/** Uses an isolated database; never edits the user's study data. */
public class DatabaseTest {
  private DatabaseHelper db;
  private Context context;
  private long user, other;

  @Before
  public void setUp() throws Exception {

    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("test_studymate.db");
    db = new DatabaseHelper(context, "test_studymate.db");
    user = db.register("One", "one@test.vn", "secret123");
    other = db.register("Two", "two@test.vn", "secret456");
  }

  @After
  public void tearDown() throws Exception {
    db.close();
    context.deleteDatabase("test_studymate.db");
  }

  @Test
  public void testLoginAndDuplicateEmail() {
    assertEquals(user, db.login("ONE@test.vn", "secret123"));
    assertEquals(-1L, db.login("one@test.vn", "wrongpass"));
    assertEquals(-1L, db.register("Duplicate", "ONE@test.vn", "secret999"));
  }

  @Test
  public void testCrudAndAccountIsolation() {
    for (String kind : new String[] {"subject", "task", "schedule", "exam", "note", "grade"}) {
      Entry e = new Entry();
      e.kind = kind;
      e.dueAt = System.currentTimeMillis() + 86400000L;
      e.endAt = e.dueAt + 3600000L;
      e.title = "Initial";
      db.save(user, e);
      assertEquals(1, db.list(user, kind).size());
      assertNull(db.get(other, e.id));
      db.delete(other, e.id);
      assertNotNull(db.get(user, e.id));
      e.title = "Updated";
      db.save(user, e);
      assertEquals("Updated", db.get(user, e.id).title);
      db.delete(user, e.id);
      assertNull(db.get(user, e.id));
    }
  }

  @Test
  public void testDeletingSubjectKeepsAssociatedRecords() {
    Entry subject = new Entry();
    subject.kind = "subject";
    subject.title = "Android";
    db.save(user, subject);
    Entry task = new Entry();
    task.kind = "task";
    task.dueAt = System.currentTimeMillis() + 86400000L;
    task.title = "Report";
    task.subjectId = subject.id;
    db.save(user, task);
    assertThrows(IllegalArgumentException.class, () -> db.delete(user, subject.id));
    assertNotNull(db.get(user, subject.id));
    assertEquals(subject.id, db.get(user, task.id).subjectId);
    task.subjectId = 0;
    db.save(user, task);
    db.delete(user, subject.id);
    assertNull(db.get(user, subject.id));
    assertNotNull(db.get(user, task.id));
  }

  @Test
  public void testRejectsCrossAccountSubject() {
    Entry subject = new Entry();
    subject.kind = "subject";
    subject.title = "Private";
    db.save(other, subject);
    Entry task = new Entry();
    task.kind = "task";
    task.title = "Report";
    task.subjectId = subject.id;
    try {
      db.save(user, task);
      fail("Expected ownership validation");
    } catch (IllegalArgumentException expected) {
      /* Correct. */
    }
  }

  @Test
  public void testReminderDeduplicationAndReschedule() {
    long now = System.currentTimeMillis();
    Entry task = new Entry();
    task.kind = "task";
    task.title = "Soon";
    task.dueAt = now + 3600000L;
    db.save(user, task);
    assertEquals(1, db.reminders(user, now).size());
    db.markNotified(user, task.id, now);
    assertTrue(db.reminders(user, now).isEmpty());
    task.dueAt += 3600000L;
    db.save(user, task);
    assertEquals(1, db.reminders(user, now).size());
    task.status = "Hoàn thành";
    db.save(user, task);
    assertTrue(db.reminders(user, now).isEmpty());
  }

  @Test
  public void testDemoSeedIsIdempotent() {
    db.seedDemo();
    db.seedDemo();
    long demo = db.login("demo@studymate.vn", "Demo123!");
    assertTrue(demo > 0);
    assertEquals(3, db.list(demo, "task").size());
    assertTrue(db.list(user, "task").isEmpty());
  }
}
