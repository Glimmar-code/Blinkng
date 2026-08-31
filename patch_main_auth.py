with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'isAuthor = post.author == uiState.myProfile.username || post.author == "efe.design" || post.author == "you"',
    'isAuthor = post.author == uiState.myProfile.username'
)

content = content.replace(
    'val isMyProfile = viewModel.isMe(profile.username) || viewModel.isMe(profile.fullName) || viewModel.isMe(profile.id) || profile.id == "user_me"',
    'val isMyProfile = viewModel.isMe(profile.username)'
)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

