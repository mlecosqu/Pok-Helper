package fr.moran.pokeislandhelper.evo

import fr.moran.pokeislandhelper.CobbleBridge
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Écran "Évolutions de l'équipe" (touche J ou /pevo) : pour chaque Pokémon de ton équipe,
 * ses évolutions possibles et où il en est (✔ remplie, ✘ pas encore, sans marque = à vérifier toi-même).
 */
class EvoScreen : Screen(Component.literal("Évolutions de l'équipe")) {
    private data class Line(val text: Component, val indent: Int)
    private var lines: List<Line> = emptyList()
    private var scroll = 0

    override fun init() {
        refresh()
        addRenderableWidget(Button.builder(Component.literal("Actualiser")) { refresh() }
            .bounds(width / 2 - 105, height - 24, 100, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Fermer")) { onClose() }
            .bounds(width / 2 + 5, height - 24, 100, 20).build())
    }

    private fun refresh() {
        lines = buildLines()
        scroll = 0
    }

    private fun buildLines(): List<Line> {
        if (!EvoIndex.ready) return listOf(Line(Component.literal("Évolutions en cours de chargement… clique sur Actualiser."), 0))
        val party = CobbleBridge.partyForEvolution()
        if (party.isEmpty()) return listOf(Line(Component.literal("Équipe vide ou illisible."), 0))
        val lex = CobbleBridge.lexicon
        val out = mutableListOf<Line>()
        for (m in party) {
            out += Line(Component.literal("${m.slot}. ${m.name}").withStyle { it.withBold(true).withColor(0xFFD000) }
                .append(Component.literal("  niv. ${m.state.level} · bonheur ${m.state.friendship}").withStyle { it.withBold(false).withColor(0xAAAAAA) }), 0)
            val evos = EvoLookup.forPokemon(EvoIndex.all(), m.speciesId, m.aspects)
            if (evos.isEmpty()) {
                out += Line(Component.literal("N'évolue pas").withStyle { it.withColor(0x888888) }, 12)
            }
            for (e in evos) {
                val parts = EvoText.parts(e, lex, m.state)
                val ready = parts.all { it.ok == true }
                val line = Component.literal("→ ${lex.species(e.resultSpecies)} : ")
                    .withStyle { it.withColor(if (ready) 0x55FF55 else 0xC080FF) }
                parts.forEachIndexed { i, p ->
                    if (i > 0) line.append(Component.literal(", ").withStyle { it.withColor(0xAAAAAA) })
                    val (mark, color) = when (p.ok) { true -> "✔ " to 0x55FF55; false -> "✘ " to 0xFF6666; null -> "" to 0xFFFFFF }
                    line.append(Component.literal(mark + p.text).withStyle { it.withColor(color) })
                }
                out += Line(line, 12)
            }
            out += Line(Component.empty(), 0)
        }
        return out
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll = (scroll - scrollY.toInt() * 3).coerceIn(0, maxOf(0, lines.size - visibleLines()))
        return true
    }

    private fun visibleLines() = maxOf(1, (height - 30 - 24) / 11)

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF)
        var y = 26
        for (l in lines.drop(scroll).take(visibleLines())) {
            g.drawString(font, l.text, 16 + l.indent, y, 0xFFFFFF, true)
            y += 11
        }
    }

    override fun isPauseScreen(): Boolean = false
}
