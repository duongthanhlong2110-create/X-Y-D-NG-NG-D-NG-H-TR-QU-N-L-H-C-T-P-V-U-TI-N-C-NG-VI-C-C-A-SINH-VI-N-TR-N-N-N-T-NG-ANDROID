package vn.edu.studymate;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.reminder.ReminderScheduler;
import vn.edu.studymate.ui.EditorActivity;
import vn.edu.studymate.ui.LoginActivity;
import vn.edu.studymate.ui.MainActivity;
import vn.edu.studymate.ui.SettingsActivity;
import vn.edu.studymate.ui.TaskListActivity;

/** Run on a test emulator. Creates a unique account and exercises actual Activity layouts. */
public class UiSmokeTest {
  private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
  private final Context context = ins.getTargetContext();

  @Test
  public void registrationNavigationEditorsAndNotification() throws Exception {
    Session.logout(context);
    if (Build.VERSION.SDK_INT >= 33) {
      ins.getUiAutomation()
          .executeShellCommand(
              "pm grant " + context.getPackageName() + " android.permission.POST_NOTIFICATIONS")
          .close();
    }
    Activity login =
        ins.startActivitySync(
            new Intent(context, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    screenshot("01-login");
    Instrumentation.ActivityMonitor monitor =
        ins.addMonitor(MainActivity.class.getName(), null, false);
    ins.runOnMainSync(
        () -> {
          login.findViewById(R.id.toggle).performClick();
          ((EditText) login.findViewById(R.id.name)).setText("Sinh viên kiểm thử");
          ((EditText) login.findViewById(R.id.email))
              .setText("ui" + System.currentTimeMillis() + "@test.vn");
          ((EditText) login.findViewById(R.id.password)).setText("Test123!");
          ((EditText) login.findViewById(R.id.confirm)).setText("Test123!");
          login.findViewById(R.id.submit).performClick();
        });
    Activity main = ins.waitForMonitorWithTimeout(monitor, 15000);
    ins.removeMonitor(monitor);
    assertNotNull("Registration should open dashboard", main);
    ins.waitForIdleSync();
    long user = Session.user(context);
    assertTrue(user > 0);
    for (String kind : new String[] {"subject", "task", "schedule", "exam", "note", "grade"}) {
      Activity editor =
          ins.startActivitySync(
              new Intent(context, EditorActivity.class)
                  .putExtra("kind", kind)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ins.waitForIdleSync();
      ins.runOnMainSync(
          () -> {
            List<EditText> inputs = new ArrayList<>();
            collect(editor.findViewById(R.id.fields), EditText.class, inputs);
            inputs.get(0).setText("Mục kiểm thử " + kind);
            if (kind.equals("grade")) {
              List<Spinner> spinners = new ArrayList<>();
              collect(editor.findViewById(R.id.fields), Spinner.class, spinners);
              spinners.get(0).setSelection(1);
              inputs.get(1).setText("8.5");
              inputs.get(2).setText("30");
            }
          });
      screenshot("editor-" + kind);
      ins.runOnMainSync(() -> editor.findViewById(R.id.save).performClick());
      ins.waitForIdleSync();
      try (DatabaseHelper db = new DatabaseHelper(context)) {
        assertEquals("Saved " + kind, 1, db.list(user, kind).size());
      }
      ins.runOnMainSync(editor::finish);
    }
    ins.runOnMainSync(
        () ->
            ((BottomNavigationView) main.findViewById(R.id.navigation))
                .setSelectedItemId(R.id.nav_home));
    ins.waitForIdleSync();
    screenshot("02-dashboard");
    for (int nav : new int[] {R.id.nav_calendar, R.id.nav_more}) {
      ins.runOnMainSync(
          () -> ((BottomNavigationView) main.findViewById(R.id.navigation)).setSelectedItemId(nav));
      ins.waitForIdleSync();
      assertNotNull(main.findViewById(R.id.content));
    }
    screenshot("03-utilities");
    Activity tasks =
        ins.startActivitySync(
            new Intent(context, TaskListActivity.class)
                .putExtra("priority", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ins.waitForIdleSync();
    Thread.sleep(700);
    screenshot("04-priority");
    ins.runOnMainSync(() -> ((EditText) tasks.findViewById(R.id.search)).setText("not-found-xyz"));
    Thread.sleep(700);
    ins.waitForIdleSync();
    assertTrue(
        ((TextView) tasks.findViewById(R.id.result)).getText().toString().contains("Không có"));
    ins.runOnMainSync(tasks::finish);
    Activity settings =
        ins.startActivitySync(
            new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ins.waitForIdleSync();
    screenshot("05-settings");
    ins.runOnMainSync(settings::finish);

    assertTrue("Notification permission and posting", ReminderScheduler.demo(context));
    ins.runOnMainSync(main::finish);
    Session.logout(context);
  }

  private <T extends View> void collect(View root, Class<T> type, List<T> list) {
    if (type.isInstance(root)) list.add(type.cast(root));
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), type, list);
    }
  }

  private void screenshot(String name) throws Exception {
    ins.waitForIdleSync();
    Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
    if (bitmap == null) return;
    File directory = new File(context.getExternalFilesDir(null), "screenshots");
    directory.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }
}
