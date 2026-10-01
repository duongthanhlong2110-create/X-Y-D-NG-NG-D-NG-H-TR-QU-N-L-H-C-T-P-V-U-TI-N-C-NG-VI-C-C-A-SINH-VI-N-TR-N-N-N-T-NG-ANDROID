package vn.edu.studymate.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import vn.edu.studymate.auth.PasswordHasher;
import vn.edu.studymate.model.Entry;

public class DatabaseHelper extends SQLiteOpenHelper {
  public static final Object DATA_LOCK = new Object();

  public DatabaseHelper(Context c) {
    this(c, "studymate.db");
  }

  public DatabaseHelper(Context c, String name) {
    super(c, name, null, 5);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,email TEXT NOT"
            + " NULL UNIQUE COLLATE NOCASE,salt TEXT NOT NULL,password TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL"
            + " REFERENCES users(id) ON DELETE CASCADE,kind TEXT NOT NULL CHECK(kind IN"
            + " ('subject','task','schedule','exam','note','grade')),title TEXT NOT NULL,detail"
            + " TEXT NOT NULL DEFAULT '',subject_id INTEGER REFERENCES entries(id) ON DELETE SET"
            + " NULL,due_at INTEGER NOT NULL DEFAULT 0,priority INTEGER NOT NULL DEFAULT 2"
            + " CHECK(priority BETWEEN 1 AND 3),status TEXT NOT NULL DEFAULT 'Chưa làm',place TEXT"
            + " NOT NULL DEFAULT '',day INTEGER NOT NULL DEFAULT 2,start_minute INTEGER NOT NULL"
            + " DEFAULT 480,end_minute INTEGER NOT NULL DEFAULT 540,score REAL NOT NULL DEFAULT"
            + " 0,weight REAL NOT NULL DEFAULT 1,credits REAL NOT NULL DEFAULT 3,notified_at"
            + " INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE INDEX entries_owner_kind ON entries(user_id,kind)");
    db.execSQL("CREATE INDEX entries_due ON entries(user_id,due_at)");
    migrateCurrentSchema(db);
    NormalizedSchema.migrate(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int version) {
    if (old < 4) migrateCurrentSchema(db);
    if (old < 5) NormalizedSchema.migrate(db);
  }

