import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

data class TimeResult(
    val time: LocalTime
)

object TimeParser {

    private val now: LocalTime
        get() = LocalTime.now()

    // =====================================================
    // ENTRY
    // =====================================================
    fun parse(input: String): TimeResult? {
        val text = normalize(input)

        parseNaturalExpression(text)?.let { return it }  // تمانيه ونص، تسعه وتلت، الا ربع، ربعايه...
        parseRelative(text)?.let { return it }           // بعد ساعة، كمان يومين، بعد شهر...
        parseNumericClock(text)?.let { return it }       // 10:30, 3:00 مساء, 09:00 AM...
     //   parseNaturalExpression(text)?.let { return it }  // تمانيه ونص، تسعه وتلت، الا ربع، ربعايه...
        parseDayPartOnly(text)?.let { return it }        // بعد الضهر، العصر، بالليل...

        return null
    }

    // =====================================================
    // NORMALIZATION
    // =====================================================
    private fun normalize(text: String): String {
        return text
            .replace("٠","0").replace("١","1").replace("٢","2").replace("٣","3")
            .replace("٤","4").replace("٥","5").replace("٦","6").replace("٧","7")
            .replace("٨","8").replace("٩","9")
            .replace("[أإآ]".toRegex(), "ا")
            .replace("ة", "ه")
            .replace(" +".toRegex(), " ")
            .lowercase(Locale.getDefault())

    }

    private fun fixMissingSpaces(text: String): String {
        return text.replace(Regex("""(\p{L}+?)و(?=\p{L}+)""")) {
            it.groupValues[1] + " و"
        }
    }

    // =====================================================
    // 1. RELATIVE (بعد / كمان + full unit)
    // =====================================================
    private fun parseRelative(text: String): TimeResult? {
        // Fixed cases (1 or 2 units) - keep for speed
        if (Regex("""(بعد|كمان)\s+(ساعه|ساعة)""").containsMatchIn(text))
            return TimeResult(now.plusHours(1).truncatedTo(ChronoUnit.MINUTES))

        if (Regex("""(بعد|كمان)\s+(ساعتين|ساعتان)""").containsMatchIn(text))
            return TimeResult(now.plusHours(2).truncatedTo(ChronoUnit.MINUTES))

        if (Regex("""(بعد|كمان)\s+(دقيقه|دقيقة)""").containsMatchIn(text))
            return TimeResult(now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES))

        if (Regex("""(بعد|كمان)\s+(دقيقتين|دقيقتان)""").containsMatchIn(text))
            return TimeResult(now.plusMinutes(2).truncatedTo(ChronoUnit.MINUTES))

        // General case: بعد/كمان + number expression + unit
        // Improved regex to capture the full number part (including "و")
        val regex = Regex("""(بعد|كمان)\s+(.+?)\s+(ساعه|ساعة|ساعات|دقيقه|دقايق|دقيقة)""")
        regex.find(text)?.let { match ->
            val prefix = match.groupValues[1]
            val numberPart = match.groupValues[2].trim()
            val unit = match.groupValues[3]

            // Parse the number part (supports "اتنين و عشرين", "تلاته", "٥", ...)
            val number = parseComplexNumber(numberPart) ?: return null
            if (number <= 0) return null

            return when (unit) {
                "ساعه", "ساعة", "ساعات" -> TimeResult(now.plusHours(number.toLong()).truncatedTo(ChronoUnit.MINUTES))
                "دقيقه", "دقايق", "دقيقة" -> TimeResult(now.plusMinutes(number.toLong()).truncatedTo(ChronoUnit.MINUTES))
                else -> null
            }
        }
        if (text.contains("ساعه كده")) {
            return TimeResult(now.plusHours(1).truncatedTo(ChronoUnit.MINUTES))
        }

