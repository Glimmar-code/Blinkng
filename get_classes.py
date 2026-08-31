import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

# find everything between toggleCommentLike and openActivity
idx1 = content.find('fun toggleCommentLike')
idx2 = content.find('fun openActivity')

if idx1 != -1 and idx2 != -1:
    print(content[idx1:idx2])
else:
    print("Not found")

