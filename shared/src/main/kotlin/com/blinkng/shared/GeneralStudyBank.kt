package com.blinkng.shared

enum class GeneralStudyDifficulty(val label: String, val code: String) {
    EASY("Easy", "E"),
    MEDIUM("Medium", "M"),
    HARD("Hard", "H"),
    EXPERT("Expert", "X"),
}

data class GeneralStudyQuestion(
    val id: String,
    val difficulty: GeneralStudyDifficulty,
    val category: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
) {
    val answer: String get() = options[correctIndex]
}

object GeneralStudyBank {
    const val QUESTIONS_PER_DIFFICULTY = 5_000
    const val TOTAL_QUESTIONS = 20_000
    const val DEFAULT_ROUND_SIZE = 10

    val categories = listOf(
        "Mathematics",
        "English",
        "Science",
        "Geography",
        "History & Civics",
        "Technology",
        "Logic",
        "General Knowledge",
    )

    fun questionAt(difficulty: GeneralStudyDifficulty, index: Int): GeneralStudyQuestion {
        require(index in 0 until QUESTIONS_PER_DIFFICULTY)
        val categoryIndex = index % categories.size
        val slot = index / categories.size
        val generated = when (categoryIndex) {
            0 -> mathematics(difficulty, slot)
            1 -> english(difficulty, slot)
            2 -> science(difficulty, slot)
            3 -> geography(difficulty, slot)
            4 -> history(difficulty, slot)
            5 -> technology(difficulty, slot)
            6 -> logic(difficulty, slot)
            else -> generalKnowledge(difficulty, slot)
        }
        return generated.copy(
            id = "GS-" + difficulty.code + "-" + (index + 1).toString().padStart(4, '0'),
            difficulty = difficulty,
            category = categories[categoryIndex],
        )
    }

    fun round(
        difficulty: GeneralStudyDifficulty,
        seed: Long,
        count: Int = DEFAULT_ROUND_SIZE,
    ): List<GeneralStudyQuestion> {
        val safeCount = count.coerceIn(1, 50)
        val start = Math.floorMod((seed xor (seed ushr 32)).toInt(), QUESTIONS_PER_DIFFICULTY)
        val step = 997
        return List(safeCount) { offset ->
            val index = (start + offset * step) % QUESTIONS_PER_DIFFICULTY
            questionAt(difficulty, index)
        }
    }

    private fun q(prompt: String, correct: String, distractors: List<String>, salt: Int) =
        GeneralStudyQuestion(
            id = "",
            difficulty = GeneralStudyDifficulty.EASY,
            category = "",
            prompt = prompt,
            options = buildChoices(correct, distractors, salt),
            correctIndex = Math.floorMod(salt, 4),
        )

    private fun buildChoices(correct: String, distractors: List<String>, salt: Int): List<String> {
        val pool = distractors.filter { it != correct }.distinct().toMutableList()
        while (pool.size < 3) pool += "None of these " + (pool.size + 1)
        val correctIndex = Math.floorMod(salt, 4)
        val result = mutableListOf<String>()
        var d = 0
        repeat(4) { i ->
            if (i == correctIndex) result += correct else result += pool[d++]
        }
        return result
    }

    private fun numberChoices(value: Long, suffix: String, salt: Int, spread: Long = 1L): List<String> {
        fun f(v: Long) = v.toString() + suffix
        val a = if (value - spread >= 0) value - spread else value + 4 * spread
        return buildChoices(
            f(value),
            listOf(f(a), f(value + spread), f(value + 2 * spread), f(value + 3 * spread)),
            salt,
        )
    }

    private fun decimalChoices(value: Double, suffix: String, salt: Int): List<String> {
        fun f(v: Double): String {
            val rounded = kotlin.math.round(v * 100.0) / 100.0
            val raw = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
            return raw + suffix
        }
        return buildChoices(
            f(value),
            listOf(f(value + 1), f(kotlin.math.max(0.0, value - 1)), f(value + 2), f(value + 5)),
            salt,
        )
    }

    private fun fromOptions(prompt: String, correct: String, options: List<String>, salt: Int) =
        q(prompt, correct, options.filter { it != correct }, salt)

