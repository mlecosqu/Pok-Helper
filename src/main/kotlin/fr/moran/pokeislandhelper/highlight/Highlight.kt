package fr.moran.pokeislandhelper.highlight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import fr.moran.pokeislandhelper.CobbleBridge
import fr.moran.pokeislandhelper.CobbleBridge.DexState
import fr.moran.pokeislandhelper.config.ModConfig
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity

/**
 * Décide si un Pokémon sauvage doit briller (contour coloré) car il manque au Pokédex.
 * Appelé à chaque image pour chaque entité : le résultat est mis en cache 10 ticks.
 */
object Highlight {
    var enabled: Boolean
        get() = ModConfig.settings.highlightEnabled
        set(v) { ModConfig.settings.highlightEnabled = v; ModConfig.saveSettings() }

    private data class Cached(val tick: Long, val color: Int?)
    private val cache = HashMap<Int, Cached>()
    private const val REFRESH_TICKS = 10L

    /** Couleur du contour, ou null si le Pokémon ne doit pas briller. */
    @JvmStatic
    fun glowColor(entity: Entity): Int? {
        if (entity !is PokemonEntity || !enabled) return null
        if (!entity.level().isClientSide) return null // serveur intégré (solo) : on ne touche à rien
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val now = level.gameTime
        val hit = cache[entity.id]
        if (hit != null && now - hit.tick < REFRESH_TICKS) return hit.color
        val color = compute(entity, mc)
        cache[entity.id] = Cached(now, color)
        if (cache.size > 512) cache.entries.removeIf { now - it.value.tick > 200 }
        return color
    }

    private fun compute(entity: PokemonEntity, mc: Minecraft): Int? {
        val s = ModConfig.settings
        val player = mc.player ?: return null
        if (!CobbleBridge.isWild(entity)) return null
        if (player.distanceToSqr(entity) > s.maxDistance * s.maxDistance) return null
        if (s.requireLineOfSight && !player.hasLineOfSight(entity)) return null
        val id = CobbleBridge.speciesIdOf(entity) ?: return null
        return when (CobbleBridge.dexState(id)) {
            DexState.UNKNOWN -> ModConfig.parseColor(s.colorUnknown, 0xFF4040)
            DexState.SEEN -> if (s.highlightSeenNotCaught) ModConfig.parseColor(s.colorSeen, 0xFFD000) else null
            DexState.CAUGHT -> null
        }
    }

    fun clear() = cache.clear()
}
