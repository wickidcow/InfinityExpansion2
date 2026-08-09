package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.Bukkit
import java.util.logging.Level

/** Runtime compatibility helpers shared by Legacy/Gugu/United/Core-style Slimefun forks. */
class SlimefunCompatibilityBridge {

    data class AliasResult(
        val installed: Int,
        val skippedExisting: Int,
        val totalResolved: Int,
        val failed: Int = 0,
    )

    fun installStartupAliases(): AliasResult = installAliases(
        LegacyIdMapper.resolvedStartupAliases(),
        "startup-safe"
    )

    fun installLegacyAliases(): AliasResult = installAliases(
        LegacyIdMapper.resolvedAliases(),
        "post-registration"
    )

    private fun installAliases(aliases: Map<String, String>, phase: String): AliasResult {
        val registry = Slimefun.getRegistry()
        val ids = findMutableMap(registry, "getSlimefunItemIds") ?: run {
            InfinityExpansion2.log(
                Level.WARNING,
                "IE1 migration: this Slimefun fork does not expose a writable item-id registry; live aliases are unavailable."
            )
            return AliasResult(0, 0, 0, 0)
        }

        var installed = 0
        var skipped = 0
        var failed = 0
        aliases.forEach { (oldId, newId) ->
            val target = SlimefunItem.getById(newId) ?: return@forEach
            if (ids.containsKey(oldId)) {
                skipped++
                return@forEach
            }

            runCatching { ids[oldId] = target }
                .onSuccess { installed++ }
                .onFailure { failed++ }
        }

        if (failed > 0) {
            InfinityExpansion2.log(
                Level.WARNING,
                "IE1 migration: $failed $phase legacy aliases could not be installed on this Slimefun implementation. " +
                    "The doctor can still migrate records after their chunks load, but make a backup before first startup."
            )
        }
        return AliasResult(installed, skipped, aliases.size, failed)
    }

    fun runtimeDescription(): String {
        val plugin = Bukkit.getPluginManager().getPlugin("Slimefun")
        val version = plugin?.description?.version ?: "unknown"
        val implementation = when {
            classExists("com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController") -> "Slimefun Legacy/Gugu storage"
            classExists("io.github.thebusybiscuit.slimefun4.storage.controller.BlockDataController") -> "Slimefun Core/United storage"
            else -> "Slimefun-compatible storage"
        }
        return "$implementation ($version)"
    }

    @Suppress("UNCHECKED_CAST")
    private fun findMutableMap(target: Any, getterName: String): MutableMap<String, Any>? = runCatching {
        target.javaClass.methods.first { it.name == getterName && it.parameterCount == 0 }
            .invoke(target) as MutableMap<String, Any>
    }.getOrNull()

    private fun classExists(name: String) = runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
}
