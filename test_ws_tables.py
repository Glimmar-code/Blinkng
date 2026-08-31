import socket
import ssl
import json
import base64
import os
import struct

def make_ws_frame(text):
    data = text.encode('utf-8')
    length = len(data)
    frame = bytearray()
    frame.append(0x81)
    if length <= 125:
        frame.append(0x80 | length)
    elif length <= 65535:
        frame.append(0x80 | 126)
        frame.extend(struct.pack('!H', length))
    else:
        frame.append(0x80 | 127)
        frame.extend(struct.pack('!Q', length))
    
    mask = os.urandom(4)
    frame.extend(mask)
    for i in range(length):
        frame.append(data[i] ^ mask[i % 4])
    return frame

def parse_ws_frame(buf):
    if len(buf) < 2:
        return None, buf
    b2 = buf[1]
    payload_len = b2 & 0x7F
    offset = 2
    if payload_len == 126:
        if len(buf) < 4:
            return None, buf
        payload_len = struct.unpack('!H', buf[2:4])[0]
        offset = 4
    elif payload_len == 127:
        if len(buf) < 10:
            return None, buf
        payload_len = struct.unpack('!Q', buf[2:10])[0]
        offset = 10
    
    if len(buf) < offset + payload_len:
        return None, buf
    
    payload = buf[offset:offset+payload_len]
    rem = buf[offset+payload_len:]
    return payload.decode('utf-8', errors='ignore'), rem

host = "jhwgifrlxwspoedxjaly.supabase.co"
port = 443
apikey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"
path = f"/realtime/v1/websocket?apikey={apikey}&v=1.0.0"

for table in ['messages', 'conversations', 'notifications', 'feed_posts']:
    ws_key = base64.b64encode(os.urandom(16)).decode()
    req = f"GET {path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {ws_key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
    context = ssl.create_default_context()
    with socket.create_connection((host, port)) as sock:
        with context.wrap_socket(sock, server_hostname=host) as ssock:
            ssock.sendall(req.encode())
            ssock.recv(4096)
            join_payload = json.dumps({
                "topic": f"realtime:public:{table}",
                "event": "phx_join",
                "payload": {
                    "config": {
                        "postgres_changes": [
                            {"event": "*", "schema": "public", "table": table}
                        ]
                    }
                },
                "ref": "1"
            })
            ssock.sendall(make_ws_frame(join_payload))
            data = ssock.recv(4096)
            msg, _ = parse_ws_frame(data)
            print(f"Table {table}: {msg}")

