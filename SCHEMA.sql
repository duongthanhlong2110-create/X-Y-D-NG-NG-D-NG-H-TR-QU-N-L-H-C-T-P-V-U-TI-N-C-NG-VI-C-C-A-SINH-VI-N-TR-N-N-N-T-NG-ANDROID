-- Exported schema only from the task-owned Android test database. No account or study data.
PRAGMA foreign_keys=ON;

CREATE TABLE android_metadata (locale TEXT);

CREATE TABLE class_schedules(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,place TEXT NOT NULL DEFAULT '',day INTEGER NOT NULL DEFAULT 2 CHECK(day BETWEEN 2 AND 8),start_minute INTEGER NOT NULL DEFAULT 480 CHECK(start_minute>=0),end_minute INTEGER NOT NULL DEFAULT 540 CHECK(end_minute<=1440 AND end_minute>start_minute),valid_from TEXT NOT NULL DEFAULT '1970-01-01',valid_to TEXT NOT NULL DEFAULT '2099-12-31' CHECK(valid_to>=valid_from),zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',subject_id INTEGER,UNIQUE(id,user_id),FOREIGN KEY(subject_id,user_id) REFERENCES subjects(id,user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED);

CREATE TABLE exams(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,due_at INTEGER NOT NULL CHECK(due_at>0),end_at INTEGER NOT NULL CHECK(end_at>due_at),place TEXT NOT NULL DEFAULT '',zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',subject_id INTEGER,UNIQUE(id,user_id),FOREIGN KEY(subject_id,user_id) REFERENCES subjects(id,user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED);

CREATE TABLE grades(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,score REAL NOT NULL DEFAULT 0 CHECK(score BETWEEN 0 AND 10),weight REAL NOT NULL DEFAULT 1 CHECK(weight>0 AND weight<=1000),subject_id INTEGER,UNIQUE(id,user_id),FOREIGN KEY(subject_id,user_id) REFERENCES subjects(id,user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED);

CREATE TABLE id_sequence(singleton INTEGER PRIMARY KEY CHECK(singleton=1),next_id INTEGER NOT NULL);

CREATE TABLE legacy_entries_v4(
  id INT,
  user_id INT,
  kind TEXT,
  title TEXT,
  detail TEXT,
  subject_id INT,
  due_at INT,
  priority INT,
  status TEXT,
  place TEXT,
  day INT,
  start_minute INT,
  end_minute INT,
  score REAL,
  weight REAL,
  credits REAL,
  notified_at INT,
  code TEXT,
  lecturer TEXT,
  valid_from TEXT,
  valid_to TEXT,
  zone_id TEXT,
  created_at INT,
  updated_at INT,
  completed_at INT,
  end_at INT,
  remind_enabled INT,
  minutes_before INT,
  handled_at INT,
  reminder_before_min INT
);

CREATE TABLE migration_issues(id INTEGER PRIMARY KEY AUTOINCREMENT,legacy_id INTEGER,reason TEXT NOT NULL);

CREATE TABLE notes(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,subject_id INTEGER,UNIQUE(id,user_id),FOREIGN KEY(subject_id,user_id) REFERENCES subjects(id,user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED);

CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,task_id INTEGER,class_schedule_id INTEGER,exam_id INTEGER,sort_order INTEGER NOT NULL DEFAULT 0 CHECK(sort_order>=0),minutes_before INTEGER NOT NULL CHECK(minutes_before BETWEEN 0 AND 10080),handled_at INTEGER NOT NULL DEFAULT 0,notified_at INTEGER NOT NULL DEFAULT 0,CHECK((task_id IS NOT NULL)+(class_schedule_id IS NOT NULL)+(exam_id IS NOT NULL)=1),FOREIGN KEY(task_id,user_id) REFERENCES tasks(id,user_id) ON DELETE CASCADE,FOREIGN KEY(class_schedule_id,user_id) REFERENCES class_schedules(id,user_id) ON DELETE CASCADE,FOREIGN KEY(exam_id,user_id) REFERENCES exams(id,user_id) ON DELETE CASCADE);

CREATE TABLE subjects(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,code TEXT NOT NULL DEFAULT '',lecturer TEXT NOT NULL DEFAULT '',credits REAL NOT NULL DEFAULT 3 CHECK(credits BETWEEN 1 AND 30 AND credits=CAST(credits AS INTEGER)),UNIQUE(id,user_id));

CREATE TABLE tasks(id INTEGER PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 160),detail TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,due_at INTEGER NOT NULL DEFAULT 0 CHECK(due_at>=0),priority INTEGER NOT NULL DEFAULT 2 CHECK(priority BETWEEN 1 AND 3),status TEXT NOT NULL DEFAULT 'Chưa làm' CHECK(status IN ('Chưa làm','Đang làm','Hoàn thành')),completed_at INTEGER NOT NULL DEFAULT 0,zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',subject_id INTEGER,UNIQUE(id,user_id),FOREIGN KEY(subject_id,user_id) REFERENCES subjects(id,user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED);

CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,email TEXT NOT NULL UNIQUE COLLATE NOCASE,salt TEXT NOT NULL,password TEXT NOT NULL, reminder_revision INTEGER NOT NULL DEFAULT 1, reminder_scheduled_revision INTEGER NOT NULL DEFAULT 0);

CREATE INDEX class_schedules_owner ON class_schedules(user_id);

CREATE INDEX exams_owner ON exams(user_id);

CREATE INDEX grades_owner ON grades(user_id);

CREATE INDEX notes_owner ON notes(user_id);

CREATE UNIQUE INDEX reminders_class_schedule_id ON reminders(class_schedule_id,minutes_before) WHERE class_schedule_id IS NOT NULL;

CREATE UNIQUE INDEX reminders_exam_id ON reminders(exam_id,minutes_before) WHERE exam_id IS NOT NULL;

CREATE INDEX reminders_owner ON reminders(user_id);

CREATE UNIQUE INDEX reminders_task_id ON reminders(task_id,minutes_before) WHERE task_id IS NOT NULL;

CREATE UNIQUE INDEX subject_code ON subjects(user_id,code COLLATE NOCASE) WHERE code!='';

CREATE INDEX subjects_owner ON subjects(user_id);

CREATE INDEX task_due ON tasks(user_id,due_at);

CREATE INDEX tasks_owner ON tasks(user_id);

CREATE VIEW entries AS SELECT CAST(t.id AS INTEGER) AS id,CAST(t.user_id AS INTEGER) AS user_id,'subject' AS kind,t.title AS title,t.detail AS detail,CAST(NULL AS INTEGER) AS subject_id,CAST(0 AS INTEGER) AS due_at,CAST(2 AS INTEGER) AS priority,'Chưa làm' AS status,'' AS place,CAST(2 AS INTEGER) AS day,CAST(480 AS INTEGER) AS start_minute,CAST(540 AS INTEGER) AS end_minute,0 AS score,1 AS weight,t.credits AS credits,t.code AS code,t.lecturer AS lecturer,'1970-01-01' AS valid_from,'2099-12-31' AS valid_to,'Asia/Ho_Chi_Minh' AS zone_id,CAST(t.created_at AS INTEGER) AS created_at,CAST(t.updated_at AS INTEGER) AS updated_at,CAST(0 AS INTEGER) AS completed_at,CAST(0 AS INTEGER) AS end_at,CAST(0 AS INTEGER) AS handled_at,CAST(0 AS INTEGER) AS notified_at,CAST(0 AS INTEGER) AS remind_enabled,CAST(60 AS INTEGER) AS minutes_before,CAST(-1 AS INTEGER) AS reminder_before_min FROM subjects t UNION ALL SELECT CAST(t.id AS INTEGER) AS id,CAST(t.user_id AS INTEGER) AS user_id,'task' AS kind,t.title AS title,t.detail AS detail,CAST(t.subject_id AS INTEGER) AS subject_id,CAST(t.due_at AS INTEGER) AS due_at,CAST(t.priority AS INTEGER) AS priority,t.status AS status,'' AS place,CAST(2 AS INTEGER) AS day,CAST(480 AS INTEGER) AS start_minute,CAST(540 AS INTEGER) AS end_minute,0 AS score,1 AS weight,3 AS credits,'' AS code,'' AS lecturer,'1970-01-01' AS valid_from,'2099-12-31' AS valid_to,t.zone_id AS zone_id,CAST(t.created_at AS INTEGER) AS created_at,CAST(t.updated_at AS INTEGER) AS updated_at,CAST(t.completed_at AS INTEGER) AS completed_at,CAST(0 AS INTEGER) AS end_at,CAST(COALESCE((SELECT handled_at FROM reminders r WHERE r.task_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),0) AS INTEGER) AS handled_at,CAST(COALESCE((SELECT notified_at FROM reminders r WHERE r.task_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),0) AS INTEGER) AS notified_at,CAST(EXISTS(SELECT 1 FROM reminders r WHERE r.task_id=t.id AND r.user_id=t.user_id) AS INTEGER) AS remind_enabled,CAST(COALESCE((SELECT minutes_before FROM reminders r WHERE r.task_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),60) AS INTEGER) AS minutes_before,CAST(COALESCE((SELECT minutes_before FROM reminders r WHERE r.task_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),-1) AS INTEGER) AS reminder_before_min FROM tasks t UNION ALL SELECT CAST(t.id AS INTEGER) AS id,CAST(t.user_id AS INTEGER) AS user_id,'schedule' AS kind,t.title AS title,t.detail AS detail,CAST(t.subject_id AS INTEGER) AS subject_id,CAST(0 AS INTEGER) AS due_at,CAST(2 AS INTEGER) AS priority,'Chưa làm' AS status,t.place AS place,CAST(t.day AS INTEGER) AS day,CAST(t.start_minute AS INTEGER) AS start_minute,CAST(t.end_minute AS INTEGER) AS end_minute,0 AS score,1 AS weight,3 AS credits,'' AS code,'' AS lecturer,t.valid_from AS valid_from,t.valid_to AS valid_to,t.zone_id AS zone_id,CAST(t.created_at AS INTEGER) AS created_at,CAST(t.updated_at AS INTEGER) AS updated_at,CAST(0 AS INTEGER) AS completed_at,CAST(0 AS INTEGER) AS end_at,CAST(COALESCE((SELECT handled_at FROM reminders r WHERE r.class_schedule_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),0) AS INTEGER) AS handled_at,CAST(COALESCE((SELECT notified_at FROM reminders r WHERE r.class_schedule_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),0) AS INTEGER) AS notified_at,CAST(EXISTS(SELECT 1 FROM reminders r WHERE r.class_schedule_id=t.id AND r.user_id=t.user_id) AS INTEGER) AS remind_enabled,CAST(COALESCE((SELECT minutes_before FROM reminders r WHERE r.class_schedule_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),60) AS INTEGER) AS minutes_before,CAST(COALESCE((SELECT minutes_before FROM reminders r WHERE r.class_schedule_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),-1) AS INTEGER) AS reminder_before_min FROM class_schedules t UNION ALL SELECT CAST(t.id AS INTEGER) AS id,CAST(t.user_id AS INTEGER) AS user_id,'exam' AS kind,t.title AS title,t.detail AS detail,CAST(t.subject_id AS INTEGER) AS subject_id,CAST(t.due_at AS INTEGER) AS due_at,CAST(2 AS INTEGER) AS priority,'Chưa làm' AS status,t.place AS place,CAST(2 AS INTEGER) AS day,CAST(480 AS INTEGER) AS start_minute,CAST(540 AS INTEGER) AS end_minute,0 AS score,1 AS weight,3 AS credits,'' AS code,'' AS lecturer,'1970-01-01' AS valid_from,'2099-12-31' AS valid_to,t.zone_id AS zone_id,CAST(t.created_at AS INTEGER) AS created_at,CAST(t.updated_at AS INTEGER) AS updated_at,CAST(0 AS INTEGER) AS completed_at,CAST(t.end_at AS INTEGER) AS end_at,CAST(COALESCE((SELECT handled_at FROM reminders r WHERE r.exam_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),0) AS INTEGER) AS handled_at,CAST(COALESCE((SELECT notified_at FROM reminders r WHERE r.exam_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),0) AS INTEGER) AS notified_at,CAST(EXISTS(SELECT 1 FROM reminders r WHERE r.exam_id=t.id AND r.user_id=t.user_id) AS INTEGER) AS remind_enabled,CAST(COALESCE((SELECT minutes_before FROM reminders r WHERE r.exam_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),60) AS INTEGER) AS minutes_before,CAST(COALESCE((SELECT minutes_before FROM reminders r WHERE r.exam_id=t.id AND r.user_id=t.user_id ORDER BY r.sort_order,r.id LIMIT 1),-1) AS INTEGER) AS reminder_before_min FROM exams t UNION ALL SELECT CAST(t.id AS INTEGER) AS id,CAST(t.user_id AS INTEGER) AS user_id,'note' AS kind,t.title AS title,t.detail AS detail,CAST(t.subject_id AS INTEGER) AS subject_id,CAST(0 AS INTEGER) AS due_at,CAST(2 AS INTEGER) AS priority,'Chưa làm' AS status,'' AS place,CAST(2 AS INTEGER) AS day,CAST(480 AS INTEGER) AS start_minute,CAST(540 AS INTEGER) AS end_minute,0 AS score,1 AS weight,3 AS credits,'' AS code,'' AS lecturer,'1970-01-01' AS valid_from,'2099-12-31' AS valid_to,'Asia/Ho_Chi_Minh' AS zone_id,CAST(t.created_at AS INTEGER) AS created_at,CAST(t.updated_at AS INTEGER) AS updated_at,CAST(0 AS INTEGER) AS completed_at,CAST(0 AS INTEGER) AS end_at,CAST(0 AS INTEGER) AS handled_at,CAST(0 AS INTEGER) AS notified_at,CAST(0 AS INTEGER) AS remind_enabled,CAST(60 AS INTEGER) AS minutes_before,CAST(-1 AS INTEGER) AS reminder_before_min FROM notes t UNION ALL SELECT CAST(t.id AS INTEGER) AS id,CAST(t.user_id AS INTEGER) AS user_id,'grade' AS kind,t.title AS title,t.detail AS detail,CAST(t.subject_id AS INTEGER) AS subject_id,CAST(0 AS INTEGER) AS due_at,CAST(2 AS INTEGER) AS priority,'Chưa làm' AS status,'' AS place,CAST(2 AS INTEGER) AS day,CAST(480 AS INTEGER) AS start_minute,CAST(540 AS INTEGER) AS end_minute,t.score AS score,t.weight AS weight,3 AS credits,'' AS code,'' AS lecturer,'1970-01-01' AS valid_from,'2099-12-31' AS valid_to,'Asia/Ho_Chi_Minh' AS zone_id,CAST(t.created_at AS INTEGER) AS created_at,CAST(t.updated_at AS INTEGER) AS updated_at,CAST(0 AS INTEGER) AS completed_at,CAST(0 AS INTEGER) AS end_at,CAST(0 AS INTEGER) AS handled_at,CAST(0 AS INTEGER) AS notified_at,CAST(0 AS INTEGER) AS remind_enabled,CAST(60 AS INTEGER) AS minutes_before,CAST(-1 AS INTEGER) AS reminder_before_min FROM grades t;

CREATE VIEW migration_id_map AS SELECT id AS old_id,id AS new_id,kind FROM legacy_entries_v4;

CREATE TRIGGER archive_DELETE BEFORE DELETE ON legacy_entries_v4 BEGIN SELECT RAISE(ABORT,'Read-only migration backup'); END;

CREATE TRIGGER archive_INSERT BEFORE INSERT ON legacy_entries_v4 BEGIN SELECT RAISE(ABORT,'Read-only migration backup'); END;

CREATE TRIGGER archive_UPDATE BEFORE UPDATE ON legacy_entries_v4 BEGIN SELECT RAISE(ABORT,'Read-only migration backup'); END;

CREATE TRIGGER dirty_class_schedules_DELETE AFTER DELETE ON class_schedules BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_class_schedules_INSERT AFTER INSERT ON class_schedules BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_class_schedules_UPDATE AFTER UPDATE ON class_schedules BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_exams_DELETE AFTER DELETE ON exams BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_exams_INSERT AFTER INSERT ON exams BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_exams_UPDATE AFTER UPDATE ON exams BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_grades_DELETE AFTER DELETE ON grades BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_grades_INSERT AFTER INSERT ON grades BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_grades_UPDATE AFTER UPDATE ON grades BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_notes_DELETE AFTER DELETE ON notes BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_notes_INSERT AFTER INSERT ON notes BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_notes_UPDATE AFTER UPDATE ON notes BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_reminders_DELETE AFTER DELETE ON reminders BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_reminders_INSERT AFTER INSERT ON reminders BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_reminders_UPDATE AFTER UPDATE ON reminders BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_subjects_DELETE AFTER DELETE ON subjects BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_subjects_INSERT AFTER INSERT ON subjects BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_subjects_UPDATE AFTER UPDATE ON subjects BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_tasks_DELETE AFTER DELETE ON tasks BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=OLD.user_id; END;

CREATE TRIGGER dirty_tasks_INSERT AFTER INSERT ON tasks BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;

CREATE TRIGGER dirty_tasks_UPDATE AFTER UPDATE ON tasks BEGIN UPDATE users SET reminder_revision=reminder_revision+1 WHERE id=NEW.user_id; END;
