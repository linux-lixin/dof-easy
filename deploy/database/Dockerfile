FROM mysql:5.6@sha256:20575ecebe6216036d25dab5903808211f1e9ba63dc7825ac20cb975e34cfcae
COPY seed.sql /docker-entrypoint-initdb.d/10-seed.sql
COPY compat-tables.sql /docker-entrypoint-initdb.d/20-compat-tables.sql
COPY my.cnf /etc/mysql/conf.d/dof.cnf
