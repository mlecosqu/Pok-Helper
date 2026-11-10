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

    override fun onInitializeClient() {
        ModConfig.load()

        keyTeam = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.team", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY))
        keyPanel = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY))
        keyHighlight = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.pokeislandhelper.highlight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY))

        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }

        // Panneau de combat : sur le HUD quand aucun écran n'est ouvert…
        HudRenderCallback.EVENT.register { g, _ ->
            if (Minecraft.getInstance().screen == null) BattlePanel.render(g)
        }
        // …et par-dessus l'interface de combat de Cobblemon quand elle est ouverte.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is BattleGUI) {
                ScreenEvents.afterRender(screen).register { _, g, _, _, _ -> BattlePanel.render(g) }
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
        while (keyPanel.consumeClick()) {
            BattlePanel.enabled = !BattlePanel.enabled
            toast(mc, "Panneau de combat : " + if (BattlePanel.enabled) "activé" else "désactivé")
        }
        while (keyHighlight.consumeClick()) {
            Highlight.enabled = !Highlight.enabled
            Highlight.clear()
            toast(mc, "Surbrillance Pokédex : " + if (Highlight.enabled) "activée" else "désactivée")
        }
    }

    private fun toast(mc: Minecraft, msg: String) {
        mc.player?.displayClientMessage(Component.literal(msg), true)
    }

    fun warnOnce(key: String, msg: String, t: Throwable) {
        if (warned.add(key)) log.warn("$msg (fonction désactivée jusqu'au prochain essai) : ${t.javaClass.simpleName}: ${t.message}", t)
    }
}
