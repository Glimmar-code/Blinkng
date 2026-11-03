import socket
import ssl
import json
import base64
import os

host = "jhwgifrlxwspoedxjaly.supabase.co"
port = 443
apikey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"
path = f"/realtime/v1/websocket?apikey={apikey}&v=1.0.0"

ws_key = base64.b64encode(os.urandom(16)).decode()

req = (
    f"GET {path} HTTP/1.1\r\n"
    f"Host: {host}\r\n"
    f"Upgrade: websocket\r\n"
    f"Connection: Upgrade\r\n"
    f"Sec-WebSocket-Key: {ws_key}\r\n"
    f"Sec-WebSocket-Version: 13\r\n"
    f"\r\n"
)

context = ssl.create_default_context()
with socket.create_connection((host, port)) as sock:
    with context.wrap_socket(sock, server_hostname=host) as ssock:
        ssock.sendall(req.encode())
        resp = ssock.recv(4096).decode('utf-8', errors='ignore')
        print("Handshake Response:\n", resp[:300])

