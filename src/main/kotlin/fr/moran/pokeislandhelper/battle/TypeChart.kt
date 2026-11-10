package fr.moran.pokeislandhelper.battle

import fr.moran.pokeislandhelper.battle.PType.*

/**
 * Table des types (Gen 6+), sans dépendance à Minecraft pour pouvoir être testée seule.
 * `id` correspond au nom des ElementalType de Cobblemon ("fire", "water"...).
 */
enum class PType(val id: String, val fr: String, val color: Int) {
    NORMAL("normal", "Normal", 0xA8A77A),
    FIRE("fire", "Feu", 0xEE8130),
    WATER("water", "Eau", 0x6390F0),
    ELECTRIC("electric", "Électrik", 0xF7D02C),
    GRASS("grass", "Plante", 0x7AC74C),
    ICE("ice", "Glace", 0x96D9D6),
    FIGHTING("fighting", "Combat", 0xC22E28),
    POISON("poison", "Poison", 0xA33EA1),
    GROUND("ground", "Sol", 0xE2BF65),
    FLYING("flying", "Vol", 0xA98FF3),
    PSYCHIC("psychic", "Psy", 0xF95587),
    BUG("bug", "Insecte", 0xA6B91A),
    ROCK("rock", "Roche", 0xB6A136),
    GHOST("ghost", "Spectre", 0x735797),
    DRAGON("dragon", "Dragon", 0x6F35FC),
    DARK("dark", "Ténèbres", 0x705746),
    STEEL("steel", "Acier", 0xB7B7CE),
    FAIRY("fairy", "Fée", 0xD685AD);

    companion object {
        private val byKey: Map<String, PType> = buildMap {
            for (t in PType.entries) {
                put(normalizeKey(t.id), t)
                put(normalizeKey(t.fr), t)
            }
        }

        /** Accepte l'id Cobblemon, le nom anglais ou le nom français ("électrik", "tenebres"...). */
        fun parse(name: String?): PType? = name?.let { byKey[normalizeKey(it)] }

        private fun normalizeKey(s: String): String =
            java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase()
                .filter { it.isLetterOrDigit() }
    }
}

object TypeChart {
    private fun row(vararg pairs: Pair<List<PType>, Double>): Map<PType, Double> =
        buildMap { for ((types, m) in pairs) for (t in types) put(t, m) }

    private val chart: Map<PType, Map<PType, Double>> = mapOf(
                NORMAL to row(listOf(ROCK, STEEL) to 0.5, listOf(GHOST) to 0.0),
                FIRE to row(listOf(GRASS, ICE, BUG, STEEL) to 2.0, listOf(FIRE, WATER, ROCK, DRAGON) to 0.5),
                WATER to row(listOf(FIRE, GROUND, ROCK) to 2.0, listOf(WATER, GRASS, DRAGON) to 0.5),
                ELECTRIC to row(listOf(WATER, FLYING) to 2.0, listOf(ELECTRIC, GRASS, DRAGON) to 0.5, listOf(GROUND) to 0.0),
                GRASS to row(listOf(WATER, GROUND, ROCK) to 2.0, listOf(FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL) to 0.5),
                ICE to row(listOf(GRASS, GROUND, FLYING, DRAGON) to 2.0, listOf(FIRE, WATER, ICE, STEEL) to 0.5),
                FIGHTING to row(listOf(NORMAL, ICE, ROCK, DARK, STEEL) to 2.0, listOf(POISON, FLYING, PSYCHIC, BUG, FAIRY) to 0.5, listOf(GHOST) to 0.0),
                POISON to row(listOf(GRASS, FAIRY) to 2.0, listOf(POISON, GROUND, ROCK, GHOST) to 0.5, listOf(STEEL) to 0.0),
                GROUND to row(listOf(FIRE, ELECTRIC, POISON, ROCK, STEEL) to 2.0, listOf(GRASS, BUG) to 0.5, listOf(FLYING) to 0.0),
                FLYING to row(listOf(GRASS, FIGHTING, BUG) to 2.0, listOf(ELECTRIC, ROCK, STEEL) to 0.5),
                PSYCHIC to row(listOf(FIGHTING, POISON) to 2.0, listOf(PSYCHIC, STEEL) to 0.5, listOf(DARK) to 0.0),
                BUG to row(listOf(GRASS, PSYCHIC, DARK) to 2.0, listOf(FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL, FAIRY) to 0.5),
                ROCK to row(listOf(FIRE, ICE, FLYING, BUG) to 2.0, listOf(FIGHTING, GROUND, STEEL) to 0.5),
                GHOST to row(listOf(PSYCHIC, GHOST) to 2.0, listOf(DARK) to 0.5, listOf(NORMAL) to 0.0),
                DRAGON to row(listOf(DRAGON) to 2.0, listOf(STEEL) to 0.5, listOf(FAIRY) to 0.0),
                DARK to row(listOf(PSYCHIC, GHOST) to 2.0, listOf(FIGHTING, DARK, FAIRY) to 0.5),
                STEEL to row(listOf(ICE, ROCK, FAIRY) to 2.0, listOf(FIRE, WATER, ELECTRIC, STEEL) to 0.5),
                FAIRY to row(listOf(FIGHTING, DRAGON, DARK) to 2.0, listOf(FIRE, POISON, STEEL) to 0.5),
            )

    /** Multiplicateur d'une attaque de type [attack] contre un défenseur de types [defender]. */
    fun effectiveness(attack: PType, defender: Collection<PType>): Double =
        defender.fold(1.0) { acc, d -> acc * (chart[attack]?.get(d) ?: 1.0) }

    /** Les 18 types attaquants regroupés par multiplicateur (4, 2, 1, 0.5, 0.25, 0). */
    fun defensiveProfile(defender: Collection<PType>): Map<Double, List<PType>> =
        PType.entries.groupBy { effectiveness(it, defender) }
}
