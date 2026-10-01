package vn.edu.studymate;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.io.*;
import java.util.*;
import org.junit.Test;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.ui.*;

public class ReportUiTest {
  private final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
  private final Context c = ins.getTargetContext();

  private <T extends View> List<T> views(View root, Class<T> type) {
    List<T> out = new ArrayList<>();
    if (type.isInstance(root)) out.add(type.cast(root));
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int i = 0; i < g.getChildCount(); i++) out.addAll(views(g.getChildAt(i), type));
    }
    return out;
  }

  private Activity open(Class<?> type, long id) {
    Activity a =
        ins.startActivitySync(
            new Intent(c, type)
                .putExtra("kind", "task")
                .putExtra("id", id)
                .putExtra("priority", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ins.waitForIdleSync();
    return a;
  }

  private void shot(String name) throws Exception {
    Thread.sleep(650);
    ins.waitForIdleSync();
    Bitmap b = ins.getUiAutomation().takeScreenshot();
    File dir = new File(c.getExternalFilesDir(null), "report-screenshots");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }

  @Test
  public void optionalDeadlineReminderAndPriorityFlow() throws Exception {
    Session.logout(c);
    long user;
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      user =
          db.register(
              "Sinh viên báo cáo",
              "reportui" + System.currentTimeMillis() + "@test.vn",
              "Test123!");
    }
    Session.login(c, user);
    Activity editor = open(EditorActivity.class, 0);
    ins.runOnMainSync(
        () -> {
          views(editor.findViewById(R.id.fields), EditText.class)
              .get(0)
              .setText("Chuẩn bị tài liệu học tập");
          views(editor.findViewById(R.id.fields), SwitchMaterial.class).get(0).setChecked(false);
        });
    shot("01-no-deadline");
    ins.runOnMainSync(() -> editor.findViewById(R.id.save).performClick());
    ins.waitForIdleSync();
    long id;
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      List<Entry> list = db.list(user, "task");
      assertEquals(1, list.size());
      Entry e = list.get(0);
      id = e.id;
      assertEquals(0, e.dueAt);
      assertFalse(e.remindEnabled);
    }
    Activity edit = open(EditorActivity.class, id);
    ins.runOnMainSync(
        () -> {
          views(edit.findViewById(R.id.fields), SwitchMaterial.class).get(0).setChecked(true);
          List<Spinner> spinners = views(edit.findViewById(R.id.fields), Spinner.class);
          spinners.get(2).setSelection(1);
          spinners.get(spinners.size() - 1).setSelection(2);
        });
    shot("02-reminder-options");
    ins.runOnMainSync(() -> edit.findViewById(R.id.save).performClick());
    ins.waitForIdleSync();
    try (DatabaseHelper db = new DatabaseHelper(c)) {
      Entry e = db.get(user, id);
      assertTrue(e.dueAt > 0);
      assertEquals(30, e.minutesBefore);
      assertEquals("Đang làm", e.status);
      Entry free = new Entry();
      free.kind = "task";
      free.title = "Đọc tài liệu bổ sung";
      free.remindEnabled = false;
      db.save(user, free);
    }
    Activity main = open(MainActivity.class, 0);
    shot("03-dashboard");
    Activity tasks = open(TaskListActivity.class, 0);
    shot("04-priority");
    final StringBuilder texts = new StringBuilder();
    ins.runOnMainSync(
        () -> {
          for (TextView t : views(tasks.findViewById(R.id.root), TextView.class))
            texts.append(t.getText()).append('\n');
        });
    assertTrue(texts.toString().contains("Đang làm (+5)"));
    ins.runOnMainSync(()->((androidx.recyclerview.widget.RecyclerView)tasks.findViewById(R.id.list)).scrollToPosition(1));
    Thread.sleep(400);ins.waitForIdleSync();
    ins.runOnMainSync(()->{for(TextView t:views(tasks.findViewById(R.id.root),TextView.class))texts.append(t.getText()).append('\n');});
    assertTrue(texts.toString().contains("Chưa đặt deadline"));
    ins.runOnMainSync(tasks::finish);
    ins.runOnMainSync(main::finish);
    Session.logout(c);
  }
}
