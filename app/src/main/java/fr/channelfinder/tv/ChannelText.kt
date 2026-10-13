package fr.channelfinder.tv

import java.text.Normalizer

/** Display cleanup, lane heuristics and accent-insensitive search for provider labels. */
object ChannelText {
    data class Label(val title: String, val country: String?, val badges: List<String>)

    enum class VideoQuality(val label: String, val badge: String) {
        UHD("4K", "4K"), FHD("Full HD", "FHD"), HD("HD", "HD"), SD("SD", "SD")
    }

    // Provider prefixes that are spelling variants of a country code.
    private val countryAliases = mapOf(
        "IND" to "IN", "USA" to "US", "ITA" to "IT", "UK" to "GB", "SR" to "RS", "PERU" to "PE", "COL" to "CO",
        "EGY" to "EG", "PAN" to "PA", "RDOM" to "DO", "MEX" to "MX", "ESP" to "ES", "POR" to "PT", "BRA" to "BR"
    )
    // Prefixes providers use for regions or languages, which collide with real ISO codes (AR = Arabic, AF = Africa…).
    private val notCountryCodes = setOf("AR", "AF", "AS", "LA", "LT", "EU", "EN", "KU", "KD", "EX")
    private val isoCountries = java.util.Locale.getISOCountries().toSet()

    /** ISO country code behind a provider prefix, or null when it is a region, language or label. */
    fun countryCode(tag: String?): String? {
        val code = tag?.uppercase()?.let { countryAliases[it] ?: it } ?: return null
        return code.takeIf { it.length == 2 && it in isoCountries && it !in notCountryCodes }
    }

    // Usual short French names where the official one is too long for a chip.
    private val shortCountryNames = mapOf(
        "CF" to "Centrafrique", "CD" to "RD Congo", "CG" to "Congo", "US" to "États-Unis", "GB" to "Royaume-Uni",
        "DO" to "Rép. dominicaine", "AE" to "Émirats", "BA" to "Bosnie", "CZ" to "Tchéquie", "KR" to "Corée du Sud",
        "MK" to "Macédoine", "SA" to "Arabie saoudite", "TT" to "Trinité-et-Tobago", "ZA" to "Afrique du Sud"
    )

