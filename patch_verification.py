with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'r') as f:
    content = f.read()

old_v = """            isVerified =
                obj.optBoolean("is_verified", false) ||
                        obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true) ||
                        obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) ||
                        author.contains("verified", ignoreCase = true) ||
                        author.equals("gbolahan", ignoreCase = true) ||
                        author.equals("golowosile", ignoreCase = true) ||
                        author.equals("efe.design", ignoreCase = true),"""

new_v = """            isVerified =
                obj.optBoolean("is_verified", false) ||
                        obj.optString("verification_badge", "").equals("GOLD", ignoreCase = true) ||
                        obj.optString("verification_badge", "").equals("BLUE", ignoreCase = true) ||
                        author.contains("verified", ignoreCase = true),"""

content = content.replace(old_v, new_v)

old_badge = """            verificationBadge =
                when (obj.optString("verification_badge", "").uppercase(Locale.US)) {
                    "GOLD" -> VerificationBadge.GOLD
                    "BLUE" -> VerificationBadge.BLUE
                    else -> if (author.equals("golowosile", ignoreCase = true) || author.equals("gbolahan", ignoreCase = true) || author.equals("efe.design", ignoreCase = true)) {
                        VerificationBadge.GOLD
                    } else if (obj.optBoolean("is_verified", false)) {
                        VerificationBadge.BLUE
                    } else {
                        VerificationBadge.NONE
                    }
                },"""

new_badge = """            verificationBadge =
                when (obj.optString("verification_badge", "").uppercase(Locale.US)) {
                    "GOLD" -> VerificationBadge.GOLD
                    "BLUE" -> VerificationBadge.BLUE
                    else -> if (obj.optBoolean("is_verified", false)) {
                        VerificationBadge.BLUE
                    } else {
                        VerificationBadge.NONE
                    }
                },"""

content = content.replace(old_badge, new_badge)

with open('app/src/main/java/com/example/data/supabase/SupabaseService.kt', 'w') as f:
    f.write(content)

