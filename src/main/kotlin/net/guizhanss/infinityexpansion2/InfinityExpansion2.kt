package net.guizhanss.infinityexpansion2

import io.papermc.lib.PaperLib
import net.byteflux.libby.Library
import net.guizhanss.guizhanlib.libraries.BukkitLibraryManager
import net.guizhanss.guizhanlib.slimefun.addon.AbstractAddon
import net.guizhanss.infinityexpansion2.core.commands.MainCommand
import net.guizhanss.infinityexpansion2.core.migration.LegacyMigrationService
import net.guizhanss.infinityexpansion2.core.services.ConfigService
import net.guizhanss.infinityexpansion2.core.services.DebugService
import net.guizhanss.infinityexpansion2.core.services.IntegrationService
import net.guizhanss.infinityexpansion2.core.services.LocalizationService
import net.guizhanss.infinityexpansion2.implementation.IEItems
import net.guizhanss.infinityexpansion2.implementation.guide.IEItemGroups
import net.guizhanss.infinityexpansion2.implementation.listeners.ArmorItemListener
import net.guizhanss.infinityexpansion2.implementation.listeners.BowItemListener
import net.guizhanss.infinityexpansion2.implementation.listeners.InfinityMatrixListener
import net.guizhanss.infinityexpansion2.implementation.listeners.SlimefunRegistryListener
import net.guizhanss.infinityexpansion2.implementation.listeners.TranslationsLoadListener
import net.guizhanss.infinityexpansion2.implementation.listeners.VeinMinerListener
import net.guizhanss.infinityexpansion2.implementation.setup.ResearchSetup
import net.guizhanss.infinityexpansion2.implementation.tasks.InfinityMatrixTask
import net.guizhanss.infinityexpansion2.utils.tags.IETag
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import java.util.logging.Level

class InfinityExpansion2 : AbstractAddon(
    GITHUB_USER, GITHUB_REPO, GITHUB_BRANCH, AUTO_UPDATE_KEY
) {

    override fun load() {
        // check if there is central repo prop defined
        val centralRepo =
            System.getProperty("centralRepository") ?: "https://maven-central.storage-download.googleapis.com/maven2/"

        logger.info("Loading libraries, please wait...")
        logger.info("If you stuck here for a long time, try to specify a mirror repository.")
        logger.info("Add -DcentralRepository=<url> to the JVM arguments.")

        // download libs
        val manager = BukkitLibraryManager(this)
        manager.addRepository(centralRepo)
        manager.loadLibrary(
            Library.builder().groupId("org.jetbrains.kotlin").artifactId("kotlin-stdlib").version("2.2.21").build()
        )
        manager.loadLibrary(
            Library.builder().groupId("org.jetbrains.kotlin").artifactId("kotlin-reflect").version("2.2.21").build()
        )

        logger.info("Loaded all required libraries.")
    }

    override fun enable() {
        instance = this

        if (PaperLib.isSpigot() && !PaperLib.isPaper()) {
            PaperLib.suggestPaper(this, Level.SEVERE)
            server.pluginManager.disablePlugin(this)
            return
        }

        // conflict check
        // TODO: display migration logs
//        if (server.pluginManager.isPluginEnabled("InfinityExpansion")) {
//            log(Level.SEVERE, "InfinityExpansion2 is not compatible with InfinityExpansion.")
//            log(Level.SEVERE, "Please remove InfinityExpansion before enabling InfinityExpansion2.")
//            server.pluginManager.disablePlugin(this)
//            return
//        }

        // config & debug
        configService = ConfigService(this)
        debugService = DebugService(this)

        // tags
        IETag.reloadAll()

        // localization
        log(Level.INFO, "Loading language...")
        val lang = configService.lang.value
        localization = LocalizationService(this, file)
        localization.idPrefix = "IE_"
        localization.addLanguage(lang)
        if (lang != DEFAULT_LANG) {
            localization.addLanguage(DEFAULT_LANG)
        }
        log(Level.INFO, "Loaded language {0}.", lang)

        // item groups setup
        IEItemGroups

        // item setup
        IEItems

        // IE1 compatibility aliases must exist before Slimefun finishes loading world block data.
        migrationService = LegacyMigrationService(this)
        if (configService.migrationEnabled.value) {
            migrationService.installAliases()
        }

        // researches setup
        if (configService.enableResearches.value) {
            ResearchSetup
        }

        // integrations
        integrationService = IntegrationService(this)

        // commands
        MainCommand(getPluginCommand("infinityexpansion2")).register()

        // listeners & tasks
        setupListeners()
        setupTasks()

        // Metrics setup
        setupMetrics()
    }

    override fun disable() {
        // no disable
    }

    override fun autoUpdate() {
        // Runtime binary replacement is intentionally disabled in this fork. Updating directly
        // from upstream could overwrite the IE1 migration/Legacy safety layer. GitHub Actions
        // handles upstream synchronization and release builds instead.
        if (configService.autoUpdate.value) {
            log(Level.WARNING, "Runtime auto-update is disabled in the Legacy compatibility fork; use GitHub releases/upstream-sync instead.")
        }
    }

    private fun setupListeners() {
        ArmorItemListener(this)
        BowItemListener(this)
        InfinityMatrixListener(this)
        SlimefunRegistryListener(this)
        TranslationsLoadListener(this)
        VeinMinerListener(this)
    }

    private fun setupTasks() {
        InfinityMatrixTask()
    }

    private fun setupMetrics() {
        val metrics = Metrics(this, 23025)

        metrics.addCustomChart(SimplePie("autoUpdate") { configService.autoUpdate.value.toString() })
    }

    companion object {

        private const val GITHUB_USER = "wickidcow"
        private const val GITHUB_REPO = "InfinityExpansion2"
        private const val GITHUB_BRANCH = "master"
        private const val AUTO_UPDATE_KEY = "auto-update"
        const val DEFAULT_LANG = "en"

        lateinit var instance: InfinityExpansion2
            private set
        lateinit var configService: ConfigService
            private set
        lateinit var debugService: DebugService
            private set
        lateinit var localization: LocalizationService
            private set
        lateinit var integrationService: IntegrationService
            private set
        lateinit var migrationService: LegacyMigrationService
            private set

        fun scheduler() = getScheduler()

        fun log(level: Level, message: String) {
            instance.logger.log(level, message)
        }

        fun log(level: Level, ex: Throwable, message: String) {
            instance.logger.log(level, ex) { message }
        }
    }
}
