#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

killall -9 df_manager_r
rm -rf pid/*.pid

# 等待MONITOR_PUBLIC_IP设置
wait_file /data/monitor_ip/MONITOR_PUBLIC_IP || exit 1
# 获取IP
MONITOR_PUBLIC_IP=$(cat /data/monitor_ip/MONITOR_PUBLIC_IP 2>/dev/null || true)
# 生成tbl文件
rm -rf /tmp/manager_server_config.tbl
rm -rf /home/neople/manager/table/server_config.tbl
cp /home/template/neople/manager/table/server_config.tbl /tmp/manager_server_config.tbl
sed -i "s/SERVER_GROUP/$SERVER_GROUP/g" /tmp/manager_server_config.tbl
sed -i "s/PUBLIC_IP/$MONITOR_PUBLIC_IP/g" /tmp/manager_server_config.tbl
cp /tmp/manager_server_config.tbl /home/neople/manager/table/server_config.tbl
# 清理tbl文件
rm -rf /tmp/manager_server_config.tbl
echo "starting manager..."

DNF_GAME_PRELOAD=/home/template/init/libhook.so /opt/dnf/ubuntu/run-game ./df_manager_r server start
sleep 2
cat pid/*.pid |xargs -n1 -I{} tail --pid={} -f /dev/null
