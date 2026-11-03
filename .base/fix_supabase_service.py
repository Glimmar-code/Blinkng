with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

# Check where getCurrentUsername is
if 'fun getCurrentUsername()' not in content:
    # Add getCurrentUsername inside session section
    user_id_idx = content.find('fun getCurrentUserId(): String?')
    if user_id_idx != -1:
        get_username_code = '''    fun getCurrentUsername(): String? {
        val context = SupabaseService.appContext ?: return null
        return context.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE)
            .getString("username", null)
    }

'''
        content = content[:user_id_idx] + get_username_code + content[user_id_idx:]

# Now let's fix the class structure: move everything after line 3915 inside the class
# Look for "// COMMENTS"
comments_idx = content.find('    // COMMENTS')
if comments_idx != -1:
    comments_block = content[comments_idx:]
    # remove comments_block from end
    content_without_comments = content[:comments_idx].rstrip()
    
    # find last closing brace of SupabaseService class
    # Let's count open/close braces to find exact end of SupabaseService class
    lines = content_without_comments.split('\n')
    open_b = 0
    last_class_brace_line = -1
    for i, line in enumerate(lines):
        open_b += line.count('{')
        open_b -= line.count('}')
        if open_b == 0 and i > 50:
            last_class_brace_line = i
            break
    
    if last_class_brace_line != -1:
        # insert comments_block before that closing brace
        lines_before = lines[:last_class_brace_line]
        lines_after = lines[last_class_brace_line:]
        content = '\n'.join(lines_before) + '\n\n' + comments_block + '\n' + '\n'.join(lines_after)

# Fix appContext references in comments block
content = content.replace('appContext?.getSharedPreferences', 'SupabaseService.appContext?.getSharedPreferences')

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

print("Fixed SupabaseService.kt")
