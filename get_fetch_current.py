with open('app/src/main/java/com/example/data/repository/ProfileRepository.kt', 'r') as f:
    content = f.read()

idx1 = content.find('suspend fun fetchCurrent(')
idx2 = content.find('suspend fun', idx1 + 10)
if idx2 == -1: idx2 = len(content)
print(content[idx1:idx2])
