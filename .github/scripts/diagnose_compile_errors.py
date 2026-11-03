from pathlib import Path

root = Path('.')
checks = {
    'app/src/main/java/com/example/ui/screens/MessagesScreen.kt': [(2418,2440),(3588,3604),(4442,4460)],
    'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt': [(130,185)],
}
for rel, ranges in checks.items():
    p = root / rel
    print(f'===== {rel} =====')
    lines = p.read_text(encoding='utf-8').splitlines()
    for start, end in ranges:
        print(f'--- lines {start}-{end} ---')
        for n in range(max(1,start), min(len(lines),end)+1):
            print(f'{n}: {lines[n-1]}')
