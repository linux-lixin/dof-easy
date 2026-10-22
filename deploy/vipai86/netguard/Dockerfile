FROM vipai86-game:local
USER root
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends iptables \
    && rm -rf /var/lib/apt/lists/*
COPY entrypoint.py /usr/local/bin/vipai86-netguard.py
RUN mkdir -p /run/netguard
ENV XTABLES_LOCKFILE=/run/netguard/xtables.lock
ENTRYPOINT ["python3", "-B", "/usr/local/bin/vipai86-netguard.py"]
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=3 CMD ["python3", "-B", "/usr/local/bin/vipai86-netguard.py", "--healthcheck"]
