# Frida database contract probe

Run only through `scripts/dof/local_environment.py` in a sealed, private network/PID namespace. Never run `run.sh` directly on a host. The probe creates a disposable MySQL 5.6 instance with networking disabled. It does not load game executables or connect to a server.

Stage `probe.py`, `run.sh`, the repository's `scripts/container/database.py` and `deploy/database/seed.sql` under `repo/`, and the verified MySQL 5.6 files under `mysql/`. Add the matching `mysql/usr/bin/mysqldump` with SHA-256 `55aaf7412c4e46017dcf49f5c0c129ecea97f1c8fe4ed1e14634f0530f0ea65b` (the same pinned client used by the game Dockerfile).

The test validates fresh schema creation, exact target-account grants, repeated migration IDs, byte-preserved UTF-8 progress, disabled-plugin backup scope, and refusal of unknown columns, unique indexes, triggers or foreign keys. Backup permissions are independently exercised through a local-socket fixture account; Docker subnet routing is intentionally **not** claimed. It also does not establish native Frida consumption, activity scheduling, rewards or original Windows-tool equivalence.

Outputs may include private temporary MySQL files. Publish only `frida-database-result.json` and namespace provenance, never database directories or credential-bearing process dumps. The initial S6 attempt lacked `mysqldump`; its failed result is retained separately from the successful second attempt.

`live-probe-s6.py` is the exact, deliberately host-bound S6 test recipe. It requires the pinned 86-grade test instance/image, local BaoTa/MySQL installation, existing private deployment directory and a fresh fault-state file. It stops/restarts **only** the game, temporarily writes malformed JSON to the one `villageattack` row, retains a private original checkpoint, restores that field with a conditional write, and checks both real channels. It neither enables an activity nor proves scheduling/rewards. Do not run it on another host or delete a previous fault state to force a rerun; inspect/reconcile that original state first. Its credential handling stays within the test server and uses the panel's official decrypting reader.

`config-probe.py` checks file preparation separately. Run it with `python3` as
the entrypoint of an existing game image, with `--network=none`, a read-only
root filesystem, and a writable `/probe` tmpfs (256 MiB in S44). A candidate
`plugins.py` may be mounted read-only at `/opt/dnf/ubuntu/plugins.py`; mount
the probe read-only as well. No instance data, credentials, Docker socket or
database configuration should be mounted. The S44 run additionally dropped
all capabilities and used `no-new-privileges`.

The probe uses the actual bundled plugin files, rotates synthetic credentials,
checks nested options and private file permissions, retains custom scripts and
logs, and refuses unknown JSON shapes before plugin copies. It never executes
the copied Frida library or a game binary and never connects to a database.
Its JSON report proves preparation in that image, not native consumption,
gameplay, active-instance deployment, or physical fault recovery. Publish only
the report; the temporary fixture tree is removed at the end.
