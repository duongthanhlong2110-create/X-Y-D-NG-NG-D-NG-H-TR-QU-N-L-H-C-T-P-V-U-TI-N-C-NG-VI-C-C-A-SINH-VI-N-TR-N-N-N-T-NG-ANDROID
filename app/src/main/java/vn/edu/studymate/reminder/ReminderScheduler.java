package vn.edu.studymate.reminder;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.studymate.R;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.logic.*;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.ui.*;

public final class ReminderScheduler {
  private static final int JOB = 2001;
  private static final String CHANNEL = "deadlines";
  private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

  public static boolean enabled(Context c) {
    long u = Session.user(c);
    return u > 0 && c.getSharedPreferences("settings_" + u, 0).getBoolean("reminders", true);
  }

  public static void setEnabled(Context c, boolean value) {
    c.getSharedPreferences("settings_" + Session.user(c), 0)
        .edit()
        .putBoolean("reminders", value)
        .apply();
    if (value) schedule(c);
    else {
      cancel(c);
      ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    }
  }

  public static void schedule(Context c) {
    if (!enabled(c)) return;
    JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (scheduler.getPendingJob(JOB) == null)
      scheduler.schedule(
          new JobInfo.Builder(JOB, new ComponentName(c, DeadlineJobService.class))
              .setPersisted(true)
              .setPeriodic(15 * 60 * 1000L)
              .build());
    checkNow(c);
  }

  private static PendingIntent alarm(Context c) {
    return PendingIntent.getBroadcast(
        c,
        5000,
        new Intent(c, ReminderReceiver.class).setAction("vn.edu.studymate.REMIND"),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static synchronized void cancel(Context c) {
    ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB);
    ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c));
  }

  public static void checkNow(Context c) {
    Context app = c.getApplicationContext();
    EXEC.execute(() -> check(app));
  }

  private static void channel(Context c) {
    NotificationChannel ch =
        new NotificationChannel(CHANNEL, "Nhắc học tập", NotificationManager.IMPORTANCE_DEFAULT);
    ch.setDescription("Công việc, buổi học và lịch thi theo cấu hình của bạn.");
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
        .createNotificationChannel(ch);
  }

  public static boolean allowed(Context c) {
    if (Build.VERSION.SDK_INT >= 33
        && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false;
    channel(c);
    NotificationChannel ch =
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
            .getNotificationChannel(CHANNEL);
    return NotificationManagerCompat.from(c).areNotificationsEnabled()
        && ch.getImportance() != NotificationManager.IMPORTANCE_NONE;
  }

  public static synchronized void check(Context c) {
    synchronized (DatabaseHelper.DATA_LOCK) {
      reconcile(c);
    }
  }

  private static void reconcile(Context c) {
    long u = Session.user(c);
    if (u < 1 || !enabled(c)) return;
    long now = System.currentTimeMillis(), nearest = Long.MAX_VALUE, revision;
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      NotificationManager notifications =
          (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      for (android.service.notification.StatusBarNotification notification :
          notifications.getActiveNotifications()) {
        if (notification.getId() == 2000000000) continue;
        Entry source = db.get(u, notification.getId());
        if (source == null
            || notification.getNotification().extras.getLong("source_updated", -1)
                != source.updatedAt
            || notification.getNotification().extras.getLong("source_user", -1) != u
            || SmartPlanner.finished(source)
            || !source.remindEnabled
            || (source.kind.equals("task") && source.dueAt == 0))
          notifications.cancel(notification.getId());
      }
      List<Entry> all = db.scheduledReminders(u);
      for (Entry e : all) {
        if (Session.user(c) != u || !enabled(c)) return;
        long occurrence = ReminderRules.next(e, now);
        if (occurrence == 0) continue;
        long trigger = ReminderRules.trigger(e, occurrence);
        if (trigger <= now) {
          if (allowed(c) && send(c, e, u, occurrence)) {
            db.markReminderHandled(u, e.reminderId, occurrence, System.currentTimeMillis());
            e.handledAt = occurrence;
            long next = ReminderRules.next(e, now);
            if (next > 0)
              nearest = Math.min(nearest, Math.max(now + 60000, ReminderRules.trigger(e, next)));
          }
        } else nearest = Math.min(nearest, trigger);
      }
      revision = db.reminderRevision(u);
    }
    if (Session.user(c) != u || !enabled(c)) return;
    AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    manager.cancel(alarm(c));
    if (nearest != Long.MAX_VALUE)
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nearest, alarm(c));
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      if (!db.acknowledgeReminders(u, revision)) checkNow(c);
    }
  }

  private static boolean send(Context c, Entry e, long user, long occurrence) {
    Intent intent =
        new Intent(c, EditorActivity.class)
            .putExtra("id", e.id)
            .putExtra("user", user)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return post(
        c,
        (int) e.id,
        e.title,
        Ui.label(e.kind)
            + " • "
            + SmartPlanner.remaining(occurrence, System.currentTimeMillis())
            + " • Nhắc trước "
            + e.minutesBefore
            + " phút",
        intent,
        e.updatedAt,
        user);
  }

  private static boolean post(Context c, int id, String title, String body, Intent intent) {
    return post(c, id, title, body, intent, 0, Session.user(c));
  }

  private static boolean post(
      Context c, int id, String title, String body, Intent intent, long updated, long user) {
    if (!allowed(c)) return false;
    PendingIntent pending =
        PendingIntent.getActivity(
            c, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    android.os.Bundle metadata = new android.os.Bundle();
    metadata.putLong("source_updated", updated);
    metadata.putLong("source_user", user);
    try {
      NotificationManagerCompat.from(c)
          .notify(
              id,
              new NotificationCompat.Builder(c, CHANNEL)
                  .addExtras(metadata)
                  .setSmallIcon(R.drawable.ic_school)
                  .setContentTitle(title)
                  .setContentText(body)
                  .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                  .setContentIntent(pending)
                  .setAutoCancel(true)
                  .build());
      return true;
    } catch (SecurityException ex) {
      return false;
    }
  }

  public static boolean demo(Context c) {
    return post(
        c,
        2000000000,
        "StudyMate đã sẵn sàng",
        "Thông báo thử đã hoạt động.",
        new Intent(c, MainActivity.class));
  }
}
