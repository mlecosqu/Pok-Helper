package fr.moran.pokeislandhelper

import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import fr.moran.pokeislandhelper.battle.PType
import fr.moran.pokeislandhelper.team.MonSnapshot
import fr.moran.pokeislandhelper.team.Names
import fr.moran.pokeislandhelper.team.StatKey
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Tous les appels à l'API de Cobblemon sont ici. Si PokeIsland passe à une autre version de
 * Cobblemon et que quelque chose casse, c'est (normalement) le seul fichier à adapter.
 * Chaque appel est protégé : une erreur désactive la fonction concernée au lieu de faire planter le jeu.
 */
object CobbleBridge {

    enum class DexState { UNKNOWN, SEEN, CAUGHT }

    // ---------- Pokédex ----------

    fun dexState(speciesId: ResourceLocation): DexState = try {
        // NONE (<1.8) ou UNREGISTERED (1.8+) / ENCOUNTERED / CAUGHT : comparé par nom pour couvrir les deux.
        when (CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(speciesId).name) {
            "CAUGHT" -> DexState.CAUGHT
            "ENCOUNTERED" -> DexState.SEEN
            else -> DexState.UNKNOWN
        }
    } catch (t: Throwable) {
        PokeIslandHelper.warnOnce("dex", "Lecture du Pokédex impossible", t)
        DexState.CAUGHT // en cas d'erreur, on ne surligne rien
    }

    fun isWild(entity: PokemonEntity): Boolean = try {
        entity.ownerUUID == null
    } catch (t: Throwable) {
        false
    }

    fun speciesIdOf(entity: PokemonEntity): ResourceLocation? = try {
        entity.pokemon.species.resourceIdentifier
    } catch (t: Throwable) {
        null
    }

    // ---------- Combat ----------

    data class Foe(
        val name: String,
        val types: List<PType>,
        val speciesId: String = "",
        val aspects: Set<String> = emptySet(),
        val own: Boolean = false,      // un de tes Pokémon
    )

