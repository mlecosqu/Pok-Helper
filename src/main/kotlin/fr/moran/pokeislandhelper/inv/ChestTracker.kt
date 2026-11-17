package fr.moran.pokeislandhelper.inv

import com.google.gson.GsonBuilder
import fr.moran.pokeislandhelper.PokeIslandHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.nio.file.Files
import java.nio.file.Path

/**
 * Mémorise le contenu des coffres, tonneaux et boîtes de Shulker que tu ouvres.
 * Le serveur envoie déjà ce contenu au jeu quand tu ouvres un coffre : rien n'est demandé en plus.
 * Les menus de boutique du serveur (qui ressemblent à des coffres) sont ignorés :
 * on ne retient que les coffres posés devant toi.
 * Sauvegardé par serveur dans config/pokeislandhelper-chests/.
 */
object ChestTracker {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var store = ChestStore()
    private var storeFile: Path? = null
    var last: ChestRecord? = null; private set

    fun all(): Collection<ChestRecord> = store.chests.values

    /** À appeler à l'ouverture de chaque écran. */
    fun onScreenOpened(screen: Screen) {
        if (screen !is AbstractContainerScreen<*>) return
        val menu = screen.menu
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val hit = mc.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return
        val pos = hit.blockPos
        // Vrai coffre posé dans le monde (et pas un menu de boutique ouvert en regardant un bloc)
        if (level.getBlockEntity(pos) !is Container) return
        val canonical = canonicalPos(pos)
        val dim = level.dimension().location().toString()
        val title = screen.title.string

        ScreenEvents.remove(screen).register {
            try {
                snapshot(screen, dim, canonical, title)
            } catch (t: Throwable) {
                PokeIslandHelper.warnOnce("chest", "Lecture du coffre impossible", t)
            }
        }
    }

    /** Un coffre double a deux moitiés : on prend toujours la même pour ne pas le compter deux fois. */
    private fun canonicalPos(pos: BlockPos): BlockPos {
        val state = Minecraft.getInstance().level?.getBlockState(pos) ?: return pos
        if (state.block !is ChestBlock || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos
        val other = pos.relative(ChestBlock.getConnectedDirection(state))
        return if (other.asLong() < pos.asLong()) other else pos
    }

    private fun snapshot(screen: AbstractContainerScreen<*>, dim: String, pos: BlockPos, title: String) {
        val items = mutableListOf<ItemCount>()
        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue // ton inventaire, pas le coffre
            val stack = slot.item
            if (stack.isEmpty) continue
            add(items, stack, stack.count)
            // Contenu des boîtes de Shulker rangées dans le coffre
            stack.get(DataComponents.CONTAINER)?.nonEmptyItems()?.forEach { inner -> add(items, inner, inner.count) }
        }
        val rec = ChestRecord().apply {
            key = "$dim|${pos.x},${pos.y},${pos.z}"
            label = title
            this.dim = dim
            x = pos.x; y = pos.y; z = pos.z
            updated = System.currentTimeMillis()
            this.items = InvFormat.aggregate(listOf(items)).toMutableList()
        }
        ensureLoaded()
        store.chests[rec.key] = rec
        last = rec
        save()
    }

    private fun add(list: MutableList<ItemCount>, stack: ItemStack, count: Int) {
        list += ItemCount().apply {
            id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            name = stack.hoverName.string
            this.count = count
        }
    }

    fun forget() {
        ensureLoaded()
        store.chests.clear()
        last = null
        save()
    }

    // ---------- Sauvegarde (un fichier par serveur) ----------

    private fun currentFile(): Path {
        val server = Minecraft.getInstance().currentServer?.ip ?: "solo"
        val safe = server.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return FabricLoader.getInstance().configDir.resolve("pokeislandhelper-chests").resolve("$safe.json")
    }

    fun ensureLoaded() {
        val file = currentFile()
        if (file == storeFile) return
        storeFile = file
        store = try {
            if (Files.exists(file)) Files.newBufferedReader(file, Charsets.UTF_8).use { gson.fromJson(it, ChestStore::class.java) } ?: ChestStore()
            else ChestStore()
        } catch (e: Exception) { ChestStore() }
        last = null
    }

    private fun save() {
        val file = storeFile ?: return
        Files.createDirectories(file.parent)
        Files.newBufferedWriter(file, Charsets.UTF_8).use { gson.toJson(store, it) }
    }

    /** Écrit les exports dans config/ et renvoie le chemin du dossier. */
    fun writeExports(discordText: String): Path {
        val dir = FabricLoader.getInstance().configDir
        Files.writeString(dir.resolve("pokeislandhelper-inventaire.csv"), "﻿" + InvFormat.csv(all()), Charsets.UTF_8)
        Files.writeString(dir.resolve("pokeislandhelper-inventaire-discord.txt"), discordText, Charsets.UTF_8)
        return dir
    }
}
