package ie.adrianszydlo.navitunes.ui.library

import ie.adrianszydlo.navitunes.data.api.GenreEntry

/**
 * Compound-genre categorisation. Navidrome/beets emit many fine-grained genre tags
 * ("melodic death metal", "uk garage", "chamber pop", …). V0.6 groups them into a
 * handful of broad, browsable **categories** for a saner genre experience and as the
 * basis for future smart recommendations.
 *
 * Categorisation is keyword-based on the lowercased genre string; order matters
 * (more specific keywords first, so "metal" isn't swallowed by "meta"-anything and
 * "hard rock" lands in Rock, "post-rock" in Rock, etc.). The generic `Music` tag that
 * spotdl writes when it can't determine a genre is treated as **Uncategorized**.
 */
object GenreCategories {

    const val UNCATEGORIZED = "Uncategorized"

    /** Ordered category → keyword list. First matching category wins. */
    private val RULES: List<Pair<String, List<String>>> = listOf(
        "Hip-Hop & Rap" to listOf("hip hop", "hip-hop", "rap", "trap", "drill", "grime", "boom bap"),
        "Electronic & Dance" to listOf(
            "electro", "electronic", "house", "techno", "trance", "edm", "dubstep",
            "drum and bass", "drum & bass", "dnb", "garage", "synthwave", "ambient",
            "idm", "downtempo", "breakbeat", "hardstyle", "future bass", "dance",
            "disco", "eurodance", "chill", "big room", "progressive house", "nu-disco"
        ),
        "Metal" to listOf("metal", "metalcore", "deathcore", "thrash", "grindcore", "djent"),
        "Punk" to listOf("punk", "hardcore", "emo", "screamo"),
        "Rock" to listOf("rock", "grunge", "shoegaze", "britpop"),
        "R&B & Soul" to listOf("r&b", "rnb", "r & b", "soul", "funk", "motown", "neo soul", "neo-soul"),
        "Jazz" to listOf("jazz", "swing", "bebop", "bossa", "big band"),
        "Blues" to listOf("blues"),
        "Classical" to listOf("classical", "orchestra", "orchestral", "baroque", "symphony", "opera", "chamber", "romantic era"),
        "Country" to listOf("country", "bluegrass", "americana", "honky"),
        "Folk & Acoustic" to listOf("folk", "singer-songwriter", "singer/songwriter", "acoustic"),
        "Reggae" to listOf("reggae", "ska", "dub", "dancehall"),
        "Latin" to listOf("latin", "reggaeton", "salsa", "bachata", "cumbia", "bossa nova", "samba"),
        "Soundtrack" to listOf("soundtrack", "score", "film", "cinematic", "video game", "musical"),
        "Pop" to listOf("pop", "k-pop", "kpop", "j-pop", "synth-pop", "synthpop"),
        "Indie & Alternative" to listOf("indie", "alternative", "alt-", "lo-fi", "lofi"),
        "World" to listOf("world", "afrobeat", "afro", "celtic", "flamenco", "arabic", "indian", "traditional")
    )

    /** The broad category a raw genre belongs to. */
    fun categoryFor(genre: String): String {
        val g = genre.trim().lowercase()
        if (g.isBlank() || g == "music" || g == "unknown" || g == "other") return UNCATEGORIZED
        for ((category, keywords) in RULES) {
            if (keywords.any { g.contains(it) }) return category
        }
        return UNCATEGORIZED
    }

    /** A browsable category: its display name, aggregate song count, and member genres. */
    data class Category(
        val name: String,
        val songCount: Int,
        val genres: List<String>
    )

    /**
     * Collapses raw [genres] into categories. [Category.songCount] is the sum of member
     * genres' counts, but note a song tagged with several genres is counted once per
     * genre — so the sum over-counts and is *not* shown as a track total (the UI shows
     * the genre count instead). Sorted alphabetically, with Uncategorized always last.
     */
    fun categorize(genres: List<GenreEntry>): List<Category> {
        val grouped = genres
            .filter { it.value.isNotBlank() }
            .groupBy { categoryFor(it.value) }
        return grouped.map { (name, entries) ->
            Category(
                name = name,
                songCount = entries.sumOf { it.songCount },
                genres = entries.map { it.value }
            )
        }.sortedWith(
            compareBy<Category> { it.name == UNCATEGORIZED }.thenBy { it.name.lowercase() }
        )
    }
}
