package fr.moran.pokeislandhelper.battle

import fr.moran.pokeislandhelper.CobbleBridge
import fr.moran.pokeislandhelper.config.ModConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/**
 * Panneau affiché pendant un combat (raids compris) :
 * types de l'adversaire, ses faiblesses / résistances, et les meilleurs Pokémon de ton équipe contre lui.
 */
object BattlePanel {
    var enabled: Boolean
        get() = ModConfig.settings.battlePanelEnabled
        set(v) { ModConfig.settings.battlePanelEnabled = v; ModConfig.saveSettings() }

    /** Type(s) forcé(s) pour l'adversaire, ex. type Téracristal d'un boss de raid. Remis à zéro en fin de combat. */
    var typeOverride: List<PType>? = null

    private const val PAD = 4
    private const val LINE = 10

    /** Pokémon regardé hors combat (mis à jour à chaque tick). */
    private var lookedAt: CobbleBridge.Foe? = null
    private var wasInBattle = false

    fun onTick() {
        val inBattle = CobbleBridge.inBattle()
        if (wasInBattle && !inBattle) typeOverride = null // fin de combat : on oublie le type forcé
        wasInBattle = inBattle
        val s = ModConfig.settings
        lookedAt = if (!inBattle && enabled && s.lookPanel) CobbleBridge.lookedAtFoe(s.lookDistance) else null
    }

    fun render(g: GuiGraphics) {
        if (!enabled) return
        val foes = if (CobbleBridge.inBattle()) CobbleBridge.currentFoes() else listOfNotNull(lookedAt)
        if (foes.isEmpty()) return
        val lines = buildLines(foes)
        if (lines.isEmpty()) return

        val mc = Minecraft.getInstance()
        val font = mc.font
        val s = ModConfig.settings
        val sw = g.guiWidth()
        val width = minOf(sw / 2, lines.maxOf { font.width(it) } + PAD * 2)
        val height = lines.size * LINE + PAD * 2 - 1
        val x = if (s.battlePanelRight) sw - width - 4 else 4
        val y = s.battlePanelY

        g.fill(x, y, x + width, y + height, 0xC0101018.toInt())
        g.fill(x, y, x + width, y + 1, 0xFF5A5AFF.toInt())
        var ly = y + PAD
        g.enableScissor(x, y, x + width, y + height)
        for (line in lines) {
            g.drawString(font, line, x + PAD, ly, 0xFFFFFFFF.toInt(), true)
            ly += LINE
        }
        g.disableScissor()
    }

    private fun buildLines(foes: List<CobbleBridge.Foe>): List<Component> {
        val out = mutableListOf<Component>()
        val override = typeOverride
        val team = CobbleBridge.partyForBattle().map {
            BattleAdvisor.Attacker(it.name, it.types, it.damagingMoves.map { m -> m.name to m.type }, it.fainted)
        }

        for (foe in foes.take(2)) {
            val types = override ?: foe.types
            out += Component.literal(foe.name).withStyle { it.withBold(true) }
                .append(Component.literal("  ")).append(typeList(types))
                .append(if (override != null) Component.literal(" (forcé)").withStyle { it.withColor(0xAAAAAA) } else Component.empty())

            val profile = TypeChart.defensiveProfile(types)
            for ((mult, label, color) in listOf(
                Triple(4.0, "Faible ×4 ", 0xFF5555), Triple(2.0, "Faible ×2 ", 0xFFAA55),
                Triple(0.5, "Résiste ×½ ", 0x88CCFF), Triple(0.25, "Résiste ×¼ ", 0x5599FF),
                Triple(0.0, "Immunisé ", 0xAAAAAA),
            )) {
                val ts = profile[mult] ?: continue
                out += Component.literal(label).withStyle { it.withColor(color) }.append(typeList(ts))
            }

            val picks = BattleAdvisor.rank(team, types).take(ModConfig.settings.recommendations)
            if (picks.isNotEmpty()) {
                out += Component.literal("À envoyer :").withStyle { it.withColor(0x55FF55).withUnderlined(true) }
                for (p in picks) {
                    val atk = p.bestType?.let { t ->
                        val what = p.bestMove ?: t.fr
                        Component.literal(" $what ").append(colored(BattleAdvisor.formatMult(p.rawEff), multColor(p.rawEff)))
                    } ?: Component.empty()
                    out += Component.literal("• ${p.name}").append(atk)
                        .append(Component.literal(" | subit ").withStyle { it.withColor(0xAAAAAA) })
                        .append(colored(BattleAdvisor.formatMult(p.worstTaken), takenColor(p.worstTaken)))
                }
            }
        }
        return out
    }

    private fun typeList(types: List<PType>): MutableComponent {
        val c = Component.empty()
        types.forEachIndexed { i, t ->
            if (i > 0) c.append(Component.literal(" "))
            c.append(Component.literal(t.fr).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(t.color))))
        }
        return c
    }

    private fun colored(text: String, color: Int) =
        Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))

    private fun multColor(m: Double) = when {
        m >= 2.0 -> 0x55FF55
        m >= 1.0 -> 0xFFFFFF
        m > 0.0 -> 0xFFAA55
        else -> 0xFF5555
    }

    private fun takenColor(m: Double) = when {
        m >= 2.0 -> 0xFF5555
        m >= 1.0 -> 0xFFFFFF
        else -> 0x55FF55
    }
}
