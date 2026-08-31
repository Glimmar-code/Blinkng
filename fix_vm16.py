with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

idx = content.find('showToast("Failed to update bookmark.")\n            }\n        }\n    }')
idx2 = content.find('fun sharePost(', idx)

content = content[:idx+len('showToast("Failed to update bookmark.")\n            }\n        }\n    }')] + "\n\n" + content[idx2:]

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)

