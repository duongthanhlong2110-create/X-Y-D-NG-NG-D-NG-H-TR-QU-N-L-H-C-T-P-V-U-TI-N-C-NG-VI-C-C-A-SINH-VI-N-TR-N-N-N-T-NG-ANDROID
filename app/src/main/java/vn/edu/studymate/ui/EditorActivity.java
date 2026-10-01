package vn.edu.studymate.ui;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import java.util.*;
import vn.edu.studymate.R;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.reminder.ReminderScheduler;

public class EditorActivity extends AppCompatActivity {
  private DatabaseHelper db;
  private long user;
  private Entry e;
  private LinearLayout fields;
  private EditText title, detail, place, score, weight, credits;
  private Spinner subject, status, priority, day;
  private List<Entry> subjects;
  private MaterialButton due, start, end, validFrom, validTo, examEnd;
  private EditText code, lecturer;
  private Spinner reminderChoice;
  private MaterialButton extraReminders;
  private ArrayList<Integer> extraOffsets = new ArrayList<>();
  private com.google.android.material.switchmaterial.SwitchMaterial hasDeadline;
  private int viewId = 1000;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    user = Session.user(this);
    if (user < 1
        || (getIntent().hasExtra("user") && getIntent().getLongExtra("user", -1) != user)) {
      startActivity(new Intent(this, LoginActivity.class));
      finish();
      return;
    }
    db = new DatabaseHelper(this);
    long id = getIntent().getLongExtra("id", 0);
    e = id > 0 ? db.get(user, id) : new Entry();
    if (e != null && id > 0) {
      extraOffsets.addAll(db.reminderOffsets(user, id, e.kind));
      if (!extraOffsets.isEmpty()) extraOffsets.remove(0);
    }
    if (b != null && b.containsKey("extraOffsets"))
      extraOffsets = b.getIntegerArrayList("extraOffsets");
    if (e == null) {
      finish();
      return;
    }
    if (id == 0) {
      e.kind = getIntent().getStringExtra("kind");
      e.dueAt = System.currentTimeMillis() + 86400000L;
      e.endAt = e.dueAt + 3600000L;
      e.validFrom = java.time.LocalDate.now().toString();
      e.validTo = java.time.LocalDate.now().plusMonths(4).toString();
    }
    if (!Arrays.asList("subject", "task", "schedule", "exam", "note", "grade").contains(e.kind)) {
      finish();
      return;
    }
    if (b != null) {
      e.validFrom = b.getString("validFrom", e.validFrom);
      e.validTo = b.getString("validTo", e.validTo);
      e.endAt = b.getLong("examEnd", e.endAt);
      e.dueAt = b.getLong("due", e.dueAt);
      e.startMinute = b.getInt("start", e.startMinute);
      e.endMinute = b.getInt("end", e.endMinute);
    }
    setContentView(R.layout.activity_editor);
    Ui.insets(this);
    fields = findViewById(R.id.fields);
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setTitle((id > 0 ? "Sửa " : "Thêm ") + Ui.label(e.kind));
    toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    toolbar.setNavigationOnClickListener(v -> finish());
    title =
        text(
            e.kind.equals("task") ? "Tên công việc *" : "Tên / tiêu đề *",
            e.title,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    if (!e.kind.equals("subject")) {
      subjects = db.list(user, "subject");
      String[] names = new String[subjects.size() + 1];
      names[0] = "Không gắn môn học";
      int selected = 0;
      for (int i = 0; i < subjects.size(); i++) {
        names[i + 1] = subjects.get(i).title;
        if (subjects.get(i).id == e.subjectId) selected = i + 1;
      }
      subject = spinner("Môn học" + (e.kind.equals("grade") ? " *" : ""), names, selected);
    }
    if (e.kind.equals("subject"))
      credits = text("Số tín chỉ (1–30) *", "" + (int) e.credits, InputType.TYPE_CLASS_NUMBER);
    if (e.kind.equals("subject")) {
      code = text("Mã môn (không trùng trong tài khoản)", e.code, InputType.TYPE_CLASS_TEXT);
      lecturer = text("Giảng viên", e.lecturer, InputType.TYPE_CLASS_TEXT);
    }
    if (e.kind.equals("task")) {
      hasDeadline = new com.google.android.material.switchmaterial.SwitchMaterial(this);
      hasDeadline.setId(viewId++);
      hasDeadline.setText("Có deadline");
      hasDeadline.setChecked(e.dueAt > 0);
      fields.addView(hasDeadline);
      hasDeadline.setOnCheckedChangeListener(
          (v, checked) -> {
            e.dueAt =
                checked ? (e.dueAt > 0 ? e.dueAt : System.currentTimeMillis() + 86400000L) : 0;
            if (reminderChoice != null) reminderChoice.setEnabled(checked);
            if (extraReminders != null) extraReminders.setEnabled(checked);
            refreshTimes();
          });
    }
    if (e.kind.equals("task") || e.kind.equals("exam")) {
      due = button("", () -> pickDate());
      refreshTimes();
    }
    if (e.kind.equals("exam")) {
      examEnd = button("", () -> pickExamEnd());
      refreshTimes();
    }
    if (e.kind.equals("task")) {
      priority = spinner("Mức ưu tiên", new String[] {"Thấp", "Vừa", "Cao"}, e.priority - 1);
      status =
          spinner(
              "Trạng thái",
              new String[] {"Chưa làm", "Đang làm", "Hoàn thành"},
              e.status.equals("Đang làm") ? 1 : e.status.equals("Hoàn thành") ? 2 : 0);
    }
    if (e.kind.equals("schedule")) {
      day =
          spinner(
              "Ngày trong tuần",
              new String[] {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"},
              e.day - 2);
      validFrom = button("", () -> pickRange(true));
      validTo = button("", () -> pickRange(false));
      start = button("", () -> pickTime(true));
      end = button("", () -> pickTime(false));
      refreshTimes();
    }
    if (e.kind.equals("exam") || e.kind.equals("schedule"))
      place = text("Phòng / địa điểm", e.place, InputType.TYPE_CLASS_TEXT);
    if (e.kind.equals("grade")) {
      score =
          text(
              "Điểm (0–10) *",
              "" + e.score,
              InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
      weight =
          text(
              "Trọng số (>0, ví dụ 30 và 70) *",
              "" + e.weight,
              InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }
    if (java.util.Arrays.asList("task", "schedule", "exam").contains(e.kind)) {
      int reminderValue = e.remindEnabled ? e.minutesBefore : -1;
      int reminderIndex = vn.edu.studymate.logic.ReminderOptions.index(reminderValue);
      String[] choices = vn.edu.studymate.logic.ReminderOptions.LABELS;
      if (reminderIndex < 0) {
        choices = java.util.Arrays.copyOf(choices, choices.length + 1);
        choices[choices.length - 1] = "Cũ: " + e.minutesBefore + " phút — hãy chọn lại";
        reminderIndex = choices.length - 1;
      }
      reminderChoice = spinner("Mốc nhắc chính", choices, reminderIndex);
      extraReminders = button("", () -> chooseExtraReminders());
      updateExtraLabel();
      extraReminders.setEnabled(!e.kind.equals("task") || e.dueAt > 0);
      reminderChoice.setEnabled(!e.kind.equals("task") || e.dueAt > 0);
      TextView note = new TextView(this);
      note.setText(
          "Chọn Không nhắc ở mốc chính để tắt mọi mốc. Các mốc trùng nhau chỉ nhắc một lần. Không"
              + " có deadline thì không nhắc. Nhắc có thể trễ do hệ thống Android. Múi giờ: "
              + e.zoneId);
      fields.addView(note);
    }
    detail =
        text(
            e.kind.equals("note") ? "Nội dung ghi chú" : "Mô tả / ghi chú",
            e.detail,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    detail.setMinLines(2);
    ((MaterialButton) findViewById(R.id.save))
        .setText(e.kind.equals("task") ? "Lưu công việc" : "Lưu thông tin");
    findViewById(R.id.save).setOnClickListener(v -> save());
  }

  private void updateExtraLabel() {
    extraReminders.setText("Mốc nhắc bổ sung (" + extraOffsets.size() + ")");
  }

  private void chooseExtraReminders() {
    int[] options = vn.edu.studymate.logic.ReminderOptions.MINUTES;
    String[] labels =
        Arrays.copyOfRange(vn.edu.studymate.logic.ReminderOptions.LABELS, 1, options.length);
    boolean[] selected = new boolean[labels.length];
    for (int i = 0; i < selected.length; i++) selected[i] = extraOffsets.contains(options[i + 1]);
    new MaterialAlertDialogBuilder(this)
        .setTitle("Chọn thêm mốc nhắc")
        .setMultiChoiceItems(labels, selected, (d, i, on) -> selected[i] = on)
        .setPositiveButton(
            "Áp dụng",
            (d, w) -> {
              extraOffsets.clear();
              for (int i = 0; i < selected.length; i++)
                if (selected[i]) extraOffsets.add(options[i + 1]);
              updateExtraLabel();
            })
        .setNegativeButton("Hủy", null)
        .show();
  }

  private EditText text(String hint, String value, int type) {
    TextInputLayout box =
        (TextInputLayout) getLayoutInflater().inflate(R.layout.field_text, fields, false);
    box.setHint(hint);
    EditText input = box.findViewById(R.id.field_input);
    input.setId(viewId++);
    input.setInputType(type);
    input.setText(value);
    fields.addView(box);
    return input;
  }

  private Spinner spinner(String label, String[] choices, int selected) {
    TextView l = new TextView(this);
    l.setText(label);
    l.setTextSize(12);
    l.setTextColor(getColor(R.color.muted));
    l.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
    fields.addView(l);
    Spinner s = new Spinner(this);
    s.setBackgroundResource(R.drawable.field_surface);
    s.setId(viewId++);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
    s.setAdapter(adapter);
    s.setSelection(Math.max(0, selected));
    fields.addView(
        s,
        new LinearLayout.LayoutParams(
            -1, 48 * getResources().getDisplayMetrics().densityDpi / 160));
    return s;
  }

  private MaterialButton button(String label, Runnable action) {
    MaterialButton b =
        new MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setCornerRadius(Ui.dp(this, 12));
    b.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
    b.setText(label);
    fields.addView(b, new LinearLayout.LayoutParams(-1, -2));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void pickDate() {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(e.dueAt);
    new DatePickerDialog(
            this,
            (v, y, m, d) -> {
              Calendar selected = Calendar.getInstance();
              selected.setTimeInMillis(e.dueAt);
              selected.set(y, m, d);
              new TimePickerDialog(
                      this,
                      (t, h, min) -> {
                        selected.set(Calendar.HOUR_OF_DAY, h);
                        selected.set(Calendar.MINUTE, min);
                        selected.set(Calendar.SECOND, 0);
                        selected.set(Calendar.MILLISECOND, 0);
                        e.dueAt = selected.getTimeInMillis();
                        refreshTimes();
                      },
                      c.get(Calendar.HOUR_OF_DAY),
                      c.get(Calendar.MINUTE),
                      true)
                  .show();
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH))
        .show();
  }

  private void pickTime(boolean first) {
    int value = first ? e.startMinute : e.endMinute;
    new TimePickerDialog(
            this,
            (v, h, m) -> {
              if (first) e.startMinute = h * 60 + m;
              else e.endMinute = h * 60 + m;
              refreshTimes();
            },
            value / 60,
            value % 60,
            true)
        .show();
  }

  private void refreshTimes() {
    if (validFrom != null) validFrom.setText("Áp dụng từ: " + e.validFrom);
    if (validTo != null) validTo.setText("Đến ngày: " + e.validTo);
    if (examEnd != null) examEnd.setText("Kết thúc thi: " + Ui.date(e.endAt));
    if (due != null) {
      due.setText("Ngày giờ: " + Ui.date(e.dueAt));
      due.setEnabled(e.dueAt > 0);
    }
    if (start != null) start.setText("Bắt đầu: " + Ui.time(e.startMinute));
    if (end != null) end.setText("Kết thúc: " + Ui.time(e.endMinute));
  }

  private void pickRange(boolean first) {
    java.time.LocalDate date = java.time.LocalDate.parse(first ? e.validFrom : e.validTo);
    new DatePickerDialog(
            this,
            (v, y, m, d) -> {
              String value = java.time.LocalDate.of(y, m + 1, d).toString();
              if (first) e.validFrom = value;
              else e.validTo = value;
              refreshTimes();
            },
            date.getYear(),
            date.getMonthValue() - 1,
            date.getDayOfMonth())
        .show();
  }

  private void pickExamEnd() {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(e.endAt);
    new DatePickerDialog(
            this,
            (v, y, m, d) ->
                new TimePickerDialog(
                        this,
                        (t, h, min) -> {
                          c.set(y, m, d, h, min, 0);
                          c.set(Calendar.MILLISECOND, 0);
                          e.endAt = c.getTimeInMillis();
                          refreshTimes();
                        },
                        c.get(Calendar.HOUR_OF_DAY),
                        c.get(Calendar.MINUTE),
                        true)
                    .show(),
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH))
        .show();
  }

  private String value(EditText input) {
    return input.getText().toString().trim();
  }

  private double number(EditText input) {
    return Double.parseDouble(value(input).replace(',', '.'));
  }

  private void save() {
    e.title = value(title);
    if (e.title.isEmpty()) {
      title.setError("Vui lòng nhập tiêu đề");
      title.requestFocus();
      return;
    }
    if (e.title.length() > 160) {
      title.setError("Tối đa 160 ký tự");
      return;
    }
    e.detail = value(detail);
    e.subjectId =
        subject == null || subject.getSelectedItemPosition() == 0
            ? 0
            : subjects.get(subject.getSelectedItemPosition() - 1).id;
    if (place != null) e.place = value(place);
    if (priority != null) {
      e.priority = priority.getSelectedItemPosition() + 1;
      e.status = status.getSelectedItem().toString();
    }
    if (day != null) {
      e.day = day.getSelectedItemPosition() + 2;
      if (e.endMinute <= e.startMinute) {
        message("Giờ kết thúc phải sau giờ bắt đầu, trong cùng ngày.");
        return;
      }
    }
    try {
      if (credits != null) {
        e.credits = number(credits);
        if (!Double.isFinite(e.credits)
            || e.credits < 1
            || e.credits > 30
            || e.credits != (int) e.credits) throw new NumberFormatException();
      }
      if (score != null) {
        e.score = number(score);
        e.weight = number(weight);
        if (!Double.isFinite(e.score)
            || !Double.isFinite(e.weight)
            || e.score < 0
            || e.score > 10
            || e.weight <= 0
            || e.weight > 1000) throw new NumberFormatException();
      }
    } catch (NumberFormatException ex) {
      message("Kiểm tra số tín chỉ (1–30), điểm (0–10), trọng số (>0 và ≤1000).");
      return;
    }
    if (e.kind.equals("grade") && e.subjectId == 0) {
      message("Hãy tạo và chọn môn học trước khi nhập điểm.");
      return;
    }
    if (code != null) {
      e.code = value(code);
      e.lecturer = value(lecturer);
    }
    if (reminderChoice != null) {
      int i = reminderChoice.getSelectedItemPosition();
      if (i >= vn.edu.studymate.logic.ReminderOptions.MINUTES.length) {
        message("Hãy chọn lại khoảng nhắc trong danh sách của báo cáo.");
        return;
      }
      int minutes = vn.edu.studymate.logic.ReminderOptions.MINUTES[i];
      e.remindEnabled = minutes >= 0 && (!e.kind.equals("task") || e.dueAt > 0);
      e.minutesBefore = Math.max(0, minutes);
      e.reminderOffsets = new ArrayList<>(extraOffsets);
    }
    try {
      vn.edu.studymate.logic.Validation.validate(e);
    } catch (Exception ex) {
      message(ex.getMessage());
      return;
    }
    if ((e.kind.equals("task") || e.kind.equals("exam"))
        && e.dueAt > 0
        && e.dueAt < System.currentTimeMillis()) {
      new MaterialAlertDialogBuilder(this)
          .setTitle("Thời gian đã qua")
          .setMessage("Mục này sẽ được hiển thị là quá hạn. Bạn vẫn muốn lưu?")
          .setNegativeButton("Sửa lại", null)
          .setPositiveButton("Vẫn lưu", (d, w) -> checkConflicts())
          .show();
      return;
    }
    checkConflicts();
  }

  private void checkConflicts() {
    if (e.kind.equals("schedule") || e.kind.equals("exam")) {
      java.util.List<Entry> entries = new java.util.ArrayList<>(db.list(user, "schedule"));
      entries.addAll(db.list(user, "exam"));
      for (Entry other : entries)
        if (vn.edu.studymate.logic.ScheduleRules.conflict(e, other)) {
          new MaterialAlertDialogBuilder(this)
              .setTitle("Trùng lịch")
              .setMessage(
                  "Trùng với “" + other.title + "” trong khoảng ngày và giờ áp dụng. Vẫn lưu?")
              .setNegativeButton("Sửa lại", null)
              .setPositiveButton("Vẫn lưu", (d, w) -> persist())
              .show();
          return;
        }
    }
    persist();
  }

  private void persist() {
    try {
      db.save(user, e);
      ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel((int) e.id);
      ReminderScheduler.schedule(this);
      ReminderScheduler.checkNow(this);
      Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show();
      finish();
    } catch (Exception ex) {
      message(
          ex instanceof android.database.sqlite.SQLiteConstraintException
              ? "Mã môn đã tồn tại. Vui lòng chọn mã khác."
              : "Không thể lưu: " + ex.getMessage());
    }
  }

  private void message(String msg) {
    new MaterialAlertDialogBuilder(this).setMessage(msg).setPositiveButton("Đã hiểu", null).show();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    if (e != null) {
      b.putIntegerArrayList("extraOffsets", extraOffsets);
      b.putString("validFrom", e.validFrom);
      b.putString("validTo", e.validTo);
      b.putLong("examEnd", e.endAt);
      b.putLong("due", e.dueAt);
      b.putInt("start", e.startMinute);
      b.putInt("end", e.endMinute);
    }
  }

  @Override
  protected void onDestroy() {
    if (db != null) db.close();
    super.onDestroy();
  }
}
