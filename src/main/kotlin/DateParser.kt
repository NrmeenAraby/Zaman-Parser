import java.time.*
import java.time.temporal.TemporalAdjusters

data class DateResult(
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isRecurring: Boolean = false
)

object DateParser {

    private val today: LocalDate
        get() = LocalDate.now()

    // =====================================================
    // ENTRY
    // =====================================================
    fun parse(input: String): DateResult? {

        val text = normalizeArabic(input.trim())

        parseRange(text)?.let { return it }
        parseRecurring(text)?.let { return it }
        parseFlexibleDate(text)?.let { return it }
        //  parseAbsoluteMonthName(text)?.let { return it }
        parseMonthWeekBoundary(text)?.let { return it }
        parseMonthOrWeekRelative(text)?.let { return it }
        //parseMonthWeekBoundary(text)?.let { return it }
        parseRelativeNumeric(text)?.let { return it }
        parseWeekday(text)?.let { return it }
       // parseRelativeNumeric(text)?.let { return it }
        parseSingleDay(text)?.let { return it }

        return null
    }

    // =====================================================
    // NORMALIZE ARABIC TEXT
    // =====================================================
    private fun normalizeArabic(text: String): String {
        return text
            // Normalize digits
            .replace("٠","0").replace("١","1").replace("٢","2").replace("٣","3")
            .replace("٤","4").replace("٥","5").replace("٦","6").replace("٧","7")
            .replace("٨","8").replace("٩","9")
            // Normalize alef variations
            .replace("[أإآ]".toRegex(), "ا")
            // Normalize ta marbuta
            .replace("ة", "ه")
            // Remove tashkeel (diacritics)
            .replace("[ًٌٍَُِْ]".toRegex(), "")
    }

