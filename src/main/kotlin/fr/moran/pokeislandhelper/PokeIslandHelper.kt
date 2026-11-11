package fr.moran.pokeislandhelper

import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.mojang.blaze3d.platform.InputConstants
import fr.moran.pokeislandhelper.battle.BattlePanel
import fr.moran.pokeislandhelper.config.ModConfig
import fr.moran.pokeislandhelper.highlight.Highlight
import fr.moran.pokeislandhelper.team.TeamScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object PokeIslandHelper : ClientModInitializer {
    private val log = LoggerFactory.getLogger("PokeIsland Helper")
    private val warned = mutableSetOf<String>()

    @Volatile var openTeamScreenNextTick = false

    private const val CATEGORY = "key.categories.pokeislandhelper"
    private lateinit var keyTeam: KeyMapping
    private lateinit var keyPanel: KeyMapping
    private lateinit var keyHighlight: KeyMapping
    private lateinit var keyEvo: KeyMapping
    @Volatile var openEvoScreenNextTick = false

    override fun onInitializeClient() {
        ModConfig.load()
        fr.moran.pokeislandhelper.evo.EvoIndex.loadAsync()

        keyTeam = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.team", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY))
        keyPanel = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY))
        keyHighlight = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.highlight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY))

        keyEvo = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.evo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY))

        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }

        // Panneau de combat : sur le HUD quand aucun écran n'est ouvert…
        HudRenderCallback.EVENT.register { g, _ ->
            if (Minecraft.getInstance().screen == null) BattlePanel.render(g)
        }
        // …et par-dessus n'importe quel écran pendant un combat (interface de combat Cobblemon,
        // écrans de raid ajoutés par le serveur…). La touche du panneau y marche aussi.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenEvents.afterRender(screen).register { _, g, _, _, _ ->
                if (screen is BattleGUI || CobbleBridge.inBattle()) BattlePanel.render(g)
            }
            ScreenKeyboardEvents.afterKeyPress(screen).register { _, key, scancode, _ ->
                if (screen !is ChatScreen && CobbleBridge.inBattle() && keyPanel.matches(key, scancode)) togglePanel()
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> Commands.register(dispatcher) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> Highlight.clear(); BattlePanel.typeOverride = null }

        log.info("PokeIsland Helper chargé")
    }

    private fun onTick(mc: Minecraft) {
        BattlePanel.onTick()
        if (openTeamScreenNextTick) {
            openTeamScreenNextTick = false
            mc.setScreen(TeamScreen())
        }
        while (keyTeam.consumeClick()) mc.setScreen(TeamScreen())
        if (openEvoScreenNextTick) {
            openEvoScreenNextTick = false
            mc.setScreen(fr.moran.pokeislandhelper.evo.EvoScreen())
        }
        while (keyEvo.consumeClick()) mc.setScreen(fr.moran.pokeislandhelper.evo.EvoScreen())
        while (keyPanel.consumeClick()) togglePanel()
        while (keyHighlight.consumeClick()) {
            Highlight.enabled = !Highlight.enabled
            Highlight.clear()
            toast(mc, "Surbrillance Pokédex : " + if (Highlight.enabled) "activée" else "désactivée")
        }
    }

    private fun togglePanel() {
        BattlePanel.enabled = !BattlePanel.enabled
        toast(Minecraft.getInstance(), "Panneau de combat : " + if (BattlePanel.enabled) "activé" else "désactivé")
    }

    private fun toast(mc: Minecraft, msg: String) {
        mc.player?.displayClientMessage(Component.literal(msg), true)
    }

    fun warnOnce(key: String, msg: String, t: Throwable) {
        if (warned.add(key)) log.warn("$msg (fonction désactivée jusqu'au prochain essai) : ${t.javaClass.simpleName}: ${t.message}", t)
    }
}
