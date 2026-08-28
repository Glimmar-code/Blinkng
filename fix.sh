#!/bin/bash
echo "package com.example.ui.components" > tmp.kt
echo "import androidx.compose.material3.MaterialTheme" >> tmp.kt
echo "import androidx.compose.animation.core.Spring" >> tmp.kt
echo "import androidx.compose.animation.core.animateFloat" >> tmp.kt
echo "import androidx.compose.foundation.text.KeyboardOptions" >> tmp.kt
tail -n +2 app/src/main/java/com/example/ui/components/CreatePostSheet.kt >> tmp.kt
mv tmp.kt app/src/main/java/com/example/ui/components/CreatePostSheet.kt

echo "package com.example.ui.components" > tmp.kt
echo "import androidx.compose.animation.core.animateFloat" >> tmp.kt
tail -n +2 app/src/main/java/com/example/ui/components/PostCard.kt >> tmp.kt
mv tmp.kt app/src/main/java/com/example/ui/components/PostCard.kt

echo "package com.example.ui.components" > tmp.kt
echo "import androidx.compose.animation.core.animateFloat" >> tmp.kt
echo "import androidx.compose.foundation.border" >> tmp.kt
tail -n +2 app/src/main/java/com/example/ui/components/StoryBar.kt >> tmp.kt
mv tmp.kt app/src/main/java/com/example/ui/components/StoryBar.kt
