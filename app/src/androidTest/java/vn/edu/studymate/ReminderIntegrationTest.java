package vn.edu.studymate;

import static org.junit.Assert.*;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.reminder.ReminderScheduler;

public class ReminderIntegrationTest {
  @Test
  public void scheduledAlarmDeliversWithoutManualCheck() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    if (Build.VERSION.SDK_INT >= 33) {
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .executeShellCommand(
              "pm grant " + c.getPackageName() + " android.permission.POST_NOTIFICATIONS")
          .close();
      SystemClock.sleep(300);
    }
    Session.logout(c);
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      long user =
          db.register("Alarm thử", "alarm" + System.currentTimeMillis() + "@test.vn", "Test123!");
      try {
        Session.login(c, user);
        Entry e = new Entry();
        e.kind = "task";
        e.title = "Nhắc theo thời gian đã hẹn";
        e.dueAt = System.currentTimeMillis() + 5000;
        e.minutesBefore = 0;
        db.save(user, e);
        ReminderScheduler.schedule(c);
        long stop = SystemClock.elapsedRealtime() + 45000;
        while (db.get(user, e.id).handledAt == 0 && SystemClock.elapsedRealtime() < stop)
          SystemClock.sleep(200);
        assertEquals(
            "Alarm receiver should handle the occurrence", e.dueAt, db.get(user, e.id).handledAt);
        NotificationManager manager =
            (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean found = false;
        for (android.service.notification.StatusBarNotification n :
            manager.getActiveNotifications()) if (n.getId() == (int) e.id) found = true;
        assertTrue("Scheduled notification is visible", found);
      } finally {
        Session.logout(c);
        db.getWritableDatabase().delete("users", "id=?", new String[] {"" + user});
      }
    }
  }

  @Test
  public void dueReminderIsPostedOnceAndOpensOwnedEntry() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    if (Build.VERSION.SDK_INT >= 33) {
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .executeShellCommand(
              "pm grant " + c.getPackageName() + " android.permission.POST_NOTIFICATIONS")
          .close();
      SystemClock.sleep(300);
    }
    Session.logout(c);
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      long user =
          db.register("Nhắc thử", "notify" + System.currentTimeMillis() + "@test.vn", "Test123!");
      Session.login(c, user);
      ReminderScheduler.setEnabled(c, true);
      Entry e = new Entry();
      e.kind = "task";
      e.title = "Nhắc đúng mục";
      e.dueAt = System.currentTimeMillis() + 1800000;
      e.minutesBefore = 60;
      db.save(user, e);
      ReminderScheduler.check(c);
      assertEquals(e.dueAt, db.get(user, e.id).handledAt);
      NotificationManager manager =
          (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      boolean posted = false;
      for (android.service.notification.StatusBarNotification n : manager.getActiveNotifications())
        if (n.getId() == (int) e.id) {
          posted = true;
          assertNotNull(n.getNotification().contentIntent);
        }
      assertTrue(posted);
      ReminderScheduler.check(c);
      assertEquals(e.dueAt, db.get(user, e.id).handledAt);
      e.dueAt += 3600000;
      db.save(user, e);
      assertEquals(0, db.get(user, e.id).handledAt);
      e.status = "Hoàn thành";
      db.save(user, e);
      ReminderScheduler.check(c);
      assertEquals(0, db.get(user, e.id).handledAt);
      Session.logout(c);
      assertEquals(0, manager.getActiveNotifications().length);
      db.getWritableDatabase().delete("users", "id=?", new String[] {"" + user});
    }
  }
}
