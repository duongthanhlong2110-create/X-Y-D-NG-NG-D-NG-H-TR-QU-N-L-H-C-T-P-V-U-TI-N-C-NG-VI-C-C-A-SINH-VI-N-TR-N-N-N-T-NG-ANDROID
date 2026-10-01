package vn.edu.studymate.auth;

import android.content.Context;

public final class Session {
  public static long user(Context c) {
    return c.getSharedPreferences("session", 0).getLong("user", -1);
  }

  public static void login(Context c, long id) {
    c.getSharedPreferences("session", 0).edit().putLong("user", id).apply();
  }

  public static void logout(Context c) {
    c.getSharedPreferences("session", 0).edit().clear().apply();
    vn.edu.studymate.reminder.ReminderScheduler.cancel(c);
    ((android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
        .cancelAll();
  }
}
