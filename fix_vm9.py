with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if line.strip() == "fun toggleCommentLike(":
        print(f"toggleCommentLike at {i+1}")