    // =====================================================
    // FLEXIBLE DATE
    // =====================================================
    private fun parseFlexibleDate(text: String): DateResult? {

        // ───────────────────────────────────────────────────────────────
        // 1. Try numeric formats: dd/mm, mm/dd, yyyy-mm-dd, dd/mm/yyyy etc.
        // ───────────────────────────────────────────────────────────────
        val numericRegex = Regex("""\b(\d{1,4})[-/\\.](\d{1,2})(?:[-/\\.](\d{1,4}))?\b""")
        numericRegex.find(text)?.let { match ->
            val p1 = match.groupValues[1]
            val p2 = match.groupValues[2]
            val p3 = match.groupValues.getOrNull(3)

            try {
                val date = when {
                    p3.isNullOrBlank() -> {
                        // assume dd/mm or mm/dd → prefer future date
                        val day = p1.toInt()
                        val month = p2.toInt()
                        var d = LocalDate.of(today.year, month, day)
                        if (!d.isAfter(today)) d = d.plusYears(1)
                        d
                    }
                    p1.length == 4 -> LocalDate.of(p1.toInt(), p2.toInt(), p3!!.toInt())
                    p3.length == 4 -> LocalDate.of(p3.toInt(), p2.toInt(), p1.toInt())
                    else -> return null
                }
                return DateResult(date)
            } catch (_: Exception) {
                // invalid date (e.g. 30/02) → continue to next parser
            }
        }

        // ───────────────────────────────────────────────────────────────
        // 2. Year - MonthName - Day  (English & Arabic support)
        // Examples:
        //   2025-Dec-10
        //   2025 Dec 10
        //   10 ديسمبر 2025
        //   2025-ديسمبر-10
        // ───────────────────────────────────────────────────────────────
        val monthNamesEnAr = mapOf(
            // English (various lengths & cases)
            "jan" to 1, "january" to 1,
            "feb" to 2, "february" to 2,
            "mar" to 3, "march" to 3,
            "apr" to 4, "april" to 4,
            "may" to 5,
            "jun" to 6, "june" to 6,
            "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8,
            "sep" to 9, "september" to 9,
            "oct" to 10, "october" to 10,
            "nov" to 11, "november" to 11,
            "dec" to 12, "december" to 12,

            // Arabic full & short
            "يناير" to 1,
            "فبراير" to 2,
            "مارس" to 3,
            "ابريل" to 4,
            "مايو" to 5,
            "يونيو" to 6,
            "يوليو" to 7,
            "اغسطس" to 8,
            "سبتمبر" to 9,
            "اكتوبر" to 10,
            "نوفمبر" to 11,
            "ديسمبر" to 12
        )

        // Flexible separators: space, -, /, .
        val monthNameRegex = Regex(
            """\b(\d{4})\s*[-/.\s]+([^\d\s-/]{3,})\s*[-/.\s]+(\d{1,2})\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        monthNameRegex.find(text)?.let { match ->
            val yearStr = match.groupValues[1]
            val monthStrRaw = match.groupValues[2].lowercase()
            val dayStr = match.groupValues[3]

            val monthNum = monthNamesEnAr[monthStrRaw]
                ?: monthNamesEnAr.entries.firstOrNull { monthStrRaw.contains(it.key) }?.value

            if (monthNum != null) {
                try {
                    val date = LocalDate.of(yearStr.toInt(), monthNum, dayStr.toInt())
                    return DateResult(date)
                } catch (_: Exception) {
                    // invalid day/month → skip
                }
            }
        }

        // Also support reverse order: day month year (very common in Arabic)
        val dayMonthYearRegex = Regex(
            """\b(\d{1,2})\s*[-/.\s]+([^\d\s-/]{3,})\s*[-/.\s]+(\d{4})\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        dayMonthYearRegex.find(text)?.let { match ->
            val dayStr = match.groupValues[1]
            val monthStrRaw = match.groupValues[2].lowercase()
            val yearStr = match.groupValues[3]

            val monthNum = monthNamesEnAr[monthStrRaw]
                ?: monthNamesEnAr.entries.firstOrNull { monthStrRaw.contains(it.key) }?.value

            if (monthNum != null) {
                try {
                    val date = LocalDate.of(yearStr.toInt(), monthNum, dayStr.toInt())
                    return DateResult(date)
                } catch (_: Exception) {
                    // invalid → skip
                }
            }
        }

        // ───────────────────────────────────────────────────────────────
        // short forms without year and without strict separator
        // Examples: 25 Dec, 23 يناير, Dec 25, يناير ٢٣, ١٥ أكتوبر
        // ───────────────────────────────────────────────────────────────
        val clean = normalizeArabic(text)
        val tokens = clean
            .split(Regex("\\s+"))
            .map { it.trim().trim(',', '.', '،', ':', ';', '!', '?', '(', ')', '[', ']', '{', '}') }
            .filter { it.isNotBlank() }

        fun normalizeToken(t: String): String =
            t.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]"), "")

        fun monthToNum(raw: String): Int? {
            val monthStr = normalizeToken(raw)
            return monthNamesEnAr[monthStr]
                ?: monthNamesEnAr.entries.firstOrNull { monthStr.contains(it.key) }?.value
        }

        for (i in tokens.indices) {
            val t1 = normalizeToken(tokens[i])

            // number + month
            if (t1.toIntOrNull() != null && i + 1 < tokens.size) {
                val day = t1.toIntOrNull() ?: continue
                val monthNum = monthToNum(tokens[i + 1]) ?: continue

                try {
                    var date = LocalDate.of(today.year, monthNum, day)
                    if (!date.isAfter(today)) date = date.plusYears(1)
                    return DateResult(date)
                } catch (_: Exception) {
                    // keep scanning
                }
            }

            // month + number
            if (monthToNum(tokens[i]) != null && i + 1 < tokens.size) {
                val monthNum = monthToNum(tokens[i]) ?: continue
                val day = normalizeToken(tokens[i + 1]).toIntOrNull() ?: continue

                try {
                    var date = LocalDate.of(today.year, monthNum, day)
                    if (!date.isAfter(today)) date = date.plusYears(1)
                    return DateResult(date)
                } catch (_: Exception) {
                    // keep scanning
                }
            }
        }

        return null
    }

