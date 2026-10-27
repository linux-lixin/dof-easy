# Populated postal read contract

Run `run.sh` only through `scripts/dof/local_environment.py` in its sealed private PID/network namespace. It starts a fresh MySQL 5.6 on namespace loopback; it never connects to the game host. Stage the verified MySQL tool files under `mysql/`, Java runtime under `java/`, backend dependency jars under `dependencies/`, compiled main/test classes under `classes/`, current `deploy/database/seed.sql`, and these scripts in a new private inputs directory. Inputs remain read-only.

`PostalReadContractTest` requires a matching server UUID, a unique disposable marker, and absence of all three game schemas before creating synthetic accounts and characters. It uses the repository's actual postal table definition and actual MyBatis mapper. Cleanup rechecks UUID and marker and removes only schemas this test created. Never point its opt-in property file at a running game database.

Four tests cover multiple characters per account (including a historical deleted character), account/character ID collision, literal SQL-looking and wildcard text, self/child guild scope, foreign/orphan rows, populated stable pagination, all projected equipment parameters, UTF-8 sender text, and inclusive time bounds. Unit/HTTP tests separately verify invalid input, missing actors and query failure responses. This does not prove original Windows-tool equivalence, real-server permissions, frontend browser behavior, sending, reward delivery, or native client receipt.

Publish only `postal-read-result.json` and namespace provenance. Keep temporary database files and fixture properties private.
