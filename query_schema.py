import urllib.request

url = "https://jhwgifrlxwspoedxjaly.supabase.co"
key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"

try:
    req = urllib.request.Request(f"{url}/rest/v1/post_comments?limit=1")
    req.add_header('apikey', key)
    req.add_header('Authorization', f'Bearer {key}')
    with urllib.request.urlopen(req) as response:
        print("post_comments success:", response.read().decode())
except Exception as e:
    print("error post_comments:", e)
    
try:
    req = urllib.request.Request(f"{url}/rest/v1/feed_comments?limit=1")
    req.add_header('apikey', key)
    req.add_header('Authorization', f'Bearer {key}')
    with urllib.request.urlopen(req) as response:
        print("feed_comments success:", response.read().decode())
except Exception as e:
    print("error feed_comments:", e)
