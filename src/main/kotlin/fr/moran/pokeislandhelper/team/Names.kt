package fr.moran.pokeislandhelper.team

import java.text.Normalizer

/** Comparaison tolérante des noms : "Peau Dure", "peau_dure", "peaudure" et "PEAU-DURE" sont égaux. */
object Names {
    fun key(s: String?): String {
        if (s == null) return ""
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    /** Natures françaises → identifiant Cobblemon (anglais). */
    private val natureFr: Map<String, String> = mapOf(
        "hardi" to "hardy", "solo" to "lonely", "brave" to "brave", "rigide" to "adamant",
        "mauvais" to "naughty", "assure" to "bold", "docile" to "docile", "relax" to "relaxed",
        "malin" to "impish", "lache" to "lax", "timide" to "timid", "presse" to "hasty",
        "serieux" to "serious", "jovial" to "jolly", "naif" to "naive", "modeste" to "modest",
        "doux" to "mild", "discret" to "quiet", "pudique" to "bashful", "foufou" to "rash",
        "calme" to "calm", "gentil" to "gentle", "malpoli" to "sassy", "prudent" to "careful",
        "bizarre" to "quirky",
    )
    private val natureEn = natureFr.values.toSet()

    /** Renvoie l'id anglais d'une nature écrite en français ou en anglais, ou null si inconnue. */
    fun natureId(input: String?): String? {
        val k = key(input)
        if (k.isEmpty()) return null
        return natureFr[k] ?: k.takeIf { it in natureEn }
    }

    /** Vrai si [wanted] correspond à l'un des noms possibles (id, nom traduit...). */
    fun matches(wanted: String?, vararg candidates: String?): Boolean {
        val w = key(wanted)
        if (w.isEmpty()) return true
        return candidates.any { key(it) == w }
    }
}
