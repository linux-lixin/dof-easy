FROM ubuntu:24.04@sha256:534baea6a22c03a63003dbc8dbe78fe34bc0d7e595d9a9dc9834884ff530eb55
USER root
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 iptables \
    && rm -rf /var/lib/apt/lists/*
COPY entrypoint.py /usr/local/bin/dof-netguard.py
RUN mkdir -p /run/netguard
ENV XTABLES_LOCKFILE=/run/netguard/xtables.lock
ENTRYPOINT ["python3", "-B", "/usr/local/bin/dof-netguard.py"]
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=3 CMD ["python3", "-B", "/usr/local/bin/dof-netguard.py", "--healthcheck"]
