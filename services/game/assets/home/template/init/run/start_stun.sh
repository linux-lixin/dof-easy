#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

killall -9 df_stun_r
rm -rf pid/*.pid

# 生成配置文件
rm -rf /tmp/stun.cfg
rm -rf /home/neople/stun/cfg/server.cfg
cp /home/template/neople/stun/cfg/server.cfg /tmp/stun.cfg
sed -i "s/SERVER_GROUP/$SERVER_GROUP/g" /tmp/stun.cfg
cp /tmp/stun.cfg /home/neople/stun/cfg/server.cfg
# 清理cfg文件
rm -rf /tmp/stun.cfg


chmod +x stun_hook.so
echo "starting stun..."
DNF_GAME_PRELOAD=/home/neople/stun/stun_hook.so /opt/dnf/ubuntu/run-game ./df_stun_r start
netstat -tulpn | grep stun
sleep 2
cat pid/*.pid |xargs -n1 -I{} tail --pid={} -f /dev/null
