"""Run only inside run_local.py's disconnected MySQL test namespace."""
import secrets
from scripts.container import database, menu


def verify(query):
    binary = menu.mysql_binary()
    password = secrets.token_hex(16)
    query("CREATE USER 'socket_admin'@'localhost' IDENTIFIED BY '"+password+"';")
    query("GRANT ALL PRIVILEGES ON *.* TO 'socket_admin'@'localhost' WITH GRANT OPTION;")
    query("CREATE USER 'tcp_admin'@'172.30.254.1' IDENTIFIED BY '"+password+"';")
    query("GRANT ALL PRIVILEGES ON *.* TO 'tcp_admin'@'172.30.254.1' WITH GRANT OPTION;")
    assert '/tmp/mysql.sock' in menu.mysql_sockets(binary)
    for account, protocol in (('socket_admin','SOCKET'), ('tcp_admin','TCP')):
        with menu.automatic_mysql_connection(binary,account,password,'172.30.254.1',3307) as defaults:
            assert 'protocol='+protocol in defaults.read_text()
            assert defaults.stat().st_mode & 0o777 == 0o600
            assert menu.check_external(binary,defaults,'172.30.254.1',3307) == '0.0.0.0'
            actual = database.host_query(binary,defaults)
            assert actual('SELECT CURRENT_USER();').startswith(account+'@')
            actual('CREATE DATABASE menu_connection_probe; DROP DATABASE menu_connection_probe;')
        assert not defaults.exists()
    try:
        with menu.automatic_mysql_connection(binary,'tcp_admin',secrets.token_hex(16),'172.30.254.1',3307):
            raise AssertionError('Wrong password accepted')
    except ValueError:
        pass
    return dict(socketAccountVerified=True,tcpFallbackVerified=True,wildcardBindAccepted=True,
                temporaryCredentialsRemoved=True,wrongPasswordRejected=True)
