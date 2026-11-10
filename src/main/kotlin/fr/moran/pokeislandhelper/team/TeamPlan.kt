package fr.moran.pokeislandhelper.team

/** Clés de stats utilisées dans le JSON et les commandes. */
enum class StatKey(val key: String, val fr: String) {
    HP("hp", "PV"), ATK("atk", "Atq"), DEF("def", "Déf"),
    SPA("spa", "AtqS"), SPD("spd", "DéfS"), SPE("spe", "Vit");
}

/**
 * Un emplacement de l'équipe cible. Tous les champs sont optionnels : un champ vide = "peu importe".
 * Classes à champs mutables nullables pour que Gson les remplisse sans surprise.
 */
class PlanSlot {
    var species: String? = null
    var nature: String? = null
    var ability: String? = null
    var item: String? = null
    var ivs: MutableMap<String, Int>? = null
    var evs: MutableMap<String, Int>? = null
    var notes: String? = null

    fun ivsOrEmpty(): MutableMap<String, Int> = ivs ?: mutableMapOf<String, Int>().also { ivs = it }
    fun evsOrEmpty(): MutableMap<String, Int> = evs ?: mutableMapOf<String, Int>().also { evs = it }
}

class TeamPlan {
    var slots: MutableList<PlanSlot>? = null
    fun slotsOrEmpty(): MutableList<PlanSlot> = slots ?: mutableListOf<PlanSlot>().also { slots = it }
}

/**
 * Lit "31 31 31 x 31 31", "31/31/31/x/31/31" ou "252,0,0,4,0,252" (ordre PV Atq Déf AtqS DéfS Vit).
 * "x", "-" ou "*" = peu importe. Renvoie null si le format est faux.
 */
fun parseStatSpread(input: String, max: Int): Map<StatKey, Int?>? {
    val parts = input.trim().split(Regex("[\\s/,;]+")).filter { it.isNotEmpty() }
    if (parts.size != 6) return null
    val out = LinkedHashMap<StatKey, Int?>()
    for ((i, p) in parts.withIndex()) {
        val stat = StatKey.entries[i]
        if (p.lowercase() in setOf("x", "-", "*", "?")) {
            out[stat] = null
        } else {
            val v = p.toIntOrNull() ?: return null
            if (v < 0 || v > max) return null
            out[stat] = v
        }
    }
    return out
}
