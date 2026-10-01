package vn.edu.studymate;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import vn.edu.studymate.auth.PasswordHasher;
import vn.edu.studymate.model.Entry;

public class LegacyDatabaseHelper extends SQLiteOpenHelper {
  public LegacyDatabaseHelper(Context c) {
    this(c, "studymate.db");
  }

  public LegacyDatabaseHelper(Context c, String name) {
    super(c, name, null, 1);
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
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int version) {
    throw new IllegalStateException("Cần migration khi nâng phiên bản database");
  }

  public long register(String name, String email, String password) {
    String salt = PasswordHasher.salt();
    ContentValues v = new ContentValues();
    v.put("name", name);
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
    return e;
  }

  private long num(Cursor c, String col) {
    return c.getLong(c.getColumnIndexOrThrow(col));
  }

  private String str(Cursor c, String col) {
    return c.getString(c.getColumnIndexOrThrow(col));
  }

  public long save(long user, Entry e) {
    if (user < 1) throw new IllegalArgumentException("Chưa đăng nhập");
    if (e.subjectId > 0) {
      Entry subject = get(user, e.subjectId);
      if (subject == null || !"subject".equals(subject.kind))
        throw new IllegalArgumentException("Môn học không hợp lệ");
    }
    Entry old = e.id > 0 ? get(user, e.id) : null;
    if (e.id > 0 && old == null) throw new IllegalArgumentException("Không tìm thấy dữ liệu");
    ContentValues v = new ContentValues();
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
    if (old == null || old.dueAt != e.dueAt || !old.status.equals(e.status))
      v.put("notified_at", 0);
    if (e.id == 0) e.id = getWritableDatabase().insertOrThrow("entries", null, v);
    else
      getWritableDatabase()
          .update("entries", v, "id=? AND user_id=?", new String[] {"" + e.id, "" + user});
    return e.id;
  }

  public void delete(long user, long id) {
    getWritableDatabase()
        .delete("entries", "id=? AND user_id=?", new String[] {"" + id, "" + user});
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

  public void markNotified(long user, long id, long now) {
    ContentValues v = new ContentValues();
    v.put("notified_at", now);
    getWritableDatabase()
        .update("entries", v, "id=? AND user_id=?", new String[] {"" + id, "" + user});
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
      Entry subject = new Entry();
      subject.kind = "subject";
      subject.title = "Lập trình Android";
      subject.detail = "Giảng viên: Nguyễn Minh • Học kỳ 1";
      save(u, subject);
      Entry subject2 = new Entry();
      subject2.kind = "subject";
      subject2.title = "Cơ sở dữ liệu";
      subject2.credits = 4;
      save(u, subject2);
      long now = System.currentTimeMillis();
      for (int i = 0; i < 3; i++) {
        Entry t = new Entry();
        t.kind = "task";
        t.title =
            new String[] {"Hoàn thiện giao diện đăng nhập", "Viết báo cáo đồ án", "Ôn truy vấn SQL"}
                [i];
        t.subjectId = i == 2 ? subject2.id : subject.id;
        t.dueAt = now + (i == 0 ? 5 : i == 1 ? 48 : 96) * 3600000L;
        t.priority = i == 0 ? 3 : 2;
        t.status = i == 0 ? "Đang làm" : "Chưa làm";
        t.detail = "Chia nhỏ công việc và kiểm tra kết quả sau khi hoàn thành.";
        save(u, t);
      }
      Entry schedule = new Entry();
      schedule.kind = "schedule";
      schedule.title = "Thực hành Android";
      schedule.subjectId = subject.id;
      schedule.place = "Phòng B203";
      schedule.day = 2;
      save(u, schedule);
      Entry exam = new Entry();
      exam.kind = "exam";
      exam.title = "Kiểm tra giữa kỳ SQL";
      exam.subjectId = subject2.id;
      exam.dueAt = now + 6 * 86400000L;
      exam.place = "Phòng A101";
      save(u, exam);
      Entry note = new Entry();
      note.kind = "note";
      note.title = "Ý tưởng trình bày đồ án";
      note.subjectId = subject.id;
      note.detail =
          "1. Giới thiệu vấn đề\n"
              + "2. Demo thêm deadline\n"
              + "3. Giải thích thuật toán xếp ưu tiên\n"
              + "4. Kiểm thử và hướng phát triển";
      save(u, note);
      Entry grade = new Entry();
      grade.kind = "grade";
      grade.title = "Điểm thực hành";
      grade.subjectId = subject.id;
      grade.score = 8.5;
      grade.weight = 30;
      save(u, grade);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}
