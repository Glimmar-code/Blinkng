import os

files_to_fix = [
    "app/src/main/java/com/example/ui/components/CreatePostSheet.kt",
    "app/src/main/java/com/example/ui/components/PostCard.kt",
    "app/src/main/java/com/example/ui/components/StoryBar.kt"
]

extra_imports = {
    "app/src/main/java/com/example/ui/components/CreatePostSheet.kt": [
        "import androidx.compose.material3.MaterialTheme",
        "import androidx.compose.animation.core.Spring",
        "import androidx.compose.animation.core.animateFloat",
        "import androidx.compose.foundation.text.KeyboardOptions"
    ],
    "app/src/main/java/com/example/ui/components/PostCard.kt": [
        "import androidx.compose.animation.core.animateFloat"
    ],
    "app/src/main/java/com/example/ui/components/StoryBar.kt": [
        "import androidx.compose.animation.core.animateFloat",
        "import androidx.compose.foundation.border"
    ]
}

for filepath in files_to_fix:
    with open(filepath, 'r') as f:
        lines = f.readlines()
        
    # Clean out all 'package com.example.ui.components\n'
    clean_lines = [l for l in lines if l.strip() != "package com.example.ui.components"]
    # Clean out the extra imports we added in case they are duplicated
    for imp in extra_imports[filepath]:
        clean_lines = [l for l in clean_lines if l.strip() != imp]
        
    final_lines = ["package com.example.ui.components\n\n"]
    for imp in extra_imports[filepath]:
        final_lines.append(imp + "\n")
        
    final_lines.extend(clean_lines)
    
    with open(filepath, 'w') as f:
        f.writelines(final_lines)
