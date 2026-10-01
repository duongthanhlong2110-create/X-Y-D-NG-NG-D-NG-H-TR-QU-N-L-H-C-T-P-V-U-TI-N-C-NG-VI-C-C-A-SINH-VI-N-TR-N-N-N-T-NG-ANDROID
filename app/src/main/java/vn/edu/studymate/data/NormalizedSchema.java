package vn.edu.studymate.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Physical schema v5. entries is a read-only union projection, never a storage table. */
final class NormalizedSchema {
  static final String[] KINDS = {"subject", "task", "schedule", "exam", "note", "grade"};
  static final String[] TABLES = {
    "subjects", "tasks", "class_schedules", "exams", "notes", "grades"
  };

  static String table(String kind) {
    for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return TABLES[i];
    throw new IllegalArgumentException("Loại dữ liệu không hợp lệ");
  }

  static final String COMMON =
      "id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title"
          + " TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL"
          + " DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT"
          + " 0";
  static final String SUBJECT =
      ",subject_id INTEGER,UNIQUE(id,user_id),FOREIGN KEY(subject_id,user_id) REFERENCES"
          + " subjects(id,user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED";

  static Set<String> columns(SQLiteDatabase db, String table) {
    Set<String> out = new LinkedHashSet<>();
    try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      while (c.moveToNext()) out.add(c.getString(1));
    }
    return out;
  }

  static void create(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE subjects("
            + COMMON
            + ",code TEXT NOT NULL DEFAULT '',lecturer TEXT NOT NULL DEFAULT '',credits REAL NOT"
            + " NULL DEFAULT 3 CHECK(credits BETWEEN 1 AND 30 AND credits=CAST(credits AS"
            + " INTEGER)),UNIQUE(id,user_id))");
    db.execSQL(
        "CREATE UNIQUE INDEX subject_code ON subjects(user_id,code COLLATE NOCASE) WHERE code!=''");
    db.execSQL(
        "CREATE TABLE tasks("
            + COMMON
            + ",due_at INTEGER NOT NULL DEFAULT 0 CHECK(due_at>=0),priority INTEGER NOT NULL"
            + " DEFAULT 2 CHECK(priority BETWEEN 1 AND 3),status TEXT NOT NULL DEFAULT 'Chưa làm'"
            + " CHECK(status IN ('Chưa làm','Đang làm','Hoàn thành')),completed_at INTEGER NOT NULL"
            + " DEFAULT 0,zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh'"
            + SUBJECT
            + ")");
    db.execSQL(
        "CREATE TABLE class_schedules("
            + COMMON
            + ",place TEXT NOT NULL DEFAULT '',day INTEGER NOT NULL DEFAULT 2 CHECK(day BETWEEN 2"
            + " AND 8),start_minute INTEGER NOT NULL DEFAULT 480 CHECK(start_minute>=0),end_minute"
            + " INTEGER NOT NULL DEFAULT 540 CHECK(end_minute<=1440 AND"
            + " end_minute>start_minute),valid_from TEXT NOT NULL DEFAULT '1970-01-01',valid_to"
            + " TEXT NOT NULL DEFAULT '2099-12-31' CHECK(valid_to>=valid_from),zone_id TEXT NOT"
            + " NULL DEFAULT 'Asia/Ho_Chi_Minh'"
            + SUBJECT
            + ")");
    db.execSQL(
        "CREATE TABLE exams("
            + COMMON
            + ",due_at INTEGER NOT NULL CHECK(due_at>0),end_at INTEGER NOT NULL"
            + " CHECK(end_at>due_at),place TEXT NOT NULL DEFAULT '',zone_id TEXT NOT NULL DEFAULT"
            + " 'Asia/Ho_Chi_Minh'"
            + SUBJECT
            + ")");
    db.execSQL("CREATE TABLE notes(" + COMMON + SUBJECT + ")");
    db.execSQL(
        "CREATE TABLE grades("
            + COMMON
            + ",score REAL NOT NULL DEFAULT 0 CHECK(score BETWEEN 0 AND 10),weight REAL NOT NULL"
            + " DEFAULT 1 CHECK(weight>0 AND weight<=1000)"
            + SUBJECT
            + ")");
    db.execSQL(
        "CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL"
            + " REFERENCES users(id) ON DELETE CASCADE,task_id INTEGER,class_schedule_id"
            + " INTEGER,exam_id INTEGER,sort_order INTEGER NOT NULL DEFAULT 0"
            + " CHECK(sort_order>=0),minutes_before INTEGER NOT NULL CHECK(minutes_before BETWEEN 0"
            + " AND 10080),handled_at INTEGER NOT NULL DEFAULT 0,notified_at INTEGER NOT NULL"
            + " DEFAULT 0,CHECK((task_id IS NOT NULL)+(class_schedule_id IS NOT NULL)+(exam_id IS"
            + " NOT NULL)=1),FOREIGN KEY(task_id,user_id) REFERENCES tasks(id,user_id) ON DELETE"
            + " CASCADE,FOREIGN KEY(class_schedule_id,user_id) REFERENCES"
            + " class_schedules(id,user_id) ON DELETE CASCADE,FOREIGN KEY(exam_id,user_id)"
            + " REFERENCES exams(id,user_id) ON DELETE CASCADE)");
    for (String fk : new String[] {"task_id", "class_schedule_id", "exam_id"})
      db.execSQL(
          "CREATE UNIQUE INDEX reminders_"
              + fk
              + " ON reminders("
              + fk
              + ",minutes_before) WHERE "
              + fk
              + " IS NOT NULL");
    db.execSQL(
        "CREATE TABLE id_sequence(singleton INTEGER PRIMARY KEY CHECK(singleton=1),next_id INTEGER"
            + " NOT NULL)");
    db.execSQL("INSERT INTO id_sequence VALUES(1,1)");
    db.execSQL(
        "CREATE TABLE migration_issues(id INTEGER PRIMARY KEY AUTOINCREMENT,legacy_id"
            + " INTEGER,reason TEXT NOT NULL)");
    for (String t : TABLES) {
      db.execSQL("CREATE INDEX " + t + "_owner ON " + t + "(user_id)");
      dirty(db, t);
    }
    dirty(db, "reminders");
    db.execSQL("CREATE INDEX task_due ON tasks(user_id,due_at)");
    db.execSQL("CREATE INDEX reminders_owner ON reminders(user_id)");
    projection(db);
  }

  private static void dirty(SQLiteDatabase db, String t) {
    for (String event : new String[] {"INSERT", "UPDATE", "DELETE"}) {
      String row = event.equals("DELETE") ? "OLD" : "NEW";
      db.execSQL(
          "CREATE TRIGGER dirty_"
              + t
              + "_"
              + event
              + " AFTER "
              + event
              + " ON "
              + t
              + " BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id="
              + row
              + ".user_id; END");
    }
  }

  static String fk(String kind) {
    return kind.equals("task")
        ? "task_id"
        : kind.equals("schedule") ? "class_schedule_id" : "exam_id";
  }

  private static void projection(SQLiteDatabase db) {
    String[] cols = {
      "id",
      "user_id",
      "kind",
      "title",
      "detail",
      "subject_id",
      "due_at",
      "priority",
      "status",
      "place",
      "day",
      "start_minute",
      "end_minute",
      "score",
      "weight",
      "credits",
      "code",
      "lecturer",
      "valid_from",
      "valid_to",
      "zone_id",
      "created_at",
      "updated_at",
      "completed_at",
      "end_at",
      "handled_at",
      "notified_at",
      "remind_enabled",
      "minutes_before",
      "reminder_before_min"
    };
    Map<String, String> defaults = new HashMap<>();
    for (String c : cols) defaults.put(c, "0");
    for (String c : new String[] {"detail", "place", "code", "lecturer"}) defaults.put(c, "''");
    defaults.put("subject_id", "NULL");
    defaults.put("priority", "2");
    defaults.put("status", "'Chưa làm'");
    defaults.put("day", "2");
    defaults.put("start_minute", "480");
    defaults.put("end_minute", "540");
    defaults.put("weight", "1");
    defaults.put("credits", "3");
    defaults.put("valid_from", "'1970-01-01'");
    defaults.put("valid_to", "'2099-12-31'");
    defaults.put("zone_id", "'Asia/Ho_Chi_Minh'");
    defaults.put("minutes_before", "60");
    defaults.put("reminder_before_min", "-1");
    List<String> selects = new ArrayList<>();
    for (int i = 0; i < KINDS.length; i++) {
      String k = KINDS[i], t = TABLES[i];
      Set<String> fields = columns(db, t);
      List<String> parts = new ArrayList<>();
      boolean has = Arrays.asList("task", "schedule", "exam").contains(k);
      String sub =
          " FROM reminders r WHERE r."
              + fk(k)
              + "=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1)";
      for (String col : cols) {
        String value = fields.contains(col) ? "t." + col : defaults.get(col);
        if (col.equals("kind")) value = "'" + k + "'";
        if (has) {
          if (col.equals("remind_enabled"))
            value =
                "EXISTS(SELECT 1 FROM reminders r WHERE r."
                    + fk(k)
                    + "=t.id AND r.user_id=t.user_id)";
          if (col.equals("minutes_before") || col.equals("reminder_before_min"))
            value =
                "COALESCE((SELECT minutes_before"
                    + sub
                    + ","
                    + (col.equals("minutes_before") ? 60 : -1)
                    + ")";
          if (col.equals("handled_at") || col.equals("notified_at"))
            value = "COALESCE((SELECT " + col + sub + ",0)";
        }
        if (Arrays.asList(
                "id",
                "user_id",
                "subject_id",
                "due_at",
                "priority",
                "day",
                "start_minute",
                "end_minute",
                "created_at",
                "updated_at",
                "completed_at",
                "end_at",
                "handled_at",
                "notified_at",
                "remind_enabled",
                "minutes_before",
                "reminder_before_min")
            .contains(col)) value = "CAST(" + value + " AS INTEGER)";
        parts.add(value + " AS " + col);
      }
      selects.add("SELECT " + String.join(",", parts) + " FROM " + t + " t");
    }
    db.execSQL("CREATE VIEW entries AS " + String.join(" UNION ALL ", selects));
  }

  static void migrate(SQLiteDatabase db) {
    // SQLiteOpenHelper wraps upgrade in a single transaction. A failure rolls back the entire
    // upgrade.
    for (String event : new String[] {"INSERT", "UPDATE", "DELETE"})
      db.execSQL("DROP TRIGGER IF EXISTS reminder_dirty_" + event);
    db.execSQL("DROP INDEX IF EXISTS subject_code");
    // Archive every original field, including malformed references, before any normalization.
    db.execSQL("CREATE TABLE legacy_entries_v4 AS SELECT * FROM entries");
    db.execSQL("DROP TABLE entries");
    create(db);
    for (int i = 0; i < KINDS.length; i++) {
      String k = KINDS[i], t = TABLES[i];
      Set<String> cols = columns(db, t);
      List<String> expressions = new ArrayList<>();
      for (String col : cols) {
        String value = "e." + col;
        if (col.equals("subject_id"))
          value =
              "CASE WHEN EXISTS(SELECT 1 FROM subjects s WHERE s.id=e.subject_id AND"
                  + " s.user_id=e.user_id) THEN e.subject_id ELSE NULL END";
        expressions.add(value);
      }
      db.execSQL(
          "INSERT INTO "
              + t
              + "("
              + String.join(",", cols)
              + ") SELECT "
              + String.join(",", expressions)
              + " FROM legacy_entries_v4 e WHERE kind='"
              + k
              + "'");
    }
    db.execSQL(
        "INSERT INTO migration_issues(legacy_id,reason) SELECT e.id,'Liên kết môn cũ không hợp lệ;"
            + " giữ bản gốc trong legacy_entries_v4 và chuyển thành mục cá nhân.' FROM"
            + " legacy_entries_v4 e WHERE e.subject_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM"
            + " subjects s WHERE s.id=e.subject_id AND s.user_id=e.user_id)");
    db.execSQL(
        "INSERT INTO"
            + " reminders(user_id,task_id,class_schedule_id,exam_id,minutes_before,handled_at,notified_at)"
            + " SELECT user_id,CASE WHEN kind='task' THEN id END,CASE WHEN kind='schedule' THEN id"
            + " END,CASE WHEN kind='exam' THEN id END,minutes_before,handled_at,notified_at FROM"
            + " legacy_entries_v4 WHERE kind IN ('task','schedule','exam') AND remind_enabled=1 AND"
            + " reminder_before_min>=0 AND (kind='schedule' OR due_at>0)");
    db.execSQL(
        "UPDATE id_sequence SET next_id=(SELECT COALESCE(MAX(id),0)+1 FROM legacy_entries_v4)");
    db.execSQL(
        "CREATE VIEW migration_id_map AS SELECT id AS old_id,id AS new_id,kind FROM"
            + " legacy_entries_v4");
    try (Cursor c =
        db.rawQuery(
            "SELECT (SELECT COUNT(*) FROM legacy_entries_v4)-(SELECT COUNT(*) FROM entries)",
            null)) {
      c.moveToFirst();
      if (c.getLong(0) != 0) throw new IllegalStateException("Không đủ dữ liệu sau chuyển đổi");
    }
    try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) {
      if (c.moveToFirst())
        throw new IllegalStateException("Khóa ngoại không hợp lệ sau chuyển đổi");
    }
    // Archived rows are recovery evidence, not a second writable source of application data.
    for (String event : new String[] {"INSERT", "UPDATE", "DELETE"})
      db.execSQL(
          "CREATE TRIGGER archive_"
              + event
              + " BEFORE "
              + event
              + " ON legacy_entries_v4 BEGIN SELECT RAISE(ABORT,'Read-only migration backup');"
              + " END");
  }
}
