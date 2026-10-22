#!/bin/bash
source /opt/dnf/ubuntu/wait.sh
# /bin/bash

killall -9 secagent
rm -rf secagent.pid
sleep 2
/opt/dnf/ubuntu/run-game ./secagent