    private fun mathematics(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val salt = n + d.ordinal * 701
        return when (d) {
            GeneralStudyDifficulty.EASY -> {
                val a = 24 + n
                val b = 3 + (n % 17)
                when (n % 4) {
                    0 -> q("What is " + a + " + " + b + "?", (a + b).toString(), numberChoices((a + b).toLong(), "", salt).filter { it != (a + b).toString() }, salt)
                    1 -> q("What is " + a + " - " + b + "?", (a - b).toString(), numberChoices((a - b).toLong(), "", salt).filter { it != (a - b).toString() }, salt)
                    2 -> {
                        val x = 3 + (n % 19)
                        val y = 2 + ((n / 3) % 12)
                        q("What is " + x + " × " + y + "?", (x * y).toString(), numberChoices((x * y).toLong(), "", salt, y.toLong().coerceAtLeast(1)).filter { it != (x * y).toString() }, salt)
                    }
                    else -> {
                        val y = 2 + (n % 12)
                        val x = 3 + ((n / 5) % 25)
                        val total = x * y
                        q("What is " + total + " ÷ " + y + "?", x.toString(), numberChoices(x.toLong(), "", salt).filter { it != x.toString() }, salt)
                    }
                }
            }
            GeneralStudyDifficulty.MEDIUM -> when (n % 4) {
                0 -> {
                    val base = 80 + n
                    val pct = listOf(10, 15, 20, 25, 30, 40)[n % 6]
                    val answer = base * pct / 100.0
                    fromOptions("Find " + pct + "% of " + base + ".", formatNumber(answer), decimalChoices(answer, "", salt).filter { it != formatNumber(answer) }, salt)
                }
                1 -> {
                    val x = 5 + (n % 80)
                    val m = 2 + (n % 8)
                    val c = 3 + ((n / 3) % 20)
                    val rhs = m * x + c
                    q("Solve for x: " + m + "x + " + c + " = " + rhs + ".", x.toString(), numberChoices(x.toLong(), "", salt).filter { it != x.toString() }, salt)
                }
                2 -> {
                    val first = 2 + (n % 18)
                    val second = 2 + ((n / 5) % 18)
                    val k = 2 + ((n / 7) % 10)
                    val total = (first + second) * k
                    val answer = first * k
                    q("A ratio is " + first + ":" + second + ". If the total is " + total + ", what is the first part?", answer.toString(), numberChoices(answer.toLong(), "", salt, k.toLong()).filter { it != answer.toString() }, salt)
                }
                else -> {
                    val a = 10 + n % 40
                    val b = a + 2 + n % 15
                    val c = b + 2 + n % 12
                    val avg = (a + b + c) / 3.0
                    fromOptions("Find the average of " + a + ", " + b + " and " + c + ".", formatNumber(avg), decimalChoices(avg, "", salt).filter { it != formatNumber(avg) }, salt)
                }
            }
            GeneralStudyDifficulty.HARD -> when (n % 4) {
                0 -> {
                    val r1 = -2 - (n % 10)
                    val r2 = 3 + ((n / 2) % 12)
                    val b = -(r1 + r2)
                    val c = r1 * r2
                    val correct = listOf(r1, r2).sorted().joinToString(", ")
                    fromOptions(
                        "Solve x² " + signed(b) + "x " + signed(c) + " = 0. Give both roots.",
                        correct,
                        listOf(
                            listOf(r1 - 1, r2).sorted().joinToString(", "),
                            listOf(r1, r2 + 1).sorted().joinToString(", "),
                            listOf(-r1, -r2).sorted().joinToString(", "),
                        ),
                        salt,
                    )
                }
                1 -> {
                    val total = 7 + (n % 10)
                    val pick = 2 + (n % 4)
                    val answer = permutation(total, pick)
                    q("How many ordered arrangements of " + pick + " objects can be selected from " + total + " distinct objects?", answer.toString(), numberChoices(answer, "", salt, kotlin.math.max(1L, answer / 10)).filter { it != answer.toString() }, salt)
                }
                2 -> {
                    val base = 10 + (n % 35)
                    val height = 5 + ((n / 4) % 30)
                    val answer = base * height / 2.0
                    fromOptions("What is the area of a triangle with base " + base + " and height " + height + "?", formatNumber(answer), decimalChoices(answer, "", salt).filter { it != formatNumber(answer) }, salt)
                }
                else -> {
                    val base = 2 + (n % 8)
                    val exp = 3 + ((n / 6) % 5)
                    val answer = pow(base.toLong(), exp)
                    q("Evaluate " + base + "^" + exp + ".", answer.toString(), numberChoices(answer, "", salt, base.toLong()).filter { it != answer.toString() }, salt)
                }
            }
            GeneralStudyDifficulty.EXPERT -> when (n % 4) {
                0 -> {
                    val a = 20 + n
                    val b = 7 + (n % 31)
                    val answer = a % b
                    q("What is " + a + " mod " + b + "?", answer.toString(), numberChoices(answer.toLong(), "", salt).filter { it != answer.toString() }, salt)
                }
                1 -> {
                    val a = 12 + (n % 70)
                    val b = 9 + ((n / 3) % 60)
                    val answer = lcm(a.toLong(), b.toLong())
                    q("Find lcm(" + a + ", " + b + ").", answer.toString(), numberChoices(answer, "", salt, kotlin.math.max(1L, answer / 12)).filter { it != answer.toString() }, salt)
                }
                2 -> {
                    val first = 2 + (n % 20)
                    val diff = 2 + ((n / 3) % 12)
                    val terms = 8 + ((n / 5) % 10)
                    val answer = terms.toLong() * (2L * first + (terms - 1L) * diff) / 2L
                    q("Find the sum of the first " + terms + " terms of an arithmetic sequence with first term " + first + " and common difference " + diff + ".", answer.toString(), numberChoices(answer, "", salt, diff.toLong()).filter { it != answer.toString() }, salt)
                }
                else -> {
                    val a = 30 + (n % 90)
                    val b = 20 + ((n / 7) % 70)
                    val answer = gcd(a.toLong(), b.toLong())
                    q("Find gcd(" + a + ", " + b + ").", answer.toString(), numberChoices(answer, "", salt).filter { it != answer.toString() }, salt)
                }
            }
        }
    }

    private val words = listOf(
        "student","letter","window","planet","orange","school","future","market","friend","camera","bridge","garden","silver","energy","travel","purple",
        "science","history","logic","number","mobile","coding","profile","message","country","capital","ocean","forest","system","binary","circle","vector",
        "answer","question","memory","language","degree","campus","library","network","screen","button","signal","people","course","result","search","design",
        "create","learn","reason","global","method","value","change","object","simple","strong","bright","smooth","secure","public","expert","medium"
    )

