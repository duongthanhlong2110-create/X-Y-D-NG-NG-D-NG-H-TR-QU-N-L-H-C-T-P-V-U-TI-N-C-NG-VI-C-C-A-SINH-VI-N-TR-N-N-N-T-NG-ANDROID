package vn.edu.studymate.model;

/** One typed record: subject, task, schedule, exam, note or grade. */
public class Entry {
  public long id, userId, subjectId, dueAt, reminderId;
  public java.util.List<Integer> reminderOffsets;
  public long createdAt, updatedAt, completedAt, endAt, handledAt;
  public String code = "",
      lecturer = "",
      validFrom = "1970-01-01",
      validTo = "2099-12-31",
      zoneId = java.time.ZoneId.systemDefault().getId();
  public boolean remindEnabled = true;
  public int minutesBefore = 60;
  public String kind = "", title = "", detail = "", place = "", status = "Chưa làm";
  public int priority = 2, day = 2, startMinute = 480, endMinute = 540;
  public double score = 0, weight = 1, credits = 3;
}
