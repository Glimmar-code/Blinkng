import websocket
import json
import time

url = "wss://jhwgifrlxwspoedxjaly.supabase.co/realtime/v1/websocket?apikey=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk&v=1.0.0"

def on_message(ws, message):
    print("Received:", message)

def on_error(ws, error):
    print("Error:", error)

def on_close(ws, close_status_code, close_msg):
    print("### closed ###")

def on_open(ws):
    print("Opened connection")
    # Join messages channel
    join_msg = {
        "topic": "realtime:public:messages",
        "event": "phx_join",
        "payload": {
            "config": {
                "postgres_changes": [
                    {"event": "*", "schema": "public", "table": "messages"}
                ]
            }
        },
        "ref": "1"
    }
    ws.send(json.dumps(join_msg))

ws = websocket.WebSocketApp(url,
                          on_open=on_open,
                          on_message=on_message,
                          on_error=on_error,
                          on_close=on_close)

# Run for 3 seconds
import threading
t = threading.Thread(target=ws.run_forever)
t.start()
time.sleep(3)
ws.close()
