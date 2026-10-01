package vn.edu.studymate.logic;

import java.util.*;
import vn.edu.studymate.model.Entry;

/** Shared rules for report sections 2.6, 2.7.7 and 3.2. */
public final class SmartPlanner {
  public static final String[] GROUPS = {
    "Quá hạn", "Rất gần hạn", "Sắp đến hạn", "Còn thời gian", "Chưa đặt deadline"
  };
  public static final String RULES =
      "Chỉ xếp việc chưa hoàn thành. Điểm = khẩn cấp + mức ưu tiên + trạng thái. Khẩn cấp: quá hạn"
          + " 100; trong 24 giờ 70; trên 24 đến 72 giờ 40; trên 72 giờ 10; chưa đặt hạn 0. Ưu tiên"
          + " thấp/vừa/cao: 10/20/30 điểm. Đang làm: cộng 5 điểm. Điểm cao trước; bằng điểm thì hạn"
          + " sớm trước (chưa đặt hạn đứng sau); cuối cùng mã nhỏ trước.";

  public static boolean finished(Entry e) {
    return "Hoàn thành".equals(e.status);
  }

  public static int group(long due, long now) {
    if (due < 0 || due > 253402300799999L)
      throw new IllegalArgumentException("Deadline không hợp lệ");
    if (due == 0) return 4;
    long d = due - now;
    return d < 0 ? 0 : d <= 86400000L ? 1 : d <= 259200000L ? 2 : 3;
  }

  public static String label(Entry e, long now) {
    return finished(e) ? "Đã hoàn thành" : GROUPS[group(e.dueAt, now)];
  }

  public static int urgency(Entry e, long now) {
    return new int[] {100, 70, 40, 10, 0}[group(e.dueAt, now)];
  }

  public static int score(Entry e, long now) {
    if (finished(e)) return 0;
    if (e.priority < 1
        || e.priority > 3
        || !Arrays.asList("Chưa làm", "Đang làm").contains(e.status))
      throw new IllegalArgumentException("Trạng thái hoặc mức ưu tiên không hợp lệ");
    return urgency(e, now) + e.priority * 10 + ("Đang làm".equals(e.status) ? 5 : 0);
  }

  public static Comparator<Entry> comparator(long now) {
    return Comparator.<Entry>comparingInt(e -> score(e, now))
        .reversed()
        .thenComparingLong(e -> e.dueAt == 0 ? Long.MAX_VALUE : e.dueAt)
        .thenComparingLong(e -> e.id);
  }

  public static List<Entry> ranked(List<Entry> entries, long now) {
    List<Entry> r = new ArrayList<>();
    for (Entry e : entries) if (!finished(e)) r.add(e);
    r.sort(comparator(now));
    return r;
  }

  public static final class Stats {
    public int total, completed, near, overdue;

    public double percent() {
      return total == 0 ? 0 : completed * 100.0 / total;
    }
  }

  public static Stats stats(List<Entry> tasks, long now) {
    Stats s = new Stats();
    s.total = tasks.size();
    for (Entry e : tasks) {
      if (finished(e)) s.completed++;
      else {
        int g = group(e.dueAt, now);
        if (g == 0) s.overdue++;
        else if (g == 1 || g == 2) s.near++;
      }
    }
    return s;
  }

  public static String remaining(long due, long now) {
    if (due == 0) return "Chưa đặt deadline";
    group(due, now);
    long d = due - now;
    if (d == 0) return "Đến hạn ngay";
    long m = Math.max(1, (long) Math.ceil(Math.abs((double) d) / 60000d));
    return (d < 0 ? "Quá hạn " : "Còn ")
        + (m >= 1440
            ? m / 1440 + " ngày " + (m % 1440) / 60 + " giờ"
            : m >= 60 ? m / 60 + " giờ " + m % 60 + " phút" : m + " phút");
  }

  public static String reason(Entry e, long now) {
    if (finished(e)) return "Đã hoàn thành";
    String time =
        e.dueAt == 0 ? "Chưa đặt deadline" : label(e, now) + " • " + remaining(e.dueAt, now);
    return time
        + " • Ưu tiên "
        + new String[] {"", "thấp", "vừa", "cao"}[e.priority]
        + (e.status.equals("Đang làm") ? " • Đang làm (+5)" : "")
        + " • "
        + score(e, now)
        + " điểm";
  }

  public static double average(List<Entry> grades) {
    double sum = 0, w = 0;
    for (Entry e : grades) {
      sum += e.score * e.weight;
      w += e.weight;
    }
    return w == 0 ? Double.NaN : sum / w;
  }
}
