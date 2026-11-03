import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

# Let's see what went wrong with my toggleCommentLike replacement
# I replaced:
# r'    fun toggleCommentLike\([\s\S]*?val updated =[\s\S]*?_uiState\.value =[\s\S]*?\n\s*\}'
# It's possible I replaced way too much.
# Let's restore from git if possible, or just look at what I replaced.
# Is git available?
import subprocess
try:
    print(subprocess.check_output(['git', 'diff', 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt']).decode()[-500:])
except Exception as e:
    print(e)
