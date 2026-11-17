package fr.moran.pokeislandhelper.inv

/** Un type d'objet et sa quantité. */
class ItemCount {
    var id: String = ""          // "minecraft:gunpowder"
    var name: String = ""        // nom affiché ("Poudre à canon")
    var count: Int = 0
}

/** Le contenu d'un coffre tel qu'il était la dernière fois que tu l'as fermé. */
class ChestRecord {
    var key: String = ""         // "minecraft:overworld|12,64,-30"
    var label: String = ""       // titre du coffre ("Coffre", ou son nom donné à l'enclume)
    var dim: String = ""
    var x: Int = 0
    var y: Int = 0
    var z: Int = 0
    var updated: Long = 0
    var items: MutableList<ItemCount> = mutableListOf()

    fun where(): String = "$label ($x, $y, $z)"
}

class ChestStore {
    var chests: MutableMap<String, ChestRecord> = linkedMapOf()
}

/** Calculs et mises en forme, sans dépendance au jeu (testables). */
object InvFormat {

    /** Additionne les objets identiques (même id et même nom). */
    fun aggregate(lists: Iterable<List<ItemCount>>): List<ItemCount> {
        val map = LinkedHashMap<String, ItemCount>()
        for (list in lists) for (it in list) {
            val k = it.id + "|" + it.name
            val cur = map.getOrPut(k) { ItemCount().apply { id = it.id; name = it.name } }
            cur.count += it.count
        }
        return map.values.sortedWith(compareBy<ItemCount> { it.name.lowercase() })
    }

    /** "3 stacks + 12" pour les gros volumes, pratique pour les ventes. */
    fun qty(n: Int): String = when {
        n >= 128 && n % 64 == 0 -> "$n (${n / 64} stacks)"
        n >= 128 -> "$n (${n / 64} stacks + ${n % 64})"
        else -> "$n"
    }

    /**
     * Texte prêt à coller sur Discord, découpé en messages de moins de [maxLen] caractères
     * (Discord refuse les messages de plus de 2000 caractères).
     */
    fun discord(title: String, items: List<ItemCount>, footer: String? = null, maxLen: Int = 1900): List<String> {
        val lines = items.map { "• ${qty(it.count)} × ${it.name}" }
        val out = mutableListOf<String>()
        var cur = StringBuilder("**$title**\n")
        for (l in lines) {
            if (cur.length + l.length + 1 > maxLen) {
                out += cur.toString().trimEnd()
                cur = StringBuilder("**$title (suite)**\n")
            }
            cur.append(l).append('\n')
        }
        if (footer != null) {
            if (cur.length + footer.length + 2 > maxLen) { out += cur.toString().trimEnd(); cur = StringBuilder() }
            cur.append('\n').append(footer)
        }
        out += cur.toString().trimEnd()
        return out
    }

    /** Export tableur : une ligne par objet et par coffre (séparateur ; pour Excel en français). */
    fun csv(chests: Collection<ChestRecord>): String {
        val sb = StringBuilder("Coffre;X;Y;Z;Dimension;Objet;Identifiant;Quantité\n")
        for (c in chests) for (it in c.items) {
            sb.append(listOf(c.label, c.x, c.y, c.z, c.dim, it.name, it.id, it.count).joinToString(";") { v ->
                v.toString().replace(";", ",").replace("\n", " ")
            }).append('\n')
        }
        return sb.toString()
    }

    /** Coffres qui contiennent un objet dont le nom ou l'id contient [query]. */
    fun search(chests: Collection<ChestRecord>, query: String): List<Pair<ChestRecord, ItemCount>> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        return chests.flatMap { c ->
            c.items.filter { norm(it.name).contains(q) || norm(it.id).contains(q) }.map { c to it }
        }.sortedByDescending { it.second.count }
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase()
}
