import sys,json,os,subprocess,tempfile,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
os.chdir(ROOT)
sys.path.insert(0,str(ROOT))
from scripts.container import manage as m, deployment as d, database as db
parser=argparse.ArgumentParser(description='Parse four deployment models with real Compose; starts no containers.')
parser.add_argument('--compose',required=True)
binary=parser.parse_args().compose
with tempfile.TemporaryDirectory() as tmp:
 root=Path(tmp)/'native86';root.mkdir()
 for kind in ('internal','external'):
  for launcher in ('dof','native'):
   args=argparse.Namespace(database=kind,db_host='172.17.0.1' if kind=='external' else None,db_port=None,db_allow_host='172.29.86.0/255.255.255.0' if kind=='external' else None,mysql_config=Path('/private/test.cnf') if kind=='external' else None,mysql_bin=None,ip='192.0.2.1',bind='0.0.0.0',admin_port=18080,plugins='dp2-frida',channels='15,52',launcher=launcher,raid_extension='on')
   values=m.initial_values(args,m.catalog()[86],root);values['DOF_DATABASE_UUID']='11111111-1111-1111-1111-111111111111'
   m.write_env(root/'.env',values);m.write_json(root/'channels.json',m.ports_override(values))
   base=[binary,'--project-directory',str(Path.cwd()),'-p','dof-native86']
   proc=subprocess.run(base+['--env-file',str(root/'.env'),'-f','compose.yaml','-f',str(root/'channels.json'),'config','--format','json'],capture_output=True,text=True,check=True)
   model=d.configure(json.loads(proc.stdout),values,root,db.DATABASES)
   db.private_json(root/'deployment.json',d.literal_model(model))
   result=subprocess.run(base+['-f',str(root/'deployment.json'),'config','--format','json'],capture_output=True,text=True)
   assert result.returncode==0,result.stderr
   final=json.loads(result.stdout)
   assert ('db' in final['services'])==(kind=='internal')
   if kind=='internal':assert '$MYSQL_ROOT_PASSWORD' in final['services']['db']['healthcheck']['test'][1]
   if launcher=='native':assert final['services']['admin']['environment']['DOF_DATABASE_UUID']==values['DOF_DATABASE_UUID']
   ports=final['services']['ingress']['ports'];assert all(x['host_ip']=='127.0.0.1' for x in ports)
   print(kind,launcher,'Compose 2.39.4 valid, native binding and literal interpolation preserved')
