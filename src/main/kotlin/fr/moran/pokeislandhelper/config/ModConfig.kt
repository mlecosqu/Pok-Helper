package fr.moran.pokeislandhelper.config

import com.google.gson.GsonBuilder
import fr.moran.pokeislandhelper.team.TeamPlan
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/** Réglages, dans .minecraft/config/pokeislandhelper.json (modifiable à la main). */
class Settings {
    // Surbrillance des Pokémon sauvages
    var highlightEnabled: Boolean = true
    var highlightSeenNotCaught: Boolean = true   // aussi les espèces vues mais pas capturées
    var colorUnknown: String = "#FF4040"          // jamais vue
    var colorSeen: String = "#FFD000"             // vue, pas capturée
    var maxDistance: Double = 32.0
    var requireLineOfSight: Boolean = true        // ne surligne pas à travers les murs

    // Panneau de combat
    var battlePanelEnabled: Boolean = true
    var battlePanelRight: Boolean = true          // à droite (sinon à gauche)
    var battlePanelY: Int = 80                 // sous la vignette de l'adversaire
    var recommendations: Int = 3
    var lookPanel: Boolean = true                 // aussi en regardant un Pokémon, hors combat
    var lookDistance: Double = 24.0
}

object ModConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dir: Path = FabricLoader.getInstance().configDir
    private val settingsFile: Path = dir.resolve("pokeislandhelper.json")
    private val teamFile: Path = dir.resolve("pokeislandhelper-team.json")

    var settings = Settings(); private set
    var team = TeamPlan(); private set

    fun load() {
        settings = read(settingsFile, Settings::class.java) ?: Settings()
        team = read(teamFile, TeamPlan::class.java) ?: TeamPlan()
        saveSettings()
        saveTeam()
    }

    fun saveSettings() = write(settingsFile, settings)
    fun saveTeam() = write(teamFile, team)

    private fun <T> read(path: Path, type: Class<T>): T? = try {
        if (Files.exists(path)) Files.newBufferedReader(path).use { gson.fromJson(it, type) } else null
    } catch (e: Exception) {
        // Fichier abîmé : on le met de côté au lieu de l'écraser
        Files.move(path, path.resolveSibling(path.fileName.toString() + ".broken"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        null
    }

    private fun write(path: Path, value: Any) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { gson.toJson(value, it) }
    }

    fun parseColor(hex: String, fallback: Int): Int =
        hex.removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: fallback
}