    fun countryName(code: String): String = shortCountryNames[code]
        ?: java.util.Locale("", code).getDisplayCountry(java.util.Locale.FRENCH).replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }

    /** "🇧🇷 Brésil" for "BR". */
    fun countryLabel(code: String): String {
        val flag = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
        return "$flag ${countryName(code)}"
    }

    /** Best quality named in the channel name, else in its category ("FRANCE • UHD/4K", "FRANCE • SD"). */
    fun quality(nameBadges: List<String>, categoryBadges: List<String>): VideoQuality? {
        fun pick(badges: List<String>) = VideoQuality.entries.firstOrNull { it.badge in badges }
        return pick(nameBadges) ?: pick(categoryBadges)
    }

    // "|FR| TF1", "FR ‖ FRANCE", "┃PT┃ RTP", "[ES] …", "FR: …", "FR - …"
    private val countryPrefix = Regex("""^\s*[|\[(┃‖]?\s*([A-Z]{2,4})\s*[|\])┃‖:\-•]+\s*""")
    private val notCountries = setOf("TV", "HD", "SD", "FHD", "UHD", "VIP", "PPV", "NEW", "LIVE")
    private val quality = Regex("""(?i)(?<![\p{L}\p{N}])(4K|UHD|FHD|FULL\s*HD|HD|SD|HEVC|H\.?265|50\s*FPS|60\s*FPS|RAW)(?![\p{L}\p{N}])""")
    private val trailingJunk = Regex("""[\s/|\-–·•:‖┃]+$""")
    private val trailingTag = Regex("""\s*[|┃‖\[]\s*([A-Z]{2,3})\s*[|┃‖\]]?\s*$""")
    private val leadingJunk = Regex("""^[\s/|\-–·•:‖┃]+""")
    private val spaces = Regex("""\s{2,}""")
    private val diacritics = Regex("""\p{Mn}+""")

    private val categoryLabels = java.util.concurrent.ConcurrentHashMap<String, Label>()
    private val categoryRanks = java.util.concurrent.ConcurrentHashMap<String, Int>()

    @Volatile private var categorySuffixes: Map<String, String> = emptyMap()

    /** Category labels repeat across thousands of channels, so parse each one once. */
    fun categoryLabel(raw: String): Label = categoryLabels.getOrPut(raw) {
        parseLabel(raw, keepQuality = true).let { label -> categorySuffixes[raw]?.let { label.copy(title = label.title + it) } ?: label }
    }

    /**
     * Providers publish some groups twice under parallel prefixes ("┃FR┃ DISNEY+ LA LIGA" and
     * "FR ‖ FR • DISNEY+ LA LIGA"), which clean up to the same title. Number the later copies, in
     * provider order, when title and country both match; a different country already shows as a badge.
     */
    fun setCategories(names: List<String>) {
        val seen = HashMap<String, Int>()
        val suffixes = HashMap<String, String>()
        names.forEach { raw ->
            val label = parseLabel(raw, keepQuality = true)
            val key = "${label.country}|${normalize(label.title)}"
            val copy = (seen[key] ?: 0) + 1
            seen[key] = copy
            if (copy > 1) suffixes[raw] = " ($copy)"
        }
        categorySuffixes = suffixes
        categoryLabels.clear()
        categoryRanks.clear()
    }

    /**
     * Group names keep their quality words ("PORTUGAL HEVC" and "PORTUGAL" are different groups);
     * channel names lose them in favour of badges.
     */
    fun parseLabel(raw: String, keepQuality: Boolean = false): Label {
        var text = raw.trim()
        var country: String? = null
        countryPrefix.find(text)?.let { match ->
            val code = match.groupValues[1]
            if (code !in notCountries && match.range.last + 1 < text.length) {
                country = code
                text = text.substring(match.range.last + 1)
                // Some groups repeat the code: "FR ‖ FR • ACTEURS FILMS" -> "ACTEURS FILMS".
                countryPrefix.find(text)?.takeIf { it.groupValues[1] == code && it.range.last + 1 < text.length }
                    ?.let { again -> text = text.substring(again.range.last + 1) }
            }
        }
        val badges = LinkedHashSet<String>()
        quality.findAll(text).forEach { badges += normalizeBadge(it.value) }
        val stripped = quality.replace(text, " ")
            .replace(trailingJunk, "")
            .replace(leadingJunk, "")
            .replace(spaces, " ")
            .trim()
        // "CANADA |FR|" -> "CANADA (FR)": keep the language tag, drop the stray pipes.
        text = text.replace(trailingTag) { " (${it.groupValues[1]})" }
        val kept = text.replace(trailingJunk, "").replace(leadingJunk, "").replace(spaces, " ").trim()
        val title = (if (keepQuality) kept else stripped).ifBlank { kept.ifBlank { raw.trim() } }
        return Label(title, country, if (stripped.isBlank()) emptyList() else badges.toList())
    }

    private fun normalizeBadge(value: String): String = when (val upper = value.uppercase().replace(" ", "")) {
        "FULLHD" -> "FHD"
        "H265", "H.265" -> "HEVC"
        "UHD" -> "4K"
        else -> upper
    }

    fun initials(title: String): String {
        val words = title.split(' ').filter { it.any(Char::isLetterOrDigit) }
        val first = words.firstOrNull().orEmpty()
        return when {
            first.length in 2..4 && first.any(Char::isDigit) -> first.uppercase()
            words.size >= 2 -> "${words[0].first { it.isLetterOrDigit() }}${words[1].first { it.isLetterOrDigit() }}".uppercase()
            else -> first.filter(Char::isLetterOrDigit).take(2).uppercase()
        }.ifBlank { "TV" }
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    fun tokens(query: String): List<String> = normalize(query).split(' ', '\t').filter(String::isNotBlank)

    /** Lower is better; null means no match. All tokens must appear somewhere in the channel text. */
    fun score(channel: Channel, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return 0
        if (tokens.any { it !in channel.searchKey }) return null
        val name = channel.nameKey
        val phrase = tokens.joinToString(" ")
        return when {
            name == phrase -> 0
            name.startsWith(phrase) -> 1
            tokens.all { token -> name.split(' ').any { it.startsWith(token) } } -> 2
            tokens.all { it in name } -> 3
            else -> 4
        }
    }

    // Event feeds only carry a picture during a match: provider "EVENTS"/"PPV" groups, explicit
    // "event only" wording, match titles with a start time, and numbered match feeds ("CANAL+ LIVE 4").
    private val eventCategory = Regex("""(?i)\b(events?|ppv)\b|\[events]""")
    private val eventName = Regex(
        """(?i)(live event|event only|no event|\(live only\)|\bppv\b|\bevents?\s*\d|pay per view|""" +
            """\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}|@ [A-Z][a-z]{2} \d{1,2} \d{1,2}:\d{2}|^\W*(\d{1,2}:\d{2}|live)\s*[|\-]| vs\.? | @ )"""
    )
    private val matchFeed = Regex("""(?i)(canal\+\s*live|ligue\s*1\s*\+|ligue\+\s*1|ligue⚽1|la\s*liga|laliga|serie\s*a|betclic)\s*\d""")
    private val eventLanguages = setOf("FR", "PT", "BR")

    fun isEvent(category: String, name: String): Boolean =
        eventCategory.containsMatchIn(category) || eventName.containsMatchIn(name) || matchFeed.containsMatchIn(name)

    /**
     * The country code on the provider's category wins over words in the channel name, so a
     * Spanish category is never filed under Português because a channel is called "Trace Brasil".
     */
    fun classifyLane(category: String, name: String): String {
        val text = "$category $name".uppercase()
        val country = categoryLabel(category).country
        val base = when {
            // Sports only in French or Portuguese: other countries' sports stay in their category and search.
            listOf("SPORT", "ESPN", "BEIN", "EUROSPORT", "RMC", "PPV", "NFL", "NBA", "UFC").any(text::contains) -> when {
                country in eventLanguages -> BrowseLane.SPORTS.label
                country == null && listOf("|FR|", "FR |", "┃FR┃", "FR ‖", "FRANCE", "|PT|", "PT |", "PT ‖", "PORTUGAL", "BRASIL").any(text::contains) ->
                    BrowseLane.SPORTS.label
                else -> OTHER
            }
            country != null -> when {
                country == "FR" -> BrowseLane.FRANCE.label
                country in setOf("CA", "BE", "CH") && Regex("""\bFR\b|FRANCE""").containsMatchIn(category.uppercase()) -> BrowseLane.FRANCE.label
                country == "PT" || country == "BR" -> BrowseLane.PORTUGUESE.label
                else -> OTHER
            }
            listOf("FRANCE", "FR |", "FR┃", "┃FR┃", "FR ‖", "|FR|").any(text::contains) -> BrowseLane.FRANCE.label
            listOf("PORTUGAL", "PORTUGUE", "PORTUGUÊ", "BRASIL", "BRAZIL", "PT |", "PT┃", "PT ‖", "|PT|").any(text::contains) -> BrowseLane.PORTUGUESE.label
            else -> OTHER
        }
        if (!isEvent(category, name)) return base
        val relevant = base == BrowseLane.FRANCE.label || base == BrowseLane.PORTUGUESE.label ||
            (base == BrowseLane.SPORTS.label && country in eventLanguages)
        // Events for other audiences stay reachable through their category and search only.
        return if (relevant) BrowseLane.EVENTS.label else OTHER
    }

    const val OTHER = "Autres"

    enum class EventStatus { LIVE, UPCOMING, FEED, ENDED, IDLE }

    /** What an event feed currently carries, parsed from names such as "DAZN FR 003| Rennes vs. Auxerre | Ligue 1+ (2026-10-11 17:15:00)". */
    data class EventInfo(val title: String, val competition: String?, val feed: String?, val start: Long?, val idle: Boolean) {
        fun status(now: Long = System.currentTimeMillis()): EventStatus = when {
            idle -> EventStatus.IDLE
            start == null -> EventStatus.FEED
            now < start -> EventStatus.UPCOMING
            now <= start + EVENT_DURATION_MS -> EventStatus.LIVE
            else -> EventStatus.ENDED
        }
    }

    private const val EVENT_DURATION_MS = 3 * 60 * 60 * 1000L
    private val isoStart = Regex("""\(?\s*(\d{4})-(\d{2})-(\d{2})\s+(\d{1,2}):(\d{2})(:\d{2})?\s*\)?""")
    private val monthStart = Regex("""@\s*([A-Z][a-z]{2})\s+(\d{1,2})\s+(\d{1,2}):(\d{2})\s*(AM|PM)?""")
    private val leadingTime = Regex("""^\W*(\d{1,2})[:h](\d{2})\s*[|\-]\s*""")
    private val leadingLive = Regex("""(?i)^\W*live\s*\|\s*""")
    private val feedCode = Regex("""^\s*([A-Z][A-Za-z+]*(?:\s+[A-Z]{2})?\s+\d{2,3})\s*\|\s*""")
    private val trailingFeed = Regex("""\s*:\s*([A-Z+]+\s+[A-Z]{2}\s+\d{1,3})\s*$""")
    private val noEvent = Regex("""(?i)no event""")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun parseEvent(raw: String, fallbackTitle: String): EventInfo {
        var text = raw.trim()
        var start: Long? = null
        var feed: String? = null
        isoStart.find(text)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            start = timestamp(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
            text = text.removeRange(m.range)
        }
        monthStart.find(text)?.let { m ->
            val month = months.indexOf(m.groupValues[1])
            if (month >= 0) {
                var hour = m.groupValues[3].toInt()
                if (m.groupValues[5] == "PM" && hour < 12) hour += 12
                if (m.groupValues[5] == "AM" && hour == 12) hour = 0
                val now = java.util.Calendar.getInstance()
                start = timestamp(now.get(java.util.Calendar.YEAR), month + 1, m.groupValues[2].toInt(), hour, m.groupValues[4].toInt())
            }
            text = text.removeRange(m.range)
        }
        leadingTime.find(text)?.let { m ->
            val now = java.util.Calendar.getInstance()
            start = timestamp(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1,
                now.get(java.util.Calendar.DAY_OF_MONTH), m.groupValues[1].toInt(), m.groupValues[2].toInt())
            text = text.removeRange(m.range)
        }
        text = text.replace(leadingLive, "")
        feedCode.find(text)?.let { m -> feed = m.groupValues[1]; text = text.removeRange(m.range) }
        trailingFeed.find(text)?.let { m -> feed = m.groupValues[1].replace(Regex("""\s+"""), " "); text = text.removeRange(m.range) }
        var parts = text.split('|').map { it.trim().trim('-', '–', '•', '●').trim() }
            .filter { it.isNotBlank() && !it.equals("all", true) && !Regex("""^[A-Z]+-[A-Z]{2}$""").matches(it) }
        // "SERIE A ENILIVE - LECCE VS. BOLOGNE": competition and match joined by a dash.
        if (parts.size == 1) {
            val pieces = parts[0].split(Regex("""\s+[-–]\s+"""))
            val matchAt = pieces.indexOfFirst { versus.containsMatchIn(it) }
            if (pieces.size > 1 && matchAt >= 0) {
                parts = listOf(pieces[matchAt], pieces.filterIndexed { i, _ -> i != matchAt }.joinToString(" – "))
            }
        }
        // The match is the part with two sides, wherever the provider puts it.
        val matchIndex = parts.indexOfFirst { versus.containsMatchIn(it) || teamDash.containsMatchIn(it) }
        if (matchIndex > 0) parts = listOf(parts[matchIndex]) + parts.filterIndexed { i, _ -> i != matchIndex }
        val idle = noEvent.containsMatchIn(raw)
        val title = if (idle || start == null) fallbackTitle
        else parts.firstOrNull()?.let { tidyMatch(it) } ?: fallbackTitle
        val competition = if (start != null) parts.getOrNull(1)?.let(::smartCase) else null
        return EventInfo(title, competition, feed, start, idle)
    }

    private val versus = Regex("""(?i)\s(vs\.?|v\.?)\s""")
    private val teamDash = Regex("""\p{L}{2,}\s+[-–]\s+\p{L}{2,}""")

    /** "REAL BETIS SÉVILLE - CA OSASUNA" -> "Real Betis Séville vs. CA Osasuna". */
    private fun tidyMatch(value: String): String {
        // Case first, while the text is still all capitals, then the separator.
        val cased = smartCase(value.replace(versus, " VS ").replace(spaces, " ").trim())
        if (versus.containsMatchIn(cased)) return cased.replace(versus, " vs. ")
        // "Real Betis - CA Osasuna" is a match; "Victoria, BC - Day 1, Session 1" is a session title.
        val sides = cased.split(Regex("""\s+[-–]\s+"""), limit = 2)
        return if (sides.size == 2 && sides.none { side -> side.any(Char::isDigit) }) "${sides[0]} vs. ${sides[1]}" else cased
    }

    /** Title-cases names the provider wrote in capitals; short club tags (FC, CA, PSG) stay upper case. */
    private fun smartCase(value: String): String {
        val letters = value.filter(Char::isLetter)
        if (letters.isEmpty() || letters.any(Char::isLowerCase)) return value
        return value.split(' ').joinToString(" ") { word ->
            when {
                word.equals("VS.", true) || word.equals("VS", true) -> "vs."
                word.length <= 3 && word.all { it.isLetter() } -> word
                word.any { it == '.' || it.isDigit() } -> word // NHL.TV, F1, 4K
                else -> word.lowercase(java.util.Locale.FRENCH).replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }
            }
        }
    }

    /** Same match from two naming styles: "Atalanta vs. Venise" and "ATALANTA VS. VENISE". */
    fun matchTitleKey(info: EventInfo): String =
        normalize(info.title).replace(Regex("""\bvs\b|[^\p{L}\p{N}]+"""), " ").replace(spaces, " ").trim()

    /**
     * One entry per match. Providers list the same match under different names and with kickoff times a few
     * minutes apart (20:53 and 21:00), so titles are compared and starts within 30 minutes are merged.
     * Keeps the first copy in the given order. Feeds without a start time are never merged.
     */
    fun distinctMatches(channels: List<Channel>): List<Channel> {
        val seen = HashMap<String, MutableList<Long>>()
        return channels.filter { channel ->
            val info = channel.event ?: return@filter true
            val start = info.start ?: return@filter true
            val starts = seen.getOrPut(matchTitleKey(info)) { mutableListOf() }
            if (starts.any { kotlin.math.abs(it - start) <= 30 * 60_000L }) false else { starts += start; true }
        }
    }

    /** "Aujourd’hui", "Demain" or "Mercredi 14 octobre", for day sections. */
    fun eventDayLabel(info: EventInfo, now: Long = System.currentTimeMillis()): String {
        val start = info.start ?: return ""
        return when (dayIndex(start) - dayIndex(now)) {
            0L -> "Aujourd’hui"
            1L -> "Demain"
            else -> java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE).format(java.util.Date(start))
                .replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }
        }
    }

    /** "Commence aujourd’hui à 18:30" / "Commence mercredi 14 octobre à 00:30". */
    fun eventStartSentence(info: EventInfo, now: Long = System.currentTimeMillis()): String {
        val start = info.start ?: return ""
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date(start))
        return when (dayIndex(start) - dayIndex(now)) {
            0L -> "Commence aujourd’hui à $time"
            1L -> "Commence demain à $time"
            else -> "Commence " + java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE).format(java.util.Date(start)) + " à $time"
        }
    }

    /** Before kickoff an event feed shows nothing; streams are only opened from 15 minutes before. */
    fun notStartedYet(info: EventInfo?, now: Long = System.currentTimeMillis()): Boolean =
        info?.start != null && !info.idle && now < info.start - 15 * 60_000L

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }.timeInMillis

    /** Short French label such as "En cours", "Aujourd’hui 19:00", "Demain 17:15" or "sam. 12 oct. 21:00". */
    fun eventTimeLabel(info: EventInfo, now: Long = System.currentTimeMillis()): String {
        val start = info.start
        return when (info.status(now)) {
            EventStatus.LIVE -> "En cours"
            EventStatus.ENDED -> "Terminé"
            EventStatus.FEED -> "Pendant les matchs"
            EventStatus.IDLE -> "Aucun événement"
            EventStatus.UPCOMING -> {
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date(start!!))
                val days = dayIndex(start) - dayIndex(now)
                when (days) {
                    0L -> "Aujourd’hui $time"
                    1L -> "Demain $time"
                    else -> java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.FRANCE).format(java.util.Date(start)) + " $time"
                }
            }
        }
    }

    private fun dayIndex(time: Long): Long {
        val zone = java.util.TimeZone.getDefault()
        return (time + zone.getOffset(time)) / 86_400_000L
    }

    // General-interest groups first, regional and local ones last. Keywords only, never provider IDs.
    private val generalGroups = Regex("""(?i)g[ée]n[ée]ral|g[ée]n[ée]raliste|\btnt\b|^\W*(portugal|brasil|france)\W*$""")
    private val secondaryGroups = Regex("""(?i)r[ée]gion|local|dom-tom|pluto|backup|\bvip\b|hevc|\bsd\b""")
    private val infoGroups = Regex("""(?i)\binfo""")

    fun categoryRank(category: String): Int = categoryRanks.getOrPut(category) {
        val title = categoryLabel(category).title
        when {
            generalGroups.containsMatchIn(title) -> 0
            infoGroups.containsMatchIn(title) -> 1
            secondaryGroups.containsMatchIn(title) -> 3
            else -> 2
        }
    }

    /** Provider "separator" rows such as "##### FRANCE #####" carry no stream worth showing. */
    fun isSeparator(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.startsWith("###") || trimmed.startsWith("===") || trimmed.startsWith("***") ||
            trimmed.startsWith("---") || trimmed.startsWith("▬▬")
    }
}
