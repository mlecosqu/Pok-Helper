package fr.moran.pokeislandhelper.team

import fr.moran.pokeislandhelper.CobbleBridge
import fr.moran.pokeislandhelper.config.ModConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Écran "Équipe cible" : pour chaque emplacement du plan, le Pokémon correspondant de l'équipe
 * et la liste des critères (nature, talent, objet, IV, EV) avec ✔ / ✘.
 */
class TeamScreen : Screen(Component.literal("Équipe cible")) {
    private var reports: List<SlotReport> = emptyList()

    override fun init() {
        refresh()
        addRenderableWidget(Button.builder(Component.literal("Actualiser")) { refresh() }
            .bounds(width / 2 - 105, height - 24, 100, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Fermer")) { onClose() }
            .bounds(width / 2 + 5, height - 24, 100, 20).build())
    }

    private fun refresh() {
        reports = TeamChecker.check(ModConfig.team, CobbleBridge.partySnapshots())
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF)

        if (reports.isEmpty()) {
            val help = listOf(
                "Aucun Pokémon dans ton plan.",
                "Ajoute-en avec :  /pteam add Carchacrok",
                "puis  /pteam nature 1 Jovial   /pteam talent 1 Peau Dure",
                "/pteam iv 1 31 31 31 x 31 31   /pteam ev 1 0 252 0 0 4 252",
                "(ou modifie config/pokeislandhelper-team.json)",
            )
            help.forEachIndexed { i, l -> g.drawCenteredString(font, l, width / 2, 40 + i * 12, 0xCCCCCC) }
            return
        }

        val cols = 2
        val gap = 8
        val lineH = 10
        val cardH = reports.maxOf { 2 + maxOf((it.checks.size + 1) / 2, 1) } * lineH + 8
        val rows = (reports.size + cols - 1) / cols
        val available = height - 20 - 30
        val scale = minOf(1f, available.toFloat() / (rows * (cardH + gap)))

        g.pose().pushPose()
        g.pose().translate(0f, 20f, 0f)
        g.pose().scale(scale, scale, 1f)
        val scaledW = (width / scale).toInt()
        val cw = (scaledW - gap * (cols + 1)) / cols

        reports.forEachIndexed { i, r ->
            val cx = gap + (i % cols) * (cw + gap)
            val cy = (i / cols) * (cardH + gap)
            drawCard(g, r, cx, cy, cw, cardH, lineH)
        }
        g.pose().popPose()
    }

    private fun drawCard(g: GuiGraphics, r: SlotReport, x: Int, y: Int, w: Int, h: Int, lineH: Int) {
        val border = when {
            r.mon == null -> 0xFF777777.toInt()
            r.complete -> 0xFF33CC55.toInt()
            else -> 0xFFDD9933.toInt()
        }
        g.fill(x, y, x + w, y + h, 0xB0000000.toInt())
        g.renderOutline(x, y, w, h, border)

        val name = r.mon?.speciesName ?: r.plan.species ?: "?"
        val status = when {
            r.mon == null -> "absent de l'équipe"
            r.total == 0 -> "aucun critère"
            else -> "${r.done}/${r.total} (équipe n°${r.mon.partySlot})"
        }
        g.drawString(font, "${r.index}. $name", x + 4, y + 4, 0xFFFFFF, true)
        g.drawString(font, status, x + w - 4 - font.width(status), y + 4, border and 0xFFFFFF, true)

        var ly = y + 4 + lineH + 2
        if (r.mon == null) {
            val wanted = buildList {
                r.plan.nature?.let { add("Nature $it") }
                r.plan.ability?.let { add("Talent $it") }
                r.plan.item?.let { add("Objet $it") }
            }.joinToString(" · ")
            if (wanted.isNotEmpty()) g.drawString(font, wanted, x + 4, ly, 0xAAAAAA, false)
            return
        }
        // Critères en deux colonnes pour gagner de la place
        val colW = (w - 8) / 2
        r.checks.forEachIndexed { i, c ->
            val cx = x + 4 + (i % 2) * colW
            val cy = ly + (i / 2) * lineH
            val mark = if (c.ok) "✔" else "✘"
            val color = if (c.ok) 0x55FF55 else 0xFF6666
            g.drawString(font, "$mark ${c.label}: ${c.detail}", cx, cy, color, false)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
