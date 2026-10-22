from pathlib import Path
import re
p=Path('/home/template/neople/secsvr/zergsvr/cfg')
f=p/'zergsvrd.xml'
f.write_text(f.read_text().replace('<get_svr_info_type>cfgsvr</','<get_svr_info_type>cfgfile</'))
f=p/'svcid.xml'
s=f.read_text(); seen=set()
def dedup(m):
 key=tuple(re.search('<'+tag+'>(.*?)</'+tag+'>',m[0],re.S)[1].strip() for tag in ('svr_type_','svr_id_'))
 if key in seen:return ''
 seen.add(key);return m[0]
s=re.sub(r'<service_info_>.*?</service_info_>',dedup,s,flags=re.S)
s=re.sub(r'<service_num_>.*?</service_num_>',f'<service_num_>{len(seen)}</service_num_>',s)
f.write_text(s)
