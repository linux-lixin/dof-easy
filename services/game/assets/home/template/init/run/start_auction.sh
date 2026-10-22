#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

killall -9 df_auction_r
rm -rf pid/*.pid

# 等待MONITOR_PUBLIC_IP设置
wait_file /data/monitor_ip/MONITOR_PUBLIC_IP || exit 1
# 获取IP
MONITOR_PUBLIC_IP=$(cat /data/monitor_ip/MONITOR_PUBLIC_IP 2>/dev/null || true)
# 生成配置文件
rm -rf /tmp/auction.cfg
rm -rf /home/neople/auction/cfg/server.cfg
cp /home/template/neople/auction/cfg/server.cfg /tmp/auction.cfg
sed -i "s/DEC_GAME_PWD/$DEC_GAME_PWD/g" /tmp/auction.cfg
sed -i "s/SERVER_GROUP_DB/$SERVER_GROUP_DB/g" /tmp/auction.cfg
sed -i "s/SERVER_GROUP/$SERVER_GROUP/g" /tmp/auction.cfg
sed -i "s/PUBLIC_IP/$MONITOR_PUBLIC_IP/g" /tmp/auction.cfg
cp /tmp/auction.cfg /home/neople/auction/cfg/server.cfg
# 清理cfg文件
rm -rf /tmp/auction.cfg
echo "starting auction..."

/opt/dnf/ubuntu/run-game ./df_auction_r ./cfg/server.cfg start ./df_auction_r
sleep 5
cat pid/*.pid |xargs -n1 -I{} tail --pid={} -f /dev/null
