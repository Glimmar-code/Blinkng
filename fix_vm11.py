with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if line.strip() == "class BlinkViewModel(":
        print(f"Starts at {i+1}")
