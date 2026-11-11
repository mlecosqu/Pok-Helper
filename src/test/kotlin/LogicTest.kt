import fr.moran.pokeislandhelper.battle.BattleAdvisor
import fr.moran.pokeislandhelper.battle.PType
import fr.moran.pokeislandhelper.battle.PType.*
import fr.moran.pokeislandhelper.battle.TypeChart
import fr.moran.pokeislandhelper.team.*
import fr.moran.pokeislandhelper.evo.*
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

    private val lex = object : Lexicon {
        val names = mapOf("espeon" to "Mentali", "leafeon" to "Phyllali", "scizor" to "Cizayox", "sylveon" to "Nymphali",
            "ninetales" to "Feunard", "mantyke" to "Babimanta", "remoraid" to "Rémoraid")
        override fun species(id: String) = names[id] ?: id
        override fun item(id: String) = mapOf("cobblemon:leaf_stone" to "Pierre Plante", "cobblemon:metal_coat" to "Peau Métal",
            "cobblemon:ice_stone" to "Pierre Glace")[id] ?: id
        override fun move(id: String) = id
        override fun biome(id: String) = id
    }

    @Test fun evolutionTexts() {
        val espeon = EvoEdge("cobblemon:eevee", emptySet(), "espeon", "level_up", null,
            listOf(EvoReq("friendship", mapOf("amount" to "160")), EvoReq("time_range", mapOf("range" to "day"))))
        assertEquals("bonheur ≥ 160, le jour", EvoText.describe(espeon, lex))
        val leafeon = EvoEdge("cobblemon:eevee", emptySet(), "leafeon", "item_interact", "cobblemon:leaf_stone", emptyList())
        assertEquals("utiliser Pierre Plante", EvoText.describe(leafeon, lex))
        val scizor = EvoEdge("cobblemon:scyther", emptySet(), "scizor", "trade", null,
            listOf(EvoReq("held_item", mapOf("itemCondition" to "cobblemon:metal_coat"))))
        assertEquals("échange, en tenant Peau Métal", EvoText.describe(scizor, lex))
        val sylveon = EvoEdge("cobblemon:eevee", emptySet(), "sylveon", "level_up", null,
            listOf(EvoReq("has_move_type", mapOf("type" to "fairy"))))
        assertEquals("connaît une attaque Fée", EvoText.describe(sylveon, lex))
        val lvl = EvoEdge("cobblemon:bulbasaur", emptySet(), "ivysaur", "level_up", null, listOf(EvoReq("level", mapOf("minLevel" to "16"))))
        assertEquals("niv. 16", EvoText.describe(lvl, lex))
        val mantine = EvoEdge("cobblemon:mantyke", emptySet(), "mantine", "level_up", null,
            listOf(EvoReq("party_member", mapOf("target" to "remoraid", "contains" to "true"))))
        assertEquals("avec Rémoraid dans l'équipe", EvoText.describe(mantine, lex))
        assertEquals("magnetic", EvoText.readable("#cobblemon:is_magnetic"))
    }

    @Test fun evolutionForms() {
        val all = listOf(
            EvoEdge("cobblemon:vulpix", emptySet(), "ninetales", "item_interact", "cobblemon:fire_stone", emptyList()),
            EvoEdge("cobblemon:vulpix", setOf("alolan"), "ninetales alolan", "item_interact", "cobblemon:ice_stone", emptyList()),
        )
        assertEquals("cobblemon:fire_stone", EvoLookup.forPokemon(all, "vulpix", emptySet()).single().context)
        assertEquals("cobblemon:ice_stone", EvoLookup.forPokemon(all, "vulpix", setOf("alolan", "male")).single().context)
        assertEquals("ninetales", all[1].resultSpecies)
        assertEquals(2, EvoLookup.preEvolutions(all, "ninetales").size)
    }

    @Test fun evolutionProgress() {
        val state = MonState(level = 12, friendship = 170, heldItem = "cobblemon:metal_coat",
            moves = setOf("drainpunch"), moveTypes = setOf("fighting"), gender = "female")
        val lvl = EvoEdge("cobblemon:bulbasaur", emptySet(), "ivysaur", "level_up", null, listOf(EvoReq("level", mapOf("minLevel" to "16"))))
        assertEquals(listOf(EvoPart("niv. 12/16", false)), EvoText.parts(lvl, lex, state))
        val espeon = EvoEdge("cobblemon:eevee", emptySet(), "espeon", "level_up", null,
            listOf(EvoReq("friendship", mapOf("amount" to "160")), EvoReq("time_range", mapOf("range" to "day"))))
        assertEquals(listOf(EvoPart("bonheur ≥ 160", true), EvoPart("le jour", null)), EvoText.parts(espeon, lex, state))
        val scizor = EvoEdge("cobblemon:scyther", emptySet(), "scizor", "trade", null,
            listOf(EvoReq("held_item", mapOf("itemCondition" to "cobblemon:metal_coat"))))
        assertEquals(true, EvoText.parts(scizor, lex, state)[1].ok)
        val sylveon = EvoEdge("cobblemon:eevee", emptySet(), "sylveon", "level_up", null, listOf(EvoReq("has_move_type", mapOf("type" to "fairy"))))
        assertEquals(false, EvoText.parts(sylveon, lex, state).single().ok)
    }
}
