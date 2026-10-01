package vn.edu.studymate;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.Test;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.logic.SmartPlanner;
import vn.edu.studymate.model.Entry;

public class PerformanceTest {
  @Test
  public void loadAndRankOneThousandTasks() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name = "performance_test.db";
    c.deleteDatabase(name);
    try (DatabaseHelper db = new DatabaseHelper(c, name)) {
      long user = db.register("Benchmark", "bench@test.vn", "Test123!"),
          now = System.currentTimeMillis();
      db.getWritableDatabase().beginTransaction();
      try {
        for (int i = 0; i < 1000; i++) {
          Entry e = new Entry();
          e.kind = "task";
          e.title = "Task " + i;
          e.dueAt = now + (i - 100) * 3600000L;
          e.priority = i % 3 + 1;
          db.save(user, e);
        }
        db.getWritableDatabase().setTransactionSuccessful();
      } finally {
        db.getWritableDatabase().endTransaction();
      }
      long[] times = new long[5];
      for (int run = 0; run < 5; run++) {
        long t = SystemClock.elapsedRealtimeNanos();
        List<Entry> tasks = db.list(user, "task");
        List<Entry> sorted = SmartPlanner.ranked(tasks, now);
        times[run] = (SystemClock.elapsedRealtimeNanos() - t) / 1000000;
        assertEquals(1000, sorted.size());
        for (int i = 1; i < sorted.size(); i++)
          assertTrue(SmartPlanner.comparator(now).compare(sorted.get(i - 1), sorted.get(i)) <= 0);
      }
      Arrays.sort(times);
      Log.i(
          "StudyMateBenchmark",
          "1000 tasks read+rank median_ms="
              + times[2]
              + " min_ms="
              + times[0]
              + " max_ms="
              + times[4]);
    } finally {
      c.deleteDatabase(name);
    }
  }
}
