package vn.edu.studymate.reminder;

import android.app.job.*;
import java.util.concurrent.*;

public class DeadlineJobService extends JobService {
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private Future<?> future;

  @Override
  public boolean onStartJob(JobParameters params) {
    future =
        executor.submit(
            () -> {
              try {
                ReminderScheduler.check(this);
              } finally {
                jobFinished(params, false);
              }
            });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    if (future != null) future.cancel(true);
    return true;
  }

  @Override
  public void onDestroy() {
    executor.shutdownNow();
    super.onDestroy();
  }
}
