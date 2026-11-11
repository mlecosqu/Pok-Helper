package fr.moran.pokeislandhelper.evo

import fr.moran.pokeislandhelper.battle.PType

/** Une condition d'évolution telle qu'écrite dans les fichiers de Cobblemon (champs simples seulement). */
data class EvoReq(val variant: String, val fields: Map<String, String>)

/** Une évolution : de l'espèce [fromId] (forme [fromAspects]) vers [result] ("espeon", "ninetales alolan"…). */
data class EvoEdge(
    val fromId: String,
    val fromAspects: Set<String>,
    val result: String,
    val variant: String,          // level_up, trade, item_interact, block_click…
    val context: String?,         // objet à utiliser (item_interact) ou bloc (block_click)
    val reqs: List<EvoReq>,
) {
    val resultSpecies: String get() = result.trim().split(" ").first().lowercase()
}

/** Traduit les identifiants en noms lisibles. Implémenté avec les traductions du jeu ; simulé dans les tests. */
interface Lexicon {
    fun species(id: String): String
    fun item(id: String): String
    fun move(id: String): String
    fun biome(id: String): String
}

/** État d'un Pokémon de l'équipe, pour savoir quelles conditions sont déjà remplies. */
data class MonState(
    val level: Int,
    val friendship: Int,
    val heldItem: String,          // "cobblemon:metal_coat" ou ""
    val moves: Set<String>,        // ids : "drainpunch"
    val moveTypes: Set<String>,    // "fairy"
    val gender: String,            // "male", "female", "genderless"
)

/** Un morceau de condition ; ok = true (remplie), false (pas encore), null (impossible à vérifier). */
data class EvoPart(val text: String, val ok: Boolean? = null)

/** Met les conditions d'évolution en français. Sans dépendance au jeu. */
object EvoText {

    fun describe(e: EvoEdge, lex: Lexicon): String = parts(e, lex, null).joinToString(", ") { it.text }

    fun parts(e: EvoEdge, lex: Lexicon, state: MonState?): List<EvoPart> {
        val parts = mutableListOf<EvoPart>()
        when (e.variant) {
            "trade" -> parts += EvoPart("échange")
            "item_interact" -> parts += EvoPart("utiliser " + (e.context?.let { tagOrItem(it, lex) } ?: "un objet"))
            "block_click" -> parts += EvoPart("clic sur " + (e.context?.let { tagOrItem(it, lex) } ?: "un bloc"))
            "level_up" -> {}
            else -> parts += EvoPart(e.variant)
        }
        for (r in e.reqs) {
            val text = req(r, lex) ?: continue
            parts += if (state == null) EvoPart(text) else progress(r, text, state)
        }
        if (parts.isEmpty()) parts += EvoPart("monter d'un niveau")
        return parts
    }

