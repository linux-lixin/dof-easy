#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

killall -9 gunnersvr
rm -rf *.pid
/opt/dnf/ubuntu/run-game ./gunnersvr -t30 -i1
sleep 2
cat *.pid |xargs -n1 -I{} tail --pid={} -f /dev/null