  private void migrateCurrentSchema(SQLiteDatabase db) {
    Set<String> existing = new HashSet<>();
    try (Cursor c = db.rawQuery("PRAGMA table_info(entries)", null)) {
      while (c.moveToNext()) existing.add(c.getString(c.getColumnIndexOrThrow("name")));
    }
    String[] columns = {
      "code TEXT NOT NULL DEFAULT ''",
      "lecturer TEXT NOT NULL DEFAULT ''",
      "valid_from TEXT NOT NULL DEFAULT '1970-01-01'",
      "valid_to TEXT NOT NULL DEFAULT '2099-12-31'",
      "zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh'",
      "created_at INTEGER NOT NULL DEFAULT 0",
      "updated_at INTEGER NOT NULL DEFAULT 0",
      "completed_at INTEGER NOT NULL DEFAULT 0",
      "end_at INTEGER NOT NULL DEFAULT 0",
      "remind_enabled INTEGER NOT NULL DEFAULT 1",
      "minutes_before INTEGER NOT NULL DEFAULT 60",
      "handled_at INTEGER NOT NULL DEFAULT 0",
      "reminder_before_min INTEGER NOT NULL DEFAULT 60"
    };
    for (String col : columns)
      if (!existing.contains(col.substring(0, col.indexOf(' '))))
        db.execSQL("ALTER TABLE entries ADD COLUMN " + col);
    if (!existing.contains("end_at"))
      db.execSQL("UPDATE entries SET end_at=due_at+3600000 WHERE kind='exam'");
    if (!existing.contains("created_at")) db.execSQL("UPDATE entries SET created_at=id");
    if (!existing.contains("updated_at")) db.execSQL("UPDATE entries SET updated_at=id");
    if (!existing.contains("handled_at"))
      db.execSQL(
          "UPDATE entries SET handled_at=due_at WHERE notified_at>0 AND kind IN ('task','exam')");
    if (!existing.contains("minutes_before") && existing.contains("reminder_before_min"))
      db.execSQL("UPDATE entries SET minutes_before=MAX(0,MIN(10080,reminder_before_min))");
    if (!existing.contains("remind_enabled") && existing.contains("reminder_before_min"))
      db.execSQL("UPDATE entries SET remind_enabled=0 WHERE reminder_before_min<0");
    db.execSQL(
        "UPDATE entries SET reminder_before_min=CASE WHEN remind_enabled=0 THEN -1 ELSE"
            + " minutes_before END");
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS subject_code ON entries(user_id,code COLLATE NOCASE)"
            + " WHERE kind='subject' AND code!=''");
    Set<String> userColumns = new HashSet<>();
    try (Cursor c = db.rawQuery("PRAGMA table_info(users)", null)) {
      while (c.moveToNext()) userColumns.add(c.getString(c.getColumnIndexOrThrow("name")));
    }
    if (!userColumns.contains("reminder_revision"))
      db.execSQL("ALTER TABLE users ADD COLUMN reminder_revision INTEGER NOT NULL DEFAULT 1");
    if (!userColumns.contains("reminder_scheduled_revision"))
      db.execSQL(
          "ALTER TABLE users ADD COLUMN reminder_scheduled_revision INTEGER NOT NULL DEFAULT 0");
    for (String event : new String[] {"INSERT", "UPDATE", "DELETE"}) {
      String row = event.equals("DELETE") ? "OLD" : "NEW";
      db.execSQL(
          "CREATE TRIGGER IF NOT EXISTS reminder_dirty_"
              + event
              + " AFTER "
              + event
              + " ON entries BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id="
              + row
              + ".user_id; END");
    }
  }

  public long reminderRevision(long user) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT reminder_revision FROM users WHERE id=?", new String[] {"" + user})) {
      return c.moveToFirst() ? c.getLong(0) : -1;
    }
  }

  public boolean acknowledgeReminders(long user, long revision) {
    ContentValues v = new ContentValues();
    v.put("reminder_scheduled_revision", revision);
    return getWritableDatabase()
            .update(
                "users", v, "id=? AND reminder_revision=?", new String[] {"" + user, "" + revision})
        == 1;
  }

  public long register(String name, String email, String password) {
    if (name == null
        || name.trim().isEmpty()
        || email == null
        || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        || password == null
        || password.length() < 6
        || password.length() > 128)
      throw new IllegalArgumentException("Họ tên, email hoặc mật khẩu không hợp lệ.");
    String salt = PasswordHasher.salt();
    ContentValues v = new ContentValues();
    v.put("name", name.trim());
    v.put("email", email.trim().toLowerCase(Locale.ROOT));
    v.put("salt", salt);
    v.put("password", PasswordHasher.hash(password, salt));
    try {
      return getWritableDatabase().insertOrThrow("users", null, v);
    } catch (SQLiteConstraintException ex) {
      return -1;
    }
  }

  public long login(String email, String password) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,salt,password FROM users WHERE email=?",
                new String[] {email.trim().toLowerCase(Locale.ROOT)})) {
      if (c.moveToFirst() && PasswordHasher.verify(password, c.getString(1), c.getString(2)))
        return c.getLong(0);
      return -1;
    }
  }

  public String name(long user) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT name FROM users WHERE id=?", new String[] {"" + user})) {
      return c.moveToFirst() ? c.getString(0) : "Sinh viên";
    }
  }

  public List<Entry> list(long user, String kind) {
    List<Entry> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "entries",
                null,
                "user_id=? AND kind=?",
                new String[] {"" + user, kind},
                null,
                null,
                "due_at ASC,id DESC")) {
      while (c.moveToNext()) result.add(read(c));
    }
    return result;
  }

  public Entry get(long user, long id) {
    try (Cursor c =
        getReadableDatabase()
            .query(
                "entries",
                null,
                "user_id=? AND id=?",
                new String[] {"" + user, "" + id},
                null,
                null,
                null)) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  private Entry read(Cursor c) {
    Entry e = new Entry();
    e.id = num(c, "id");
    e.userId = num(c, "user_id");
    e.kind = str(c, "kind");
    e.title = str(c, "title");
    e.detail = str(c, "detail");
    e.subjectId = num(c, "subject_id");
    e.dueAt = num(c, "due_at");
    e.priority = (int) num(c, "priority");
    e.status = str(c, "status");
    e.place = str(c, "place");
    e.day = (int) num(c, "day");
    e.startMinute = (int) num(c, "start_minute");
    e.endMinute = (int) num(c, "end_minute");
    e.score = c.getDouble(c.getColumnIndexOrThrow("score"));
    e.weight = c.getDouble(c.getColumnIndexOrThrow("weight"));
    e.credits = c.getDouble(c.getColumnIndexOrThrow("credits"));
    e.code = str(c, "code");
    e.lecturer = str(c, "lecturer");
    e.validFrom = str(c, "valid_from");
    e.validTo = str(c, "valid_to");
    e.zoneId = str(c, "zone_id");
    e.createdAt = num(c, "created_at");
    e.updatedAt = num(c, "updated_at");
    e.completedAt = num(c, "completed_at");
    e.endAt = num(c, "end_at");
    e.handledAt = num(c, "handled_at");
    e.remindEnabled = num(c, "remind_enabled") != 0;
    e.minutesBefore = (int) num(c, "minutes_before");
    return e;
  }

  private long num(Cursor c, String col) {
    return c.getLong(c.getColumnIndexOrThrow(col));
  }

  private String str(Cursor c, String col) {
    return c.getString(c.getColumnIndexOrThrow(col));
  }

  public long save(long user,Entry e){
    synchronized(DATA_LOCK){
      SQLiteDatabase sql=getWritableDatabase();long originalId=e.id;
      try{sql.beginTransaction();try{long id=saveLocked(user,e);sql.setTransactionSuccessful();return id;}finally{sql.endTransaction();}}
      catch(RuntimeException failure){e.id=originalId;throw failure;}
    }
  }

  private long saveLocked(long user, Entry e) {
    if (user < 1) throw new IllegalArgumentException("Chưa đăng nhập");
    if (e.subjectId > 0) {
      Entry subject = get(user, e.subjectId);
      if (subject == null || !"subject".equals(subject.kind))
        throw new IllegalArgumentException("Môn học không hợp lệ");
    }
    Entry old = e.id > 0 ? get(user, e.id) : null;
    if (e.id > 0 && old == null) throw new IllegalArgumentException("Không tìm thấy dữ liệu");
    if (old != null && !old.kind.equals(e.kind))
      throw new IllegalArgumentException("Không thể đổi loại bản ghi.");
    e.title = e.title == null ? "" : e.title.trim();
    vn.edu.studymate.logic.Validation.validate(e);
    long now = System.currentTimeMillis();
    e.createdAt = old == null ? now : old.createdAt;
    e.updatedAt = now;
    e.completedAt =
        "Hoàn thành".equals(e.status)
            ? (old != null && old.completedAt > 0 ? old.completedAt : now)
            : 0;
    ContentValues v = new ContentValues();
    v.put("code", e.code.trim().toUpperCase(Locale.ROOT));
    v.put("lecturer", e.lecturer);
    v.put("valid_from", e.validFrom);
    v.put("valid_to", e.validTo);
    v.put("zone_id", e.zoneId);
    v.put("created_at", e.createdAt);
    v.put("updated_at", e.updatedAt);
    v.put("completed_at", e.completedAt);
    v.put("end_at", e.endAt);
    v.put("remind_enabled", e.remindEnabled ? 1 : 0);
    v.put("minutes_before", e.minutesBefore);
    v.put("reminder_before_min", e.remindEnabled ? e.minutesBefore : -1);
    if (old == null
        || old.dueAt != e.dueAt
        || (e.kind.equals("exam") && old.endAt != e.endAt)
        || (e.kind.equals("schedule") && old.day != e.day)
        || (e.kind.equals("schedule") && old.startMinute != e.startMinute)
        || (e.kind.equals("schedule") && old.endMinute != e.endMinute)
        || (e.kind.equals("schedule") && !old.validFrom.equals(e.validFrom))
        || (e.kind.equals("schedule") && !old.validTo.equals(e.validTo))
        || !old.zoneId.equals(e.zoneId)
        || old.remindEnabled != e.remindEnabled
        || old.minutesBefore != e.minutesBefore
        || !old.status.equals(e.status)) v.put("handled_at", 0);
    v.put("user_id", user);
    v.put("kind", e.kind);
    v.put("title", e.title);
    v.put("detail", e.detail);
    if (e.subjectId > 0) v.put("subject_id", e.subjectId);
    else v.putNull("subject_id");
    v.put("due_at", e.dueAt);
    v.put("priority", e.priority);
    v.put("status", e.status);
    v.put("place", e.place);
    v.put("day", e.day);
    v.put("start_minute", e.startMinute);
    v.put("end_minute", e.endMinute);
    v.put("score", e.score);
    v.put("weight", e.weight);
    v.put("credits", e.credits);
    if (old == null
        || old.dueAt != e.dueAt
        || !old.status.equals(e.status)
        || old.minutesBefore != e.minutesBefore
        || old.remindEnabled != e.remindEnabled) v.put("notified_at", 0);
    SQLiteDatabase sql = getWritableDatabase();
    String table = NormalizedSchema.table(e.kind);
    ContentValues values = new ContentValues(v);
    Set<String> columns = NormalizedSchema.columns(sql, table);
    for (String key : new ArrayList<>(values.keySet()))
      if (!columns.contains(key)) values.remove(key);
    if (e.id == 0) {
      try (Cursor c = sql.rawQuery("SELECT next_id FROM id_sequence WHERE singleton=1", null)) {
        c.moveToFirst();
        e.id = c.getLong(0);
      }
      sql.execSQL("UPDATE id_sequence SET next_id=next_id+1 WHERE singleton=1");
      values.put("id", e.id);
      sql.insertOrThrow(table, null, values);
    } else sql.update(table, values, "id=? AND user_id=?", new String[] {"" + e.id, "" + user});
    if (Arrays.asList("task", "schedule", "exam").contains(e.kind)) {
      String fk = NormalizedSchema.fk(e.kind);
      List<Integer> offsets = new ArrayList<>();
      if (e.remindEnabled && (!e.kind.equals("task") || e.dueAt > 0)) {
        offsets.add(e.minutesBefore);
        if (e.reminderOffsets != null) offsets.addAll(e.reminderOffsets);
        else if (old != null) {
          List<Integer> existing = reminderOffsets(user, e.id, e.kind);
          if (!existing.isEmpty()) existing.remove(0);
          offsets.addAll(existing);
        }
      }
      LinkedHashSet<Integer> wanted = new LinkedHashSet<>(offsets);
      for (int minutes : wanted)
        if (minutes < 0 || minutes > 10080)
          throw new IllegalArgumentException("Khoảng nhắc không hợp lệ");
      Map<Integer, Long> stored = new LinkedHashMap<>();
      try (Cursor c =
          sql.rawQuery(
              "SELECT id,minutes_before FROM reminders WHERE user_id=? AND "
                  + fk
                  + "=? ORDER BY id",
              new String[] {"" + user, "" + e.id})) {
        while (c.moveToNext()) stored.put(c.getInt(1), c.getLong(0));
      }
      for (Map.Entry<Integer, Long> row : stored.entrySet())
        if (!wanted.contains(row.getKey()))
          sql.delete("reminders", "id=?", new String[] {"" + row.getValue()});
      boolean reset = v.containsKey("handled_at");
      int order = 0;
      for (int minutes : wanted) {
        ContentValues r = new ContentValues();
        r.put("sort_order", order++);
        r.put("user_id", user);
        r.put(fk, e.id);
        r.put("minutes_before", minutes);
        if (reset) {
          r.put("handled_at", 0);
          r.put("notified_at", 0);
        }
        if (stored.containsKey(minutes))
          sql.update("reminders", r, "id=?", new String[] {"" + stored.get(minutes)});
        else sql.insertOrThrow("reminders", null, r);
      }
    }
    return e.id;
  }

  public void delete(long user, long id) {
    synchronized (DATA_LOCK) {
      deleteLocked(user, id);
    }
  }

  private void deleteLocked(long user, long id) {
    SQLiteDatabase sql = getWritableDatabase();
    sql.beginTransaction();
    try {
      Entry target = get(user, id);
      if (target != null && target.kind.equals("subject")) {
        try (Cursor c =
            sql.rawQuery(
                "SELECT COUNT(*) FROM entries WHERE subject_id=CAST(? AS INTEGER)",
                new String[] {"" + id})) {
          c.moveToFirst();
          if (c.getLong(0) > 0)
            throw new IllegalArgumentException(
                "Môn còn công việc, lịch, ghi chú hoặc điểm liên quan. Hãy đổi môn hoặc xử lý các"
                    + " mục đó trước khi xóa.");
        }
      }
      if (target != null)
        sql.delete(
            NormalizedSchema.table(target.kind),
            "id=? AND user_id=?",
            new String[] {"" + id, "" + user});
      sql.setTransactionSuccessful();
    } finally {
      sql.endTransaction();
    }
  }

  public List<Entry> reminders(long user, long now) {
    List<Entry> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM entries WHERE user_id=? AND kind IN ('task','exam') AND"
                    + " status!='Hoàn thành' AND due_at<=? AND due_at>=? AND notified_at=0",
                new String[] {"" + user, "" + (now + 86400000L), "" + (now - 86400000L)})) {
      while (c.moveToNext()) out.add(read(c));
    }
    return out;
  }

  public void markHandled(long user, long id, long occurrence) {
    ContentValues v = new ContentValues();
    v.put("handled_at", occurrence);
    Entry e = get(user, id);
    if (e != null && Arrays.asList("task", "schedule", "exam").contains(e.kind))
      getWritableDatabase()
          .update(
              "reminders",
              v,
              NormalizedSchema.fk(e.kind) + "=? AND user_id=?",
              new String[] {"" + id, "" + user});
  }

  public void markNotified(long user, long id, long now) {
    ContentValues v = new ContentValues();
    v.put("notified_at", now);
    Entry e = get(user, id);
    if (e != null && Arrays.asList("task", "schedule", "exam").contains(e.kind))
      getWritableDatabase()
          .update(
              "reminders",
              v,
              NormalizedSchema.fk(e.kind) + "=? AND user_id=?",
              new String[] {"" + id, "" + user});
  }

  public List<Integer> reminderOffsets(long user, long id, String kind) {
    List<Integer> values = new ArrayList<>();
    if (!Arrays.asList("task", "schedule", "exam").contains(kind)) return values;
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT minutes_before FROM reminders WHERE user_id=? AND "
                    + NormalizedSchema.fk(kind)
                    + "=? ORDER BY sort_order,id",
                new String[] {"" + user, "" + id})) {
      while (c.moveToNext()) values.add(c.getInt(0));
    }
    return values;
  }

  /** One projection for each independently tracked reminder of an owned target. */
  public List<Entry> scheduledReminders(long user) {
    List<Entry> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT"
                    + " id,COALESCE(task_id,class_schedule_id,exam_id),minutes_before,handled_at,notified_at"
                    + " FROM reminders WHERE user_id=? ORDER BY id",
                new String[] {"" + user})) {
      while (c.moveToNext()) {
        Entry e = get(user, c.getLong(1));
        if (e != null) {
          e.reminderId = c.getLong(0);
          e.minutesBefore = c.getInt(2);
          e.handledAt = c.getLong(3);
          e.remindEnabled = true;
          out.add(e);
        }
      }
    }
    return out;
  }

  public void markReminderHandled(long user, long reminderId, long occurrence, long receivedAt) {
    ContentValues v = new ContentValues();
    v.put("handled_at", occurrence);
    v.put("notified_at", receivedAt);
    getWritableDatabase()
        .update("reminders", v, "id=? AND user_id=?", new String[] {"" + reminderId, "" + user});
  }

  public synchronized void seedDemo() {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      try (Cursor c = db.rawQuery("SELECT id FROM users WHERE email='demo@studymate.vn'", null)) {
        if (c.moveToFirst()) {
          db.setTransactionSuccessful();
          return;
        }
      }
      long u = register("Sinh viên Demo", "demo@studymate.vn", "Demo123!");

      // Môn 1: Phân tích dữ liệu với Python (Thứ 3: Tiết 6-9)
      Entry s1 = new Entry();
      s1.kind = "subject";
      s1.title = "Phân tích dữ liệu với Python";
      s1.code = "PY505";
      s1.lecturer = "Nguyễn Thị Mười Phương";
      s1.detail = "Giảng viên chủ nhiệm lớp 523100C";
      s1.credits = 3;
      save(u, s1);

      Entry sch1 = new Entry();
      sch1.kind = "schedule";
      sch1.title = "Phân tích dữ liệu với Python";
      sch1.subjectId = s1.id;
      sch1.place = "Phòng 505";
      sch1.day = 3; // Thứ 3
      sch1.startMinute = 12 * 60 + 30; // Tiết 6: 12:30 -> 750
      sch1.endMinute = 16 * 60 + 0; // Tiết 9 kết thúc: 16:00 -> 960
      save(u, sch1);

      // Môn 2: Tiếng Anh chuyên ngành (Thứ 4: Tiết 6-9)
      Entry s2 = new Entry();
      s2.kind = "subject";
      s2.title = "Tiếng Anh chuyên ngành";
      s2.code = "EN505";
      s2.lecturer = "Lê Thị Kiều Oanh";
      s2.credits = 3;
      save(u, s2);

      Entry sch2 = new Entry();
      sch2.kind = "schedule";
      sch2.title = "Tiếng Anh chuyên ngành";
      sch2.subjectId = s2.id;
      sch2.place = "Phòng 505";
      sch2.day = 4; // Thứ 4
      sch2.startMinute = 12 * 60 + 30; // 12:30
      sch2.endMinute = 16 * 60 + 0; // 16:00
      save(u, sch2);

      // Môn 3: Tương tác người máy (Thứ 5: Tiết 6-8)
      Entry s3 = new Entry();
      s3.kind = "subject";
      s3.title = "Tương tác người máy";
      s3.code = "HCI505";
      s3.lecturer = "Trần Thị Đông";
      s3.credits = 3;
      save(u, s3);

      Entry sch3 = new Entry();
      sch3.kind = "schedule";
      sch3.title = "Tương tác người máy";
      sch3.subjectId = s3.id;
      sch3.place = "Phòng 505";
      sch3.day = 5; // Thứ 5
      sch3.startMinute = 12 * 60 + 30; // 12:30
      sch3.endMinute = 15 * 60 + 0; // Tiết 8 kết thúc: 15:00 -> 900
      save(u, sch3);

      // Môn 4: Thị giác máy tính và ứng dụng (Thứ 6: Tiết 6-9)
      Entry s4 = new Entry();
      s4.kind = "subject";
      s4.title = "Thị giác máy tính và ứng dụng";
      s4.code = "CV505";
      s4.lecturer = "Cô Chu Hải Hà";
      s4.credits = 3;
      save(s4.userId = u, s4);

      Entry sch4 = new Entry();
      sch4.kind = "schedule";
      sch4.title = "Thị giác máy tính và ứng dụng";
      sch4.subjectId = s4.id;
      sch4.place = "Phòng 505";
      sch4.day = 6; // Thứ 6
      sch4.startMinute = 12 * 60 + 30; // 12:30
      sch4.endMinute = 16 * 60 + 0; // 16:00
      save(u, sch4);

      long now = System.currentTimeMillis();
      for (int i = 0; i < 3; i++) {
        Entry t = new Entry();
        t.kind = "task";
        t.title =
            new String[] {
                  "Bài tập thực hành Python số 1",
                  "Dịch thuật ngữ Tiếng Anh",
                  "Vẽ wireframe Tương tác"
                }
                [i];
        t.subjectId = i == 0 ? s1.id : i == 1 ? s2.id : s3.id;
        t.dueAt = now + (i == 0 ? 5 : i == 1 ? 48 : 96) * 3600000L;
        t.priority = i == 0 ? 3 : 2;
        t.status = i == 0 ? "Đang làm" : "Chưa làm";
        t.detail = "Hoàn thành đúng hạn phục vụ đánh giá quá trình lớp 523100C.";
        save(u, t);
      }

      Entry note = new Entry();
      note.kind = "note";
      note.title = "Thông tin GVCN Lớp 523100C";
      note.subjectId = s1.id;
      note.detail = "GVCN: Cô Nguyễn Thị Lan Phương\nSĐT: 0942.761.580\nHọc kỳ học chiều.";
      save(u, note);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}
