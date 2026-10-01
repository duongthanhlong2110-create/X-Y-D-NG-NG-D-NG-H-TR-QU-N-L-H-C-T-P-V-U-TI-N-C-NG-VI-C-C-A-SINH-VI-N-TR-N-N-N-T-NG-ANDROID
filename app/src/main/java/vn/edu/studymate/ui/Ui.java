package vn.edu.studymate.ui;

import android.app.Activity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Ui {

  public static int dp(android.content.Context c, int n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static void pill(android.widget.TextView v, String text, int fg, int bg) {
    v.setText(text);
    v.setTextColor(fg);
    v.setVisibility(View.VISIBLE);
    v.setPadding(
        dp(v.getContext(), 8), dp(v.getContext(), 4), dp(v.getContext(), 8), dp(v.getContext(), 4));
    android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
    d.setColor(bg);
    d.setCornerRadius(dp(v.getContext(), 8));
    v.setBackground(d);
  }

  public static void bindTask(
      View card, vn.edu.studymate.model.Entry e, String subject, long now, int rank) {
    android.content.Context c = card.getContext();
    android.widget.TextView title = card.findViewById(vn.edu.studymate.R.id.card_title),
        sub = card.findViewById(vn.edu.studymate.R.id.card_subject),
        badge = card.findViewById(vn.edu.studymate.R.id.card_badge),
        score = card.findViewById(vn.edu.studymate.R.id.card_score),
        number = card.findViewById(vn.edu.studymate.R.id.card_rank),
        body = card.findViewById(vn.edu.studymate.R.id.card_body);
    title.setText(e.title);
    sub.setText(subject.isEmpty() ? "Công việc cá nhân" : subject.trim());
    sub.setVisibility(View.VISIBLE);
    number.setVisibility(rank > 0 ? View.VISIBLE : View.GONE);
    number.setText(String.format(Locale.ROOT, "%02d", rank));
    boolean done = vn.edu.studymate.logic.SmartPlanner.finished(e);
    int group = vn.edu.studymate.logic.SmartPlanner.group(e.dueAt, now);
    int fg =
        c.getColor(
            done
                ? vn.edu.studymate.R.color.secondary
                : group == 0
                    ? vn.edu.studymate.R.color.danger
                    : group < 3
                        ? vn.edu.studymate.R.color.warning
                        : vn.edu.studymate.R.color.muted);
    int bg =
        c.getColor(
            done
                ? vn.edu.studymate.R.color.soft_green
                : group == 0
                    ? vn.edu.studymate.R.color.soft_red
                    : group < 3
                        ? vn.edu.studymate.R.color.soft_amber
                        : vn.edu.studymate.R.color.background);
    String status =
        done
            ? "Đã hoàn thành"
            : e.dueAt == 0
                ? "Chưa đặt deadline"
                : vn.edu.studymate.logic.SmartPlanner.label(e, now)
                    + " · "
                    + vn.edu.studymate.logic.SmartPlanner.remaining(e.dueAt, now)
                        .replace("Quá hạn ", "Trễ ");
    pill(badge, status, fg, bg);
    score.setVisibility(done ? View.GONE : View.VISIBLE);
    if (!done)
      pill(
          score,
          vn.edu.studymate.logic.SmartPlanner.score(e, now) + " điểm",
          c.getColor(vn.edu.studymate.R.color.primary),
          c.getColor(vn.edu.studymate.R.color.soft_blue));
    String text =
        "Ưu tiên "
            + new String[] {"", "thấp", "vừa", "cao"}[e.priority]
            + " · "
            + e.status
            + (e.status.equals("Đang làm") ? " (+5)" : "");
    if (e.dueAt > 0) text += "\n" + date(e.dueAt);
    if (rank > 0 && !done)
      text +=
          "\n"
              + "Lý do: "
              + vn.edu.studymate.logic.SmartPlanner.label(e, now)
              + "; ưu tiên "
              + new String[] {"", "thấp", "vừa", "cao"}[e.priority]
              + "; "
              + e.status.toLowerCase(java.util.Locale.forLanguageTag("vi"))
              + (e.status.equals("Đang làm") ? " (+5 điểm)." : " (+0 điểm).");
    if (!e.detail.isEmpty() && rank > 0) text += "\n" + e.detail;
    body.setText(text);
  }

  public static void insets(Activity a) {
    View root = a.findViewById(vn.edu.studymate.R.id.root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, w) -> {
          Insets i =
              w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(i.left, i.top, i.right, i.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    ViewCompat.requestApplyInsets(root);
  }

  public static String date(long millis) {
    if (millis == 0) return "Chưa đặt deadline";
    return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(millis));
  }

  public static String time(int minute) {
    return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60);
  }

  public static String day(int d) {
    return d == 8 ? "Chủ nhật" : "Thứ " + d;
  }

  public static String label(String kind) {
    switch (kind) {
      case "subject":
        return "Môn học";
      case "task":
        return "Công việc";
      case "schedule":
        return "Lịch học";
      case "exam":
        return "Lịch thi / kiểm tra";
      case "note":
        return "Ghi chú";
      case "grade":
        return "Điểm số";
      default:
        return "Tổng quan";
    }
  }
}
