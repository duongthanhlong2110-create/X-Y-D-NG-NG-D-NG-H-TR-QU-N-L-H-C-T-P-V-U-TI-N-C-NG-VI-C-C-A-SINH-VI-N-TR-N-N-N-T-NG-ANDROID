package vn.edu.studymate;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.json.JSONObject;
import vn.edu.studymate.auth.Session;
import vn.edu.studymate.data.DatabaseHelper;
import vn.edu.studymate.model.Entry;
import vn.edu.studymate.reminder.ReminderScheduler;
import vn.edu.studymate.ui.*;
/** Shell-controlled probes. Only used on disposable emulators, never a user's database. */
public class EnvironmentProbe {
 @Test public void probe() throws Exception {
   Bundle args=InstrumentationRegistry.getArguments();String action=args.getString("action");assumeNotNull(action);
   Instrumentation ins=InstrumentationRegistry.getInstrumentation();Context c=ins.getTargetContext();SharedPreferences p=c.getSharedPreferences("environment_probe",0);
   try(DatabaseHelper db=new DatabaseHelper(c)){
     if(action.equals("seed")){
       Session.logout(c);long user=db.register("Kiểm thử Android","env"+System.currentTimeMillis()+"@test.vn","Test123!");Session.login(c,user);
       Entry e=new Entry();e.kind="task";e.title="Kiểm thử "+args.getString("case","Android");e.dueAt=System.currentTimeMillis()+Long.parseLong(args.getString("delay","30000"));e.minutesBefore=0;db.save(user,e);
       p.edit().putLong("user",user).putLong("id",e.id).putLong("due",e.dueAt).commit();
       c.getSharedPreferences("session",0).edit().putLong("user",user).commit();
       ReminderScheduler.schedule(c);ReminderScheduler.check(c);
       System.out.println("PROBE "+new JSONObject().put("action",action).put("user",user).put("id",e.id).put("due",e.dueAt).put("now",System.currentTimeMillis()));
     }else if(action.equals("observe")){
       long user=p.getLong("user",-1),id=p.getLong("id",-1),due=p.getLong("due",0);Entry e=db.get(user,id);assertNotNull(e);
       long received=0;try(Cursor row=db.getReadableDatabase().rawQuery("SELECT MAX(notified_at) FROM reminders WHERE user_id=? AND task_id=?",new String[]{""+user,""+id})){row.moveToFirst();received=row.getLong(0);}
       String expected=args.getString("expected","posted");if(expected.equals("posted")){assertEquals(due,e.handledAt);assertTrue(received>=due);}else assertEquals(0,e.handledAt);
       System.out.println("PROBE "+new JSONObject().put("action",action).put("case",args.getString("case","")).put("id",id).put("due",due).put("received",received).put("delayMs",received==0?-1:received-due).put("now",System.currentTimeMillis()).put("expected",expected));
     }else if(action.equals("cleanup")){Session.logout(c);}
   }
 }
}
