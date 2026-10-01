package vn.edu.studymate.reminder;

import android.content.*;

public class ReminderReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    if (intent == null) return;
    String action = intent.getAction();
    if (!"vn.edu.studymate.REMIND".equals(action)
        && !Intent.ACTION_BOOT_COMPLETED.equals(action)
        && !Intent.ACTION_TIME_CHANGED.equals(action)
        && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
        && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
    PendingResult result = goAsync();
    Context app = c.getApplicationContext();
    new Thread(
            () -> {
              try {
                ReminderScheduler.schedule(app);
                ReminderScheduler.check(app);
              } finally {
                result.finish();
              }
            },
            "restore-reminders")
        .start();
  }
}
