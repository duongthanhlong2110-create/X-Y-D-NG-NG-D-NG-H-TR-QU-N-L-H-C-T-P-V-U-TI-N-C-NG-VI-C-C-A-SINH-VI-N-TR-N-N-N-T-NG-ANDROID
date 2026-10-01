package vn.edu.studymate;
import static org.junit.Assert.*;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import vn.edu.studymate.auth.Session;import vn.edu.studymate.data.DatabaseHelper;import vn.edu.studymate.ui.EditorActivity;
public class RotationTest {
 EditText first(View root){if(root instanceof EditText)return (EditText)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){EditText e=first(g.getChildAt(i));if(e!=null)return e;}}return null;}
 @Test public void AD10RecreationKeepsDraftAndSavesOnlyOnce() throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Session.logout(c);try(DatabaseHelper db=new DatabaseHelper(c)){long user=db.register("Rotate","rotate"+System.nanoTime()+"@test.vn","Test123!");Session.login(c,user);
   try(ActivityScenario<EditorActivity> a=ActivityScenario.launch(new Intent(c,EditorActivity.class).putExtra("kind","task"))){a.onActivity(x->first(x.findViewById(R.id.fields)).setText("Bản nháp qua xoay màn hình"));a.recreate();a.onActivity(x->{assertEquals("Bản nháp qua xoay màn hình",first(x.findViewById(R.id.fields)).getText().toString());assertTrue(x.findViewById(R.id.save).isShown());x.findViewById(R.id.save).performClick();});Thread.sleep(300);assertEquals(1,db.list(user,"task").size());assertEquals("Bản nháp qua xoay màn hình",db.list(user,"task").get(0).title);}
   finally{Session.logout(c);db.getWritableDatabase().delete("users","id=?",new String[]{""+user});}
  }
 }
}
