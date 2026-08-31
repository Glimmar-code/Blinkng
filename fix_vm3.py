import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

# Let's inspect everything between toggleCommentLike (2459) and openChatWithUser (2495)
# Because some syntax error occurred, maybe unbalanced brackets.
idx1 = content.find('fun toggleCommentLike')
idx2 = content.find('fun openChatWithUser')
print(content[idx1:idx2])
