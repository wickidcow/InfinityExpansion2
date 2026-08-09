package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import net.guizhanss.infinityexpansion2.core.persistent.PersistentStorageCacheType
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache
import net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Reads IE1 storage-item data without loading InfinityLib.
 *
 * IE1's deprecated ITEM_STACK_OLD type stored its ItemStack as a YAML string,
 * while the amount used PersistentDataType.INTEGER. Reading the primitive types
 * directly keeps filled storage units migratable even after IE1 has been removed.
 */
object LegacyStorageBridge {

    data class LegacyStorageContents(
        val item: ItemStack?,
        val amount: Int,
        val voidExcess: Boolean,
    )

    fun read(stack: ItemStack): LegacyStorageContents? {
        if (!stack.hasItemMeta()) return null
        val pdc = stack.itemMeta.persistentDataContainer

        val itemKey = pdc.keys.firstOrNull {
            it.key == "item" && it.namespace.contains("infinityexpansion") && it.namespace != "infinityexpansion2"
        }
        val amountKey = pdc.keys.firstOrNull {
            it.key == "stored" && it.namespace.contains("infinityexpansion") && it.namespace != "infinityexpansion2"
        }
        if (itemKey == null && amountKey == null) return null

        val amount = amountKey?.let { pdc.get(it, PersistentDataType.INTEGER) } ?: 0
        val item = itemKey?.let { key ->
            pdc.get(key, PersistentDataType.STRING)?.let(::decodeLegacyItem)
        }
        val voidKey = pdc.keys.firstOrNull {
            it.key == "void_excess" && it.namespace.contains("infinityexpansion") && it.namespace != "infinityexpansion2"
        }
        val voidExcess = voidKey?.let { key ->
            runCatching { pdc.get(key, PersistentDataType.BYTE)?.toInt() == 1 }.getOrDefault(false) ||
                runCatching { pdc.get(key, PersistentDataType.INTEGER) == 1 }.getOrDefault(false)
        } ?: false

        return LegacyStorageContents(item, amount.coerceAtLeast(0), voidExcess)
    }

    fun writeModern(target: ItemStack, targetId: String, legacy: LegacyStorageContents): Boolean {
        val storage = SlimefunItem.getById(targetId) as? StorageUnit ?: return false
        val item = legacy.item?.clone()?.apply { amount = 1 }
        val amount = legacy.amount.coerceAtMost(storage.capacity)
        val cache = StorageCache(item, amount, storage.capacity, legacy.voidExcess)

        val meta = target.itemMeta
        // copyPdc() intentionally preserved unknown addon data. Remove only the obsolete IE1
        // storage payload now that it has been translated, so future refresh passes cannot
        // accidentally prefer stale legacy state.
        meta.persistentDataContainer.keys
            .filter { key ->
                key.namespace.contains("infinityexpansion") && key.namespace != "infinityexpansion2" &&
                    key.key in setOf("item", "stored", "void_excess")
            }
            .forEach(meta.persistentDataContainer::remove)
        meta.persistentDataContainer.set(StorageUnit.STORAGE_KEY, PersistentStorageCacheType.TYPE, cache)
        cache.addLore(meta)
        target.itemMeta = meta
        return true
    }

    private fun decodeLegacyItem(raw: String): ItemStack? {
        return runCatching {
            val yaml = YamlConfiguration()
            yaml.loadFromString(raw)
            yaml.getItemStack("item")
        }.getOrNull()
    }
}