    // =====================================================
    // RANGE
    // =====================================================
    private fun parseRange(text: String): DateResult? {
        // من يوم الاحد ليوم الثلاثاء
        // من الاحد للثلاثاء
        // من يوم الخميس الى يوم الجمعة
        // من الأحد إلى الثلاثاء
        //من 5/3 الى  7/3
        //من يوم الاحد الجاي للثلاثاء بعد الجاي
        val regex = Regex(
            """من\s+(?:يوم\s+)?(.+?)\s+(?:ل|الى|إلى)\s*(?:يوم\s+)?(.+)""",
            RegexOption.DOT_MATCHES_ALL
        )

        val match = regex.find(text) ?: return null

        val startText = match.groupValues[1].trim()
        val endText   = match.groupValues[2].trim()

        //  use full parser instead of manual weekday logic
        val start = parse(startText)
        val end   = parse(endText)
        if (start != null && end != null) {
            var endDate = end.startDate

            while (!endDate.isAfter(start.startDate)) {
                endDate = endDate.plusWeeks(1)
            }
            return DateResult(start.startDate, endDate)
        }

        return null
    }
    // =====================================================
    // RECURRING
    // =====================================================
    private fun parseRecurring(text: String): DateResult? {

        val regex = Regex(
            """كل\s+(?:يوم\s+)?(?:ال)?\s*(احد|أحد|اثنين|اتنين|ثلاثاء|تلات|اربعاء|اربع|خميس|جمعة|جمعه|سبت)"""
        )

        val match = regex.find(text) ?: return null

        val day = resolveWeekday(match.groupValues[1]) ?: return null
        val next = nextWeekday(day)

        return DateResult(next, isRecurring = true)
    }



    // =====================================================
    // MONTH / WEEK RELATIVE
    // =====================================================
    private fun parseMonthOrWeekRelative(text: String): DateResult? {
        val regex = Regex("""(الشهر|الاسبوع|الأسبوع)\s+(الجاي|اللي\s+جاي|القادم|بعد\s+الجاي|بعد\s+اللي\s+جاي)""")
        val match = regex.find(text) ?: return null

        val unit = match.groupValues[1]
        val modifier = match.groupValues[2]

        return when (unit) {

            "الشهر" -> {
                var date = today.plusMonths(1).withDayOfMonth(1)
                if (modifier.contains("بعد")) date = date.plusMonths(1)
                DateResult(date)
            }

            "الاسبوع", "الأسبوع" -> {
                var date = today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                if (modifier.contains("بعد")) date = date.plusWeeks(1)
                DateResult(date)
            }

            else -> null
        }
    }

    // =====================================================
    // MONTH / WEEK BOUNDARY
    // =====================================================

