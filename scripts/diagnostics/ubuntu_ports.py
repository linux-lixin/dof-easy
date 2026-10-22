"""Render the split-deployment port matrix from actual game configuration templates."""
import argparse
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]
TEMPLATE=ROOT/'services/game/assets/home/template'

def value(file,key,group,channel=None):
    text=(TEMPLATE/file).read_text(encoding='utf8',errors='replace')
    text=text.replace('SERVER_GROUP',str(group))
    if channel is not None: text=text.replace('CHANNEL_NO',f'{channel:02d}')
    found=re.search(r'^'+re.escape(key)+r'\s*(?:=\s*)?(\d+)\s*(?://.*)?$',text,re.M)
    if not found: raise ValueError('Missing port in '+file+': '+key)
    port=int(found[1])
    if not 1<=port<=65535: raise ValueError('Invalid derived port')
    return port

def render(group,channels):
    rows=[]
    fields=[('CORE','玩家频道列表','channel/cfg/server.cfg','this_tcp_port','TCP/UDP'),
            ('CORE','节点 bridge','bridge/cfg/server.cfg','this_tcp_port','TCP/UDP'),
            ('CORE','节点 monitor','monitor/cfg/server.cfg','4.Define_Server_TCP_Port','TCP/UDP'),
            ('CORE','节点 guild','guild/cfg/server.cfg','4.Define_Server_TCP_Port','TCP/UDP'),
            ('CORE','节点 statistic','statics/cfg/server.cfg','2.Define_Server_UDP_Port','UDP'),
            ('CORE','节点 auction','auction/cfg/server.cfg','this_tcp_port','TCP/UDP'),
            ('CORE','节点 point','point/cfg/server.cfg','this_tcp_port','TCP/UDP'),
            ('CORE','节点 community','community/cfg/server.cfg','port','TCP'),
            ('P2P','玩家 relay','relay/cfg/relay.cfg','this_tcp_port','TCP/UDP')]
    for role,purpose,file,key,protocol in fields:
        rows.append((role,purpose,protocol,str(value('neople/'+file,key,group)),file))
    for channel in channels:
        for key,protocol in (('tcp_port','TCP'),('udp_port','UDP')):
            file='game/cfg/server.template'
            rows.append(('GAME','玩家频道 '+str(channel),protocol,str(value('neople/'+file,key,group,channel)),file))
    stun=(TEMPLATE/'neople/stun/cfg/server.cfg').read_text().replace('SERVER_GROUP',str(group)).split()
    assert all(x.isdigit() and 0<int(x)<65536 for x in stun)
    rows.append(('P2P','玩家 STUN','UDP','、'.join(stun),'stun/cfg/server.cfg'))
    rows.append(('CORE','节点 doublecheck','UDP',str(value('neople/game/cfg/server.template','udp_port_of_doublecheck',group)),'game/cfg/server.template'))
    relay=(ROOT/'services/game/runtime/ubuntu/relay.py').read_text()
    prefix=re.search(r"int\('([0-9]+)'\+group\)",relay)[1]
    rows.append(('CORE','节点 relay 注册','TCP',prefix+str(group),'ubuntu/relay.py'))
    result=['# Ubuntu 角色端口表','',f'由 `python3 scripts/diagnostics/ubuntu_ports.py --group {group} --channels '+','.join(map(str,channels))+'` 从配置生成。',
            '','节点端口仅向可信节点开放；管理、容器内部端口见部署指南。','','| 角色 | 用途 | 协议 | 端口 | 配置来源 |','|---|---|---|---|---|']
    result+=['| '+' | '.join(row)+' |' for row in rows]
    return '\n'.join(result)+'\n'

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--group',type=int,choices=range(1,7),default=3)
    parser.add_argument('--channels',default='11,52')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    text=render(args.group,[int(x) for x in args.channels.split(',')])
    if args.output: args.output.write_text(text,encoding='utf8')
    else: print(text,end='')