        if (text.contains("نص ساعه كده") ) {
            return TimeResult(now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES))
        }
        return null
    }


    // =====================================================
    // 2. NUMERIC CLOCK (10:30, 3:00 مساء, 09:00 AM...)
    // =====================================================
    private fun parseNumericClock(text: String): TimeResult? {
        val regex = Regex(
            """\b(\d{1,2}):(\d{1,2})\b""",
            RegexOption.IGNORE_CASE
        )

        val match = regex.find(text) ?: return null

        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0

        if (rawHour > 23 || minute > 59) return null

        // Use the same safe adjustByContext (returns null on ambiguity)
        val finalHour = adjustByContext(rawHour, text) ?: return null

        return TimeResult(LocalTime.of(finalHour, minute))
    }

    // =====================================================
    // 3. NATURAL EXPRESSION (تمانيه ونص, الا ربع, ربعايه, تسعه وتلت...)
    // =====================================================
    private fun parseNaturalExpression(text: String): TimeResult? {

        // 1. Very short fractions (no hour)
        when {
            text.contains("نصايه") || text.contains("نص ساعه") || text.contains("نصف ساعه") ->
                return TimeResult(now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES))

            text.contains("ربعايه") || text.contains("ربع ساعه")  ->
                return TimeResult(now.plusMinutes(15).truncatedTo(ChronoUnit.MINUTES))

            text.contains("تلتايه") || text.contains("تلت ساعه")  ->
                return TimeResult(now.plusMinutes(20).truncatedTo(ChronoUnit.MINUTES))

            text.contains("تلتين") ->
                return TimeResult(now.plusMinutes(40).truncatedTo(ChronoUnit.MINUTES))
        }

        // 2. Base hour
        val hourRegex = Regex("""\b(\d{1,2}|\p{L}+)\b""")
        val hourMatch = hourRegex.find(text) ?: return null
        val baseHour = parseNumber(hourMatch.value) ?: return null
        if (baseHour !in 0..23) return null

        var hour = baseHour
        var totalMinutes = 0
        var hadAddition = false

        // 3. Additions (و ...)
        val addRegex = Regex("""و\s*([^\s]+)""")
        addRegex.findAll(text).forEach { match ->
            var token = match.groupValues[1]

            token = token
                .replace("بالليل","")
                .replace("الصبح","")
                .replace("العشا","")
                .replace("العشاء","")
                .replace("مساء","")

            val addMin = when {
                token.contains("نص") -> 30
                token.contains("ربع") -> 15
                token.contains("تلت") -> 20
                token == "تلتين" -> 40
                else -> parseNumber(token) ?: 0
            }

            if (addMin > 0) {
                hadAddition = true
                totalMinutes += addMin
            }
        }

        // 4. Subtraction (الا ...)
        val minusRegex = Regex("""الا\s+([^\s]+)""")
        minusRegex.find(text)?.let {
            var token = it.groupValues[1]

            token = token
                .replace("بالليل","")
                .replace("الصبح","")
                .replace("العشا","")
                .replace("العشاء","")
                .replace("مساء","")

            val minus = when {
                token.contains("نص") -> 30
                token.contains("ربع") -> 15
                token.contains("تلت") -> 20
                token == "تلتين" -> 40
                else -> parseNumber(token) ?: 0
            }

            if (hadAddition) {
                // 8 ونص الا 5  → (8 + 30) - 5
                totalMinutes -= minus
            } else {
                // 11 الا ربع → (11 - 1) + (60 - 15)
                hour -= 1
                totalMinutes = 60 - minus
            }

            if (totalMinutes < 0) {
                totalMinutes += 60
                hour -= 1
            }
        }

        totalMinutes = totalMinutes.coerceIn(0, 59)

        val adjustedHour = adjustByContext(hour, text) ?: return null

        return TimeResult(LocalTime.of(adjustedHour, totalMinutes))
    }
    // =====================================================
    // 4. DAY PART ONLY
    // =====================================================
    private fun parseDayPartOnly(text: String): TimeResult? {
        val lower = text.lowercase().trim()

        return when {
            // اول / بداية اليوم (first part of the day)
            lower.contains(Regex("""(اول|بدايه|بداية)\s*(?:ال)?\s*(يوم|اليوم)""")) ->
                TimeResult(LocalTime.of(9, 0))

            // نص / منتصف اليوم (midday)
            lower.contains(Regex("""(نص|منتصف)\s*(?:ال)?\s*(يوم|اليوم)""")) ->
                TimeResult(LocalTime.NOON)

            // آخر / نهاية اليوم (end of day)
            lower.contains(Regex("""(اخر|آخر|نهايه|نهاية)\s*(?:ال)?\s*(يوم|اليوم)""")) ->
                TimeResult(LocalTime.of(23, 0))

            // بعد الضهر / الضهر / الظهر
            lower.contains(Regex("""(بعد\s+الضهر|الضهر|الظهر)""")) ->
                TimeResult(LocalTime.of(13, 0))

            // العصر / عصرا
            lower.contains(Regex("""(العصر|عصرا)""")) ->
                TimeResult(LocalTime.of(16, 0))

            // بعد العشا / بعد العشاء / العشا / العشاء
            lower.contains(Regex("""(بعد\s+(العشا|العشاء)|العشا|العشاء)""")) ->
                TimeResult(LocalTime.of(21, 0))

            // بالليل / ليل
            lower.contains(Regex("""(بالليل|ليل)""")) ->
                TimeResult(LocalTime.of(21, 0))

            else -> null
        }
    }
    // =====================================================
    // HELPERS
    // =====================================================
    private fun adjustByContext(hour: Int, text: String): Int? {
        val lowerText = text.lowercase()

        return when {
            // Clear PM / afternoon / evening context
            lowerText.contains("مساء") ||
                    lowerText.contains("عشا") ||
                    lowerText.contains("عشاء") ||
                    lowerText.contains("ليل") ||
                    lowerText.contains("بالليل") ||
                    lowerText.contains("ضهر") ||
                    lowerText.contains("ظهرا") ||
                    lowerText.contains("ضهرا") ||
                    lowerText.contains("ظهر") ||
                    lowerText.contains("عصر") ||
                    lowerText.contains("عصرا")||
                    lowerText.contains("pm") ||
                    lowerText.contains("p.m") -> {
                if (hour < 12) hour + 12 else hour
            }

            // Clear AM / morning context
            lowerText.contains("صباح") ||
                    lowerText.contains("الصبح") ||
                    lowerText.contains("am") ||
                    lowerText.contains("a.m") -> {
                if (hour == 12) 0 else hour
            }

            // Ambiguous hour in 1..11 with no context → caller should ask user
            hour in 1..11 -> null

            // Otherwise (e.g. hour 12–23 or 0) → keep as is (likely 24h format)
            else -> hour
        }
    }
    // New helper to parse chained numbers like "اتنين و عشرين" → 22
    private fun parseComplexNumber(text: String): Int? {
        val parts = text.split(" و ")
        if (parts.size == 1) {
            // Single number
            return parseNumber(parts[0].trim())
        }

        // Chained: "اتنين و عشرين" = 2 + 20 = 22
        var total = 0
        for (part in parts) {
            val num = parseNumber(part.trim()) ?: return null
            total += num
        }
        return total
    }
    private fun parseNumber(token: String): Int? {
        val word = token.trim()
        word.toIntOrNull()?.let {
            if (it in 0..59) return it
        }
        return numberMap[word]
    }

    private val numberMap = buildMap {
        val base = listOf("واحد","اتنين","اثنين","تلاته","ثلاثه","اربعه","خمسه","سته","سبعه","تمانيه","تسعه")
        put("واحد",1); put("واحده",1)
        put("اتنين",2); put("اثنين",2)
        put("تلاته",3); put("ثلاثه",3)
        put("اربعه",4)
        put("خمسه",5)
        put("سته",6)
        put("سبعه",7)
        put("تمانيه",8)
        put("تسعه",9)
        put("عشره",10)
        put("حداشر",11)
        put("اتناشر",12)
        put("تلتاشر",13)
        put("اربعتاشر",14)
        put("خمستاشر",15)
        put("ستاشر",16)
        put("سبعتاشر",17)
        put("تمنتاشر",18)
        put("تسعتاشر",19)
        put("عشرين",20);put("عشرون",20)
        put("ثلاثين",30); put("تلاتين",30)
        put("اربعين",40)
        put("خمسين",50)
        put("ربع",15)
        put("تلت",20)
        put("نص",30)
        put("نصف",30)
        for (i in 1..9) {
            val u = base[i-1]
            put("$u وعشرين",20+i)
            put("$u وثلاثين",30+i)
            put("$u واربعين",40+i)
            put("$u وخمسين",50+i)
        }
    }
}