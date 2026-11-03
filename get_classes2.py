import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

idx1 = content.find('fun toggleCommentLike')
print(content[idx1:idx1+1000])

