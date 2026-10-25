# Private-namespace compatibility listener; only loopback, no configurable hosts.
import socket,socketserver,selectors
class Handler(socketserver.BaseRequestHandler):
 def handle(self):
  try:
   with socket.create_connection(('127.0.0.1',3307),timeout=3) as peer:
    peer.settimeout(None)
    with selectors.DefaultSelector() as events:
     events.register(self.request,selectors.EVENT_READ,peer)
     events.register(peer,selectors.EVENT_READ,self.request)
     while True:
      for key,_ in events.select():
       data=key.fileobj.recv(65536)
       if not data:return
       key.data.sendall(data)
  except (ConnectionError,OSError):return
class Server(socketserver.ThreadingTCPServer):
 allow_reuse_address=True
 daemon_threads=True
with Server(('127.0.0.1',3306),Handler) as server:server.serve_forever()
