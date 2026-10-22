#!/bin/bash
set -e
source /opt/dnf/ubuntu/wait.sh
# 启动monitor_ip
echo "starting monitor_ip ..."
supervisorctl -c /etc/supervisord.conf restart monitor_ip:*
# 这里会一直等PUBLIC_IP设置完成
wait_file /data/monitor_ip/MONITOR_PUBLIC_IP || exit 1

# 启动CORE[如果配置了MAIN_BRIDGE_IP则不启动bridge以及channel]只有CORE需要初始化数据库
if [ "$SERVER_TYPE" = "CORE" ] || [ "$SERVER_TYPE" = "ALL" ]; then
  echo "starting mysql_proxy ..."
  supervisorctl -c /etc/supervisord.conf restart mysql_proxy:main_mysql_proxy
  supervisorctl -c /etc/supervisord.conf restart mysql_proxy:sg_mysql_proxy
  echo "starting core ..."
  supervisorctl -c /etc/supervisord.conf restart core:relay_config_server
  supervisorctl -c /etc/supervisord.conf restart core:daily_job
  supervisorctl -c /etc/supervisord.conf restart core:monitor
  supervisorctl -c /etc/supervisord.conf restart core:manager
  if [ -z "$MAIN_BRIDGE_IP" ] || [ "$MAIN_BRIDGE_IP" = "127.0.0.1" ]; then
    echo "starting bridge ..."
    supervisorctl -c /etc/supervisord.conf restart core:bridge
    echo "starting channel ..."
    supervisorctl -c /etc/supervisord.conf restart core:channel
  fi
  supervisorctl -c /etc/supervisord.conf restart core:dbmw_guild
  supervisorctl -c /etc/supervisord.conf restart core:dbmw_mnt
  supervisorctl -c /etc/supervisord.conf restart core:dbmw_stat
  supervisorctl -c /etc/supervisord.conf restart core:auction
  supervisorctl -c /etc/supervisord.conf restart core:point
  supervisorctl -c /etc/supervisord.conf restart core:guild
  supervisorctl -c /etc/supervisord.conf restart core:statics
  supervisorctl -c /etc/supervisord.conf restart core:coserver
  supervisorctl -c /etc/supervisord.conf restart core:community
fi

# 启动P2P
if [ "$SERVER_TYPE" = "P2P" ] || [ "$SERVER_TYPE" = "ALL" ]; then
  # P2P服务必须配置P2P_RELAY_INDEX
  if [ -n "$P2P_RELAY_INDEX" ]; then
    echo "starting p2p[stun & relay] ..."
    supervisorctl -c /etc/supervisord.conf restart p2p:*
  else
    echo "WARNING: empty P2P_RELAY_INDEX"
  fi
fi

# 启动GAME
if [ "$SERVER_TYPE" = "GAME" ] || [ "$SERVER_TYPE" = "ALL" ]; then
  echo "starting security system ..."
  supervisorctl -c /etc/supervisord.conf restart tss:gunnersvr
  supervisorctl -c /etc/supervisord.conf restart tss:zergsvr_secagent
  supervisorctl -c /etc/supervisord.conf restart tss:zergsvr
  echo "starting mysql_proxy ..."
  supervisorctl -c /etc/supervisord.conf restart mysql_proxy:main_mysql_proxy
  supervisorctl -c /etc/supervisord.conf restart mysql_proxy:sg_mysql_proxy
  sleep 2
  echo "starting game ..."
  supervisorctl -c /etc/supervisord.conf restart game:*
fi

# 退出前sleep 5秒
sleep 5
