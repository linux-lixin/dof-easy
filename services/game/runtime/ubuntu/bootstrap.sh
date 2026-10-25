#!/bin/bash
set -e
# /dev/shm is a fresh runtime mount; directories made by image build probes do
# not survive container creation. The original TSS SDK requires this bus path.
if [ -L /dev/shm/sec ]; then
  echo "invalid TSS shared-memory directory" >&2
  exit 1
fi
mkdir -p -m 700 /dev/shm/sec
# 清除MONITOR_PUBLIC_IP文件
rm -rf /data/monitor_ip/MONITOR_PUBLIC_IP
# 清除MONITOR_RELAY_CONFIG文件
rm -rf /data/relay/MONITOR_RELAY_CONFIG
# Preserve logs across restarts.
# 清理/dp2目录
rm -rf /dp2
# 给supervisor扩展文件赋予权限[可用于扩展第三方网关]
mkdir -p /data/conf.d
# 创建DP目录
mkdir -p /data/dp
ln -s /data/dp /dp2
# 创建日志目录
mkdir -p /data/log
# 创建ip监控目录
mkdir -p /data/monitor_ip
# 创建run脚本目录
mkdir -p /data/run
# 创建daily_job和relay目录
if [ "$SERVER_TYPE" = "CORE" ] || [ "$SERVER_TYPE" = "ALL" ]; then
  mkdir -p /data/daily_job
  mkdir -p /data/relay
fi
date +%s > /run/dnf/started-at
# 初始化数据
bash /home/template/init/init.sh
error_code=$?
if [ ! $error_code -eq 0 ]; then
  echo "init failed!!!!!"
  exit -1
fi
# 赋予权限
if [ $(find /data/conf.d -name "*.conf" | wc -l) -gt 0 ]; then
  echo "Add permissions to the extension configuration."
  chmod 700 /data/conf.d/*.conf
else
  echo "Extension configuration not set up."
fi
# 删除无用文件
rm -rf /home/template/neople-tmp
mkdir -p /home/neople
# 复制待使用文件
cp -r /home/template/neople /home/template/neople-tmp
# 将结果文件拷贝到对应目录[这里是为了保住日志文件目录,将日志文件挂载到宿主机外,因此采用复制而不是mv]
cp -rf /home/template/neople-tmp/* /home/neople
# Remove stale PID files only.
find /home/neople/ -name '*.pid' -type f -print -exec rm -f {} \;
chmod -R u+rwX,go-rwx /home/neople
rm -rf /home/template/neople-tmp
# 复制版本文件
if [ -f "/data/Script.pvf" ];then
  cp /data/Script.pvf /home/neople/game/Script.pvf
  chmod 700 /home/neople/game/Script.pvf
fi
# 复制等级文件
if [ -f "/data/df_game_r" ];then
  cp /data/df_game_r /home/neople/game/df_game_r
  chmod 700 /home/neople/game/df_game_r
fi
# Copy the public login verification key.
if [ -f "/data/publickey.pem" ];then
  cp /data/publickey.pem /home/neople/game/
fi
# Limit persistent plugin permissions to the container owner.
chmod 700 -R /data/dp
# Install the explicitly selected ordinary plugins.
python3 /opt/dnf/ubuntu/vipai86.py || exit 1
python3 /opt/dof-easy/runtime/gateway/service.py prepare || exit 1
# 重设supervisor web网页密码
sed -i "s/^username=.*/username=$WEB_USER/" /etc/supervisord.conf
sed -i "s/^password=.*/password=$WEB_PASS/" /etc/supervisord.conf
# 传递环境变量
SUPERVISORD_ENV="SERVER_TYPE=\"$SERVER_TYPE\",CORE_PUBLIC_IP=\"$CORE_PUBLIC_IP\",P2P_PUBLIC_IP=\"$P2P_PUBLIC_IP\",P2P_RELAY_INDEX=\"$P2P_RELAY_INDEX\",MAIN_BRIDGE_IP=\"$MAIN_BRIDGE_IP\",SERVER_GROUP_NAME=\"$SERVER_GROUP_NAME\",SERVER_GROUP_DB=\"$SERVER_GROUP_DB\",CUR_MAIN_DB_HOST=\"$CUR_MAIN_DB_HOST\",CUR_MAIN_DB_PORT=\"$CUR_MAIN_DB_PORT\",CUR_SG_DB_HOST=\"$CUR_SG_DB_HOST\",CUR_SG_DB_PORT=\"$CUR_SG_DB_PORT\""
sed -i "s/^environment=.*/environment=$SUPERVISORD_ENV/" /etc/supervisord.conf
# 切换到主目录
cd /root
exec /usr/bin/supervisord -c /etc/supervisord.conf
