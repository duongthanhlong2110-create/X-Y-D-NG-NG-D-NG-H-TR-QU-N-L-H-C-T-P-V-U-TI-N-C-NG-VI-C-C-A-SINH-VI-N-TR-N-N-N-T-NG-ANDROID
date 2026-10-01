package vn.edu.studymate.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import vn.edu.studymate.R;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.reminder.ReminderScheduler;

public class SettingsActivity extends AppCompatActivity {
  private TextView status;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    if (Session.user(this) < 1) {
      finish();
      return;
    }
    setContentView(R.layout.activity_editor);
    Ui.insets(this);
    MaterialToolbar t = findViewById(R.id.toolbar);
    t.setTitle("Cài đặt nhắc nhở");
    t.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    t.setNavigationOnClickListener(v -> finish());
    findViewById(R.id.save).setVisibility(android.view.View.GONE);
    LinearLayout fields = findViewById(R.id.fields);
    SwitchMaterial enabled = new SwitchMaterial(this);
    enabled.setText("Bật nhắc cho tài khoản này");
    enabled.setChecked(ReminderScheduler.enabled(this));
    fields.addView(enabled);
    enabled.setOnCheckedChangeListener(
        (v, on) -> {
          ReminderScheduler.setEnabled(this, on);
          update();
        });
    status = new TextView(this);
    fields.addView(status);
    TextView info = new TextView(this);
    info.setText(
        "\n"
            + "Chọn khoảng nhắc trước ở từng công việc, lịch học hoặc lịch thi.\n\n"
            + "Lịch học được nhắc theo từng buổi trong khoảng ngày áp dụng. Sửa thời gian hoặc cấu"
            + " hình sẽ cập nhật nhắc. Hoàn thành, xóa hoặc đăng xuất sẽ hủy nhắc liên quan.\n\n"
            + "Android có thể trì hoãn nhắc khi tiết kiệm pin. Sau khi buộc dừng ứng dụng, cần mở"
            + " lại để tiếp tục nhắc.\n");
    fields.addView(info);
    MaterialButton permission = new MaterialButton(this);
    permission.setText("Quyền thông báo hệ thống");
    fields.addView(permission);
    permission.setOnClickListener(
        v ->
            startActivity(
                new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())));
    MaterialButton test = new MaterialButton(this);
    test.setText("Gửi thông báo thử");
    fields.addView(test);
    test.setOnClickListener(
        v ->
            Toast.makeText(
                    this,
                    ReminderScheduler.demo(this)
                        ? "Đã gửi thông báo thử"
                        : "Hãy bật quyền thông báo",
                    Toast.LENGTH_LONG)
                .show());
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (status != null) update();
  }

  private void update() {
    status.setText(
        !ReminderScheduler.enabled(this)
            ? "Nhắc tự động đang tắt."
            : ReminderScheduler.allowed(this)
                ? "Nhắc đang bật; hệ thống đã cấp quyền."
                : "Chưa có quyền thông báo hoặc kênh nhắc đang tắt.");
  }
}
