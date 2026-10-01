package vn.edu.studymate.ui;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import vn.edu.studymate.R;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.logic.SmartPlanner;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.reminder.*;

public class MainActivity extends AppCompatActivity {
  private DatabaseHelper db;
  private final java.util.concurrent.ExecutorService loader =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private Map<String, List<Entry>> snapshot = new HashMap<>();
  private Map<Long, Entry> byId = new HashMap<>();
  private String displayName = "";
  private int loadVersion;
  private long user;
  private LinearLayout content;
  private MaterialToolbar toolbar;
  private String page = "home", filter = "Tất cả";
  private java.time.LocalDate calendarDate = java.time.LocalDate.now();
  private boolean week = false;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          render();
          handler.postDelayed(this, 60000);
        }
      };

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    user = Session.user(this);
    if (user < 1) {
      startActivity(new Intent(this, LoginActivity.class));
      finish();
      return;
    }
    page = getIntent().getStringExtra("page") == null ? "home" : getIntent().getStringExtra("page");
    db = new DatabaseHelper(this);
    setContentView(R.layout.activity_main);
    Ui.insets(this);
    content = findViewById(R.id.content);
    toolbar = findViewById(R.id.toolbar);
    if (b != null) {
      page = b.getString("page", "home");
      filter = b.getString("filter", "Tất cả");
    }
    toolbar
        .getMenu()
        .add("Đăng xuất")
        .setOnMenuItemClickListener(
            item -> {
              new MaterialAlertDialogBuilder(this)
                  .setTitle("Đăng xuất?")
                  .setMessage("Dữ liệu đã lưu sẽ được giữ lại trên máy.")
                  .setNegativeButton("Ở lại", null)
                  .setPositiveButton(
                      "Đăng xuất",
                      (d, w) -> {
                        Session.logout(this);
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                      })
                  .show();
              return true;
            });
    BottomNavigationView nav = findViewById(R.id.navigation);
    nav.setSelectedItemId(
        page.equals("home")
            ? R.id.nav_home
            : page.equals("task")
                ? R.id.nav_task
                : page.equals("schedule") ? R.id.nav_calendar : R.id.nav_more);
    nav.setOnItemSelectedListener(
        item -> {
          int id = item.getItemId();
          if (id == R.id.nav_task || id == R.id.nav_priority) {
            startActivity(
                new Intent(this, TaskListActivity.class)
                    .putExtra("priority", id == R.id.nav_priority));
            return false;
          }
          page =
              id == R.id.nav_home
                  ? "home"
                  : id == R.id.nav_task ? "task" : id == R.id.nav_calendar ? "schedule" : "more";
          filter = "Tất cả";
          render();
          return true;
        });
    ReminderScheduler.schedule(this);
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        && !getPreferences(0).getBoolean("asked", false)) {
      getPreferences(0).edit().putBoolean("asked", true).apply();
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 40);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (db != null) {
      if (Session.user(this) != user) {
        finish();
        return;
      }
      render();
      ReminderScheduler.checkNow(this);
      handler.postDelayed(refresh, 60000);
    }
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    loader.shutdown();
    if (db != null) db.close();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("page", page);
    b.putString("filter", filter);
  }

  private List<Entry> readEntries(String kind) {
    return new ArrayList<>(snapshot.getOrDefault(kind, Collections.emptyList()));
  }

  private void render() {
    if (isFinishing() || isDestroyed()) return;
    int version = ++loadVersion;
    loader.execute(
        () -> {
          try (DatabaseHelper source = new DatabaseHelper(getApplicationContext())) {
            Map<String, List<Entry>> data = new HashMap<>();
            Map<Long, Entry> lookup = new HashMap<>();
            for (String kind :
                new String[] {"task", "subject", "schedule", "exam", "grade", "note"}) {
              List<Entry> list = source.list(user, kind);
              data.put(kind, list);
              for (Entry e : list) lookup.put(e.id, e);
            }
            String name = source.name(user);
            runOnUiThread(
                () -> {
                  if (isDestroyed() || version != loadVersion || Session.user(this) != user) return;
                  snapshot = data;
                  byId = lookup;
                  displayName = name;
                  renderNow();
                });
          } catch (Exception ex) {
            runOnUiThread(
                () -> {
                  if (!isDestroyed())
                    Toast.makeText(this, "Không tải được dữ liệu", Toast.LENGTH_LONG).show();
                });
          }
        });
  }

  private void renderNow() {
    if (content == null) return;
    content.removeAllViews();
    toolbar.setTitle(
        page.equals("home") ? "StudyMate" : page.equals("more") ? "Tiện ích" : Ui.label(page));
    if (page.equals("home")) {
      dashboard();
      return;
    }
    if (page.equals("schedule")) {
      calendarPage();
      return;
    }
    if (page.equals("more")) {
      more();
      return;
    }
    listPage();
  }

  private void heading(String title, String sub) {
    TextView h = new TextView(this);
    h.setText(title);
    h.setTextSize(18);
    h.setTypeface(null, android.graphics.Typeface.BOLD);
    h.setTextColor(getColor(R.color.ink));
    h.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
    content.addView(h);

    if (sub != null && !sub.isEmpty()) {
      TextView s = new TextView(this);
      s.setText(sub);
      s.setTextSize(13);
      s.setTextColor(getColor(R.color.muted));
      s.setPadding(0, 0, 0, 12);
      content.addView(s);
    }
  }

  private void button(String title, Runnable click) {
    MaterialButton b = new MaterialButton(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setCornerRadius(Ui.dp(this, 12));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 4, 0, 12);
    content.addView(b, lp);
    b.setOnClickListener(v -> click.run());
  }

  private View card(String title, String body) {
    View v = getLayoutInflater().inflate(R.layout.item_card, content, false);
    ((TextView) v.findViewById(R.id.card_title)).setText(title);
    ((TextView) v.findViewById(R.id.card_body)).setText(body);
    content.addView(v);
    return v;
  }

  private void action(View card, String title, Runnable click) {
    LinearLayout row = card.findViewById(R.id.card_actions);
    MaterialButton b =
        new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
    b.setText(title);
    b.setTextSize(12);
    b.setPadding(4, 0, 4, 0);
    row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    b.setOnClickListener(v -> click.run());
  }

  private void gotoPage(String p) {
    displayNow = 0;
    page = p;
    filter = "Tất cả";
    render();
  }

  private long displayNow;

  private void dashboard() {
    long now = System.currentTimeMillis();
    List<Entry> tasks = readEntries("task"), ranked = SmartPlanner.ranked(tasks, now);
    displayNow = now;
    SmartPlanner.Stats stats = SmartPlanner.stats(tasks, now);
    String firstName = displayName.trim();
    if (firstName.contains(" ")) firstName = firstName.substring(firstName.lastIndexOf(' ') + 1);
    heading(
        "Chào " + firstName + "!",
        new java.text.SimpleDateFormat("EEEE, dd 'tháng' MM", new Locale("vi", "VN"))
            .format(new Date()));
    View progress =
        card("Tiến độ học tập", stats.completed + " / " + stats.total + " công việc đã hoàn thành");
    TextView value = progress.findViewById(R.id.card_score);
    Ui.pill(
        value,
        String.format(new Locale("vi", "VN"), "%.1f%%", stats.percent()),
        getColor(R.color.primary),
        getColor(R.color.soft_blue));
    android.widget.ProgressBar bar =
        new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(1000);
    bar.setProgress((int) (stats.percent() * 10));
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.primary)));
    bar.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(getColor(R.color.line)));
    LinearLayout inside = (LinearLayout) progress.findViewById(R.id.card_body).getParent();
    LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 8));
    barLp.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
    inside.addView(bar, 1, barLp);
    LinearLayout metrics = new LinearLayout(this);
    metrics.setOrientation(LinearLayout.HORIZONTAL);
    content.addView(metrics);
    metric(metrics, stats.near, "Gần hạn · 72 giờ", R.color.warning, R.color.soft_amber, 1);
    metric(metrics, stats.overdue, "Quá hạn", R.color.danger, R.color.soft_red, 2);
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
      View warning =
          card(
              "Bật thông báo để nhận nhắc hạn",
              "Bạn vẫn xem được cảnh báo trong app khi chưa cấp quyền.");
      action(
          warning,
          "Mở cài đặt",
          () ->
              startActivity(
                  new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                      .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())));
    }
    heading("Nên làm tiếp theo", "Công việc cần bạn chú ý trước");
    TextView all = new TextView(this);
    all.setText("Xem tất cả  →");
    all.setTextColor(getColor(R.color.primary));
    all.setMinHeight(Ui.dp(this, 48));
    all.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
    content.addView(all);
    all.setOnClickListener(
        v -> startActivity(new Intent(this, TaskListActivity.class).putExtra("priority", true)));
    if (ranked.isEmpty())
      card("Bạn đã bắt kịp kế hoạch", "Chưa có công việc đang chờ. Hãy thêm mục tiêu tiếp theo.");
    else for (int i = 0; i < Math.min(3, ranked.size()); i++) entryCard(ranked.get(i));
    button("+ Thêm công việc", () -> edit("task", 0));
    int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    int today = day == 1 ? 8 : day;
    List<Entry> schedules = readEntries("schedule");
    schedules.sort(Comparator.comparingInt(e -> e.startMinute));
    heading("Lịch học hôm nay", Ui.day(today));
    boolean any = false;
    for (Entry e : schedules)
      if (vn.edu.studymate.logic.ScheduleRules.occurs(
          e, java.time.LocalDate.now(java.time.ZoneId.of(e.zoneId)))) {
        entryCard(e);
        any = true;
      }
    if (!any) card("Không có buổi học hôm nay", "Bạn có thể dành thời gian cho tự học và ôn tập.");
    List<Entry> exams = readEntries("exam");
    heading("Lịch thi sắp tới", "Trong 7 ngày tới");
    int count = 0;
    for (Entry e : exams)
      if (e.dueAt >= now && e.dueAt <= now + 7 * 86400000L) {
        count++;
        entryCard(e);
      }
    if (count == 0) card("Chưa có kỳ thi sắp tới", "Không có kỳ thi trong 7 ngày tới.");
  }

  private void metric(LinearLayout parent, int count, String label, int fg, int bg, int kind) {
    LinearLayout tile = new LinearLayout(this);
    tile.setOrientation(LinearLayout.VERTICAL);
    tile.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
    android.graphics.drawable.GradientDrawable shape =
        new android.graphics.drawable.GradientDrawable();
    shape.setColor(getColor(bg));
    shape.setCornerRadius(Ui.dp(this, 14));
    tile.setBackground(shape);
    TextView number = new TextView(this);
    number.setText("" + count);
    number.setTextSize(26);
    number.setTypeface(null, android.graphics.Typeface.BOLD);
    number.setTextColor(getColor(fg));
    tile.addView(number);
    TextView caption = new TextView(this);
    caption.setText(label);
    caption.setTextSize(12);
    caption.setTextColor(getColor(fg));
    tile.addView(caption);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(
        kind == 2 ? Ui.dp(this, 6) : 0, 0, kind == 1 ? Ui.dp(this, 6) : 0, Ui.dp(this, 8));
    parent.addView(tile, lp);
    tile.setContentDescription(count + " " + label + ". Xem công việc");
    tile.setOnClickListener(
        v ->
            startActivity(
                new Intent(this, TaskListActivity.class)
                    .putExtra("near", kind == 1)
                    .putExtra("overdue", kind == 2)));
  }

  private void more() {
    heading("Góc học tập", "Quản lý mọi thứ phục vụ học kỳ của bạn.");
    for (String k : new String[] {"subject", "exam", "note", "grade"}) {
      View c = card(Ui.label(k), readEntries(k).size() + " mục đã lưu");
      action(c, "Mở danh sách", () -> gotoPage(k));
    }
    button(
        "Xem công việc ưu tiên",
        () -> startActivity(new Intent(this, TaskListActivity.class).putExtra("priority", true)));
    button("Cài đặt nhắc nhở", () -> startActivity(new Intent(this, SettingsActivity.class)));
    card("Quy tắc ưu tiên tự động", SmartPlanner.RULES);
    button(
        "Gửi thông báo thử",
        () -> {
          if (ReminderScheduler.demo(this))
            Toast.makeText(this, "Đã gửi thông báo thử", Toast.LENGTH_SHORT).show();
          else
            Toast.makeText(this, "Hãy bật quyền thông báo trong cài đặt", Toast.LENGTH_LONG).show();
        });
    card(
        "Về StudyMate",
        "Đồ án: Xây dựng ứng dụng hỗ trợ quản lý học tập và ưu tiên công việc của sinh viên trên"
            + " nền tảng Android.\n"
            + "Java • XML • SQLite • Rule-based\n"
            + "Tài khoản và dữ liệu chỉ tồn tại trên thiết bị này.");
  }

  private void listPage() {
    if (page.equals("all_schedules")) {
      heading("Tất cả lịch học", "Bao gồm lịch chưa bắt đầu hoặc đã kết thúc.");
      button("+ Thêm lịch học", () -> edit("schedule", 0));
      for (Entry e : readEntries("schedule")) entryCard(e);
      return;
    }
    heading(
        Ui.label(page),
        page.equals("schedule")
            ? "Lịch lặp lại mỗi tuần, theo giờ địa phương."
            : page.equals("grade")
                ? "Thang điểm 10. Trung bình theo trọng số các cột đã nhập."
                : "Thêm, sửa và theo dõi thông tin học tập.");
    String kind = page;
    button("+ Thêm " + Ui.label(kind).toLowerCase(Locale.ROOT), () -> edit(kind, 0));
    List<Entry> entries = readEntries(page);
    if (page.equals("task")) {
      button(
          "Lọc: " + filter,
          () -> {
            String[] choices = {
              "Tất cả", "Chưa làm", "Đang làm", "Hoàn thành", "Quá hạn", "Trong 24 giờ"
            };
            new MaterialAlertDialogBuilder(this)
                .setTitle("Lọc công việc")
                .setItems(
                    choices,
                    (d, w) -> {
                      filter = choices[w];
                      render();
                    })
                .show();
          });
      long now = System.currentTimeMillis();
      entries.sort(
          Comparator.<Entry>comparingInt(e -> SmartPlanner.score(e, now))
              .reversed()
              .thenComparingLong(e -> e.dueAt));
      entries.removeIf(
          e ->
              !filter.equals("Tất cả")
                  && (filter.equals("Quá hạn")
                      ? SmartPlanner.finished(e) || e.dueAt >= now
                      : filter.equals("Trong 24 giờ")
                          ? SmartPlanner.finished(e) || e.dueAt < now || e.dueAt > now + 86400000L
                          : !e.status.equals(filter)));
    }
    if (page.equals("schedule"))
      entries.sort(Comparator.<Entry>comparingInt(e -> e.day).thenComparingInt(e -> e.startMinute));
    if (page.equals("grade")) {
      double overall = 0, credits = 0;
      for (Entry s : readEntries("subject")) {
        List<Entry> related = new ArrayList<>();
        for (Entry e : entries) if (e.subjectId == s.id) related.add(e);
        double avg = SmartPlanner.average(related);
        if (!Double.isNaN(avg)) {
          overall += avg * s.credits;
          credits += s.credits;
          card(
              s.title,
              String.format(
                  Locale.getDefault(), "Trung bình: %.2f / 10 • %.0f tín chỉ", avg, s.credits));
        }
      }
      card(
          "Trung bình học tập",
          credits == 0
              ? "Chưa có điểm gắn với môn học."
              : String.format(
                  Locale.getDefault(),
                  "%.2f / 10 (theo tín chỉ các môn đã có điểm)\n"
                      + "Đây là trung bình tạm tính, không quy đổi GPA thang 4.",
                  overall / credits));
    }
    if (entries.isEmpty()) card("Chưa có dữ liệu phù hợp", "Nhấn nút Thêm để bắt đầu.");
    for (Entry e : entries) entryCard(e);
  }

  private void entryCard(Entry e) {
    String subject = "";
    if (e.subjectId > 0) {
      Entry s = byId.get(e.subjectId);
      if (s != null) subject = s.title + "\n";
    }
    String body = subject;
    switch (e.kind) {
      case "subject":
        body +=
            e.code
                + " • "
                + e.lecturer
                + "\n"
                + String.format(Locale.getDefault(), "%.0f tín chỉ", e.credits);
        break;
      case "task":
        body +=
            e.status
                + " • "
                + Ui.date(e.dueAt)
                + "\n"
                + SmartPlanner.reason(e, displayNow > 0 ? displayNow : System.currentTimeMillis());
        break;
      case "exam":
        body +=
            Ui.date(e.dueAt)
                + " • "
                + e.place
                + "\n"
                + SmartPlanner.remaining(e.dueAt, System.currentTimeMillis());
        break;
      case "schedule":
        body +=
            Ui.day(e.day)
                + " • "
                + Ui.time(e.startMinute)
                + " – "
                + Ui.time(e.endMinute)
                + "\n"
                + e.place;
        break;
      case "grade":
        body += String.format(Locale.getDefault(), "%.2f / 10 • Trọng số %.2f", e.score, e.weight);
        break;
    }
    if (e.kind.equals("schedule")) body += "\n" + e.validFrom + " → " + e.validTo;
    if (e.kind.equals("exam")) body += "\nKết thúc: " + Ui.date(e.endAt);
    if (!e.detail.isEmpty()) body += "\n" + e.detail;
    View c = card(e.title, body.trim());
    if (e.kind.equals("task"))
      Ui.bindTask(c, e, subject, displayNow > 0 ? displayNow : System.currentTimeMillis(), 0);
    action(c, "Sửa", () -> edit(e.kind, e.id));
    if (e.kind.equals("task") && !SmartPlanner.finished(e))
      action(
          c,
          "Xong",
          () -> {
            e.status = "Hoàn thành";
            db.save(user, e);
            ReminderScheduler.checkNow(this);
            ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .cancel((int) e.id);
            render();
          });
    action(
        c,
        "Xóa",
        () ->
            new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa " + e.title + "?")
                .setMessage(
                    e.kind.equals("subject")
                        ? "Chỉ xóa được môn không còn dữ liệu liên quan. Hãy chuyển liên kết trước"
                            + " nếu cần."
                        : "Không thể hoàn tác thao tác này.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton(
                    "Xóa",
                    (d, w) -> {
                      try {
                        db.delete(user, e.id);
                      } catch (Exception ex) {
                        new MaterialAlertDialogBuilder(this)
                            .setTitle("Chưa thể xóa")
                            .setMessage(ex.getMessage())
                            .setPositiveButton("Đã hiểu", null)
                            .show();
                        return;
                      }
                      ReminderScheduler.checkNow(this);
                      ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                          .cancel((int) e.id);
                      render();
                    })
                .show());
  }

  private void calendarPage() {
    heading(
        "Lịch học và lịch thi", week ? "Tuần chứa ngày " + calendarDate : "Ngày " + calendarDate);
    button(
        "Chọn ngày",
        () ->
            new android.app.DatePickerDialog(
                    this,
                    (v, y, m, d) -> {
                      calendarDate = java.time.LocalDate.of(y, m + 1, d);
                      render();
                    },
                    calendarDate.getYear(),
                    calendarDate.getMonthValue() - 1,
                    calendarDate.getDayOfMonth())
                .show());
    button(
        week ? "Đổi sang xem ngày" : "Đổi sang xem tuần",
        () -> {
          week = !week;
          render();
        });
    button("+ Thêm lịch học", () -> edit("schedule", 0));
    button("+ Thêm lịch thi", () -> edit("exam", 0));
    button(
        "Quản lý tất cả lịch học",
        () -> {
          page = "all_schedules";
          render();
        });
    java.time.LocalDate first =
        week
            ? calendarDate.with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            : calendarDate;
    List<Entry> schedules = readEntries("schedule"), exams = readEntries("exam");
    for (int i = 0; i < (week ? 7 : 1); i++) {
      java.time.LocalDate date = first.plusDays(i);
      List<Entry> events = new ArrayList<>();
      for (Entry e : schedules)
        if (vn.edu.studymate.logic.ScheduleRules.occurs(e, date)) events.add(e);
      for (Entry e : exams)
        if (java.time.Instant.ofEpochMilli(e.dueAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .equals(date)) events.add(e);
      events.sort(
          Comparator.comparingLong(
              e ->
                  e.kind.equals("exam")
                      ? e.dueAt
                      : vn.edu.studymate.logic.ScheduleRules.at(e, date, e.startMinute)));
      heading(Ui.day(date.getDayOfWeek().getValue() + 1), date.toString());
      if (events.isEmpty()) card("Không có lịch", "Chưa có buổi học hoặc kỳ thi trong ngày này.");
      for (Entry e : events) entryCard(e);
    }
  }

  private void edit(String kind, long id) {
    startActivity(new Intent(this, EditorActivity.class).putExtra("kind", kind).putExtra("id", id));
  }
}
