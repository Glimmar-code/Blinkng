from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# The previous automated patch accidentally duplicated four MainAppContent when branches.
# Remove only an immediately repeated block; do not touch unrelated navigation logic.
main = ROOT / "app/src/main/java/com/example/MainActivity.kt"
s = main.read_text()
block = '''            uiState.isGetVerifiedOpen -> viewModel.openGetVerified(false)\n            uiState.isCreatePostOpen -> viewModel.openCreatePost(false)\n            uiState.activeViewingStory != null -> viewModel.closeStory()\n            uiState.showSellerCongratulationsDialog -> viewModel.dismissSellerCongratulations()'''
double = block + "\n" + block
if double in s:
    s = s.replace(double, block, 1)
    main.write_text(s)
    print("Removed duplicated MainAppContent branches.")
else:
    print("No duplicated MainAppContent branch block found.")
