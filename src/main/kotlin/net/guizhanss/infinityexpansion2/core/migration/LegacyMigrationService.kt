package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.entity.Item
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Field
import java.util.ArrayDeque
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * IE1 -> IE2 live migration and doctor service.
 *
 * Migration is deliberately chunk-driven rather than force-loading an entire world. Legacy IDs
 * are aliased to their IE2 item before Slimefun resolves loaded data, then each loaded record is
 * rewritten permanently. This prevents the classic "unknown SF id -> block breaks after restart"
 * failure while keeping startup load bounded on large servers.
 */
class LegacyMigrationService(private val plugin: InfinityExpansion2) : Listener {

    data class MigrationStats(
        var legacyBlocksFound: Int = 0,
        var blocksMigrated: Int = 0,
        var blockFailures: Int = 0,
        var legacyItemsFound: Int = 0,
        var itemsMigrated: Int = 0,
        var refreshedItems: Int = 0,
        var itemFailures: Int = 0,
    ) {
        operator fun plusAssign(other: MigrationStats) {
            legacyBlocksFound += other.legacyBlocksFound
            blocksMigrated += other.blocksMigrated
            blockFailures += other.blockFailures
            legacyItemsFound += other.legacyItemsFound
            itemsMigrated += other.itemsMigrated
            refreshedItems += other.refreshedItems
            itemFailures += other.itemFailures
        }
    }

    val compatibility = SlimefunCompatibilityBridge()
    val itemMigrator = LegacyItemMigrator()
    private val migrationInProgress = AtomicBoolean(false)

    /*
     * Chunk-load migration must stay on the server thread because it touches Bukkit inventories,
     * entities and Slimefun block data. Do not run that work directly from every ChunkLoadEvent:
     * large view distances/teleports can fire hundreds of events together.
     *
     * Instead we de-duplicate chunk requests and process at most one chunk every few ticks.
     * Legacy aliases keep old block IDs resolvable while a chunk waits in this queue.
     */
    private data class QueuedChunk(val worldId: UUID, val x: Int, val z: Int)

    private val pendingChunks = ArrayDeque<QueuedChunk>()
    private val queuedChunkKeys = HashSet<QueuedChunk>()
    private val scannedThisSession = HashSet<QueuedChunk>()

