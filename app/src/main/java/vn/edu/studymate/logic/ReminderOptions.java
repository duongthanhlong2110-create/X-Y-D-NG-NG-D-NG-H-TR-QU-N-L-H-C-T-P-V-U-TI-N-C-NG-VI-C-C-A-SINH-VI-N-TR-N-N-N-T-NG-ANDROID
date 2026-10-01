package vn.edu.studymate.logic;

public final class ReminderOptions {
  public static final int[] MINUTES = {-1, 0, 30, 60, 360, 1440, 4320};
  public static final String[] LABELS = {
    "Không nhắc",
    "Đúng thời điểm",
    "Trước 30 phút",
    "Trước 1 giờ",
    "Trước 6 giờ",
    "Trước 1 ngày",
    "Trước 3 ngày"
  };

  public static int index(int value) {
    for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == value) return i;
    return -1;
  }
}