    private fun english(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val word = words[n % words.size]
        val variant = (n / words.size) % 10
        val salt = n + d.ordinal * 809
        val vowels = word.count { it.lowercaseChar() in "aeiou" }
        val consonants = word.count { it.isLetter() && it.lowercaseChar() !in "aeiou" }
        val firstPos = word.first().lowercaseChar() - 'a' + 1
        val lastPos = word.last().lowercaseChar() - 'a' + 1
        return when (d) {
            GeneralStudyDifficulty.EASY -> when (variant) {
                0 -> q("How many letters are in the word '" + word + "'?", word.length.toString(), numberChoices(word.length.toLong(), "", salt).filter { it != word.length.toString() }, salt)
                1 -> q("How many vowels are in '" + word + "'?", vowels.toString(), numberChoices(vowels.toLong(), "", salt).filter { it != vowels.toString() }, salt)
                2 -> q("How many consonants are in '" + word + "'?", consonants.toString(), numberChoices(consonants.toLong(), "", salt).filter { it != consonants.toString() }, salt)
                3 -> fromOptions("What is the first letter of '" + word + "'?", word.first().toString(), listOf(word.last().toString(), "a", "e"), salt)
                4 -> fromOptions("What is the last letter of '" + word + "'?", word.last().toString(), listOf(word.first().toString(), "s", "n"), salt)
                5 -> fromOptions("Reverse the word '" + word + "'.", word.reversed(), listOf(word, word.drop(1) + word.first(), word.sorted().joinToString("")), salt)
                6 -> q("What is the alphabet position of the first letter in '" + word + "'?", firstPos.toString(), numberChoices(firstPos.toLong(), "", salt).filter { it != firstPos.toString() }, salt)
                7 -> q("What is the alphabet position of the last letter in '" + word + "'?", lastPos.toString(), numberChoices(lastPos.toLong(), "", salt).filter { it != lastPos.toString() }, salt)
                8 -> fromOptions("Does '" + word + "' begin with a vowel?", if (word.first().lowercaseChar() in "aeiou") "Yes" else "No", listOf("Yes", "No", "Sometimes", "Not enough information"), salt)
                else -> fromOptions("Does '" + word + "' end with a vowel?", if (word.last().lowercaseChar() in "aeiou") "Yes" else "No", listOf("Yes", "No", "Sometimes", "Not enough information"), salt)
            }
            GeneralStudyDifficulty.MEDIUM -> when (variant) {
                0 -> fromOptions("Sort the letters of '" + word + "' alphabetically.", word.toList().sorted().joinToString(""), listOf(word, word.reversed(), word.toList().sortedDescending().joinToString("")), salt)
                1 -> q("How many distinct letters are in '" + word + "'?", word.toSet().size.toString(), numberChoices(word.toSet().size.toLong(), "", salt).filter { it != word.toSet().size.toString() }, salt)
                2 -> fromOptions("Write only the vowels in '" + word + "' in their original order.", word.filter { it.lowercaseChar() in "aeiou" }.ifBlank { "none" }, listOf(word.filter { it.lowercaseChar() !in "aeiou" }, word.reversed().filter { it.lowercaseChar() in "aeiou" }, "none"), salt)
                3 -> fromOptions("Write only the consonants in '" + word + "' in their original order.", word.filter { it.lowercaseChar() !in "aeiou" }, listOf(word.filter { it.lowercaseChar() in "aeiou" }, word.reversed().filter { it.lowercaseChar() !in "aeiou" }, word), salt)
                4 -> q("Add the alphabet positions of the first and last letters of '" + word + "'.", (firstPos + lastPos).toString(), numberChoices((firstPos + lastPos).toLong(), "", salt).filter { it != (firstPos + lastPos).toString() }, salt)
                5 -> fromOptions("What are the first and last letters of '" + word + "'?", "" + word.first() + word.last(), listOf("" + word.last() + word.first(), word.take(2), word.takeLast(2)), salt)
                6 -> q("How many times does the first letter of '" + word + "' appear in the word?", word.count { it == word.first() }.toString(), numberChoices(word.count { it == word.first() }.toLong(), "", salt).filter { it != word.count { it == word.first() }.toString() }, salt)
                7 -> fromOptions("What is the second letter of '" + word + "'?", word[1].toString(), listOf(word.first().toString(), word.last().toString(), word[word.length - 2].toString()), salt)
                8 -> fromOptions("What is the penultimate letter of '" + word + "'?", word[word.length - 2].toString(), listOf(word.first().toString(), word.last().toString(), word[1].toString()), salt)
                else -> q("What is the difference between the alphabet positions of the first and last letters of '" + word + "'?", kotlin.math.abs(firstPos - lastPos).toString(), numberChoices(kotlin.math.abs(firstPos - lastPos).toLong(), "", salt).filter { it != kotlin.math.abs(firstPos - lastPos).toString() }, salt)
            }
            GeneralStudyDifficulty.HARD -> when (variant) {
                0 -> q("Using A=1, B=2, ..., what is the alphabet-score of '" + word + "'?", alphabetScore(word).toString(), numberChoices(alphabetScore(word).toLong(), "", salt, 3).filter { it != alphabetScore(word).toString() }, salt)
                1 -> q("Using A=1, B=2, ..., what is the vowel-score of '" + word + "'?", alphabetScore(word.filter { it.lowercaseChar() in "aeiou" }).toString(), numberChoices(alphabetScore(word.filter { it.lowercaseChar() in "aeiou" }).toLong(), "", salt, 2).filter { it != alphabetScore(word.filter { it.lowercaseChar() in "aeiou" }).toString() }, salt)
                2 -> q("Using A=1, B=2, ..., what is the consonant-score of '" + word + "'?", alphabetScore(word.filter { it.lowercaseChar() !in "aeiou" }).toString(), numberChoices(alphabetScore(word.filter { it.lowercaseChar() !in "aeiou" }).toLong(), "", salt, 3).filter { it != alphabetScore(word.filter { it.lowercaseChar() !in "aeiou" }).toString() }, salt)
                3 -> fromOptions("Alphabetize the distinct letters in '" + word + "'.", word.toSet().sorted().joinToString(""), listOf(word.toSet().sortedDescending().joinToString(""), word.toList().sorted().joinToString(""), word.reversed()), salt)
                4 -> q("How many letters remain in '" + word + "' after removing all vowels?", consonants.toString(), numberChoices(consonants.toLong(), "", salt).filter { it != consonants.toString() }, salt)
                5 -> q("What is the product of the alphabet positions of the first and last letters of '" + word + "'?", (firstPos * lastPos).toString(), numberChoices((firstPos * lastPos).toLong(), "", salt, lastPos.toLong().coerceAtLeast(1)).filter { it != (firstPos * lastPos).toString() }, salt)
                6 -> fromOptions("Move the first letter of '" + word + "' to the end.", word.drop(1) + word.first(), listOf(word, word.reversed(), word.last() + word.dropLast(1)), salt)
                7 -> fromOptions("Move the last letter of '" + word + "' to the front.", word.last() + word.dropLast(1), listOf(word, word.reversed(), word.drop(1) + word.first()), salt)
                8 -> q("How many pairs of adjacent letters are in '" + word + "'?", (word.length - 1).toString(), numberChoices((word.length - 1).toLong(), "", salt).filter { it != (word.length - 1).toString() }, salt)
                else -> q("If each vowel is worth 2 points and each consonant 1 point, what is the score of '" + word + "'?", (vowels * 2 + consonants).toString(), numberChoices((vowels * 2 + consonants).toLong(), "", salt).filter { it != (vowels * 2 + consonants).toString() }, salt)
            }
            GeneralStudyDifficulty.EXPERT -> when (variant) {
                0 -> q("Using A=1 to Z=26, find the sum of the first two letters of '" + word + "'.", alphabetScore(word.take(2)).toString(), numberChoices(alphabetScore(word.take(2)).toLong(), "", salt).filter { it != alphabetScore(word.take(2)).toString() }, salt)
                1 -> q("Using A=1 to Z=26, find the sum of the last two letters of '" + word + "'.", alphabetScore(word.takeLast(2)).toString(), numberChoices(alphabetScore(word.takeLast(2)).toLong(), "", salt).filter { it != alphabetScore(word.takeLast(2)).toString() }, salt)
                2 -> q("Using A=1 to Z=26, find the total alphabet-score of the distinct letters in '" + word + "'.", alphabetScore(word.toSet().joinToString("")).toString(), numberChoices(alphabetScore(word.toSet().joinToString("")).toLong(), "", salt, 4).filter { it != alphabetScore(word.toSet().joinToString("")).toString() }, salt)
                3 -> fromOptions("Sort the distinct letters of '" + word + "' in descending alphabetical order.", word.toSet().sortedDescending().joinToString(""), listOf(word.toSet().sorted().joinToString(""), word.toList().sortedDescending().joinToString(""), word.reversed()), salt)
                4 -> q("What percentage of the letters in '" + word + "' are vowels, rounded to the nearest whole percent?", kotlin.math.round(vowels * 100.0 / word.length).toInt().toString() + "%", listOf((vowels * 10).toString() + "%", (consonants * 10).toString() + "%", "50%"), salt)
                5 -> q("How many unordered pairs of letter positions can be chosen from '" + word + "'?", (word.length * (word.length - 1) / 2).toString(), numberChoices((word.length * (word.length - 1) / 2).toLong(), "", salt).filter { it != (word.length * (word.length - 1) / 2).toString() }, salt)
                6 -> q("What is the 1-based position of the first vowel in '" + word + "'?", (word.indexOfFirst { it.lowercaseChar() in "aeiou" } + 1).toString(), numberChoices((word.indexOfFirst { it.lowercaseChar() in "aeiou" } + 1).toLong(), "", salt).filter { it != (word.indexOfFirst { it.lowercaseChar() in "aeiou" } + 1).toString() }, salt)
                7 -> q("What is the 1-based position of the last vowel in '" + word + "'?", (word.indexOfLast { it.lowercaseChar() in "aeiou" } + 1).toString(), numberChoices((word.indexOfLast { it.lowercaseChar() in "aeiou" } + 1).toLong(), "", salt).filter { it != (word.indexOfLast { it.lowercaseChar() in "aeiou" } + 1).toString() }, salt)
                8 -> q("Subtract the vowel-score from the total alphabet-score of '" + word + "'.", alphabetScore(word.filter { it.lowercaseChar() !in "aeiou" }).toString(), numberChoices(alphabetScore(word.filter { it.lowercaseChar() !in "aeiou" }).toLong(), "", salt, 3).filter { it != alphabetScore(word.filter { it.lowercaseChar() !in "aeiou" }).toString() }, salt)
                else -> q("Multiply the number of vowels by the number of consonants in '" + word + "'.", (vowels * consonants).toString(), numberChoices((vowels * consonants).toLong(), "", salt).filter { it != (vowels * consonants).toString() }, salt)
            }
        }
    }

