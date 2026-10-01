import subprocess, pathlib, json, time, base64, sqlite3, os, sys, hashlib
import argparse
parser=argparse.ArgumentParser(description='Run only on a disposable API 33 emulator with app and test APK installed.')
parser.add_argument('--serial',required=True);args=parser.parse_args()
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'verification'/'rerun';OUT.mkdir(parents=True,exist_ok=True)
case='AD03-doze-15min'
log=[]
def record(step,result):
 row={'hostTime':time.strftime('%Y-%m-%dT%H:%M:%S'),'step':step,'result':result};log.append(row);(OUT/(case+'.json')).write_text(json.dumps(log,ensure_ascii=False,indent=2),encoding='utf-8');print(step,str(result)[:180],flush=True)
def sh(cmd,timeout=30):
 return subprocess.check_output(['adb','-s',args.serial,'shell',cmd],timeout=timeout).decode('utf-8',errors='replace').strip()
def snapshot():
 data=base64.b64decode(sh('run-as vn.edu.studymate cat databases/studymate.db | base64'))
 path=OUT/(case+'.db');path.write_bytes(data)
 with sqlite3.connect(path) as db:
  return db.execute('SELECT t.id,t.due_at,MAX(r.notified_at),MAX(r.handled_at) FROM tasks t JOIN reminders r ON r.task_id=t.id WHERE t.id=? GROUP BY t.id',(seed['id'],)).fetchone()
record('apkSha256',hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest())
record('environment',sh('getprop ro.product.model; getprop ro.build.version.sdk; wm size; wm density; settings get system font_scale'))
record('grant',sh('pm grant vn.edu.studymate android.permission.POST_NOTIFICATIONS'))
record('seed',sh('am instrument -w -e class vn.edu.studymate.EnvironmentProbe -e action seed -e case '+case+' -e delay 120000 vn.edu.studymate.test/androidx.test.runner.AndroidJUnitRunner'))
lines=sh('logcat -d -s System.out').splitlines();seed=json.loads([s.split('PROBE ',1)[1] for s in lines if 'PROBE ' in s][-1]);record('oracle',seed)
record('open',sh('am start -W -n vn.edu.studymate/.ui.LoginActivity'))
record('home',sh('input keyevent 3'))
record('unplug',sh('dumpsys battery unplug'))
record('screenOff',sh('input keyevent 223'))
record('enableDoze',sh('dumpsys deviceidle enable'))
record('forceDoze',sh('dumpsys deviceidle force-idle'))
assert sh('dumpsys deviceidle get deep')=='IDLE'
record('idleStart',sh('dumpsys deviceidle get deep'))
start=time.monotonic();received=None
try:
 while time.monotonic()-start<900:
  time.sleep(20)
  row=snapshot()
  if row and row[2] and received is None:received=row;record('delivered',{'id':row[0],'due':row[1],'received':row[2],'delayMs':row[2]-row[1]})
  if int(time.monotonic()-start)//60>int(time.monotonic()-start-20)//60:record('idleSample',{'elapsedSec':round(time.monotonic()-start),'state':sh('dumpsys deviceidle get deep'),'row':row})
 record('durationSeconds',round(time.monotonic()-start));record('finalRow',snapshot());record('notification',sh('dumpsys notification --noredact'))
 record('result','PASS measured delay within predefined 15 minutes' if received and 0<=received[2]-received[1]<=900000 else 'FAIL or no delivery during observation')
finally:
 record('unforce',sh('dumpsys deviceidle unforce'));record('resetBattery',sh('dumpsys battery reset'));record('wake',sh('input keyevent 224'))

