import fr.moran.pokeislandhelper.battle.BattleAdvisor
import fr.moran.pokeislandhelper.battle.PType
import fr.moran.pokeislandhelper.battle.PType.*
import fr.moran.pokeislandhelper.battle.TypeChart
import fr.moran.pokeislandhelper.team.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogicTest {
    @Test fun typeChartMatchesOfficialTotals() {
        var se = 0; var nve = 0; var imm = 0
        for (a in PType.entries) for (d in PType.entries) when (TypeChart.effectiveness(a, listOf(d))) {
            2.0 -> se++; 0.5 -> nve++; 0.0 -> imm++
        }
        assertEquals(51, se); assertEquals(61, nve); assertEquals(8, imm)
    }

    @Test fun dualTypes() {
        assertEquals(4.0, TypeChart.effectiveness(ROCK, listOf(FIRE, FLYING)))
        assertEquals(4.0, TypeChart.effectiveness(ICE, listOf(DRAGON, GROUND)))
        assertEquals(0.25, TypeChart.effectiveness(FIRE, listOf(WATER, DRAGON)))
    }

    @Test fun frenchNames() {
        assertEquals(ELECTRIC, PType.parse("Électrik"))
        assertEquals("jolly", Names.natureId("Jovial"))
        assertNull(Names.natureId("Super"))
        assertTrue(Names.matches("peau-dure", "roughskin", "Peau Dure"))
    }

    @Test fun spreads() {
        assertEquals(31, parseStatSpread("31/31/31/x/31/31", 31)?.get(StatKey.SPE))
        assertNull(parseStatSpread("31 31 31 32 31 31", 31))
    }

    @Test fun teamCheck() {
        val plan = TeamPlan().apply { slotsOrEmpty() += PlanSlot().apply {
            species = "Carchacrok"; nature = "Jovial"; ivs = mutableMapOf("spe" to 31) } }
        val mon = MonSnapshot(1, "garchomp", "Carchacrok", "adamant", "Rigide", "roughskin", "Peau Dure", "", "",
            StatKey.entries.associateWith { 31 }, emptyMap())
        val r = TeamChecker.check(plan, listOf(mon)).single()
        assertEquals(1, r.done); assertEquals(2, r.total)
    }

    @Test fun ranking() {
        val team = listOf(
            BattleAdvisor.Attacker("Florizarre", listOf(GRASS, POISON), listOf("Tranch'Herbe" to GRASS), false),
            BattleAdvisor.Attacker("Carchacrok", listOf(DRAGON, GROUND), listOf("Séisme" to GROUND), false),
        )
        assertEquals("Carchacrok", BattleAdvisor.rank(team, listOf(STEEL, FAIRY)).first().name)
    }
}