    /** Ajoute la progression (niv. 12/16, bonheur 120/160…) quand on peut la vérifier. */
    private fun progress(r: EvoReq, text: String, s: MonState): EvoPart {
        val f = r.fields
        fun num(k: String) = f[k]?.toIntOrNull()
        return when (r.variant) {
            "level" -> {
                val min = num("minLevel") ?: num("amount")
                val max = num("maxLevel")
                val ok = (min == null || s.level >= min) && (max == null || s.level <= max)
                EvoPart(if (ok || min == null) text else "niv. ${s.level}/$min", ok)
            }
            "friendship" -> {
                val need = num("amount") ?: return EvoPart(text)
                EvoPart(if (s.friendship >= need) text else "bonheur ${s.friendship}/$need", s.friendship >= need)
            }
            "held_item" -> {
                val want = f["itemCondition"] ?: return EvoPart(text)
                if (want.startsWith("#")) EvoPart(text) else EvoPart(text, want == s.heldItem)
            }
            "has_move" -> EvoPart(text, f["move"]?.let { norm(it) in s.moves.map(::norm) })
            "has_move_type" -> EvoPart(text, f["type"]?.lowercase()?.let { it in s.moveTypes })
            "properties" -> {
                val t = f["target"]?.lowercase() ?: return EvoPart(text)
                when {
                    "female" in t -> EvoPart(text, s.gender == "female")
                    "male" in t -> EvoPart(text, s.gender == "male")
                    else -> EvoPart(text)
                }
            }
            else -> EvoPart(text)
        }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun req(r: EvoReq, lex: Lexicon): String? {
        val f = r.fields
        return when (r.variant) {
            "level" -> {
                val min = f["minLevel"] ?: f["amount"]
                val max = f["maxLevel"]?.takeIf { it != "2147483647" && it != "100" }
                when {
                    min != null && max != null -> "niv. $min à $max"
                    min != null -> "niv. $min"
                    max != null -> "niv. $max max"
                    else -> null
                }
            }
            "friendship" -> "bonheur ≥ ${f["amount"] ?: "?"}"
            "time_range" -> when (f["range"]?.lowercase()) {
                "day" -> "le jour"; "night" -> "la nuit"; "dusk" -> "au crépuscule"; "dawn" -> "à l'aube"
                "morning" -> "le matin"; "noon" -> "à midi"; "evening" -> "le soir"; "midnight" -> "à minuit"
                null -> null
                else -> "moment : ${f["range"]}"
            }
            "held_item" -> f["itemCondition"]?.let { "en tenant ${tagOrItem(it, lex)}" }
            "biome" -> (f["biomeCondition"] ?: f["biomeAnticondition"])?.let {
                val name = if (it.startsWith("#")) readable(it) else lex.biome(it)
                if (f.containsKey("biomeAnticondition") && !f.containsKey("biomeCondition")) "hors biome $name" else "biome $name"
            }
            "weather" -> when {
                f["isThundering"] == "true" -> "pendant un orage"
                f["isRaining"] == "true" -> "sous la pluie"
                f["isRaining"] == "false" -> "sans pluie"
                else -> "météo particulière"
            }
            "has_move" -> f["move"]?.let { "connaît ${lex.move(it)}" }
            "has_move_type" -> f["type"]?.let { "connaît une attaque ${PType.parse(it)?.fr ?: it}" }
            "party_member" -> f["target"]?.let {
                val who = lex.species(it.split(" ").first())
                if (f["contains"] == "false") "sans $who dans l'équipe" else "avec $who dans l'équipe"
            }
            "attack_defence_ratio" -> when (f["ratio"]?.uppercase()) {
                "ATTACK_HIGHER" -> "Atq > Déf"; "DEFENCE_HIGHER" -> "Déf > Atq"; "EQUAL" -> "Atq = Déf"; else -> null
            }
            "stat_compare" -> if (f["highStat"] != null && f["lowStat"] != null)
                "${stat(f["highStat"]!!)} > ${stat(f["lowStat"]!!)}" else null
            "moon_phase" -> f["moonPhase"]?.let { moon(it) }
            "properties" -> f["target"]?.let { properties(it) }
            "defeat" -> "vaincre ${f["amount"] ?: "?"} × ${f["target"]?.let { lex.species(it.split(" ").first()) } ?: "Pokémon"}"
            "use_move" -> "utiliser ${f["move"]?.let(lex::move) ?: "une attaque"} ${f["amount"] ?: "?"} fois"
            "recoil" -> "subir ${f["amount"] ?: "?"} PV de contrecoup"
            "damage_taken" -> "perdre ${f["amount"] ?: "?"} PV sans être K.O."
            "blocks_traveled" -> "parcourir ${f["amount"] ?: "?"} blocs"
            "structure" -> (f["structureCondition"] ?: f["structureAnticondition"])?.let { "près de ${readable(it)}" }
            "world" -> f["identifier"]?.let { "dans ${readable(it)}" }
            "area" -> "dans une zone précise"
            "level_up" -> null
            else -> r.variant.replace('_', ' ')
        }
    }

    private fun tagOrItem(id: String, lex: Lexicon) = if (id.startsWith("#")) readable(id) else lex.item(id)

    /** "#cobblemon:is_magnetic" → "magnetic" ; "minecraft:the_nether" → "the nether". */
    fun readable(id: String): String = id.removePrefix("#").substringAfter(':').substringAfterLast('/')
        .removePrefix("is_").replace('_', ' ')

    private fun stat(s: String) = when (s.lowercase()) {
        "hp" -> "PV"; "attack" -> "Atq"; "defence", "defense" -> "Déf"
        "special_attack" -> "AtqS"; "special_defence", "special_defense" -> "DéfS"; "speed" -> "Vit"
        else -> s
    }

    private fun moon(p: String) = when (p.uppercase()) {
        "FULL_MOON" -> "pleine lune"; "NEW_MOON" -> "nouvelle lune"
        "FIRST_QUARTER" -> "premier quartier"; "THIRD_QUARTER", "LAST_QUARTER" -> "dernier quartier"
        else -> "lune : " + p.lowercase().replace('_', ' ')
    }

    private fun properties(t: String): String {
        val parts = t.split(" ").map { p ->
            when (p.lowercase()) {
                "gender=female", "female" -> "femelle"
                "gender=male", "male" -> "mâle"
                else -> p.replace('=', ' ')
            }
        }
        return parts.joinToString(" ")
    }
}

/** Sélection des évolutions qui s'appliquent à une espèce / forme donnée. */
object EvoLookup {
    fun forPokemon(all: List<EvoEdge>, speciesId: String, aspects: Set<String>): List<EvoEdge> {
        val mine = all.filter { it.fromId.substringAfter(':') == speciesId }
        // Une forme (ex. Alola) a ses propres évolutions : elles remplacent celles de la forme de base.
        val formSpecific = mine.filter { it.fromAspects.isNotEmpty() && aspects.containsAll(it.fromAspects) }
        return if (formSpecific.isNotEmpty()) formSpecific else mine.filter { it.fromAspects.isEmpty() }
    }

    fun preEvolutions(all: List<EvoEdge>, speciesId: String): List<EvoEdge> =
        all.filter { it.resultSpecies == speciesId }.distinctBy { it.fromId + it.fromAspects }
}
