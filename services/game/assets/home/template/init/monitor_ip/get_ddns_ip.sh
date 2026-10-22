#! /bin/bash

# 请注意, 如果修改该脚本请确保输出结果为纯IP
echo $(curl --connect-timeout 2 --max-time 2 -fsS https://ipv4.icanhazip.com 2>/dev/null || true)
