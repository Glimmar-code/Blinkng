with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

old_isme = """        return clean.equals("you", ignoreCase = true) ||
                clean.equals("me", ignoreCase = true) ||
                clean.equals("self", ignoreCase = true) ||
                clean.equals("user_me", ignoreCase = true) ||
                clean.equals("Your Story", ignoreCase = true) ||
                clean.equals(myUser, ignoreCase = true) ||
                clean.equals(myName, ignoreCase = true) ||
                clean.equals(myId, ignoreCase = true) ||
                clean.equals("efe.design", ignoreCase = true) ||
                clean.equals("Efe Chukwu", ignoreCase = true) ||
                clean.equals("golowosile", ignoreCase = true) ||
                clean.replace(" ", ".").equals(myUser, ignoreCase = true) ||
                myUser.replace(".", " ").equals(clean, ignoreCase = true)"""

new_isme = """        return clean.equals("you", ignoreCase = true) ||
                clean.equals("me", ignoreCase = true) ||
                clean.equals("self", ignoreCase = true) ||
                clean.equals("Your Story", ignoreCase = true) ||
                clean.equals(myUser, ignoreCase = true) ||
                clean.equals(myName, ignoreCase = true) ||
                clean.equals(myId, ignoreCase = true) ||
                clean.replace(" ", ".").equals(myUser, ignoreCase = true) ||
                myUser.replace(".", " ").equals(clean, ignoreCase = true)"""

content = content.replace(old_isme, new_isme)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)

