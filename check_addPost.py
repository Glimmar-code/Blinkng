with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

idx1 = content.find('fun addPost(')
idx2 = content.find('fun ', idx1 + 10)
print(content[idx1:idx2])
