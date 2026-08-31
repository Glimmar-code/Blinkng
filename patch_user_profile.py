with open('app/src/main/java/com/example/data/models/UserProfileModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'var username: String = "efe.design",',
    'var username: String = "",'
)
content = content.replace(
    'var id: String = "user_me",',
    'var id: String = "",'
)
content = content.replace(
    'var fullName: String = "Efe Chukwu",',
    'var fullName: String = "",'
)

with open('app/src/main/java/com/example/data/models/UserProfileModel.kt', 'w') as f:
    f.write(content)

