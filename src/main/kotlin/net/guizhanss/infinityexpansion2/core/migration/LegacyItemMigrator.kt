package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataContainer
import java.util.logging.Level

class LegacyItemMigrator {

    data class Result(val item: ItemStack, val changed: Boolean, val fromId: String? = null, val toId: String? = null)

    fun migrate(stack: ItemStack?, refreshModern: Boolean = false): Result? = migrateInternal(stack, refreshModern, 0)

    private fun migrateInternal(stack: ItemStack?, refreshModern: Boolean, depth: Int): Result? {
        if (stack == null || stack.type.isAir) return null
        if (depth > MAX_NESTING_DEPTH) return Result(stack, false, rawSlimefunId(stack), null)

        val sourceId = rawSlimefunId(stack)
        val targetId = when {
            sourceId == null -> null
            sourceId.startsWith("IE_") && refreshModern -> sourceId
            else -> LegacyIdMapper.targetFor(sourceId)
        }

        var changedNested = false
        var working = stack

        if (targetId != null) {
            val targetItem = SlimefunItem.getById(targetId) ?: return Result(stack, false, sourceId, null)
            val modern = targetItem.item.clone()
            modern.amount = stack.amount
            preserveGameplayState(stack, modern)

            // Preserve addon PDC that is not supplied by the new template. This retains
            // useful custom state while the new Slimefun id wins on key collisions.
            copyPdc(stack, modern)

            // IE1 filled storage units need a real data-format conversion, not only an id rename.
            // Also migrate the identity of the item stored inside the unit, if that stored type
            // is itself an IE1 Slimefun item.
            LegacyStorageBridge.read(stack)?.let { legacy ->
                val storedItem = legacy.item?.let { stored ->
                    migrateInternal(stored, false, depth + 1)?.item ?: stored
                }
                LegacyStorageBridge.writeModern(modern, targetId, legacy.copy(item = storedItem))
            }

            working = modern
        }

        val nested = migrateNestedContents(working, refreshModern, depth)
        if (nested != null) {
            working = nested
            changedNested = true
        }

        val changedSelf = targetId != null && !sameSemanticStack(stack, working)
        return Result(working, changedSelf || changedNested, sourceId, targetId)
    }

    fun rawSlimefunId(stack: ItemStack): String? {
        if (!stack.hasItemMeta()) return null
        return runCatching {
            Slimefun.getItemDataService().getItemData(stack).orElse(null)
        }.recoverCatching {
            SlimefunItem.getByItem(stack)?.id
        }.getOrNull()
    }

    private fun preserveGameplayState(source: ItemStack, target: ItemStack) {
        if (!source.hasItemMeta() || !target.hasItemMeta()) return
        val oldMeta = source.itemMeta
        val newMeta = target.itemMeta

        if (oldMeta is Damageable && newMeta is Damageable) {
            newMeta.damage = oldMeta.damage.coerceAtLeast(0)
        }

        // Keep player-added enchantments where they are higher/different than the IE2 template.
        oldMeta.enchants.forEach { (enchantment, level) ->
            runCatching { newMeta.addEnchant(enchantment, level, true) }
        }

        // Keep modern armor trims; IE2's name/lore/attributes are deliberately refreshed.
        if (oldMeta is ArmorMeta && newMeta is ArmorMeta && oldMeta.hasTrim()) {
            newMeta.trim = oldMeta.trim
        }

        target.itemMeta = newMeta
    }

    private fun copyPdc(source: ItemStack, target: ItemStack) {
        if (!source.hasItemMeta() || !target.hasItemMeta()) return
        runCatching {
            val oldMeta = source.itemMeta
            val newMeta = target.itemMeta
            copyPdcReflective(oldMeta.persistentDataContainer, newMeta.persistentDataContainer)
            target.itemMeta = newMeta
        }.onFailure {
            InfinityExpansion2.log(Level.FINE, "Could not copy legacy item PDC during migration: ${it.message}")
        }
    }

    /** Paper/Bukkit added PersistentDataContainer#copyTo long ago, but reflection keeps fork compatibility. */
    private fun copyPdcReflective(source: PersistentDataContainer, target: PersistentDataContainer) {
        val method = source.javaClass.methods.firstOrNull {
            it.name == "copyTo" && it.parameterCount == 2
        } ?: return
        // replace=false: the fresh IE2 Slimefun id and IE2-native data must win.
        method.invoke(source, target, false)
    }

    private fun migrateNestedContents(stack: ItemStack, refreshModern: Boolean, depth: Int): ItemStack? {
        if (!stack.hasItemMeta() || depth >= MAX_NESTING_DEPTH) return null
        val meta = stack.itemMeta

        if (meta is BlockStateMeta) {
            val state = meta.blockState as? Container ?: return null
            var changed = false
            val inv = state.inventory
            for (slot in 0 until inv.size) {
                val result = migrateInternal(inv.getItem(slot), refreshModern, depth + 1) ?: continue
                if (result.changed) {
                    inv.setItem(slot, result.item)
                    changed = true
                }
            }
            if (!changed) return null
            meta.blockState = state
            return stack.clone().apply { itemMeta = meta }
        }

        if (meta is BundleMeta) {
            var changed = false
            val items = meta.items.map { item ->
                val result = migrateInternal(item, refreshModern, depth + 1)
                if (result?.changed == true) {
                    changed = true
                    result.item
                } else {
                    item
                }
            }
            if (!changed) return null
            meta.setItems(items)
            return stack.clone().apply { itemMeta = meta }
        }

        return null
    }

    private fun sameSemanticStack(a: ItemStack, b: ItemStack): Boolean = a == b

    companion object {
        private const val MAX_NESTING_DEPTH = 8
    }
}
