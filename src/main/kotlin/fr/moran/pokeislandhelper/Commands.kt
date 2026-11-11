package fr.moran.pokeislandhelper

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import fr.moran.pokeislandhelper.battle.BattlePanel
import fr.moran.pokeislandhelper.battle.PType
import fr.moran.pokeislandhelper.config.ModConfig
import fr.moran.pokeislandhelper.evo.EvoIndex
import fr.moran.pokeislandhelper.evo.EvoLookup
import fr.moran.pokeislandhelper.evo.EvoText
import fr.moran.pokeislandhelper.team.Names
import fr.moran.pokeislandhelper.team.PlanSlot
import fr.moran.pokeislandhelper.team.StatKey
import fr.moran.pokeislandhelper.team.parseStatSpread
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Commandes côté client (le serveur ne les voit pas) :
 *  /pteam ...  → plan d'équipe
 *  /praid ...  → réglages du panneau de combat
 */
private typealias Ctx = CommandContext<FabricClientCommandSource>

object Commands {
    fun register(d: CommandDispatcher<FabricClientCommandSource>) {
        d.register(literal("pteam")
            .executes { PokeIslandHelper.openTeamScreenNextTick = true; 1 }
            .then(literal("list").executes(::list))
            .then(literal("add").then(argument("espece", StringArgumentType.greedyString()).executes(::add)))
            .then(literal("remove").then(slotArg().executes { edit(it, "retiré") { _ -> null } }))
            .then(literal("clear").executes { ModConfig.team.slotsOrEmpty().clear(); ModConfig.saveTeam(); ok(it, "Plan vidé.") })
            .then(literal("nature").then(slotArg().then(argument("nature", StringArgumentType.greedyString()).executes(::nature))))
            .then(literal("talent").then(slotArg().then(argument("talent", StringArgumentType.greedyString()).executes { c ->
                val v = StringArgumentType.getString(c, "talent")
                edit(c, "talent = $v") { s -> s.ability = v; s }
            })))
            .then(literal("objet").then(slotArg().then(argument("objet", StringArgumentType.greedyString()).executes { c ->
                val v = StringArgumentType.getString(c, "objet")
                edit(c, "objet = $v") { s -> s.item = v; s }
            })))
            .then(literal("iv").then(slotArg().then(argument("valeurs", StringArgumentType.greedyString()).executes { spread(it, isIv = true) })))
            .then(literal("ev").then(slotArg().then(argument("valeurs", StringArgumentType.greedyString()).executes { spread(it, isIv = false) })))
            .then(literal("reload").executes { ModConfig.load(); ok(it, "Fichiers rechargés.") })
        )

        d.register(literal("pevo")
            .executes { PokeIslandHelper.openEvoScreenNextTick = true; 1 }
            .then(argument("espece", StringArgumentType.greedyString()).executes(::evo)))

        d.register(literal("praid")
            .then(literal("tera").then(argument("types", StringArgumentType.greedyString()).executes(::tera)))
            .then(literal("reset").executes { BattlePanel.typeOverride = null; ok(it, "Types de l'adversaire : automatiques.") })
            .then(literal("debug").executes(::debug))
        )
    }

    /** Diagnostic : ce que le mod voit du combat en cours (à lancer pendant un raid). */
    private fun debug(c: Ctx): Int {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val foes = CobbleBridge.currentFoes()
        val lines = listOf(
            "Combat Cobblemon détecté : ${if (CobbleBridge.inBattle()) "oui" else "non"}",
            "Panneau activé : ${if (BattlePanel.enabled) "oui" else "non"}",
            "Écran ouvert : ${mc.screen?.javaClass?.name ?: "aucun"}",
            "Adversaires lus : ${foes.size} " + foes.joinToString { "${it.name} (${it.types.joinToString("/") { t -> t.fr }})" },
            "Pokémon dans l'équipe : ${CobbleBridge.partyForBattle().size}",
        )
        lines.forEach { c.source.sendFeedback(Component.literal(it).withStyle(ChatFormatting.AQUA)) }
        return 1
    }

