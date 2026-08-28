import re

filepath = "app/src/main/java/com/example/ui/components/PostCard.kt"
with open(filepath, 'r') as f:
    content = f.read()

# Fix horizontal + top/bottom back to horizontal + vertical
content = re.sub(r'horizontal = ([0-9]+)\.dp,\s*top = ([0-9]+)\.dp,\s*bottom = \2\.dp', 
                 r'horizontal = \1.dp,\n                vertical = \2.dp', 
                 content)

with open(filepath, 'w') as f:
    f.write(content)
