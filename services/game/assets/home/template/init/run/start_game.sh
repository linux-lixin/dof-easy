#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

# 获得频道传入参数
channel_no=$1
process_sequence=$2
channel_name="$SERVER_GROUP_NAME$channel_no"

echo "channel_name is $channel_name"
echo "prepare to start ch.$channel_no, process_sequence is $process_sequence"
# 等待bridge启动,最多等待30秒
wait_tcp "$MAIN_BRIDGE_IP" 7000 core-service || exit 1
# 等待monitor启动,最多等待30秒
wait_tcp "$CORE_PUBLIC_IP" "3${SERVER_GROUP}303" core-service || exit 1
# 等待guild启动,最多等待30秒
wait_tcp "$CORE_PUBLIC_IP" "3${SERVER_GROUP}403" core-service || exit 1
# 等待MONITOR_PUBLIC_IP设置
wait_file /data/monitor_ip/MONITOR_PUBLIC_IP || exit 1
# 获取IP
MONITOR_PUBLIC_IP=$(cat /data/monitor_ip/MONITOR_PUBLIC_IP 2>/dev/null || true)
echo "MONITOR_PUBLIC_IP is $MONITOR_PUBLIC_IP"
# 生成配置文件
rm -rf /tmp/$channel_name.cfg
cp /home/template/neople/game/cfg/server.template /tmp/$channel_name.cfg
# 重设PUBLIC_IP,game密码,频道编号,端口信息等
sed -i "s/MAIN_BRIDGE_IP/$MAIN_BRIDGE_IP/g" /tmp/$channel_name.cfg
sed -i "s/CHANNEL_NO/$channel_no/g" /tmp/$channel_name.cfg
sed -i "s/PROCESS_SEQUENCE/$process_sequence/g" /tmp/$channel_name.cfg
sed -i "s/CORE_PUBLIC_IP/$CORE_PUBLIC_IP/g" /tmp/$channel_name.cfg
sed -i "s/P2P_PUBLIC_IP/$P2P_PUBLIC_IP/g" /tmp/$channel_name.cfg
sed -i "s/PUBLIC_IP/$MONITOR_PUBLIC_IP/g" /tmp/$channel_name.cfg
sed -i "s/DEC_GAME_PWD/$DEC_GAME_PWD/g" /tmp/$channel_name.cfg
sed -i "s/SERVER_GROUP/$SERVER_GROUP/g" /tmp/$channel_name.cfg
cp /tmp/$channel_name.cfg /home/neople/game/cfg/$channel_name.cfg
echo "generate $channel_name.cfg success"
# 清理cfg文件
rm -rf /tmp/$channel_name.cfg
# 配置DB配置文件
db_config=$(mktemp /home/neople/game/cfg/.db_info.XXXXXX)
cp /home/template/neople/game/cfg/db_info_tw.cfg "$db_config"
sed -i "s/SERVER_GROUP_NAME/$SERVER_GROUP_NAME/g" "$db_config"
sed -i "s/SERVER_GROUP/$SERVER_GROUP/g" "$db_config"
sed -i "s/CORE_PUBLIC_IP/$CORE_PUBLIC_IP/g" "$db_config"
mv -f "$db_config" /home/neople/game/cfg/db_info_tw.cfg

# 启动服务
old_pid=$(pgrep -f "df_game_r $channel_name (start|nofork)")
echo "ch.$channel_no old pid is $old_pid"
if [ -n "$old_pid" ]; then
  echo "old pid not empty, kill $old_pid"
  kill -9 $old_pid
fi
rm -rf pid/$channel_name.pid

# 加载DP并启动[确保DP路径已经被正确映射]
python3 /opt/dnf/ubuntu/channel_logs.py "$channel_name" || exit 1
# The archived 86 binary supports nofork: supervisor must own the live game PID.
DNF_GAME_PRELOAD=/dp2/libhook.so exec /opt/dnf/ubuntu/run-game ./df_game_r "$channel_name" nofork
