import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.3.0"
    id("de.eldoria.plugin-yml.bukkit") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "net.guizhanss"
description = "InfinityExpansion2 Legacy Compatibility Fork"

val timestamp: String = DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneOffset.UTC).format(Instant.now())
val mainPackage = "net.guizhanss.infinityexpansion2"
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("26.2.build.+")
val slimefunApiCoordinate = providers.gradleProperty("slimefunApiCoordinate")
    .orElse("com.github.slimefun:Slimefun4:experimental-SNAPSHOT")

// CI builds use a stable Legacy version family instead of the old "preview" label.
// Tagged/manual releases can still provide an explicit version with -PbuildVersion=...
version = providers.gradleProperty("buildVersion").orElse("1.0.$timestamp").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://repo.alessiodp.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(kotlin("stdlib")) // loaded through library loader
    compileOnly(kotlin("reflect")) // loaded through library loader
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")

    // Compile against the stable/common Slimefun API surface. Runtime compatibility with
    // Legacy/Gugu/United/Core-style forks is handled without linking to fork-specific internals.
    compileOnly(slimefunApiCoordinate.get())
    compileOnly("net.guizhanss:SlimefunTranslation:e03b01a7b7")
    compileOnly("com.github.schntgaispock:SlimeHUD:1.3.0")
    implementation("net.guizhanss:guizhanlib-all:2.5.0")
    implementation("net.guizhanss:guizhanlib-kt-all:0.2.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("com.jeff-media:MorePersistentDataTypes:2.4.0")
}

java {
    disableAutoTargetJvm()
    // Paper 26.2's build toolchain is Java 25, while the fork emits Java 21 bytecode so the
    // addon itself remains usable on Java-21-capable Slimefun runtimes where their server allows it.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        javaParameters = true
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.shadowJar {
    fun doRelocate(from: String, to: String? = null) {
        val last = to ?: from.split(".").last()
        relocate(from, "$mainPackage.libs.$last")
    }

    doRelocate("net.byteflux.libby")
    doRelocate("net.guizhanss.guizhanlib")
    doRelocate("org.bstats")
    doRelocate("io.github.seggan.sf4k")
    doRelocate("io.papermc.lib", "paperlib")
    doRelocate("com.jeff_media.morepersistentdatatypes")
    minimize()
    archiveClassifier = ""
    archiveFileName.set("SF_IE2Legacy${project.version}.jar")
}

bukkit {
    main = "$mainPackage.InfinityExpansion2"
    // Keep an older API floor because this fork intentionally retains cross-version/fork support.
    // Paper 26.2 remains the CI and primary runtime target.
    apiVersion = "1.21"
    authors = listOf("ybw0014", "Mooy1", "wickidcow")
    description = "InfinityExpansion2 - Legacy-first Slimefun compatibility and IE1 migration fork"
    depend = listOf("Slimefun")
    softDepend = listOf("GuizhanLibPlugin", "SlimefunTranslation", "InfinityExpansion", "SlimeHUD")
    loadBefore = listOf("SlimeCustomizer", "RykenSlimeCustomizer", "SlimeFunRecipe")

    commands {
        register("infinityexpansion2") {
            description = "InfinityExpansion2 command"
            aliases = listOf("ie", "ie2")
        }
    }

    permissions {
        register("infinityexpansion2.command.doctor") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("infinityexpansion2.command.giverecipe") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("infinityexpansion2.command.guide") {
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("infinityexpansion2.command.printitem") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("infinityexpansion2.command.id") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}

tasks {
    runServer {
        // Deliberately do not auto-download a Slimefun implementation here. Put the exact
        // Slimefun Legacy/Gugu/United/Core JAR you want to test into run/plugins/.
        jvmArgs("-Dcom.mojang.eula.agree=true")
        minecraftVersion("26.2")
    }
}
