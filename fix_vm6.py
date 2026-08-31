import os
import shutil

# Check if there are backup files we can use
for f in os.listdir('app/src/main/java/com/example/viewmodel/'):
    print(f)
