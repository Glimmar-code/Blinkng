with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Fix double closing brace and broken comment at the end
bad_end = '''4057: }
4058: }'''

# Let's search for "enum class ProfileMediaType"
idx_enum = content.find('enum class ProfileMediaType')
if idx_enum != -1:
    # Find the last closing brace of toggleCommentLike before idx_enum
    idx_toggle_like = content.rfind('toggleCommentLike', 0, idx_enum)
    if idx_toggle_like != -1:
        # Find closing brace of toggleCommentLike method
        method_end = content.find('    }', idx_toggle_like)
        if method_end != -1:
            method_end += 5
            # Now close the SupabaseService class once
            class_close = "\n}\n\n"
            
            # Helper functions at top level
            top_level = '''enum class ProfileMediaType {
    AVATAR,
    COVER
}

private fun String.capitalizeWords(): String {
    return split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.uppercase() }
        }
}
'''
            content = content[:method_end] + class_close + top_level

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

print("Fixed SupabaseService end")