    @Volatile
    var aliasesInstalled: SlimefunCompatibilityBridge.AliasResult =
        SlimefunCompatibilityBridge.AliasResult(0, 0, 0, 0)
        private set

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Keep automatic migration bounded. Four chunks/second is intentionally conservative:
        // aliases make waiting safe, while TPS remains far more important than migration speed.
        plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { processNextQueuedChunk() },
            AUTO_QUEUE_PERIOD_TICKS,
            AUTO_QUEUE_PERIOD_TICKS
        )
    }

    fun installStartupAliases() {
        aliasesInstalled = compatibility.installStartupAliases()
        if (aliasesInstalled.installed > 0 || aliasesInstalled.failed > 0) {
            InfinityExpansion2.log(
                Level.INFO,
                "IE1 compatibility: ${aliasesInstalled.installed} startup-safe aliases installed, " +
                    "${aliasesInstalled.skippedExisting} already owned, ${aliasesInstalled.failed} failed."
            )
        }
    }

    fun installAliases() {
        LegacyIdMapper.enablePostRegistrationMappings()
        aliasesInstalled = compatibility.installLegacyAliases()
        if (aliasesInstalled.installed > 0 || aliasesInstalled.failed > 0) {
            InfinityExpansion2.log(
                Level.INFO,
                "IE1 compatibility: ${aliasesInstalled.installed} legacy aliases installed, " +
                    "${aliasesInstalled.skippedExisting} already owned, ${aliasesInstalled.failed} failed."
            )
        }
    }

    fun scanLoaded(migrate: Boolean): MigrationStats {
        if (migrate && !migrationInProgress.compareAndSet(false, true)) return MigrationStats()
        return try {
            val stats = MigrationStats()

            // The old implementation walked Slimefun's complete loaded block cache once for
            // blocks and again for virtual menus. One combined snapshot cuts that cost in half.
            stats += scanSlimefunData(
                chunkFilter = null,
                migrate = migrate,
                scanBlocks = true,
                scanMenus = true
            )

            plugin.server.worlds.forEach { world ->
                world.loadedChunks.forEach { chunk -> stats += scanChunkItems(chunk, migrate) }
            }
            plugin.server.onlinePlayers.forEach { stats += scanPlayer(it, migrate, false) }
            stats
        } finally {
            if (migrate) migrationInProgress.set(false)
        }
    }

    fun scanChunk(chunk: Chunk, migrate: Boolean): MigrationStats {
        val stats = MigrationStats()
        val scanBlocks = InfinityExpansion2.configService.migrationAutoBlocks.value || !migrate
        val scanItems = InfinityExpansion2.configService.migrationAutoItems.value || !migrate

        if (scanBlocks || scanItems) {
            stats += scanSlimefunData(
                chunkFilter = chunk,
                migrate = migrate,
                scanBlocks = scanBlocks,
                scanMenus = scanItems
            )
        }
        if (scanItems) {
            stats += scanChunkItems(chunk, migrate)
        }
        return stats
    }

    fun scanPlayer(player: Player, migrate: Boolean, refreshModern: Boolean): MigrationStats {
        val stats = MigrationStats()
        stats += scanInventory(player.inventory, migrate, refreshModern)
        stats += scanInventory(player.enderChest, migrate, refreshModern)
        return stats
    }

    fun refreshPlayer(player: Player): MigrationStats = scanPlayer(player, true, true)

    private fun scanInventory(inventory: Inventory, migrate: Boolean, refreshModern: Boolean): MigrationStats {
        val stats = MigrationStats()
        for (slot in 0 until inventory.size) {
            val original = inventory.getItem(slot) ?: continue
            val sourceId = itemMigrator.rawSlimefunId(original)
            val legacyTarget = LegacyIdMapper.targetFor(sourceId)
            if (legacyTarget != null) stats.legacyItemsFound++

            if (!migrate) continue
            try {
                val result = itemMigrator.migrate(original, refreshModern) ?: continue
                if (result.changed) {
                    inventory.setItem(slot, result.item)
                    if (legacyTarget != null) stats.itemsMigrated++ else if (refreshModern) stats.refreshedItems++
                }
            } catch (t: Throwable) {
                stats.itemFailures++
                InfinityExpansion2.log(Level.WARNING, t, "IE1 item migration failed in inventory slot $slot")
            }
        }
        return stats
    }

    private fun scanChunkItems(chunk: Chunk, migrate: Boolean): MigrationStats {
        val stats = MigrationStats()

        // Vanilla block inventories (chests, barrels, hoppers, furnaces, shulkers, etc.).
        chunk.tileEntities.forEach { state ->
            val container = state as? Container ?: return@forEach
            stats += scanInventory(container.inventory, migrate, false)
        }

        // Existing entities are not re-fired through ItemSpawnEvent when their chunk loads.
        chunk.entities.forEach { entity ->
            when (entity) {
                is Player -> Unit // handled through player/ender-chest scanning
                is Item -> {
                    val result = migrateSingle(entity.itemStack, migrate)
                    stats += result.first
                    if (migrate && result.second != null) entity.itemStack = result.second!!
                }
                is ItemFrame -> {
                    val result = migrateSingle(entity.item, migrate)
                    stats += result.first
                    if (migrate && result.second != null) entity.setItem(result.second!!)
                }
                is ItemDisplay -> {
                    val result = migrateSingle(entity.itemStack, migrate)
                    stats += result.first
                    if (migrate && result.second != null) entity.setItemStack(result.second!!)
                }
                is LivingEntity -> {
                    val equipment = entity.equipment
                    if (equipment != null) {
                        val original = equipment.armorContents
                        var changed = false
                        val modern = original.map { stack ->
                            val result = migrateSingle(stack, migrate)
                            stats += result.first
                            if (result.second != null) changed = true
                            result.second ?: stack
                        }.toTypedArray()
                        if (migrate && changed) equipment.armorContents = modern

                        val main = migrateSingle(equipment.itemInMainHand, migrate)
                        stats += main.first
                        if (migrate && main.second != null) equipment.setItemInMainHand(main.second)
                        val off = migrateSingle(equipment.itemInOffHand, migrate)
                        stats += off.first
                        if (migrate && off.second != null) equipment.setItemInOffHand(off.second)
                    }
                }
            }

            if (entity is InventoryHolder && entity !is Player) {
                stats += scanInventory(entity.inventory, migrate, false)
            }
        }
        return stats
    }

    private fun migrateSingle(stack: ItemStack?, migrate: Boolean): Pair<MigrationStats, ItemStack?> {
        val stats = MigrationStats()
        if (stack == null || stack.type.isAir) return stats to null
        val sourceId = itemMigrator.rawSlimefunId(stack)
        val target = LegacyIdMapper.targetFor(sourceId)
        if (target != null) stats.legacyItemsFound++
        if (!migrate || target == null) return stats to null

        return try {
            val result = itemMigrator.migrate(stack, false)
            if (result?.changed == true) {
                stats.itemsMigrated++
                stats to result.item
            } else {
                stats to null
            }
        } catch (t: Throwable) {
            stats.itemFailures++
            InfinityExpansion2.log(Level.WARNING, t, "IE1 item migration failed for an entity-held item")
            stats to null
        }
    }

    /**
     * Scan Slimefun block records and virtual menus from one controller snapshot.
     *
     * This is deliberately shared by block and item migration. The previous implementation
     * reconstructed the complete loaded Slimefun data list twice for every ChunkLoadEvent,
     * then filtered both copies down to a single chunk. On a large server that becomes
     * O(chunk-loads x all-loaded-Slimefun-blocks) work on the primary server thread.
     */
    private fun scanSlimefunData(
        chunkFilter: Chunk?,
        migrate: Boolean,
        scanBlocks: Boolean,
        scanMenus: Boolean,
    ): MigrationStats {
        val stats = MigrationStats()
        val controller = blockDataController() ?: return stats
        val blockData = loadedBlockData(controller)
        if (blockData.isEmpty()) return stats

        // loadedBlockData() is already a snapshot, which is important because migrateBlock()
        // removes/recreates records in the controller.
        blockData.forEach { data ->
            val location = call(data, "getLocation") as? Location ?: return@forEach
            if (chunkFilter != null && !sameChunk(location, chunkFilter)) return@forEach

            // Scan the old menu before a possible record rewrite. migrateBlock() snapshots the
            // updated contents, so migrated IE1 items inside machine menus are retained.
            if (scanMenus) {
                val menu = call(data, "getBlockMenu") as? BlockMenu
                if (menu != null) {
                    stats += scanInventory(menu.toInventory(), migrate, false)
                }
            }

            if (!scanBlocks) return@forEach
            val sourceId = call(data, "getSfId") as? String ?: return@forEach
            val targetId = LegacyIdMapper.targetFor(sourceId) ?: return@forEach
            stats.legacyBlocksFound++
            if (!migrate) return@forEach

            try {
                migrateBlock(controller, data, sourceId, targetId)
                stats.blocksMigrated++
            } catch (t: Throwable) {
                stats.blockFailures++
                InfinityExpansion2.log(
                    Level.WARNING,
                    t,
                    "Failed to migrate IE1 block $sourceId -> $targetId at $location"
                )
            }
        }
        return stats
    }

    private fun migrateBlock(controller: Any, oldData: Any, sourceId: String, targetId: String) {
        val location = call(oldData, "getLocation") as? Location
            ?: error("Slimefun block data did not expose a location")
        SlimefunItem.getById(targetId) ?: error("Target Slimefun item $targetId is not registered")

        @Suppress("UNCHECKED_CAST")
        val data = ((call(oldData, "getAllData") as? Map<String, String>) ?: emptyMap()).toMutableMap()
        val menuContents = snapshotMenu(oldData)?.map { stack ->
            stack?.let { itemMigrator.migrate(it, false)?.item ?: it.clone() }
        }

        // IE1 StorageUnit block data used "stored"; IE2 uses "stored_amount".
        if (sourceId in LEGACY_STORAGE_IDS) {
            data["stored"]?.let { oldAmount -> data.putIfAbsent("stored_amount", oldAmount) }
            data.remove("stored")
        }

        val remove = controller.javaClass.methods.firstOrNull {
            it.name == "removeBlock" && it.parameterCount == 1 &&
                it.parameterTypes[0].isAssignableFrom(Location::class.java)
        } ?: controller.javaClass.methods.firstOrNull { it.name == "removeBlock" && it.parameterCount == 1 }
            ?: error("Slimefun block controller has no compatible removeBlock method")
        remove.invoke(controller, location)

        // Never change the physical Bukkit block here. IE1 and IE2 often use different display
        // materials for the same logical machine; changing Material during data migration can
        // destroy block-state inventories or trigger unrelated placement/break logic.
        val create = controller.javaClass.methods.firstOrNull {
            it.name == "createBlock" && it.parameterCount == 2
        } ?: error("Slimefun block controller has no compatible createBlock method")
        val newData = create.invoke(controller, location, targetId)
            ?: error("Slimefun block controller returned null while creating $targetId")

        val setData = newData.javaClass.methods.firstOrNull { it.name == "setData" && it.parameterCount == 2 }
        data.forEach { (key, value) -> setData?.invoke(newData, key, value) }

        val newMenu = call(newData, "getBlockMenu") as? BlockMenu
        if (newMenu != null && menuContents != null) {
            menuContents.forEachIndexed { slot, stack ->
                if (stack != null && slot < newMenu.toInventory().size) {
                    newMenu.replaceExistingItem(slot, stack)
                }
            }
        }

        // Unknown Slimefun KV keys are harmless and make doctor diagnostics auditable.
        setData?.invoke(newData, MIGRATION_KEY, "ie1:$sourceId")
    }

    private fun snapshotMenu(data: Any): Array<ItemStack?>? {
        val direct = call(data, "getMenuContents")
        if (direct is Array<*>) {
            return Array(direct.size) { i -> (direct[i] as? ItemStack)?.clone() }
        }
        if (direct is Collection<*>) {
            return direct.map { (it as? ItemStack)?.clone() }.toTypedArray()
        }
        val menu = call(data, "getBlockMenu") as? BlockMenu ?: return null
        return menu.toInventory().contents.map { it?.clone() }.toTypedArray()
    }

    private fun blockDataController(): Any? {
        // Keep database-manager lookup reflective so the migration layer does not link against
        // a fork-specific storage implementation at compile time.
        val databaseManager = runCatching {
            Slimefun::class.java.methods.first { it.name == "getDatabaseManager" && it.parameterCount == 0 }
                .invoke(null)
        }.getOrNull() ?: return null
        val getter = databaseManager.javaClass.methods.firstOrNull {
            it.name == "getBlockDataController" && it.parameterCount == 0
        }
        getter?.let { return runCatching { it.invoke(databaseManager) }.getOrNull() }

        val field = findField(databaseManager.javaClass, "blockDataController") ?: return null
        return runCatching {
            field.isAccessible = true
            field.get(databaseManager)
        }.getOrNull()
    }

    private fun loadedBlockData(controller: Any): List<Any> {
        val result = ArrayList<Any>()
        loadedChunkData(controller).forEach { chunkData ->
            @Suppress("UNCHECKED_CAST")
            val data = call(chunkData, "getAllBlockData") as? Collection<Any> ?: return@forEach
            result.addAll(data)
        }
        return result
    }

    private fun loadedChunkData(controller: Any): Collection<Any> {
        val preferred = listOf("loadedChunk", "loadedChunks", "loadedChunkData")
            .asSequence()
            .mapNotNull { findField(controller.javaClass, it) }
            .mapNotNull { mapValues(controller, it) }
            .firstOrNull { it.isNotEmpty() }
        if (preferred != null) return preferred

        // Fallback for forks that renamed the field: find a Map whose value looks like chunk data.
        for (field in allFields(controller.javaClass)) {
            val values = mapValues(controller, field) ?: continue
            val sample = values.firstOrNull() ?: continue
            if (sample.javaClass.methods.any { it.name == "getAllBlockData" && it.parameterCount == 0 }) {
                return values
            }
        }
        return emptyList()
    }

    private fun mapValues(target: Any, field: Field): Collection<Any>? = runCatching {
        field.isAccessible = true
        val value = field.get(target) as? Map<*, *> ?: return@runCatching null
        value.values.filterNotNull()
    }.getOrNull()

    private fun allFields(type: Class<*>): Sequence<Field> = sequence {
        var current: Class<*>? = type
        while (current != null) {
            val clazz = current
            yieldAll(clazz.declaredFields.asSequence())
            current = clazz.superclass
        }
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            val clazz = current
            try {
                return clazz.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = clazz.superclass
            }
        }
        return null
    }

    private fun call(target: Any, name: String): Any? = runCatching {
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }.invoke(target)
    }.getOrNull()

    private fun sameChunk(location: Location, chunk: Chunk): Boolean =
        location.world?.uid == chunk.world.uid && (location.blockX shr 4) == chunk.x && (location.blockZ shr 4) == chunk.z

    private fun queueChunk(chunk: Chunk) {
        val key = QueuedChunk(chunk.world.uid, chunk.x, chunk.z)
        if (key in scannedThisSession || !queuedChunkKeys.add(key)) return
        pendingChunks.addLast(key)
    }

    private fun processNextQueuedChunk() {
        if (!InfinityExpansion2.configService.migrationEnabled.value ||
            (!InfinityExpansion2.configService.migrationAutoBlocks.value &&
                !InfinityExpansion2.configService.migrationAutoItems.value)
        ) {
            return
        }

        while (pendingChunks.isNotEmpty()) {
            val key = pendingChunks.removeFirst()
            queuedChunkKeys.remove(key)

            val world = plugin.server.getWorld(key.worldId) ?: continue

            // Never force-load a chunk just for migration. If it unloaded before reaching the
            // queue head, its next genuine ChunkLoadEvent will enqueue it again.
            if (!world.isChunkLoaded(key.x, key.z)) continue

            scannedThisSession.add(key)
            val chunk = world.getChunkAt(key.x, key.z)
            val stats = scanChunk(chunk, true)
            logAutoStats("chunk ${key.x},${key.z}", stats)
            return
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onServerLoad(event: ServerLoadEvent) {
        if (!InfinityExpansion2.configService.migrationEnabled.value) return
        installAliases() // dynamic cards/oscillators are registered by now
        if (!InfinityExpansion2.configService.migrationAutoBlocks.value &&
            !InfinityExpansion2.configService.migrationAutoItems.value
        ) return

        // Do not synchronously rescan every loaded chunk two seconds after startup. Queue the
        // already-loaded set and let the bounded worker drain it gradually.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.server.worlds.forEach { world ->
                world.loadedChunks.forEach(::queueChunk)
            }
        }, 40L)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!InfinityExpansion2.configService.migrationEnabled.value ||
            (!InfinityExpansion2.configService.migrationAutoBlocks.value &&
                !InfinityExpansion2.configService.migrationAutoItems.value)
        ) return

        // Wait briefly for Slimefun to hydrate its block/menu data, then only enqueue. The actual
        // scan is rate-limited by processNextQueuedChunk().
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (event.chunk.isLoaded) {
                queueChunk(event.chunk)
            }
        }, 2L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (!InfinityExpansion2.configService.migrationEnabled.value ||
            !InfinityExpansion2.configService.migrationAutoItems.value
        ) return
        plugin.server.scheduler.runTask(plugin, Runnable {
            val stats = scanPlayer(
                event.player,
                true,
                InfinityExpansion2.configService.migrationRefreshModernItems.value
            )
            logAutoStats("player ${event.player.name}", stats)
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        if (!InfinityExpansion2.configService.migrationEnabled.value ||
            !InfinityExpansion2.configService.migrationAutoItems.value
        ) return
        val entity: Item = event.entity
        val result = runCatching { itemMigrator.migrate(entity.itemStack, false) }.getOrNull() ?: return
        if (result.changed) entity.itemStack = result.item
    }

    private fun logAutoStats(context: String, stats: MigrationStats) {
        if (stats.blocksMigrated == 0 && stats.itemsMigrated == 0 &&
            stats.blockFailures == 0 && stats.itemFailures == 0
        ) return
        InfinityExpansion2.log(
            Level.INFO,
            "IE1 migration [$context]: ${stats.blocksMigrated}/${stats.legacyBlocksFound} blocks, " +
                "${stats.itemsMigrated}/${stats.legacyItemsFound} items, " +
                "${stats.blockFailures + stats.itemFailures} failures."
        )
    }

    companion object {
        const val MIGRATION_KEY = "ie2_legacy_migrated"
        private const val AUTO_QUEUE_PERIOD_TICKS = 5L
        private val LEGACY_STORAGE_IDS = setOf(
            "BASIC_STORAGE", "ADVANCED_STORAGE", "REINFORCED_STORAGE", "VOID_STORAGE", "INFINITY_STORAGE"
        )
    }
}
