with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Remove anything from enum class ProfileMediaType onwards
enum_idx = content.find('enum class ProfileMediaType')
if enum_idx != -1:
    content = content[:enum_idx].rstrip()

# Now count open and close braces
open_b = content.count('{')
close_b = content.count('}')

diff = open_b - close_b
print(f"open_b: {open_b}, close_b: {close_b}, diff: {diff}")

# Append '}' diff times to close class
for _ in range(diff):
    content += "\n}"

content += '''

enum class ProfileMediaType {
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

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

print("Braces fixed!")