    /** Pokémon adverses actifs dans le combat en cours (vide hors combat). */
    fun currentFoes(): List<Foe> = try {
        val battle = CobblemonClient.battle
        val me = Minecraft.getInstance().player?.uuid
        if (battle == null || me == null) emptyList()
        else {
            val mySide1 = battle.side1.actors.any { it.uuid == me }
            val mySide2 = battle.side2.actors.any { it.uuid == me }
            val foeSide = when {
                mySide1 -> battle.side2
                mySide2 -> battle.side1
                else -> battle.side2 // spectateur : on montre le côté 2
            }
            foeSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon?.let(::foeOf) }
        }
    } catch (t: Throwable) {
        PokeIslandHelper.warnOnce("battle", "Lecture du combat impossible", t)
        emptyList()
    }

    private fun foeOf(p: ClientBattlePokemon): Foe? {
        val form: FormData = p.species.getForm(p.properties.aspects)
        val types = listOfNotNull(form.primaryType, form.secondaryType).mapNotNull { PType.parse(it.name) }
        if (types.isEmpty()) return null
        return Foe(p.displayName.string, types)
    }

    fun inBattle(): Boolean = try { CobblemonClient.battle != null } catch (t: Throwable) { false }

    /**
     * Pokémon visé au viseur, jusqu'à [maxDist] blocs (bien plus loin que la portée normale, pour
     * les boss de raid). Un bloc entre toi et lui bloque la visée. Tes propres Pokémon sont ignorés.
     */
    fun lookedAtFoe(maxDist: Double): Foe? = try {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null || mc.level == null) null
        else {
            val eye = player.getEyePosition(1f)
            val end = eye.add(player.getViewVector(1f).scale(maxDist))
            val box = player.boundingBox.expandTowards(end.subtract(eye)).inflate(1.0)
            val hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                { e -> e is PokemonEntity && !e.isSpectator }, maxDist * maxDist)
            val entity = hit?.entity as? PokemonEntity
            if (entity == null) null
            else {
                val blockDist = player.pick(maxDist, 1f, false).location.distanceToSqr(eye)
                if (blockDist < hit.location.distanceToSqr(eye)) null
                else {
                    val form = entity.pokemon.form
                    val types = listOfNotNull(form.primaryType, form.secondaryType).mapNotNull { PType.parse(it.name) }
                    val aspects = runCatching { entity.pokemon.aspects.toSet() }.getOrDefault(emptySet())
                    if (types.isEmpty()) null else Foe(entity.pokemon.species.translatedName.string, types,
                        entity.pokemon.species.resourceIdentifier.path, aspects, entity.ownerUUID == player.uuid)
                }
            }
        }
    } catch (t: Throwable) {
        PokeIslandHelper.warnOnce("look", "Lecture du Pokémon visé impossible", t)
        null
    }

    // ---------- Équipe ----------

    data class MoveInfo(val name: String, val type: PType)
    data class PartyMon(val slot: Int, val name: String, val types: List<PType>, val damagingMoves: List<MoveInfo>, val fainted: Boolean)

    // Le nom de l'équipe change selon la version de Cobblemon (party / myParty) : on essaie les deux.
    private fun party(): List<Pair<Int, Pokemon>> = try {
        val storage: Any = CobblemonClient.storage
        val partyObj = listOf("getParty", "getMyParty").firstNotNullOfOrNull { name ->
            runCatching { storage.javaClass.getMethod(name).invoke(storage) }.getOrNull()
        } ?: error("équipe introuvable")
        val slots: List<Any?> = runCatching { partyObj.javaClass.getMethod("getSlots").invoke(partyObj) as List<*> }
            .getOrElse { (partyObj as Iterable<*>).toList() }
        slots.mapIndexedNotNull { i, p -> (p as? Pokemon)?.let { i to it } }
    } catch (t: Throwable) {
        PokeIslandHelper.warnOnce("party", "Lecture de l'équipe impossible", t)
        emptyList()
    }

    fun partyForBattle(): List<PartyMon> = party().mapNotNull { (i, p) ->
        try {
            val types = listOfNotNull(p.form.primaryType, p.form.secondaryType).mapNotNull { PType.parse(it.name) }
            val moves = p.moveSet.getMoves()
                .filter { it.template.damageCategory != DamageCategories.STATUS }
                .mapNotNull { m -> PType.parse(m.template.elementalType.name)?.let { MoveInfo(m.displayName.string, it) } }
            PartyMon(i + 1, p.species.translatedName.string, types, moves, p.currentHealth <= 0)
        } catch (t: Throwable) {
            null
        }
    }

    private val statMap: Map<StatKey, Stat> = mapOf(
        StatKey.HP to Stats.HP, StatKey.ATK to Stats.ATTACK, StatKey.DEF to Stats.DEFENCE,
        StatKey.SPA to Stats.SPECIAL_ATTACK, StatKey.SPD to Stats.SPECIAL_DEFENCE, StatKey.SPE to Stats.SPEED,
    )

    fun partySnapshots(): List<MonSnapshot> = party().mapNotNull { (i, p) ->
        try {
            val item = p.heldItem()
            val itemId = if (item.isEmpty) "" else BuiltInRegistries.ITEM.getKey(item.item).path
            MonSnapshot(
                partySlot = i + 1,
                speciesId = p.species.resourceIdentifier.path,
                speciesName = p.species.translatedName.string,
                natureId = p.effectiveNature.name.path,
                natureName = Component.translatable(p.effectiveNature.displayName).string,
                abilityId = p.ability.name,
                abilityName = Component.translatable(p.ability.displayName).string,
                itemId = itemId,
                itemName = if (item.isEmpty) "" else item.hoverName.string,
                ivs = statMap.mapValues { (_, s) -> p.ivs[s] ?: 0 },
                evs = statMap.mapValues { (_, s) -> p.evs[s] ?: 0 },
            )
        } catch (t: Throwable) {
            PokeIslandHelper.warnOnce("snapshot", "Lecture des stats impossible", t)
            null
        }
    }

    data class PartyEvoMon(val slot: Int, val name: String, val speciesId: String, val aspects: Set<String>,
                           val state: fr.moran.pokeislandhelper.evo.MonState)

    /** Équipe avec ce qu'il faut pour suivre les évolutions (niveau, bonheur, objet tenu, attaques). */
    fun partyForEvolution(): List<PartyEvoMon> = party().mapNotNull { (i, p) ->
        try {
            val item = p.heldItem()
            val moves = runCatching { p.moveSet.getMoves() }.getOrDefault(emptyList())
            PartyEvoMon(
                slot = i + 1,
                name = p.species.translatedName.string,
                speciesId = p.species.resourceIdentifier.path,
                aspects = runCatching { p.aspects.toSet() }.getOrDefault(emptySet()),
                state = fr.moran.pokeislandhelper.evo.MonState(
                    level = p.level,
                    friendship = p.friendship,
                    heldItem = if (item.isEmpty) "" else BuiltInRegistries.ITEM.getKey(item.item).toString(),
                    moves = moves.map { it.template.name }.toSet(),
                    moveTypes = moves.map { it.template.elementalType.name.lowercase() }.toSet(),
                    gender = p.gender.name.lowercase(),
                ),
            )
        } catch (t: Throwable) {
            PokeIslandHelper.warnOnce("partyevo", "Lecture de l'équipe (évolutions) impossible", t)
            null
        }
    }

    /** Id d'espèce ("eevee") à partir d'un nom anglais ou traduit ("Évoli"), ou null. */
    fun findSpeciesId(input: String): String? = try {
        (PokemonSpecies.getByName(Names.key(input))
            ?: PokemonSpecies.species.firstOrNull { Names.key(it.translatedName.string) == Names.key(input) })
            ?.resourceIdentifier?.path
    } catch (t: Throwable) {
        null
    }

    /** Noms traduits utilisés pour décrire les évolutions. */
    val lexicon = object : fr.moran.pokeislandhelper.evo.Lexicon {
        override fun species(id: String) = runCatching { PokemonSpecies.getByName(id)?.translatedName?.string }
            .getOrNull() ?: id
        override fun item(id: String): String = runCatching {
            val rl = ResourceLocation.tryParse(id) ?: return id
            if (!BuiltInRegistries.ITEM.containsKey(rl)) return fr.moran.pokeislandhelper.evo.EvoText.readable(id)
            BuiltInRegistries.ITEM.get(rl).description.string
        }.getOrDefault(id)
        override fun move(id: String): String = translated("cobblemon.move.$id") ?: id
        override fun biome(id: String): String {
            val rl = ResourceLocation.tryParse(id) ?: return id
            return translated("biome.${rl.namespace}.${rl.path}") ?: fr.moran.pokeislandhelper.evo.EvoText.readable(id)
        }
        private fun translated(key: String): String? =
            Component.translatable(key).string.takeIf { it != key }
    }

    /** Vérifie qu'une espèce existe (nom anglais ou traduit). Renvoie son nom affiché, ou null. */
    fun findSpeciesName(input: String): String? = try {
        PokemonSpecies.getByName(Names.key(input))?.translatedName?.string
            ?: PokemonSpecies.species.firstOrNull { Names.key(it.translatedName.string) == Names.key(input) }
                ?.translatedName?.string
    } catch (t: Throwable) {
        input // on ne bloque pas si la recherche échoue
    }
}