    private fun parseMonthWeekBoundary(text: String): DateResult? {

        val regex = Regex(
            """(اول|بدايه|بداية|نص|منتصف|نصف|اخر|آخر)\s*(?:\p{L}+\s+)?(الشهر|الاسبوع|الأسبوع)(?:\s+(الجاي|اللي\s+جاي|القادم|بعد\s+الجاي|بعد\s+اللي\s+جاي))?""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val match = regex.find(text) ?: return null

        val position = match.groupValues[1]
        val unit = match.groupValues[2]
        // <-- FIX: modifier is group 3 (not 4)
        val modifier = match.groupValues.getOrNull(3) ?: ""

        when (unit) {

            "الشهر" -> {
                // candidate in current month
                val dayOfMonth = when (position) {
                    "اول","بدايه","بداية" -> 1
                    "نص","منتصف","نصف" -> 15
                    "اخر","آخر" -> today.lengthOfMonth()
                    else -> return null
                }

                val candidateThisMonth = try {
                    today.withDayOfMonth(dayOfMonth)
                } catch (_: Exception) {
                    // invalid day (shouldn't happen) → fall back to next month
                    today.plusMonths(1).withDayOfMonth(dayOfMonth)
                }

                val baseMonth = when {
                    // explicit "بعد" means two months ahead of today
                    modifier.contains("بعد") -> today.plusMonths(2)
                    // explicit next means next month
                    modifier.isNotBlank() -> today.plusMonths(1)
                    // no modifier: if candidate is still upcoming (>= today) use current month, else next month
                    candidateThisMonth.isBefore(today) -> today.plusMonths(1)
                    else -> today
                }

                val date = when (position) {
                    "اول","بدايه","بداية" -> baseMonth.withDayOfMonth(1)
                    "نص","منتصف","نصف" -> baseMonth.withDayOfMonth(15)
                    "اخر","آخر" -> baseMonth.withDayOfMonth(baseMonth.lengthOfMonth())
                    else -> return null
                }

                return DateResult(date)
            }

            "الاسبوع","الأسبوع" -> {
                // define week start as Sunday of the current week (previousOrSame)
                val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

                val candidateThisWeek = when (position) {
                    "اول","بدايه","بداية" -> currentWeekStart
                    "نص","منتصف","نصف" -> currentWeekStart.plusDays(2) // keep original +2 behavior
                    "اخر","آخر" -> currentWeekStart.plusDays(6)
                    else -> return null
                }

                val baseWeekStart = when {
                    modifier.contains("بعد") -> currentWeekStart.plusWeeks(2)
                    modifier.isNotBlank() -> currentWeekStart.plusWeeks(1)
                    // no modifier: use current week if the candidate hasn't passed yet, else next week
                    candidateThisWeek.isBefore(today) -> currentWeekStart.plusWeeks(1)
                    else -> currentWeekStart
                }

                val date = when (position) {
                    "اول","بدايه","بداية" -> baseWeekStart
                    "نص","منتصف","نصف" -> baseWeekStart.plusDays(2)
                    "اخر","آخر" -> baseWeekStart.plusDays(6)
                    else -> return null
                }

                return DateResult(date)
            }

            else -> return null
        }
    }

    // =====================================================
    // WEEKDAY
    // =====================================================
    private fun parseWeekday(text: String): DateResult? {
        val regex = Regex(
            """(?:يوم\s+)?(?:ال)?\s*(احد|أحد|اثنين|اتنين|ثلاثاء|تلات|اربعاء|اربع|خميس|جمعة|جمعه|سبت)"""
                    + """\s*"""
                    + """(الجاي|اللي\s+جاي|القادم|القادمة|بعد\s+الجاي|بعد\s+اللي\s+جاي)?\b"""
        )

        val match = regex.find(text) ?: return null
        val day = resolveWeekday(match.groupValues[1]) ?: return null
        var date = nextWeekday(day)

        val modifier = match.groupValues.getOrNull(2)?.trim() ?: ""

        // or shift +1 week if modifier is exactly "بعد الجاي" / "بعد اللي جاي"
        if (modifier.matches(Regex("""بعد\s+(القادم|القادمة|اللي\s+قادمة|اللي\s+قادم|الجاي|اللي\s+جاي)""")))
         {
            date = date.plusWeeks(1)
        }

        return DateResult(date)
    }

    // =====================================================
    // RELATIVE NUMERIC
    // =====================================================
    private fun parseRelativeNumeric(text: String): DateResult? {

        val dual = Regex("""(بعد|كمان)\s+(يومين|يومان|اسبوعين|أسبوعين|شهرين|شهران|سنتين|عامين)""")
        dual.find(text)?.let {
            val token = it.groupValues[2]
            val date = when (token) {
                "يومين","يومان" -> today.plusDays(2)
                "اسبوعين","أسبوعين" -> today.plusWeeks(2)
                "شهرين","شهران" -> today.plusMonths(2)
                "سنتين","عامين" -> today.plusYears(2)
                else -> return null
            }
            return DateResult(date)
        }
        // Dual forms (no number → assume 1)
        val singleUnit =
            Regex("""(بعد|كمان)\s+(شهر|شهور|اسبوع|أسبوع|اسابيع|يوم|ايام|سنه|سنة|عام|سنين)""")
        singleUnit.find(text)?.let {
            val unit = it.groupValues[2]
            return when (unit) {
                "شهر", "شهور" -> DateResult(today.plusMonths(1))
                "اسبوع", "أسبوع", "اسابيع" -> DateResult(today.plusWeeks(1))
                "يوم", "ايام" -> DateResult(today.plusDays(1))
                "سنه", "سنة", "عام", "سنين" -> DateResult(today.plusYears(1))
                else -> null
            }
        }


        val regex = Regex(
            """(بعد|كمان)\s+(?:\p{L}+\s+)*?(\d+|\p{L}+)\s+(يوم|ايام|اسبوع|اسابيع|شهر|شهور|سنة|سنين|عام|اعوام)"""
        )

        val match = regex.find(text) ?: return null

        val rawNumber = match.groupValues[2]
        val unit = match.groupValues[3]

        val normalizedNumber = normalizeArabic(rawNumber)

        val number = normalizedNumber.toLongOrNull()
            ?: numberWords[normalizedNumber]?.toLong()
            ?: return null

        val date = when (unit) {
            "يوم","ايام" -> today.plusDays(number)
            "اسبوع","اسابيع" -> today.plusWeeks(number)
            "شهر","شهور" -> today.plusMonths(number)
            "سنة","سنين","عام","اعوام" -> today.plusYears(number)
            else -> return null
        }

        return DateResult(date)
    }

    // =====================================================
    // SINGLE DAY
    // =====================================================
    private fun parseSingleDay(text: String): DateResult? {
        if (Regex("""(بعد|كمان)\s+(بكره|يوم|بكرة|غد)""").containsMatchIn(text))
            return DateResult(today.plusDays(2))

        if (Regex("""(بكره|بكرة|غد|غدا)""").containsMatchIn(text))
            return DateResult(today.plusDays(1))

        return null
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private fun extractWeekday(text: String): DayOfWeek? {
        val cleanedText = text.trim()
        val regex = Regex(
            """(?:يوم\s+)?(?:ال)?\s*(احد|أحد|اثنين|اتنين|ثلاثاء|تلات|اربعاء|اربع|خميس|جمع[ةه]|جمعه?|سبت)\b""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(cleanedText) ?: return null
        val dayStr = match.groupValues[1]
        return resolveWeekday(dayStr)
    }
    private fun resolveWeekday(ar: String): DayOfWeek? {
        val cleaned = ar
            .replace("ال", "")
            .replace("\\s+".toRegex(), "")
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("ة", "ه")

        return when {
            cleaned in listOf("احد", "أحد", "احد") -> DayOfWeek.SUNDAY
            cleaned in listOf("اثنين", "اتنين", "اثنين") -> DayOfWeek.MONDAY
            cleaned in listOf("ثلاثاء", "تلات", "ثلاثاء") -> DayOfWeek.TUESDAY
            cleaned in listOf("اربعاء", "اربع", "اربعاء") -> DayOfWeek.WEDNESDAY
            cleaned.contains("خميس") -> DayOfWeek.THURSDAY
            cleaned in listOf("جمعة", "جمعه", "جمعه") -> DayOfWeek.FRIDAY
            cleaned.contains("سبت") -> DayOfWeek.SATURDAY
            else -> null
        }
    }

    private fun nextWeekday(target: DayOfWeek): LocalDate {
        var daysUntil = target.value - today.dayOfWeek.value
        if (daysUntil <= 0) daysUntil += 7
        return today.plusDays(daysUntil.toLong())
    }

    private fun normalizeDigits(text: String): String =
        text.replace("٠","0")
            .replace("١","1")
            .replace("٢","2")
            .replace("٣","3")
            .replace("٤","4")
            .replace("٥","5")
            .replace("٦","6")
            .replace("٧","7")
            .replace("٨","8")
            .replace("٩","9")

    private val numberWords = mapOf(
        "واحد" to 1,"واحدة" to 1,
        "اتنين" to 2,"اثنين" to 2,
        "تلاته" to 3,"تلات" to 3,"ثلاث" to 3,"ثلاثة" to 3,
        "اربعة" to 4,"خمسة" to 5,
        "ستة" to 6,"سبعة" to 7,
        "تمانية" to 8,"ثمانية" to 8,
        "تسعة" to 9,"عشرة" to 10
    )
}