    private fun science(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val salt = n + d.ordinal * 907
        return when (d) {
            GeneralStudyDifficulty.EASY -> when (n % 4) {
                0 -> {
                    val m = 10 + n
                    val answer = m * 100
                    q("Convert " + m + " metres to centimetres.", answer.toString() + " cm", numberChoices(answer.toLong(), " cm", salt, 100).filter { it != answer.toString() + " cm" }, salt)
                }
                1 -> {
                    val kg = 2 + n
                    val answer = kg * 1000
                    q("Convert " + kg + " kilograms to grams.", answer.toString() + " g", numberChoices(answer.toLong(), " g", salt, 1000).filter { it != answer.toString() + " g" }, salt)
                }
                2 -> {
                    val minutes = 2 + (n % 90)
                    val seconds = minutes * 60
                    q("Convert " + seconds + " seconds to minutes.", minutes.toString() + " min", numberChoices(minutes.toLong(), " min", salt).filter { it != minutes.toString() + " min" }, salt)
                }
                else -> {
                    val distance = 20 + n
                    val time = 2 + (n % 9)
                    val speed = distance.toDouble() / time
                    fromOptions("An object travels " + distance + " m in " + time + " s. Find its average speed.", formatNumber(speed) + " m/s", decimalChoices(speed, " m/s", salt).filter { it != formatNumber(speed) + " m/s" }, salt)
                }
            }
            GeneralStudyDifficulty.MEDIUM -> when (n % 4) {
                0 -> {
                    val current = 2 + (n % 20)
                    val resistance = 2 + ((n / 2) % 20)
                    val voltage = current * resistance
                    q("Using V = IR, find V when I=" + current + " A and R=" + resistance + " Ω.", voltage.toString() + " V", numberChoices(voltage.toLong(), " V", salt, resistance.toLong()).filter { it != voltage.toString() + " V" }, salt)
                }
                1 -> {
                    val freq = 3 + (n % 30)
                    val wavelength = 2 + ((n / 3) % 15)
                    val speed = freq * wavelength
                    q("A wave has frequency " + freq + " Hz and wavelength " + wavelength + " m. What is its speed?", speed.toString() + " m/s", numberChoices(speed.toLong(), " m/s", salt, freq.toLong()).filter { it != speed.toString() + " m/s" }, salt)
                }
                2 -> {
                    val mass = 10 + n
                    val volume = 2 + (n % 12)
                    val density = mass.toDouble() / volume
                    fromOptions("A sample has mass " + mass + " g and volume " + volume + " cm³. Find its density.", formatNumber(density) + " g/cm³", decimalChoices(density, " g/cm³", salt).filter { it != formatNumber(density) + " g/cm³" }, salt)
                }
                else -> {
                    val charge = 10 + n
                    val time = 2 + (n % 15)
                    val current = charge.toDouble() / time
                    fromOptions("If " + charge + " C of charge passes in " + time + " s, find the current.", formatNumber(current) + " A", decimalChoices(current, " A", salt).filter { it != formatNumber(current) + " A" }, salt)
                }
            }
            GeneralStudyDifficulty.HARD -> when (n % 4) {
                0 -> {
                    val mass = 5 + (n % 40)
                    val velocity = 2 + ((n / 2) % 15)
                    val ke = 0.5 * mass * velocity * velocity
                    fromOptions("Find the kinetic energy of a " + mass + " kg object moving at " + velocity + " m/s.", formatNumber(ke) + " J", decimalChoices(ke, " J", salt).filter { it != formatNumber(ke) + " J" }, salt)
                }
                1 -> {
                    val mass = 5 + (n % 40)
                    val height = 2 + ((n / 3) % 25)
                    val pe = mass * 10L * height
                    q("Using g=10 m/s², find gravitational potential energy for mass " + mass + " kg at height " + height + " m.", pe.toString() + " J", numberChoices(pe, " J", salt, (mass * 10L)).filter { it != pe.toString() + " J" }, salt)
                }
                2 -> {
                    val force = 5 + (n % 50)
                    val distance = 2 + ((n / 5) % 30)
                    val work = force * distance
                    q("A force of " + force + " N moves an object " + distance + " m in the force direction. Find the work.", work.toString() + " J", numberChoices(work.toLong(), " J", salt, force.toLong()).filter { it != work.toString() + " J" }, salt)
                }
                else -> {
                    val work = 100 + n * 2
                    val time = 2 + (n % 20)
                    val power = work.toDouble() / time
                    fromOptions("If " + work + " J of work is done in " + time + " s, find the power.", formatNumber(power) + " W", decimalChoices(power, " W", salt).filter { it != formatNumber(power) + " W" }, salt)
                }
            }
            GeneralStudyDifficulty.EXPERT -> when (n % 4) {
                0 -> {
                    val mass = 5 + (n % 45)
                    val velocity = 3 + ((n / 2) % 25)
                    val p = mass * velocity
                    q("Find momentum for a " + mass + " kg object moving at " + velocity + " m/s.", p.toString() + " kg·m/s", numberChoices(p.toLong(), " kg·m/s", salt, mass.toLong()).filter { it != p.toString() + " kg·m/s" }, salt)
                }
                1 -> {
                    val pressure = 2 + (n % 30)
                    val volume = 1 + ((n / 3) % 20)
                    val work = pressure * volume
                    q("A constant pressure of " + pressure + " Pa causes a volume change of " + volume + " m³. Using W=PΔV, find the work.", work.toString() + " J", numberChoices(work.toLong(), " J", salt, pressure.toLong()).filter { it != work.toString() + " J" }, salt)
                }
                2 -> {
                    val halfLives = 2 + (n % 7)
                    val finalAmount = 2 + ((n / 4) % 40)
                    val initial = finalAmount * (1 shl halfLives)
                    q("A " + initial + " g sample undergoes " + halfLives + " half-lives. How much remains?", finalAmount.toString() + " g", numberChoices(finalAmount.toLong(), " g", salt, finalAmount.toLong().coerceAtLeast(1)).filter { it != finalAmount.toString() + " g" }, salt)
                }
                else -> {
                    val force = 20 + (n % 80)
                    val area = 2 + ((n / 5) % 20)
                    val pressure = force.toDouble() / area
                    fromOptions("A force of " + force + " N acts uniformly over " + area + " m². Find the pressure.", formatNumber(pressure) + " Pa", decimalChoices(pressure, " Pa", salt).filter { it != formatNumber(pressure) + " Pa" }, salt)
                }
            }
        }
    }

