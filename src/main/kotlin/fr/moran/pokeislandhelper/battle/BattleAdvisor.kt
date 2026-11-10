package fr.moran.pokeislandhelper.battle

/**
 * Logique pure du conseil en combat : profil de faiblesses de l'adversaire
 * et classement des Pokémon de l'équipe face à lui. Aucune dépendance au jeu.
 */
object BattleAdvisor {

    data class Attacker(val name: String, val types: List<PType>, val moves: List<Pair<String, PType>>, val fainted: Boolean)

    data class Pick(
        val name: String,
        val bestMove: String?,     // null = calcul sur les types (attaques inconnues)
        val bestType: PType?,
        val offense: Double,       // multiplicateur, STAB compris
        val rawEff: Double,        // efficacité du type seule
        val worstTaken: Double,    // pire multiplicateur subi si l'adversaire attaque avec ses propres types
    )

    fun pick(a: Attacker, foe: List<PType>): Pick {
        val options: List<Pair<String?, PType>> =
            if (a.moves.isNotEmpty()) a.moves.map { it.first to it.second } else a.types.map { null to it }
        val best = options.maxByOrNull { (_, t) -> score(t, a.types, foe) }
        val worstTaken = foe.maxOfOrNull { TypeChart.effectiveness(it, a.types) } ?: 1.0
        return if (best == null) Pick(a.name, null, null, 1.0, 1.0, worstTaken)
        else Pick(a.name, best.first, best.second, score(best.second, a.types, foe),
            TypeChart.effectiveness(best.second, foe), worstTaken)
    }

    private fun score(t: PType, own: List<PType>, foe: List<PType>): Double =
        TypeChart.effectiveness(t, foe) * (if (t in own) 1.5 else 1.0)

    /** Classe l'équipe : meilleure attaque d'abord, puis celui qui encaisse le mieux. */
    fun rank(team: List<Attacker>, foe: List<PType>): List<Pick> =
        team.filter { !it.fainted }
            .map { pick(it, foe) }
            .sortedWith(compareByDescending<Pick> { it.offense / maxOf(it.worstTaken, 0.25) }
                .thenByDescending { it.offense })

    fun formatMult(m: Double): String = when (m) {
        0.0 -> "×0"
        0.25 -> "×¼"
        0.5 -> "×½"
        else -> if (m == Math.floor(m)) "×${m.toInt()}" else "×${"%.2f".format(m).trimEnd('0').trimEnd('.', ',')}"
    }
}
