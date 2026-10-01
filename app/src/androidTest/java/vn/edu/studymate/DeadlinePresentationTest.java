package vn.edu.studymate;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.ui.Ui;

public class DeadlinePresentationTest {
  @Test public void cardsExplainEveryDeadlineGroupAndAllPriorityInputs() {
    var ins = InstrumentationRegistry.getInstrumentation();
    ins.runOnMainSync(() -> {
      var context = new ContextThemeWrapper(ins.getTargetContext(), R.style.Theme_StudyMate);
      View card = LayoutInflater.from(context).inflate(R.layout.item_card, null);
      long now = 1790000000000L;
      long[] due = {now-60000, now+7800000, now+172800000, now+345600000, 0};
      String[] groups = {"Quá hạn", "Rất gần hạn", "Sắp đến hạn", "Còn thời gian", "Chưa đặt deadline"};
      for (int i=0; i<due.length; i++) {
        Entry e = new Entry(); e.kind="task"; e.title="Kiểm tra nhãn";
        e.dueAt=due[i]; e.priority=2; e.status="Đang làm";
        Ui.bindTask(card,e,"",now,1);
        String badge=((TextView)card.findViewById(R.id.card_badge)).getText().toString();
        String body=((TextView)card.findViewById(R.id.card_body)).getText().toString();
        assertTrue(badge, badge.startsWith(groups[i]));
        assertTrue(body,body.contains("Lý do: "+groups[i]+"; ưu tiên vừa; đang làm (+5 điểm)."));
        if (i==1) assertEquals("Rất gần hạn · Còn 2 giờ 10 phút",badge);
        if (i==0) assertEquals("Quá hạn · Trễ 1 phút",badge);
        e.status="Hoàn thành";Ui.bindTask(card,e,"",now,1);
        assertEquals("Đã hoàn thành",((TextView)card.findViewById(R.id.card_badge)).getText().toString());
        assertEquals(View.GONE,card.findViewById(R.id.card_score).getVisibility());
        assertFalse(((TextView)card.findViewById(R.id.card_body)).getText().toString().contains("Lý do:"));
      }
    });
  }
}