    private data class Place(val country: String, val capital: String, val region: String, val a2: String, val a3: String)
    private val places = listOf(
        Place("Nigeria","Abuja","Africa","NG","NGA"), Place("Ghana","Accra","Africa","GH","GHA"),
        Place("Kenya","Nairobi","Africa","KE","KEN"), Place("Egypt","Cairo","Africa","EG","EGY"),
        Place("South Africa","Pretoria","Africa","ZA","ZAF"), Place("Ethiopia","Addis Ababa","Africa","ET","ETH"),
        Place("Morocco","Rabat","Africa","MA","MAR"), Place("Senegal","Dakar","Africa","SN","SEN"),
        Place("Canada","Ottawa","Americas","CA","CAN"), Place("Brazil","Brasilia","Americas","BR","BRA"),
        Place("Argentina","Buenos Aires","Americas","AR","ARG"), Place("Mexico","Mexico City","Americas","MX","MEX"),
        Place("United States","Washington, D.C.","Americas","US","USA"), Place("Jamaica","Kingston","Americas","JM","JAM"),
        Place("United Kingdom","London","Europe","GB","GBR"), Place("France","Paris","Europe","FR","FRA"),
        Place("Germany","Berlin","Europe","DE","DEU"), Place("Italy","Rome","Europe","IT","ITA"),
        Place("Spain","Madrid","Europe","ES","ESP"), Place("Portugal","Lisbon","Europe","PT","PRT"),
        Place("Norway","Oslo","Europe","NO","NOR"), Place("Sweden","Stockholm","Europe","SE","SWE"),
        Place("Denmark","Copenhagen","Europe","DK","DNK"), Place("Finland","Helsinki","Europe","FI","FIN"),
        Place("Poland","Warsaw","Europe","PL","POL"), Place("Greece","Athens","Europe","GR","GRC"),
        Place("China","Beijing","Asia","CN","CHN"), Place("Japan","Tokyo","Asia","JP","JPN"),
        Place("India","New Delhi","Asia","IN","IND"), Place("South Korea","Seoul","Asia","KR","KOR"),
        Place("Thailand","Bangkok","Asia","TH","THA"), Place("Vietnam","Hanoi","Asia","VN","VNM"),
        Place("Indonesia","Jakarta","Asia","ID","IDN"), Place("Malaysia","Kuala Lumpur","Asia","MY","MYS"),
        Place("Saudi Arabia","Riyadh","Asia","SA","SAU"), Place("United Arab Emirates","Abu Dhabi","Asia","AE","ARE"),
        Place("Australia","Canberra","Oceania","AU","AUS"), Place("New Zealand","Wellington","Oceania","NZ","NZL"),
        Place("Fiji","Suva","Oceania","FJ","FJI"), Place("Papua New Guinea","Port Moresby","Oceania","PG","PNG"),
        Place("Chile","Santiago","Americas","CL","CHL"), Place("Peru","Lima","Americas","PE","PER"),
        Place("Colombia","Bogota","Americas","CO","COL"), Place("Cuba","Havana","Americas","CU","CUB"),
        Place("Ireland","Dublin","Europe","IE","IRL"), Place("Netherlands","Amsterdam","Europe","NL","NLD"),
        Place("Belgium","Brussels","Europe","BE","BEL"), Place("Switzerland","Bern","Europe","CH","CHE"),
        Place("Austria","Vienna","Europe","AT","AUT"), Place("Turkey","Ankara","Asia","TR","TUR"),
        Place("Pakistan","Islamabad","Asia","PK","PAK"), Place("Bangladesh","Dhaka","Asia","BD","BGD"),
        Place("Philippines","Manila","Asia","PH","PHL"), Place("Singapore","Singapore","Asia","SG","SGP"),
        Place("Nepal","Kathmandu","Asia","NP","NPL"), Place("Sri Lanka","Sri Jayawardenepura Kotte","Asia","LK","LKA"),
        Place("Tanzania","Dodoma","Africa","TZ","TZA"), Place("Uganda","Kampala","Africa","UG","UGA"),
        Place("Rwanda","Kigali","Africa","RW","RWA"), Place("Zambia","Lusaka","Africa","ZM","ZMB"),
        Place("Zimbabwe","Harare","Africa","ZW","ZWE"), Place("Cameroon","Yaounde","Africa","CM","CMR"),
        Place("Algeria","Algiers","Africa","DZ","DZA"), Place("Tunisia","Tunis","Africa","TN","TUN")
    )

