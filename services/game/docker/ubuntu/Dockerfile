# syntax=docker/dockerfile:1
FROM mysql:5.6@sha256:20575ecebe6216036d25dab5903808211f1e9ba63dc7825ac20cb975e34cfcae AS mysql56_client
# This manifest is pinned; build on an x86_64 Docker host using its default platform.
FROM ubuntu:24.04@sha256:534baea6a22c03a63003dbc8dbe78fe34bc0d7e595d9a9dc9834884ff530eb55 AS runtime
ENV TZ=Asia/Shanghai
RUN dpkg --add-architecture i386 \
 && apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates tzdata python3 supervisor socat netcat-openbsd mariadb-client \
    bash coreutils findutils grep sed gawk perl procps psmisc binutils openssl curl iproute2 net-tools \
    libc6:i386 libstdc++6:i386 libgcc-s1:i386 zlib1g:i386 \
    libstdc++6 libgcc-s1 zlib1g libcrypt1 patchelf \
 && rm -rf /var/lib/apt/lists/* \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

FROM runtime AS hooks
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends gcc gcc-multilib libc6-dev autoconf automake libtool make \
 && rm -rf /var/lib/apt/lists/*
COPY assets/home/template/neople/stun/stun_hook.c /tmp/stun_hook.c
RUN gcc -shared -fPIC -o /tmp/stun_hook.so /tmp/stun_hook.c -ldl -Wall -O2
COPY docker/ubuntu/geoip_1.6.12.orig.tar.gz /tmp/geoip.tar.gz
RUN echo '99b119f8e21e94f1dfd6d49fbeed29a70df1544896e76cd456f25e397b07d476  /tmp/geoip.tar.gz' | sha256sum -c - \
 && mkdir /tmp/geoip && tar -xzf /tmp/geoip.tar.gz -C /tmp/geoip --strip-components=1 \
 && cd /tmp/geoip && autoreconf -fi \
 && CC='gcc -m32' ./configure --host=i686-linux-gnu --prefix=/opt/dnf/compat/i386 --disable-static \
 && make -j2 && make install
COPY docker/ubuntu/audit/geoip-probe.c /tmp/geoip-probe.c
RUN gcc -m32 -I/tmp/geoip/libGeoIP -L/opt/dnf/compat/i386/lib -o /tmp/geoip-probe /tmp/geoip-probe.c -lGeoIP
COPY docker/ubuntu/legacy-shm.c /tmp/legacy-shm.c
COPY docker/ubuntu/legacy-stat.c /tmp/legacy-stat.c
COPY docker/ubuntu/admin-runtime-digest.c /tmp/admin-runtime-digest.c
RUN gcc -m32 -shared -fPIC -fvisibility=hidden -fno-ident -Wl,--build-id=none -Wall -Wextra -Werror -O2 -o /tmp/libdof-admin-runtime.so /tmp/admin-runtime-digest.c
RUN gcc -m32 -shared -fPIC -Wall -Wextra -Werror -O2 -o /tmp/liblegacy-stat.so /tmp/legacy-stat.c -ldl
COPY docker/ubuntu/frida-compat.c docker/ubuntu/frida-compat.map /tmp/
COPY docker/ubuntu/audit/frida-probe.c /tmp/frida-probe.c
RUN gcc -m32 -shared -fPIC -Wall -Wextra -Werror -O2 -Wl,--version-script=/tmp/frida-compat.map -o /tmp/libfrida-compat.so /tmp/frida-compat.c -ldl \
 && gcc -m32 -Wall -Wextra -Werror -rdynamic -o /tmp/frida-probe /tmp/frida-probe.c -ldl
COPY docker/ubuntu/audit/shm-probe.c /tmp/shm-probe.c
RUN echo 'd8fb57b31d9b29ad576e0100d6fa67e71aa19c3e6d994e5e0a5957e41caec772  /tmp/legacy-shm.c' | sha256sum -c - \
 && gcc -m32 -shared -fPIC -Wall -Wextra -Werror -O2 -o /tmp/liblegacy-shm.so /tmp/legacy-shm.c -ldl \
 && gcc -m32 -Wall -Wextra -Werror -o /tmp/shm-probe /tmp/shm-probe.c -lrt

FROM runtime AS game
# Modern mariadb-dump queries generated-column metadata absent in MySQL 5.6.
# Use the matching pinned client without adding/running a database service.
COPY --from=mysql56_client /usr/bin/mysqldump /usr/local/bin/dof-mysqldump56
COPY --from=mysql56_client /usr/share/doc/mysql-community-client/copyright /opt/dnf/licenses/mysql56-client-copyright
RUN echo '55aaf7412c4e46017dcf49f5c0c129ecea97f1c8fe4ed1e14634f0530f0ea65b  /usr/local/bin/dof-mysqldump56' | sha256sum -c - \
 && /usr/local/bin/dof-mysqldump56 --version
COPY assets/home/template/ /home/template/
COPY assets/TeaEncrypt /TeaEncrypt
COPY assets/etc/supervisor/ /etc/supervisor/
COPY assets/etc/supervisord.conf /etc/supervisord.conf
COPY --from=hooks /tmp/stun_hook.so /home/template/neople/stun/stun_hook.so
COPY --from=hooks /opt/dnf/compat/i386/lib/libGeoIP.so.1.6.12 /opt/dnf/compat/i386/libGeoIP.so.1
COPY --from=hooks /tmp/geoip/COPYING /opt/dnf/licenses/GeoIP-COPYING
COPY --from=hooks /tmp/geoip/LICENSE /opt/dnf/licenses/GeoIP-LICENSE
COPY --from=hooks /tmp/geoip-probe /opt/dnf/audit/geoip-probe
COPY --from=hooks /tmp/liblegacy-shm.so /opt/dnf/compat/i386/liblegacy-shm.so
COPY --from=hooks /tmp/liblegacy-stat.so /opt/dnf/compat/i386/liblegacy-stat.so
COPY --from=hooks /tmp/libdof-admin-runtime.so /opt/dnf/compat/i386/libdof-admin-runtime.so
COPY docker/ubuntu/admin-runtime-digest.c /opt/dnf/compat/i386/admin-runtime-digest.c
COPY --from=hooks /tmp/shm-probe /opt/dnf/audit/shm-probe
COPY --from=hooks /tmp/libfrida-compat.so /opt/dnf/compat/i386/libfrida-compat.so
COPY --from=hooks /tmp/frida-probe /opt/dnf/audit/frida-probe
COPY docker/ubuntu/audit/frida-probe.py /opt/dnf/audit/frida-probe.py
COPY docker/ubuntu/SHM-COMPAT-LICENSE /opt/dnf/licenses/SHM-COMPAT-LICENSE
COPY docker/ubuntu/GeoIP.dat /opt/dnf/compat/i386/share/GeoIP/GeoIP.dat
COPY docker/ubuntu/GEOIP-DATA-README /opt/dnf/licenses/GeoIP-DATA-README
COPY docker/ubuntu/compat-lock.json /opt/dnf/audit/compat-lock.json
COPY docker/ubuntu/run-game /opt/dnf/ubuntu/run-game
COPY docker/ubuntu/audit/elf-audit.py /opt/dnf/audit/elf-audit.py
RUN mkdir -p /opt/dnf/compat/i386 \
 && cp /home/template/neople/game/libnxencryption.so /opt/dnf/compat/i386/ \
 && echo 'cd1d9db9700d581e40547ff609a26bcf04415198fe9f3e4ecfc910eefc437f76  /opt/dnf/compat/i386/libnxencryption.so' | sha256sum -c - \
 && echo '58fcdeaac15e222a744b27333a776a2b91dc493d212acbe971ba6b987265a73c  /opt/dnf/compat/i386/share/GeoIP/GeoIP.dat' | sha256sum -c - \
 && find /home/template -type f -name '*.sh' -exec sed -i 's/\r$//' {} + \
 && sed -i 's/\r$//' /opt/dnf/ubuntu/run-game \
 && chmod 755 /TeaEncrypt /opt/dnf/ubuntu/run-game \
 && find /home/template -type f -exec chmod u+x {} + \
 && python3 /opt/dnf/audit/elf-audit.py /home/template --extra /TeaEncrypt --output /opt/dnf/audit/elf-report.json \
 && python3 /opt/dnf/audit/elf-audit.py /opt/dnf/compat --output /opt/dnf/audit/compat-report.json \
 && LD_LIBRARY_PATH=/opt/dnf/compat/i386 /opt/dnf/audit/geoip-probe > /opt/dnf/audit/geoip-start-proof.txt \
 && /opt/dnf/ubuntu/run-game /opt/dnf/audit/shm-probe > /opt/dnf/audit/shm-start-proof.txt \
 && DNF_GAME_PRELOAD=/home/template/init/libhook.so /opt/dnf/ubuntu/run-game /opt/dnf/audit/shm-probe >> /opt/dnf/audit/shm-start-proof.txt \
 && dpkg-query -W > /opt/dnf/audit/packages.tsv

FROM game AS final
COPY runtime/ubuntu/ /opt/dnf/ubuntu/
RUN mkdir -p /opt/dof-easy/runtime && mv /opt/dnf/ubuntu/gateway /opt/dof-easy/runtime/gateway
# Keep the repository input intact. Repair the image's fixed bridge template
# before instance initialization copies it, and retain the derivation evidence.
RUN python3 /opt/dnf/ubuntu/bridge_compat.py \
      --source /home/template/neople/bridge/df_bridge_r \
      --output /opt/dnf/audit/bridge-repair \
 && install -m 755 /opt/dnf/audit/bridge-repair/df_bridge_r /home/template/neople/bridge/df_bridge_r \
 && rm /opt/dnf/audit/bridge-repair/df_bridge_r \
 && python3 /opt/dnf/audit/elf-audit.py /home/template/neople/bridge --output /opt/dnf/audit/bridge-elf.json
COPY resources/plugins/dp2/ /opt/dnf/plugins/dp2/
COPY resources/plugins/frida/ /opt/dnf/plugins/frida/
COPY resources/plugins/raid/ /opt/dnf/plugins/raid/
RUN python3 /opt/dnf/audit/elf-audit.py /opt/dnf/plugins --preload-i386 /opt/dnf/compat/i386/libfrida-compat.so --output /opt/dnf/audit/plugins-elf.json
RUN --network=none python3 /opt/dnf/audit/frida-probe.py --gadget /opt/dnf/plugins/frida/frida.so --shim /opt/dnf/compat/i386/libfrida-compat.so --probe /opt/dnf/audit/frida-probe > /opt/dnf/audit/frida-start-proof.txt
RUN python3 /opt/dnf/ubuntu/prepare_image.py \
 && python3 /opt/dnf/ubuntu/tss_config.py
WORKDIR /root
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 CMD ["python3", "/opt/dnf/ubuntu/health.py"]
ENTRYPOINT ["/bin/bash", "/opt/dnf/ubuntu/entrypoint.sh"]
