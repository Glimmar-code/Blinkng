with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    lines = f.readlines()

open_braces = 0
for i in range(179, len(lines)):
    line = lines[i]
    open_braces += line.count('{')
    open_braces -= line.count('}')
    if open_braces == 0 and '{' in ''.join(lines[179:i+1]):
        print(f"Class ended at {i+1}")
        print(lines[i])
        break
