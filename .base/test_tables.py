import urllib.request
import json

url = "https://jhwgifrlxwspoedxjaly.supabase.co"
key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"

tables = ['stories', 'story_views', 'story_likes', 'story_reactions', 'story_replies', 'feed_posts', 'post_likes', 'post_bookmarks', 'poll_votes', 'feed_comments', 'comment_likes']

for t in tables:
    try:
        req = urllib.request.Request(f"{url}/rest/v1/{t}?limit=1")
        req.add_header('apikey', key)
        req.add_header('Authorization', f'Bearer {key}')
        with urllib.request.urlopen(req) as response:
            print(f"Table '{t}': SUCCESS ({response.getcode()})")
    except Exception as e:
        print(f"Table '{t}': {e}")
