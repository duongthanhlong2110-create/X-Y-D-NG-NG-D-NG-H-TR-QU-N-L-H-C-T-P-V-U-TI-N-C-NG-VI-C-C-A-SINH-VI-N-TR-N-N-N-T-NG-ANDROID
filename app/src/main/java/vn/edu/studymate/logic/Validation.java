package vn.edu.studymate.logic;

import java.time.*;
import vn.edu.studymate.model.Entry;

public final class Validation {
  public static void validate(Entry e) {
    if (!java.util.Arrays.asList("subject", "task", "schedule", "exam", "note", "grade")
        .contains(e.kind)) throw new IllegalArgumentException("Loại dữ liệu không hợp lệ.");
    if (e.title == null || e.title.trim().isEmpty() || e.title.length() > 160)
      throw new IllegalArgumentException("Tiêu đề phải có từ 1 đến 160 ký tự.");
    if (e.priority < 1 || e.priority > 3)
      throw new IllegalArgumentException("Mức quan trọng không hợp lệ.");
    if (!java.util.Arrays.asList("Chưa làm", "Đang làm", "Hoàn thành").contains(e.status))
      throw new IllegalArgumentException("Trạng thái không hợp lệ.");
    if (e.remindEnabled && (e.minutesBefore < 0 || ReminderOptions.index(e.minutesBefore) < 0))
      throw new IllegalArgumentException("Hãy chọn một khoảng nhắc trong danh sách.");
    ZoneId.of(e.zoneId);
    if (e.dueAt < 0 || e.dueAt > 253402300799999L)
      throw new IllegalArgumentException("Deadline không hợp lệ.");
    if (e.kind.equals("exam") && e.dueAt == 0)
      throw new IllegalArgumentException("Vui lòng chọn ngày giờ thi.");
    if (e.kind.equals("exam") && e.endAt <= e.dueAt)
      throw new IllegalArgumentException("Giờ kết thúc thi phải sau giờ bắt đầu.");
    if (e.kind.equals("schedule")) {
      if (e.day < 2
          || e.day > 8
          || e.startMinute < 0
          || e.endMinute > 1440
          || e.endMinute <= e.startMinute)
        throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.");
      if (LocalDate.parse(e.validTo).isBefore(LocalDate.parse(e.validFrom)))
        throw new IllegalArgumentException("Ngày kết thúc áp dụng phải từ ngày bắt đầu trở đi.");
    }
    if (e.kind.equals("subject")
        && (!Double.isFinite(e.credits)
            || e.credits < 1
            || e.credits > 30
            || e.credits != (int) e.credits))
      throw new IllegalArgumentException("Số tín chỉ phải là số nguyên từ 1 đến 30.");
    if (e.kind.equals("grade")
        && (!Double.isFinite(e.score)
            || e.score < 0
            || e.score > 10
            || !Double.isFinite(e.weight)
            || e.weight <= 0
            || e.weight > 1000))
      throw new IllegalArgumentException("Điểm từ 0 đến 10; trọng số lớn hơn 0 và không quá 1000.");
  }
}
