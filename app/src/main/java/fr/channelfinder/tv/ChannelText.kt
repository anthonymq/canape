package fr.channelfinder.tv

import java.text.Normalizer

/** Display cleanup, lane heuristics and accent-insensitive search for provider labels. */
object ChannelText {
    data class Label(val title: String, val country: String?, val badges: List<String>)

    // "|FR| TF1", "FR ‖ FRANCE", "┃PT┃ RTP", "[ES] …", "FR: …", "FR - …"
    private val countryPrefix = Regex("""^\s*[|\[(┃‖]?\s*([A-Z]{2,4})\s*[|\])┃‖:\-•]+\s*""")
    private val notCountries = setOf("TV", "HD", "SD", "FHD", "UHD", "VIP", "PPV", "NEW", "LIVE")
    private val quality = Regex("""(?i)(?<![\p{L}\p{N}])(4K|UHD|FHD|FULL\s*HD|HD|SD|HEVC|H\.?265|50\s*FPS|60\s*FPS|RAW)(?![\p{L}\p{N}])""")
    private val trailingJunk = Regex("""[\s/|\-–·•:‖┃]+$""")
    private val leadingJunk = Regex("""^[\s/|\-–·•:‖┃]+""")
    private val spaces = Regex("""\s{2,}""")
    private val diacritics = Regex("""\p{Mn}+""")

    private val categoryLabels = java.util.concurrent.ConcurrentHashMap<String, Label>()

    /** Category labels repeat across thousands of channels, so parse each one once. */
    fun categoryLabel(raw: String): Label = categoryLabels.getOrPut(raw) { parseLabel(raw) }

    fun parseLabel(raw: String): Label {
        var text = raw.trim()
        var country: String? = null
        countryPrefix.find(text)?.let { match ->
            val code = match.groupValues[1]
            if (code !in notCountries && match.range.last + 1 < text.length) {
                country = code
                text = text.substring(match.range.last + 1)
            }
        }
        val badges = LinkedHashSet<String>()
        quality.findAll(text).forEach { badges += normalizeBadge(it.value) }
        val stripped = quality.replace(text, " ")
            .replace(trailingJunk, "")
            .replace(leadingJunk, "")
            .replace(spaces, " ")
            .trim()
        val title = stripped.ifBlank { text.replace(trailingJunk, "").trim().ifBlank { raw.trim() } }
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

    fun classifyLane(category: String, name: String): String {
        val text = "$category $name".uppercase()
        if (listOf("SPORT", "ESPN", "BEIN", "EUROSPORT", "RMC", "PPV", "NFL", "NBA", "UFC").any(text::contains)) {
            return BrowseLane.SPORTS.label
        }
        if (listOf("FRANCE", "FR |", "FR┃", "┃FR┃", "FR ‖", "|FR|").any(text::contains)) return BrowseLane.FRANCE.label
        if (listOf("PORTUGAL", "PORTUGUE", "PORTUGUÊ", "BRASIL", "BRAZIL", "PT |", "PT┃", "PT ‖", "|PT|").any(text::contains)) {
            return BrowseLane.PORTUGUESE.label
        }
        return "Autres"
    }

    /** Provider "separator" rows such as "##### FRANCE #####" carry no stream worth showing. */
    fun isSeparator(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.startsWith("###") || trimmed.startsWith("===") || trimmed.startsWith("***") ||
            trimmed.startsWith("---") || trimmed.startsWith("▬▬")
    }
}
