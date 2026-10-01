package vn.edu.studymate.ui;

import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.studymate.R;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.logic.SmartPlanner;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.reminder.ReminderScheduler;

public class TaskListActivity extends AppCompatActivity {
  private long user, subjectId = 0;
  private boolean priorityOnly;
  private boolean nearOnly;
  private long evaluatedAt;
  private int state = 0, group = 0, generation = 0;
  private String query = "";
  private boolean active;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private List<Entry> subjects = new ArrayList<>(), shown = new ArrayList<>();
  private Map<Long, String> names = new HashMap<>();
  private Rows adapter;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          load();
          handler.postDelayed(this, 60000);
        }
      };

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    user = Session.user(this);
    if (user < 1) {
      finish();
      return;
    }
    priorityOnly = getIntent().getBooleanExtra("priority", false);
    nearOnly = getIntent().getBooleanExtra("near", false);
    if (getIntent().getBooleanExtra("overdue", false)) group = 1;
    if (b != null) {
      nearOnly = b.getBoolean("near");
      priorityOnly = b.getBoolean("priority", priorityOnly);
      state = b.getInt("state");
      group = b.getInt("group");
      subjectId = b.getLong("subject");
      query = b.getString("query", "");
    }
    setContentView(R.layout.activity_tasks);
    Ui.insets(this);
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setTitle(priorityOnly ? "Công việc ưu tiên" : "Quản lý công việc");
    toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    toolbar.setNavigationOnClickListener(v -> finish());
    toolbar
        .getMenu()
        .add("Cách xếp ưu tiên")
        .setOnMenuItemClickListener(
            i -> {
              new MaterialAlertDialogBuilder(this)
                  .setTitle("Quy tắc ưu tiên")
                  .setMessage(SmartPlanner.RULES)
                  .setPositiveButton("Đã hiểu", null)
                  .show();
              return true;
            });
    RecyclerView list = findViewById(R.id.list);
    list.setLayoutManager(new LinearLayoutManager(this));
    adapter = new Rows();
    list.setAdapter(adapter);
    EditText search = findViewById(R.id.search);
    search.setText(query);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            query = s.toString();
            load();
          }

          public void afterTextChanged(Editable e) {}
        });
    findViewById(R.id.filters).setOnClickListener(v -> filters());
    findViewById(R.id.filter_clear)
        .setOnClickListener(
            v -> {
              nearOnly = false;
              state = 0;
              group = 0;
              subjectId = 0;
              ((EditText) findViewById(R.id.search)).setText("");
              load();
            });
    findViewById(R.id.filter_subject)
        .setOnClickListener(
            v -> {
              String[] options = new String[subjects.size() + 1];
              options[0] = "Tất cả môn";
              for (int i = 0; i < subjects.size(); i++) options[i + 1] = subjects.get(i).title;
              new MaterialAlertDialogBuilder(this)
                  .setTitle("Chọn môn học")
                  .setItems(
                      options,
                      (d, n) -> {
                        subjectId = n == 0 ? 0 : subjects.get(n - 1).id;
                        load();
                      })
                  .show();
            });
    com.google.android.material.bottomnavigation.BottomNavigationView nav =
        findViewById(R.id.navigation);
    nav.setSelectedItemId(priorityOnly ? R.id.nav_priority : R.id.nav_task);
    nav.setOnItemSelectedListener(
        item -> {
          int id = item.getItemId();
          if (id == R.id.nav_task || id == R.id.nav_priority) {
            priorityOnly = id == R.id.nav_priority;
            nearOnly = false;
            state = 0;
            group = 0;
            toolbar.setTitle(priorityOnly ? "Công việc ưu tiên" : "Quản lý công việc");
            load();
            return true;
          }
          startActivity(
              new Intent(this, MainActivity.class)
                  .putExtra(
                      "page",
                      id == R.id.nav_home ? "home" : id == R.id.nav_calendar ? "schedule" : "more")
                  .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
          finish();
          return true;
        });
    findViewById(R.id.add).setOnClickListener(v -> edit(0));
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (Session.user(this) != user) {
      finish();
      return;
    }
    active = true;
    load();
    handler.postDelayed(tick, 60000);
  }

  @Override
  protected void onPause() {
    active = false;
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    executor.shutdown();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putBoolean("near", nearOnly);
    b.putBoolean("priority", priorityOnly);
    b.putInt("state", state);
    b.putInt("group", group);
    b.putLong("subject", subjectId);
    b.putString("query", query);
  }

  private void edit(long id) {
    startActivity(
        new Intent(this, EditorActivity.class).putExtra("kind", "task").putExtra("id", id));
  }

  private void load() {
    if (!active || isFinishing()) return;
    int token = ++generation;
    String q = query.trim().toLowerCase(Locale.ROOT);
    int st = state, gr = group;
    boolean onlyNear = nearOnly;
    long sub = subjectId, now = System.currentTimeMillis();
    executor.execute(
        () -> {
          try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
            List<Entry> items = db.list(user, "task"), ss = db.list(user, "subject");
            Map<Long, String> nn = new HashMap<>();
            for (Entry e : ss) nn.put(e.id, e.title);
            items.removeIf(
                e ->
                    (priorityOnly && SmartPlanner.finished(e))
                        || (onlyNear
                            && (SmartPlanner.finished(e)
                                || SmartPlanner.group(e.dueAt, now) < 1
                                || SmartPlanner.group(e.dueAt, now) > 2))
                        || (sub > 0 && e.subjectId != sub)
                        || (st > 0
                            && !e.status.equals(
                                new String[] {"", "Chưa làm", "Đang làm", "Hoàn thành"}[st]))
                        || (gr > 0
                            && (SmartPlanner.finished(e)
                                || SmartPlanner.group(e.dueAt, now) != gr - 1))
                        || !(e.title + " " + e.detail).toLowerCase(Locale.ROOT).contains(q));
            items.sort(SmartPlanner.comparator(now));
            runOnUiThread(
                () -> {
                  if (isDestroyed() || token != generation || Session.user(this) != user) return;
                  MaterialButton clear = findViewById(R.id.filter_clear);
                  clear.setBackgroundTintList(
                      android.content.res.ColorStateList.valueOf(
                          getColor(
                              st + gr + (sub > 0 ? 1 : 0) + (onlyNear ? 1 : 0) == 0
                                  ? R.color.soft_blue
                                  : android.R.color.white)));
                  subjects = ss;
                  names = nn;
                  shown = items;
                  evaluatedAt = now;
                  adapter.notifyDataSetChanged();
                  ((TextView) findViewById(R.id.result))
                      .setText(
                          items.isEmpty()
                              ? "Không có công việc phù hợp."
                              : items.size()
                                  + " công việc • "
                                  + (priorityOnly
                                      ? "đã loại việc hoàn thành"
                                      : "xếp theo ưu tiên"));
                  ((MaterialButton) findViewById(R.id.filters))
                      .setText(
                          st + gr + (sub > 0 ? 1 : 0) + (onlyNear ? 1 : 0) == 0
                              ? "Bộ lọc"
                              : "Đang lọc");
                });
          } catch (Exception ex) {
            runOnUiThread(
                () -> {
                  if (!isDestroyed())
                    ((TextView) findViewById(R.id.result))
                        .setText("Không tải được dữ liệu. Hãy mở lại màn hình.");
                });
          }
        });
  }

  private void filters() {
    new MaterialAlertDialogBuilder(this)
        .setTitle("Lọc công việc")
        .setItems(
            new String[] {
              "Theo môn học", "Theo trạng thái", "Theo nhóm deadline", "Xóa tất cả bộ lọc"
            },
            (d, w) -> {
              if (w == 3) {
                nearOnly = false;
                state = 0;
                group = 0;
                subjectId = 0;
                ((EditText) findViewById(R.id.search)).setText("");
                load();
                return;
              }
              if (w == 0) {
                String[] choices = new String[subjects.size() + 1];
                choices[0] = "Tất cả môn";
                for (int i = 0; i < subjects.size(); i++) choices[i + 1] = subjects.get(i).title;
                new MaterialAlertDialogBuilder(this)
                    .setItems(
                        choices,
                        (dd, n) -> {
                          subjectId = n == 0 ? 0 : subjects.get(n - 1).id;
                          load();
                        })
                    .show();
              }
              if (w == 1)
                new MaterialAlertDialogBuilder(this)
                    .setItems(
                        priorityOnly
                            ? new String[] {"Tất cả chưa hoàn thành", "Chưa làm", "Đang làm"}
                            : new String[] {"Tất cả", "Chưa làm", "Đang làm", "Hoàn thành"},
                        (dd, n) -> {
                          state = n;
                          load();
                        })
                    .show();
              if (w == 2)
                new MaterialAlertDialogBuilder(this)
                    .setItems(
                        new String[] {
                          "Tất cả",
                          "Quá hạn",
                          "Rất gần hạn (≤24 giờ)",
                          "Sắp đến hạn (24–72 giờ)",
                          "Còn thời gian (>72 giờ)",
                          "Chưa đặt deadline"
                        },
                        (dd, n) -> {
                          group = n;
                          load();
                        })
                    .show();
            })
        .show();
  }

  private void change(Entry e, boolean delete) {
    executor.execute(
        () -> {
          try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
            if (delete) db.delete(user, e.id);
            else {
              e.status = SmartPlanner.finished(e) ? "Chưa làm" : "Hoàn thành";
              db.save(user, e);
            }
          } catch (Exception ex) {
            runOnUiThread(
                () -> {
                  if (!isDestroyed())
                    new MaterialAlertDialogBuilder(this)
                        .setMessage("Không thể cập nhật: " + ex.getMessage())
                        .setPositiveButton("Đã hiểu", null)
                        .show();
                });
            return;
          }
          runOnUiThread(
              () -> {
                ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .cancel((int) e.id);
                ReminderScheduler.checkNow(this);
                load();
              });
        });
  }

  private class Rows extends RecyclerView.Adapter<Holder> {
    public Holder onCreateViewHolder(ViewGroup parent, int type) {
      return new Holder(getLayoutInflater().inflate(R.layout.item_card, parent, false));
    }

    public int getItemCount() {
      return shown.size();
    }

    public void onBindViewHolder(Holder h, int i) {
      Entry e = shown.get(i);
      Ui.bindTask(
          h.itemView,
          e,
          names.getOrDefault(e.subjectId, "Cá nhân"),
          evaluatedAt,
          priorityOnly ? i + 1 : 0);
      LinearLayout row = h.itemView.findViewById(R.id.card_actions);
      row.removeAllViews();
      action(row, "Sửa", () -> edit(e.id));
      action(row, SmartPlanner.finished(e) ? "Mở lại" : "Xong", () -> change(e, false));
      action(
          row,
          "Xóa",
          () ->
              new MaterialAlertDialogBuilder(TaskListActivity.this)
                  .setTitle("Xóa công việc?")
                  .setMessage(e.title)
                  .setNegativeButton("Hủy", null)
                  .setPositiveButton("Xóa", (d, w) -> change(e, true))
                  .show());
    }
  }

  private static class Holder extends RecyclerView.ViewHolder {
    Holder(View v) {
      super(v);
    }
  }

  private void action(LinearLayout row, String label, Runnable run) {
    MaterialButton b =
        new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
    b.setText(label);
    row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    b.setOnClickListener(v -> run.run());
  }
}
