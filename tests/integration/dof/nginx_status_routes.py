"""Exercise actual Nginx status routes using a disposable local upstream.

Run with DOF_TEST_NGINX=/path/to/nginx; no production services are used.
"""
import os
from pathlib import Path
import subprocess,tempfile,threading,http.server,socket,time,urllib.request,json
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  body=json.dumps({'path':self.path}).encode();self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*a):pass
backend=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=backend.serve_forever,daemon=True).start()
with tempfile.TemporaryDirectory(prefix='dof-nginx-route-')as td:
 root=Path(td);(root/'mime.types').write_text('types {}\n');sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
 conf=Path('services/admin/docker/dof-nginx.conf.template').read_text().replace('${NGINX_PORT}',str(port)).replace('${SERVER_PORT}',str(backend.server_port)).replace('/etc/nginx/mime.types',str(root/'mime.types'))
 conf='pid '+str(root/'nginx.pid')+';\nerror_log '+str(root/'error.log')+';\n'+conf.replace('http {','http {\naccess_log off;\nclient_body_temp_path '+str(root/'body')+';\nproxy_temp_path '+str(root/'proxy')+';\nfastcgi_temp_path '+str(root/'fastcgi')+';\nuwsgi_temp_path '+str(root/'uwsgi')+';\nscgi_temp_path '+str(root/'scgi')+';')
 (root/'nginx.conf').write_text(conf)
 process=subprocess.Popen([os.environ['DOF_TEST_NGINX'],'-p',str(root),'-c',str(root/'nginx.conf'),'-g','daemon off;'],stderr=subprocess.PIPE)
 try:
  for _ in range(30):
   try:
    with socket.create_connection(('127.0.0.1',port),timeout=.2):break
   except OSError:
    if process.poll() is not None:raise RuntimeError(process.stderr.read().decode())
    time.sleep(.1)
  rows=[]
  for path in ['/api/v1/admin/dof/client','/api/v1/admin/dof/script','/api/v1/admin/dof/client/tasks','/api/v1/admin/dof/script/tasks','/api/v1/admin/dof/script/uploads']:
   url='http://127.0.0.1:'+str(port)+path
   with urllib.request.urlopen(url,timeout=3) as response:
    assert response.status==200 and response.url==url
    assert json.load(response)['path']==path
    rows.append({'path':path,'http':200,'redirected':False})
  print(json.dumps(rows))
 finally:
  process.terminate();process.wait(timeout=5);backend.shutdown()