    private fun geography(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val place = places[n % places.size]
        val variant = (n / places.size) % 10
        val salt = n + d.ordinal * 1009
        val other = places[(n * 7 + 11) % places.size]
        val third = places[(n * 13 + 17) % places.size]
        val fourth = places[(n * 19 + 23) % places.size]
        fun pool(selector: (Place) -> String) = listOf(selector(other), selector(third), selector(fourth))
        return when (d) {
            GeneralStudyDifficulty.EASY -> when (variant) {
                0, 3, 6 -> q("What is the capital of " + place.country + "?", place.capital, pool { it.capital }, salt)
                1, 4, 7 -> q(place.capital + " is the capital of which country?", place.country, pool { it.country }, salt)
                else -> q("Which world region contains " + place.country + "?", place.region, listOf("Africa","Americas","Asia","Europe","Oceania").filter { it != place.region }, salt)
            }
            GeneralStudyDifficulty.MEDIUM -> when (variant) {
                0, 5 -> q("What is the ISO alpha-2 code for " + place.country + "?", place.a2, pool { it.a2 }, salt)
                1, 6 -> q("What is the ISO alpha-3 code for " + place.country + "?", place.a3, pool { it.a3 }, salt)
                2, 7 -> q("Which country uses ISO alpha-2 code " + place.a2 + "?", place.country, pool { it.country }, salt)
                3, 8 -> q("Which country uses ISO alpha-3 code " + place.a3 + "?", place.country, pool { it.country }, salt)
                else -> q("State the capital and region of " + place.country + ".", place.capital + "; " + place.region, pool { it.capital + "; " + it.region }, salt)
            }
            GeneralStudyDifficulty.HARD -> when (variant) {
                0, 4, 8 -> q("Identify the country from this capital: " + place.capital + ".", place.country, pool { it.country }, salt)
                1, 5, 9 -> q("Give the ISO alpha-3 code for the country whose capital is " + place.capital + ".", place.a3, pool { it.a3 }, salt)
                2, 6 -> q("Which capital belongs to the country with ISO alpha-2 code " + place.a2 + "?", place.capital, pool { it.capital }, salt)
                else -> q("State the capital, region and alpha-2 code of " + place.country + ".", place.capital + "; " + place.region + "; " + place.a2, pool { it.capital + "; " + it.region + "; " + it.a2 }, salt)
            }
            GeneralStudyDifficulty.EXPERT -> when (variant) {
                0, 5 -> q("Give both ISO alpha-2 and alpha-3 codes for " + place.country + ".", place.a2 + ", " + place.a3, pool { it.a2 + ", " + it.a3 }, salt)
                1, 6 -> q("Which country is represented by ISO alpha-3 code " + place.a3 + "?", place.country, pool { it.country }, salt)
                2, 7 -> q("Name the country and region associated with the capital " + place.capital + ".", place.country + "; " + place.region, pool { it.country + "; " + it.region }, salt)
                3, 8 -> q("Give the capital and alpha-3 code for " + place.country + ".", place.capital + "; " + place.a3, pool { it.capital + "; " + it.a3 }, salt)
                else -> q("From ISO alpha-2 code " + place.a2 + ", identify the country and capital.", place.country + "; " + place.capital, pool { it.country + "; " + it.capital }, salt)
            }
        }
    }

    private data class Event(val title: String, val year: Int)
    private val events = listOf(
        Event("The Magna Carta was sealed",1215), Event("The printing press spread in Europe",1450),
        Event("Columbus reached the Americas",1492), Event("The Protestant Reformation began",1517),
        Event("The Spanish Armada was defeated",1588), Event("The English Civil War began",1642),
        Event("The United States Declaration of Independence was adopted",1776), Event("The French Revolution began",1789),
        Event("Napoleon was defeated at Waterloo",1815), Event("The first modern Olympic Games were held",1896),
        Event("Nigeria was amalgamated into one colony",1914), Event("World War I began",1914),
        Event("World War I ended",1918), Event("The League of Nations began operating",1920),
        Event("The Great Depression began",1929), Event("World War II began",1939),
        Event("World War II ended",1945), Event("The United Nations was founded",1945),
        Event("India became independent",1947), Event("NATO was founded",1949),
        Event("Ghana became independent",1957), Event("Nigeria became independent",1960),
        Event("The first human landed on the Moon",1969), Event("The first Earth Day was observed",1970),
        Event("The United Kingdom joined the EEC",1973), Event("The Berlin Wall fell",1989),
        Event("The World Wide Web was proposed",1989), Event("Nelson Mandela was released from prison",1990),
        Event("South Africa held its first multiracial democratic election",1994), Event("The African Union was launched",2002),
        Event("The euro entered circulation as notes and coins",2002), Event("The first iPhone was released",2007)
    )

    private fun history(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val size = events.size
        val a = events[n % size]
        var bIndex = ((n / size) * 7 + (n % size) * 11 + 5) % size
        if (bIndex == n % size) bIndex = (bIndex + 1) % size
        val b = events[bIndex]
        val earlier = if (a.year <= b.year) a else b
        val later = if (a.year <= b.year) b else a
        val gap = kotlin.math.abs(a.year - b.year)
        val salt = n + d.ordinal * 1103
        return when (d) {
            GeneralStudyDifficulty.EASY -> q("Which happened earlier: '" + a.title + "' or '" + b.title + "'?", earlier.title, listOf(later.title, "They happened in the same year", "Neither event"), salt)
            GeneralStudyDifficulty.MEDIUM -> q("Calculate the number of years from " + earlier.title + " (" + earlier.year + ") to " + later.title + " (" + later.year + ").", gap.toString(), numberChoices(gap.toLong(), "", salt).filter { it != gap.toString() }, salt)
            GeneralStudyDifficulty.HARD -> if (n % 2 == 0) {
                q("Chronologically, which came first: '" + a.title + "' or '" + b.title + "'?", earlier.title, listOf(later.title, "They happened in the same year", "Cannot be determined"), salt)
            } else {
                q("How many years elapsed from " + earlier.title + " in " + earlier.year + " to " + later.title + " in " + later.year + "?", gap.toString(), numberChoices(gap.toLong(), "", salt).filter { it != gap.toString() }, salt)
            }
            GeneralStudyDifficulty.EXPERT -> if (n % 2 == 0) {
                q("Determine which event precedes the other in time: '" + a.title + "' or '" + b.title + "'.", earlier.title, listOf(later.title, "Same year", "Insufficient information"), salt)
            } else {
                q("Compute the historical interval, in years, between '" + a.title + "' and '" + b.title + "'.", gap.toString(), numberChoices(gap.toLong(), "", salt).filter { it != gap.toString() }, salt)
            }
        }
    }

