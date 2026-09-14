"""Local-only packaged integration smoke test; uses the existing disposable Forge server."""
import pathlib,subprocess,threading,queue,time,json,http.server,hmac,hashlib
root=pathlib.Path(__file__).resolve().parents[2]
server=root/'.tools/forge-server'
assert server.resolve()==(root/'.tools/forge-server').resolve() and (server/'eula.txt').read_text().strip()=='eula=true'
received=[]
class Receiver(http.server.BaseHTTPRequestHandler):
 def do_POST(self):
  raw=self.rfile.read(int(self.headers['Content-Length']))
  received.append((self.path,dict(self.headers),raw))
  self.send_response(202);self.end_headers()
 def log_message(self,*args):pass
http=http.server.ThreadingHTTPServer(('127.0.0.1',0),Receiver)
threading.Thread(target=http.serve_forever,daemon=True).start()
url=f'http://127.0.0.1:{http.server_port}'
old=server/'mods/WebhookIntegrations-Forge-1.20.1-5.5.0-SNAPSHOT.jar'
clan=server/'mods/LightweightClans-Forge-1.20.1-1.1.1-kncraft.1.jar'
activityConfig=server/'config/webhookintegrations.json';clanConfig=server/'config/lightweightclans-webhook.json'
files=[old,clan,activityConfig,clanConfig];backup={p:p.read_bytes() if p.exists() else None for p in files}
process=None;log=[]
try:
 old.write_bytes((root/'forge/build/libs/WebhookIntegrations-Forge-1.20.1-5.6.1-kncraft.1.jar').read_bytes())
 clan.write_bytes((root.parent/'Custom-Clan-Plugin/forge/build/libs/LightweightClans-Forge-1.20.1-1.1.1-kncraft.1.jar').read_bytes())
 activityConfig.write_text(json.dumps({'webhooks':{'main':url+'/activity'},'events':{'onServerStart':{'announce':True,'target':'main','headers':{'X-Webhook-Token':'test-activity-key','X-Minecraft-Server':'kncraft'},'message':{'type':'join','playerName':'LocalContractFixture','timestamp':'$timestamp$','serverId':'kncraft'}}}}))
 clanConfig.write_text(json.dumps({'enabled':True,'endpoint':url+'/clans','secret':'test-clans-key','serverId':'kncraft','periodicFullSyncSeconds':7200}))
 process=subprocess.Popen([r'C:\Users\maste\.jdks\ms-17.0.14\bin\java.exe','-Xmx1G','@libraries/net/minecraftforge/forge/1.20.1-47.4.0/win_args.txt','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
 lines=queue.Queue()
 def read():
  for line in process.stdout:log.append(line);lines.put(line)
 threading.Thread(target=read,daemon=True).start()
 deadline=time.monotonic()+120;ready=False
 while time.monotonic()<deadline:
  try:
   if 'Done (' in lines.get(timeout=.5):ready=True;break
  except queue.Empty:pass
  if process.poll() is not None:break
 assert ready,'Forge did not finish startup; inspect smoke log'
 deadline=time.monotonic()+15
 while len(received)<2 and time.monotonic()<deadline:time.sleep(.1)
 activity=next(x for x in received if x[0]=='/activity');clans=next(x for x in received if x[0]=='/clans')
 ah={k.lower():v for k,v in activity[1].items()};ch={k.lower():v for k,v in clans[1].items()}
 assert ah['x-webhook-token']=='test-activity-key'
 assert ch['x-webhook-source']=='lightweight-clans'
 expected='sha256='+hmac.new(b'test-clans-key',ch['x-webhook-timestamp'].encode()+b'.'+clans[2],hashlib.sha256).hexdigest()
 assert ch['x-webhook-signature']==expected
 assert json.loads(clans[2])['event']=='clan.snapshot'
 process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=45)
 assert process.returncode==0
 print('PASS: Both packaged Forge JARs loaded together; activity token and signed clan startup snapshot received by isolated localhost receiver; clean shutdown.')
finally:
 if process and process.poll() is None:process.terminate();process.wait(timeout=20)
 http.shutdown()
 (root/'forge/build/kncraft-packaged-smoke.log').write_text(''.join(log),encoding='utf-8')
 for p,data in backup.items():
  assert p.resolve().is_relative_to(server.resolve())
  if data is None:p.unlink(missing_ok=True)
  else:p.write_bytes(data)