    /** /pevo <espèce> : comment elle évolue, et de quoi elle évolue. */
    private fun evo(c: Ctx): Int {
        if (!EvoIndex.ready) return err(c, "Évolutions encore en chargement, réessaie dans quelques secondes.")
        val input = StringArgumentType.getString(c, "espece")
        val id = CobbleBridge.findSpeciesId(input) ?: return err(c, "Espèce inconnue : $input")
        val lex = CobbleBridge.lexicon
        val all = EvoIndex.all()
        c.source.sendFeedback(Component.literal(lex.species(id)).withStyle(ChatFormatting.GOLD))
        val pre = EvoLookup.preEvolutions(all, id)
        pre.forEach { e ->
            val form = if (e.fromAspects.isEmpty()) "" else " (${e.fromAspects.joinToString()})"
            c.source.sendFeedback(Component.literal("← évolue de ${lex.species(e.fromId.substringAfter(':'))}$form : ${EvoText.describe(e, lex)}")
                .withStyle(ChatFormatting.GRAY))
        }
        val next = all.filter { it.fromId.substringAfter(':') == id }
        if (next.isEmpty()) c.source.sendFeedback(Component.literal("N'évolue pas.").withStyle(ChatFormatting.GRAY))
        next.forEach { e ->
            val form = if (e.fromAspects.isEmpty()) "" else " [forme ${e.fromAspects.joinToString()}]"
            c.source.sendFeedback(Component.literal("→ ${lex.species(e.resultSpecies)}$form : ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(EvoText.describe(e, lex)).withStyle(ChatFormatting.WHITE)))
        }
        return 1
    }

    private fun slotArg() = argument("n", IntegerArgumentType.integer(1, 6))

    private fun list(c: Ctx): Int {
        val slots = ModConfig.team.slotsOrEmpty()
        if (slots.isEmpty()) return ok(c, "Plan vide. /pteam add <espèce>")
        slots.forEachIndexed { i, s ->
            val iv = s.ivs?.let { m -> StatKey.entries.joinToString("/") { m[it.key]?.toString() ?: "x" } }
            val ev = s.evs?.let { m -> StatKey.entries.joinToString("/") { m[it.key]?.toString() ?: "x" } }
            val parts = listOfNotNull(
                s.nature?.let { "Nature $it" }, s.ability?.let { "Talent $it" }, s.item?.let { "Objet $it" },
                iv?.let { "IV $it" }, ev?.let { "EV $it" },
            )
            c.source.sendFeedback(Component.literal("${i + 1}. ${s.species}  ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(parts.joinToString(" · ")).withStyle(ChatFormatting.GRAY)))
        }
        return 1
    }

    private fun add(c: Ctx): Int {
        val slots = ModConfig.team.slotsOrEmpty()
        if (slots.size >= 6) return err(c, "Ton plan a déjà 6 Pokémon. Retire-en un avec /pteam remove <n>.")
        val input = StringArgumentType.getString(c, "espece")
        val name = CobbleBridge.findSpeciesName(input) ?: return err(c, "Espèce inconnue : $input")
        slots += PlanSlot().apply { species = name }
        ModConfig.saveTeam()
        return ok(c, "$name ajouté en position ${slots.size}.")
    }

    private fun nature(c: Ctx): Int {
        val v = StringArgumentType.getString(c, "nature")
        if (Names.natureId(v) == null) return err(c, "Nature inconnue : $v (ex. Jovial, Rigide, Timide, Modeste…)")
        return edit(c, "nature = $v") { s -> s.nature = v; s }
    }

    private fun spread(c: Ctx, isIv: Boolean): Int {
        val raw = StringArgumentType.getString(c, "valeurs")
        val values = parseStatSpread(raw, if (isIv) 31 else 252)
            ?: return err(c, "Format : 6 valeurs PV Atq Déf AtqS DéfS Vit, ex. ${if (isIv) "31 31 31 x 31 31" else "0 252 0 0 4 252"} (x = peu importe)")
        if (!isIv) {
            val total = values.values.filterNotNull().sum()
            if (total > 510) return err(c, "Total d'EV = $total, le maximum est 510.")
        }
        val label = if (isIv) "IV" else "EV"
        return edit(c, "$label = ${values.values.joinToString("/") { it?.toString() ?: "x" }}") { s ->
            val m = if (isIv) s.ivsOrEmpty() else s.evsOrEmpty()
            m.clear()
            values.forEach { (k, v) -> if (v != null) m[k.key] = v }
            s
        }
    }

    private fun tera(c: Ctx): Int {
        val raw = StringArgumentType.getString(c, "types")
        val types = raw.split(Regex("[\\s/,]+")).filter { it.isNotBlank() }.map { PType.parse(it) ?: return err(c, "Type inconnu : $it") }
        if (types.isEmpty() || types.size > 2) return err(c, "Donne 1 ou 2 types, ex. /praid tera Acier")
        BattlePanel.typeOverride = types
        return ok(c, "Adversaire traité comme ${types.joinToString("/") { it.fr }} jusqu'à la fin du prochain combat (ou /praid reset).")
    }

    /** Modifie l'emplacement n ; renvoyer null supprime l'emplacement. */
    private fun edit(c: Ctx, what: String, change: (PlanSlot) -> PlanSlot?): Int {
        val n = IntegerArgumentType.getInteger(c, "n")
        val slots = ModConfig.team.slotsOrEmpty()
        if (n > slots.size) return err(c, "Pas de Pokémon en position $n (le plan en a ${slots.size}).")
        val result = change(slots[n - 1])
        val name = slots[n - 1].species
        if (result == null) slots.removeAt(n - 1)
        ModConfig.saveTeam()
        return ok(c, "$n. $name : $what")
    }

    private fun ok(c: Ctx, msg: String): Int {
        c.source.sendFeedback(Component.literal(msg).withStyle(ChatFormatting.GREEN)); return 1
    }

    private fun err(c: Ctx, msg: String): Int {
        c.source.sendError(Component.literal(msg)); return 0
    }
}
