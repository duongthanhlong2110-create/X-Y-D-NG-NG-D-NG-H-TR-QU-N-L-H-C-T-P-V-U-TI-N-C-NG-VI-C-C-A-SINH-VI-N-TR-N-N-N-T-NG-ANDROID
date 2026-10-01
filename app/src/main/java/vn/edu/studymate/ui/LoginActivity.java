package vn.edu.studymate.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.*;
import vn.edu.studymate.R;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;

public class LoginActivity extends AppCompatActivity {
  private boolean register = false, busy = false;
  private EditText name, email, password, confirm;
  private TextView error;
  private MaterialButton submit, toggle;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    if (Session.user(this) > 0) {
      openMain();
      return;
    }
    setContentView(R.layout.activity_login);
    Ui.insets(this);
    name = findViewById(R.id.name);
    email = findViewById(R.id.email);
    password = findViewById(R.id.password);
    confirm = findViewById(R.id.confirm);
    error = findViewById(R.id.error);
    submit = findViewById(R.id.submit);
    toggle = findViewById(R.id.toggle);
    if (b != null) register = b.getBoolean("register");
    showMode();
    toggle.setOnClickListener(
        v -> {
          register = !register;
          showMode();
        });
    submit.setOnClickListener(v -> authenticate());
  }

  private void showMode() {
    findViewById(R.id.name_box).setVisibility(register ? View.VISIBLE : View.GONE);
    findViewById(R.id.confirm_box).setVisibility(register ? View.VISIBLE : View.GONE);
    submit.setText(register ? "Tạo tài khoản" : "Đăng nhập");
    toggle.setText(register ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký");
    error.setText("");
  }

  private void authenticate() {
    if (busy) return;
    String n = name.getText().toString().trim(),
        e = email.getText().toString().trim(),
        p = password.getText().toString();
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
      error.setText("Vui lòng nhập email hợp lệ.");
      return;
    }
    if (p.length() < 6) {
      error.setText("Mật khẩu phải có ít nhất 6 ký tự.");
      return;
    }
    if (register && n.isEmpty()) {
      error.setText("Vui lòng nhập họ và tên.");
      return;
    }
    if (register && !p.equals(confirm.getText().toString())) {
      error.setText("Mật khẩu xác nhận chưa khớp.");
      return;
    }
    busy = true;
    submit.setEnabled(false);
    toggle.setEnabled(false);
    error.setText("Đang xử lý…");
    boolean creating = register;
    executor.execute(
        () -> {
          long id = -1;
          String message = "";
          try (DatabaseHelper db = new DatabaseHelper(this)) {
            db.seedDemo();
            id = creating ? db.register(n, e, p) : db.login(e, p);
            if (id < 0)
              message = creating ? "Email đã được sử dụng." : "Email hoặc mật khẩu chưa đúng.";
          } catch (Exception ex) {
            message = "Không thể mở dữ liệu. Vui lòng thử lại.";
          }
          final long result = id;
          final String msg = message;
          runOnUiThread(
              () -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                submit.setEnabled(true);
                toggle.setEnabled(true);
                if (result > 0) {
                  Session.login(this, result);
                  openMain();
                } else error.setText(msg);
              });
        });
  }

  private void openMain() {
    startActivity(new Intent(this, MainActivity.class));
    finish();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putBoolean("register", register);
  }

  @Override
  protected void onDestroy() {
    executor.shutdown();
    super.onDestroy();
  }
}
