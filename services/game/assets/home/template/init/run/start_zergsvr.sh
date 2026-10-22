#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

killall -9 zergsvr
rm -rf zergsvr.pid
/opt/dnf/ubuntu/run-game ./zergsvr -t30 -i1
sleep 5
cat zergsvr.pid |xargs -n1 -I{} tail --pid={} -f /dev/null