    private fun technology(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val salt = n + d.ordinal * 1201
        return when (d) {
            GeneralStudyDifficulty.EASY -> when (n % 3) {
                0 -> {
                    val value = 16 + n
                    val answer = value.toString(2)
                    q("Convert decimal " + value + " to binary.", answer, listOf((value + 1).toString(2), (value - 1).coerceAtLeast(0).toString(2), value.toString(16).uppercase()), salt)
                }
                1 -> {
                    val value = 10 + n
                    val bytes = value * 1024L
                    q("Using 1 KB = 1024 bytes, how many bytes are in " + value + " KB?", bytes.toString(), numberChoices(bytes, "", salt, 1024).filter { it != bytes.toString() }, salt)
                }
                else -> {
                    val value = 8 + n
                    val binary = value.toString(2)
                    q("Convert binary " + binary + " to decimal.", value.toString(), numberChoices(value.toLong(), "", salt).filter { it != value.toString() }, salt)
                }
            }
            GeneralStudyDifficulty.MEDIUM -> when (n % 3) {
                0 -> {
                    val value = 100 + n
                    val hex = value.toString(16).uppercase()
                    q("Convert decimal " + value + " to hexadecimal.", hex, listOf((value + 1).toString(16).uppercase(), (value - 1).toString(16).uppercase(), value.toString(2)), salt)
                }
                1 -> {
                    val value = 50 + n
                    val binary = value.toString(2)
                    q("Convert binary " + binary + " to decimal.", value.toString(), numberChoices(value.toLong(), "", salt).filter { it != value.toString() }, salt)
                }
                else -> {
                    val mb = 2 + n
                    val kb = mb * 1024L
                    q("Using 1 MB = 1024 KB, how many KB are in " + mb + " MB?", kb.toString(), numberChoices(kb, "", salt, 1024).filter { it != kb.toString() }, salt)
                }
            }
            GeneralStudyDifficulty.HARD -> when (n % 3) {
                0 -> {
                    val value = 256 + n
                    val hex = value.toString(16).uppercase()
                    val binary = value.toString(2)
                    q("Convert hexadecimal " + hex + " to binary.", binary, listOf((value + 1).toString(2), (value - 1).toString(2), hex), salt)
                }
                1 -> {
                    val value = 300 + n
                    val binary = value.toString(2)
                    val hex = value.toString(16).uppercase()
                    q("Convert binary " + binary + " to hexadecimal.", hex, listOf((value + 1).toString(16).uppercase(), (value - 1).toString(16).uppercase(), binary), salt)
                }
                else -> {
                    val value = 128 + n
                    val ones = value.toString(2).count { it == '1' }
                    q("How many 1s are in the binary form of " + value + "?", ones.toString(), numberChoices(ones.toLong(), "", salt).filter { it != ones.toString() }, salt)
                }
            }
            GeneralStudyDifficulty.EXPERT -> {
                val value = 512 + n * 7
                val binary = value.toString(2)
                val hex = value.toString(16).uppercase()
                q("Convert decimal " + value + " to both binary and hexadecimal.", binary + "; " + hex, listOf((value + 1).toString(2) + "; " + hex, binary + "; " + (value + 1).toString(16).uppercase(), value.toString(8) + "; " + hex), salt)
            }
        }
    }

    private fun logic(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val salt = n + d.ordinal * 1301
        return when (d) {
            GeneralStudyDifficulty.EASY -> when (n % 3) {
                0 -> {
                    val start = 2 + (n % 20)
                    val step = 2 + ((n / 3) % 12)
                    val seq = List(5) { start + it * step }
                    val answer = start + 5 * step
                    q("What comes next: " + seq.joinToString(", ") + ", ?", answer.toString(), numberChoices(answer.toLong(), "", salt, step.toLong()).filter { it != answer.toString() }, salt)
                }
                1 -> {
                    val value = 100 + n
                    q("Is " + value + " even or odd?", if (value % 2 == 0) "Even" else "Odd", listOf("Even","Odd","Prime","Neither"), salt)
                }
                else -> {
                    val a = 10 + n
                    val b = 5 + (n % 30)
                    q("If A=" + a + " and B=" + b + ", what is A+B?", (a + b).toString(), numberChoices((a + b).toLong(), "", salt).filter { it != (a + b).toString() }, salt)
                }
            }
            GeneralStudyDifficulty.MEDIUM -> when (n % 4) {
                0 -> {
                    val value = 100 + n
                    val sum = value.toString().sumOf { it.digitToInt() }
                    q("What is the sum of the digits of " + value + "?", sum.toString(), numberChoices(sum.toLong(), "", salt).filter { it != sum.toString() }, salt)
                }
                1 -> {
                    val value = 100 + n
                    val product = value.toString().fold(1) { acc, c -> acc * c.digitToInt() }
                    q("What is the product of the digits of " + value + "?", product.toString(), numberChoices(product.toLong(), "", salt).filter { it != product.toString() }, salt)
                }
                2 -> {
                    val value = 100 + n
                    q("Reverse the digits of " + value + ".", value.toString().reversed().toInt().toString(), listOf((value + 1).toString(), value.toString(), (value - 1).toString()), salt)
                }
                else -> {
                    val value = 90 + n
                    q("Is " + value + " divisible by 3?", if (value % 3 == 0) "Yes" else "No", listOf("Yes","No","Only if even","Only if odd"), salt)
                }
            }
            GeneralStudyDifficulty.HARD -> when (n % 3) {
                0 -> {
                    val a = 2 + (n % 12)
                    val b = 3 + ((n / 2) % 12)
                    val seq = mutableListOf(a, b)
                    repeat(5) { seq += seq[seq.lastIndex] + seq[seq.lastIndex - 1] }
                    q("The sequence starts " + a + ", " + b + " and each new term is the sum of the previous two. What is the 7th term?", seq[6].toString(), numberChoices(seq[6].toLong(), "", salt, seq[5].toLong().coerceAtLeast(1)).filter { it != seq[6].toString() }, salt)
                }
                1 -> {
                    val value = 64 + n
                    val zeros = value.toString(2).count { it == '0' }
                    q("How many 0s are in the binary form of " + value + "?", zeros.toString(), numberChoices(zeros.toLong(), "", salt).filter { it != zeros.toString() }, salt)
                }
                else -> {
                    val start = 4 + (n % 20)
                    val seq = List(5) { i -> start + i * i + i }
                    val answer = start + 5 * 5 + 5
                    q("Find the next term: " + seq.joinToString(", ") + ", ?", answer.toString(), numberChoices(answer.toLong(), "", salt, 2).filter { it != answer.toString() }, salt)
                }
            }
            GeneralStudyDifficulty.EXPERT -> when (n % 2) {
                0 -> {
                    val a1 = 2 + (n % 12)
                    val a2 = 4 + ((n / 3) % 12)
                    val seq = mutableListOf(a1, a2)
                    repeat(5) { seq += 2 * seq[seq.lastIndex] + seq[seq.lastIndex - 1] }
                    q("Sequence rule: aₙ = 2aₙ₋₁ + aₙ₋₂, with a₁=" + a1 + ", a₂=" + a2 + ". Find a₇.", seq[6].toString(), numberChoices(seq[6].toLong(), "", salt, seq[5].toLong().coerceAtLeast(1)).filter { it != seq[6].toString() }, salt)
                }
                else -> {
                    val value = 180 + n
                    val binary = value.toString(2)
                    q("Evaluate the binary numeral " + binary + " in base 10.", value.toString(), numberChoices(value.toLong(), "", salt).filter { it != value.toString() }, salt)
                }
            }
        }
    }

