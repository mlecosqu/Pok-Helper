package fr.moran.pokeislandhelper.evo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.moran.pokeislandhelper.PokeIslandHelper
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Lit les évolutions dans les fichiers d'espèces des mods installés (data/<ns>/species/…json),
 * comme le fait le mod Cobbliki. Chargé une fois en arrière-plan au démarrage.
 * Limite : les modifications faites uniquement côté serveur (datapack PokeIsland) ne sont pas visibles.
 */
object EvoIndex {
    @Volatile private var edges: List<EvoEdge> = emptyList()
    @Volatile var ready = false; private set

    fun all(): List<EvoEdge> = edges

    fun loadAsync() {
        CompletableFuture.runAsync {
            edges = try { scan() } catch (t: Throwable) {
                PokeIslandHelper.warnOnce("evo", "Lecture des évolutions impossible", t); emptyList()
            }
            ready = true
        }
    }

    private fun scan(): List<EvoEdge> {
        val out = ArrayList<EvoEdge>()
        for (mod in FabricLoader.getInstance().allMods) {
            for (root in mod.rootPaths) scanRoot(root, out)
        }
        return out
    }

    private fun scanRoot(root: Path, out: MutableList<EvoEdge>) {
        val dataDir = root.resolve("data")
        if (!Files.isDirectory(dataDir)) return
        runCatching {
            Files.newDirectoryStream(dataDir).use { dirs ->
                for (nsDir in dirs) {
                    val ns = nsDir.fileName.toString().trim('/')
                    val species = nsDir.resolve("species")
                    if (!Files.isDirectory(species)) continue
                    Files.walk(species).use { walk ->
                        walk.filter { it.fileName.toString().endsWith(".json") }.forEach { f ->
                            runCatching {
                                val obj = Files.newBufferedReader(f).use { JsonParser.parseReader(it).asJsonObject }
                                val id = "$ns:" + f.fileName.toString().removeSuffix(".json")
                                collect(id, emptySet(), obj, out)
                                obj.getAsJsonArray("forms")?.forEach { fe ->
                                    val form = fe as? JsonObject ?: return@forEach
                                    val aspects = form.getAsJsonArray("aspects")?.map { it.asString }?.toSet() ?: return@forEach
                                    collect(id, aspects, form, out)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun collect(id: String, aspects: Set<String>, obj: JsonObject, out: MutableList<EvoEdge>) {
        val arr = obj.getAsJsonArray("evolutions") ?: return
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val result = o.get("result")?.asString ?: continue
            val reqs = o.getAsJsonArray("requirements")?.mapNotNull { r ->
                val ro = r as? JsonObject ?: return@mapNotNull null
                val variant = ro.get("variant")?.asString ?: return@mapNotNull null
                val fields = ro.entrySet().filter { it.value.isJsonPrimitive }.associate { it.key to it.value.asString }
                EvoReq(variant, fields)
            }.orEmpty()
            out += EvoEdge(
                fromId = id,
                fromAspects = aspects,
                result = result,
                variant = o.get("variant")?.asString ?: "level_up",
                context = o.get("requiredContext")?.takeIf { it.isJsonPrimitive }?.asString,
                reqs = reqs,
            )
        }
    }
}
