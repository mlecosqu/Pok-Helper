plugins {
    id("fabric-loom") version "1.17.12"
    kotlin("jvm") version "2.3.20"
}

val modVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project
val minecraftVersion: String by project
val loaderVersion: String by project
val fabricVersion: String by project
val kotlinLoaderVersion: String by project
val cobblemonVersion: String by project

version = modVersion
group = mavenGroup
base { archivesName.set(archivesBaseName) }

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.impactdev.net/repository/development/") // Cobblemon
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // Noms officiels Mojang (Minecraft.getInstance(), GuiGraphics...)
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    modImplementation("com.cobblemon:fabric:$cobblemonVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { jvmToolchain(21) }
