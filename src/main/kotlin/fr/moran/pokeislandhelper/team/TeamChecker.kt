package fr.moran.pokeislandhelper.team

/** Photo d'un Pokémon de l'équipe, extraite de Cobblemon par CobbleBridge (testable sans le jeu). */
data class MonSnapshot(
    val partySlot: Int,
    val speciesId: String,          // "garchomp"
    val speciesName: String,        // nom traduit ("Carchacrok")
    val natureId: String,           // "jolly"
    val natureName: String,         // nom traduit
    val abilityId: String,          // "roughskin"
    val abilityName: String,        // nom traduit
    val itemId: String,             // "choice_scarf" ou ""
    val itemName: String,
    val ivs: Map<StatKey, Int>,
    val evs: Map<StatKey, Int>,
)

data class Check(val label: String, val ok: Boolean, val detail: String)

data class SlotReport(
    val index: Int,
    val plan: PlanSlot,
    val mon: MonSnapshot?,
    val checks: List<Check>,
) {
    val done: Int get() = checks.count { it.ok }
    val total: Int get() = checks.size
    val complete: Boolean get() = mon != null && checks.all { it.ok }
}

object TeamChecker {

    fun speciesMatches(plan: PlanSlot, mon: MonSnapshot): Boolean =
        Names.matches(plan.species, mon.speciesId, mon.speciesName)

    /** Associe chaque emplacement du plan au premier Pokémon de la même espèce non encore utilisé. */
    fun check(plan: TeamPlan, party: List<MonSnapshot>): List<SlotReport> {
        val used = mutableSetOf<Int>()
        return plan.slotsOrEmpty().mapIndexed { i, slot ->
            val mon = party.firstOrNull { it.partySlot !in used && speciesMatches(slot, it) }
            if (mon != null) used += mon.partySlot
            SlotReport(i + 1, slot, mon, if (mon == null) emptyList() else checksFor(slot, mon))
        }
    }

    fun checksFor(slot: PlanSlot, mon: MonSnapshot): List<Check> {
        val checks = mutableListOf<Check>()

        slot.nature?.takeIf { it.isNotBlank() }?.let { wanted ->
            val wantedId = Names.natureId(wanted)
            val ok = if (wantedId != null) wantedId == Names.key(mon.natureId)
                     else Names.matches(wanted, mon.natureId, mon.natureName)
            checks += Check("Nature", ok, if (ok) mon.natureName else "${mon.natureName} → $wanted")
        }
        slot.ability?.takeIf { it.isNotBlank() }?.let { wanted ->
            val ok = Names.matches(wanted, mon.abilityId, mon.abilityName)
            checks += Check("Talent", ok, if (ok) mon.abilityName else "${mon.abilityName} → $wanted")
        }
        slot.item?.takeIf { it.isNotBlank() }?.let { wanted ->
            val ok = Names.matches(wanted, mon.itemId, mon.itemName)
            val have = mon.itemName.ifBlank { "aucun" }
            checks += Check("Objet", ok, if (ok) have else "$have → $wanted")
        }
        slot.ivs?.let { targets -> checks += statChecks("IV", targets, mon.ivs) }
        slot.evs?.let { targets -> checks += statChecks("EV", targets, mon.evs) }
        return checks
    }

    private fun statChecks(prefix: String, targets: Map<String, Int>, actual: Map<StatKey, Int>): List<Check> =
        StatKey.entries.mapNotNull { stat ->
            val want = targets[stat.key] ?: return@mapNotNull null
            val have = actual[stat] ?: 0
            val ok = have == want
            Check("$prefix ${stat.fr}", ok, if (ok) "$have" else "$have → $want")
        }
}