    private fun generalKnowledge(d: GeneralStudyDifficulty, n: Int): GeneralStudyQuestion {
        val salt = n + d.ordinal * 1409
        return when (d) {
            GeneralStudyDifficulty.EASY -> when (n % 4) {
                0 -> {
                    val letter = ('A'.code + (n % 26)).toChar()
                    val pos = n % 26 + 1
                    q("What is the alphabet position of the letter " + letter + "?", pos.toString(), numberChoices(pos.toLong(), "", salt).filter { it != pos.toString() }, salt)
                }
                1 -> {
                    val weeks = 10 + n
                    val days = weeks * 7
                    q("How many days are in " + weeks + " weeks?", days.toString(), numberChoices(days.toLong(), "", salt, 7).filter { it != days.toString() }, salt)
                }
                2 -> {
                    val cm = 100 + n
                    val mm = cm * 10
                    q("Convert " + cm + " centimetres to millimetres.", mm.toString() + " mm", numberChoices(mm.toLong(), " mm", salt, 10).filter { it != mm.toString() + " mm" }, salt)
                }
                else -> {
                    val value = 1 + (n % 3999)
                    q("Write " + value + " as a Roman numeral.", roman(value), listOf(roman((value + 1).coerceAtMost(3999)), roman((value + 2).coerceAtMost(3999)), roman((value + 5).coerceAtMost(3999))), salt)
                }
            }
            GeneralStudyDifficulty.MEDIUM -> when (n % 4) {
                0 -> {
                    val days = 100 + n
                    q("Convert " + days + " days to weeks and remaining days.", (days / 7).toString() + " weeks " + (days % 7) + " days", listOf((days / 7 + 1).toString() + " weeks " + (days % 7) + " days", (days / 7).toString() + " weeks " + ((days % 7 + 1) % 7) + " days", days.toString() + " weeks"), salt)
                }
                1 -> {
                    val minutes = 120 + n
                    q("Convert " + minutes + " minutes to hours and minutes.", (minutes / 60).toString() + " h " + (minutes % 60) + " min", listOf((minutes / 60 + 1).toString() + " h " + (minutes % 60) + " min", (minutes / 60).toString() + " h " + ((minutes % 60 + 5) % 60) + " min", minutes.toString() + " h"), salt)
                }
                2 -> {
                    val value = 50 + (n % 3950)
                    q("Write " + value + " in Roman numerals.", roman(value), listOf(roman((value + 1).coerceAtMost(3999)), roman((value + 10).coerceAtMost(3999)), roman((value + 50).coerceAtMost(3999))), salt)
                }
                else -> q("Expand the abbreviation " + if (n % 2 == 0) "AM." else "PM.", if (n % 2 == 0) "ante meridiem" else "post meridiem", listOf("after meridiem","post meridiem","ante meridiem","annual measure"), salt)
            }
            GeneralStudyDifficulty.HARD -> when (n % 3) {
                0 -> {
                    val value = 100 + (n % 3800)
                    q("Convert the Roman numeral " + roman(value) + " to an Arabic numeral.", value.toString(), numberChoices(value.toLong(), "", salt).filter { it != value.toString() }, salt)
                }
                1 -> {
                    val year = 1600 + n
                    q("Is " + year + " a leap year in the Gregorian calendar?", if (isLeap(year)) "Yes" else "No", listOf("Yes","No","Only in the Julian calendar","Cannot be determined"), salt)
                }
                else -> {
                    val seconds = 3600 + n * 10
                    q("Convert " + seconds + " seconds to minutes.", formatNumber(seconds / 60.0) + " min", decimalChoices(seconds / 60.0, " min", salt).filter { it != formatNumber(seconds / 60.0) + " min" }, salt)
                }
            }
            GeneralStudyDifficulty.EXPERT -> when (n % 3) {
                0 -> {
                    val value = 500 + (n % 3499)
                    q("Interpret the Roman numeral " + roman(value) + " as a decimal number.", value.toString(), numberChoices(value.toLong(), "", salt).filter { it != value.toString() }, salt)
                }
                1 -> {
                    val value = 700 + (n % 3299)
                    q("Convert " + value + " to a Roman numeral.", roman(value), listOf(roman((value + 1).coerceAtMost(3999)), roman((value + 4).coerceAtMost(3999)), roman((value + 9).coerceAtMost(3999))), salt)
                }
                else -> {
                    val year = 400 + n
                    q("Using Gregorian leap-year rules, determine whether " + year + " is a leap year.", if (isLeap(year)) "Yes" else "No", listOf("Yes","No","Only if divisible by 100","Only if odd"), salt)
                }
            }
        }
    }

    private fun signed(value: Int): String = if (value >= 0) "+ " + value + " " else "- " + kotlin.math.abs(value) + " "
    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
    private fun alphabetScore(value: String): Int = value.lowercase().filter { it in 'a'..'z' }.sumOf { it - 'a' + 1 }
    private fun permutation(n: Int, r: Int): Long {
        var out = 1L
        repeat(r) { i -> out *= (n - i).toLong() }
        return out
    }
    private fun pow(base: Long, exp: Int): Long {
        var out = 1L
        repeat(exp) { out *= base }
        return out
    }
    private fun gcd(a: Long, b: Long): Long {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }
    private fun lcm(a: Long, b: Long): Long = kotlin.math.abs(a / gcd(a, b) * b)
    private fun isLeap(year: Int): Boolean = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)

    private fun roman(value: Int): String {
        var number = value.coerceIn(1, 3999)
        val values = intArrayOf(1000,900,500,400,100,90,50,40,10,9,5,4,1)
        val numerals = arrayOf("M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I")
        val out = StringBuilder()
        for (i in values.indices) {
            while (number >= values[i]) {
                out.append(numerals[i])
                number -= values[i]
            }
        }
        return out.toString()
    }
}
