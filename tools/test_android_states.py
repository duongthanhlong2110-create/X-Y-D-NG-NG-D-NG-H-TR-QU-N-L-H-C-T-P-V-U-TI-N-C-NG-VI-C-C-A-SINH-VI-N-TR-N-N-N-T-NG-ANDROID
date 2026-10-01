import subprocess,pathlib,json,time,base64,sqlite3,os,hashlib
import argparse
parser=argparse.ArgumentParser(description='Run only on a disposable API 33 emulator with app and test APK installed.')
parser.add_argument('--serial',required=True);args=parser.parse_args()
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'verification'/'rerun';OUT.mkdir(parents=True,exist_ok=True);log=[];seed={}
def record(case,step,result):
 row={'hostTime':time.strftime('%Y-%m-%dT%H:%M:%S'),'case':case,'step':step,'result':result};log.append(row);(OUT/'compact-cases.json').write_text(json.dumps(log,ensure_ascii=False,indent=2),encoding='utf-8');print(case,step,str(result)[:200],flush=True)
def sh(cmd,timeout=30):return subprocess.check_output(['adb','-s',args.serial,'shell',cmd],timeout=timeout,stderr=subprocess.STDOUT).decode(errors='replace').strip()
def row():
 path=OUT/'compact-snapshot.db';path.write_bytes(base64.b64decode(sh('run-as vn.edu.studymate cat databases/studymate.db | base64')))
 with sqlite3.connect(path) as db:return db.execute('SELECT t.id,t.due_at,MAX(r.notified_at),MAX(r.handled_at) FROM tasks t JOIN reminders r ON r.task_id=t.id WHERE t.id=? GROUP BY t.id',(seed['id'],)).fetchone()
def start(case,delay=15000):
 global seed
 result=sh('am instrument -w -e class vn.edu.studymate.EnvironmentProbe -e action seed -e case '+case+' -e delay '+str(delay)+' vn.edu.studymate.test/androidx.test.runner.AndroidJUnitRunner');assert 'OK (1 test)' in result
 seed=json.loads([s.split('PROBE ',1)[1] for s in sh('logcat -d -s System.out').splitlines() if 'PROBE ' in s][-1]);record(case,'oracle',seed)
 record(case,'open',sh('am start -W -n vn.edu.studymate/.ui.LoginActivity'))
def wait_delivery(case,seconds=180):
 starttime=time.monotonic()
 while time.monotonic()-starttime<seconds:
  r=row()
  if r[2]:
   result={'id':r[0],'due':r[1],'received':r[2],'delayMs':r[2]-r[1]};record(case,'delivery',result);assert 0<=result['delayMs']<=180000;record(case,'result','PASS');return
  time.sleep(5)
 record(case,'result','FAIL no notification observed');raise AssertionError(case)
def screenshot(case):
 sh('screencap -p /sdcard/environment.png');(OUT/(case+'.png')).write_bytes(base64.b64decode(sh('base64 /sdcard/environment.png')))
record('ENV','apkSha256',hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest())
record('ENV','device',sh('getprop ro.product.model; getprop ro.build.version.sdk; wm size; wm density'))
sh('pm grant vn.edu.studymate android.permission.POST_NOTIFICATIONS')
start('AD02-foreground');wait_delivery('AD02-foreground');screenshot('AD02-foreground')
start('AD02-background');sh('input keyevent 3');wait_delivery('AD02-background')
start('AD02-screen-off');sh('input keyevent 223');wait_delivery('AD02-screen-off');sh('input keyevent 224');sh('wm dismiss-keyguard')
sh('pm revoke vn.edu.studymate android.permission.POST_NOTIFICATIONS');sh('pm set-permission-flags vn.edu.studymate android.permission.POST_NOTIFICATIONS user-set user-fixed')
start('AD01-denied');time.sleep(25);r=row();record('AD01-denied','row',r);assert r[2]==0;record('AD01-denied','result','PASS no notification while denied');screenshot('AD01-denied')
sh('pm clear-permission-flags vn.edu.studymate android.permission.POST_NOTIFICATIONS user-fixed');sh('pm grant vn.edu.studymate android.permission.POST_NOTIFICATIONS');sh('am start -W -n vn.edu.studymate/.ui.LoginActivity');wait_delivery('AD01-granted-again')
start('AD06-force-stop');sh('am force-stop vn.edu.studymate');time.sleep(25);r=row();record('AD06-force-stop','rowWhileStopped',r);assert r[2]==0;sh('am start -W -n vn.edu.studymate/.ui.LoginActivity');wait_delivery('AD06-reopen')
start('AD05-reboot',60000);before=sh('cat /proc/sys/kernel/random/boot_id');record('AD05-reboot','bootIdBefore',before)
try:sh('reboot',10)
except Exception as e:record('AD05-reboot','transportDuringReboot',str(type(e).__name__))
time.sleep(4)
for attempt in range(50):
 try:
  after=sh('cat /proc/sys/kernel/random/boot_id',5)
  if after!=before and sh('getprop sys.boot_completed',5)=='1':break
 except Exception:pass
 time.sleep(3)
else:raise AssertionError('Device did not reboot')
record('AD05-reboot','bootIdAfter',after);sh('input keyevent 82');wait_delivery('AD05-reboot',180)
# Do not open the app before checking delivery after the actual reboot.
start('AD07-timezone',15000);before=row();record('AD07-timezone','before',before);record('AD07-timezone','change',sh('cmd alarm set-timezone Asia/Tokyo'));sh('am start -W -n vn.edu.studymate/.ui.LoginActivity');assert row()[1]==before[1];wait_delivery('AD07-timezone');sh('cmd alarm set-timezone Asia/Ho_Chi_Minh')
sh('svc wifi disable');sh('svc data disable');before=row();sh('am force-stop vn.edu.studymate');sh('am start -W -n vn.edu.studymate/.ui.LoginActivity');assert row()==before;record('AD09-offline','result','PASS persisted record unchanged after reopening without network');sh('svc wifi enable')
record('DONE','result','Environment scenarios completed')
