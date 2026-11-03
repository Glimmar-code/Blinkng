import urllib.request
import json

url = "https://jhwgifrlxwspoedxjaly.supabase.co"
key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"

try:
    req = urllib.request.Request(f"{url}/rest/v1/poll_votes?limit=1")
    req.add_header('apikey', key)
    req.add_header('Authorization', f'Bearer {key}')
    with urllib.request.urlopen(req) as response:
        print("poll_votes table exists! Code:", response.getcode())
except Exception as e:
    print("poll_votes error:", e)
