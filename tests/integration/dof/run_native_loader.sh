#!/bin/sh
set -eu
# Executed only by local_environment.py; no network or project root is mounted.
ip -j route show > /output/routes.json
python3 - <<'PY'
import errno,json,socket
assert json.load(open('/output/routes.json')) == []
s=socket.socket();s.settimeout(1)
try:
 s.connect(('198.51.100.1',9))
 raise RuntimeError('Offline namespace unexpectedly has external connectivity')
except OSError as e:
 assert e.errno == errno.ENETUNREACH, e
finally:s.close()
print('Private network verified: no routes; external connect returns ENETUNREACH')
PY
gcc -m32 -Wall -Wextra -Werror -O2 /inputs/probe.c -ldl -o /output/probe32
failed=0
for spec in original-inline.so:original libdof-inline.so:renamed original-plugin.so:original libdof-plugin.so:renamed; do
    file=${spec%:*}
    mode=${spec#*:}
    result=0
    timeout 30s /output/probe32 "/inputs/$file" "$mode" > "/output/$file.stdout" 2> "/output/$file.stderr" || result=$?
    printf '%s\t%s\n' "$file" "$result" >> /output/status.tsv
    if [ "$result" -ne 0 ]; then failed=1; fi
done
cat /output/status.tsv
exit "$failed"
