package net.guizhanss.infinityexpansion2.core.commands.subcommands

import net.guizhanss.guizhanlib.minecraft.commands.AbstractCommand
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.commands.AbstractSubCommand
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale

class DoctorCommand(parent: AbstractCommand) : AbstractSubCommand(
    parent, "doctor", "<status|scan|migrate|refresh>"
) {
    override fun onExecute(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission()) {
            sender.sendMessage("${ChatColor.RED}You do not have permission to use IE2 Doctor.")
            return
        }

        val service = InfinityExpansion2.migrationService
        when (args.firstOrNull()?.lowercase(Locale.ENGLISH) ?: "status") {
            "status" -> {
                val aliases = service.aliasesInstalled
                sender.sendMessage("${ChatColor.GOLD}InfinityExpansion2 Doctor")
                sender.sendMessage("${ChatColor.GRAY}Slimefun runtime: ${ChatColor.WHITE}${service.compatibility.runtimeDescription()}")
                sender.sendMessage("${ChatColor.GRAY}Migration enabled: ${status(InfinityExpansion2.configService.migrationEnabled.value)}")
                sender.sendMessage("${ChatColor.GRAY}Automatic block migration: ${status(InfinityExpansion2.configService.migrationAutoBlocks.value)}")
                sender.sendMessage("${ChatColor.GRAY}Automatic item migration: ${status(InfinityExpansion2.configService.migrationAutoItems.value)}")
                sender.sendMessage("${ChatColor.GRAY}Refresh current IE2 gear on join: ${status(InfinityExpansion2.configService.migrationRefreshModernItems.value)}")
                sender.sendMessage("${ChatColor.GRAY}Legacy aliases: ${ChatColor.WHITE}${aliases.installed} installed, ${aliases.skippedExisting} already owned, ${aliases.totalResolved} resolved, ${aliases.failed} failed")
            }

            "scan" -> {
                val stats = service.scanLoaded(false)
                sender.sendMessage("${ChatColor.GOLD}IE2 Doctor scan complete (loaded Slimefun blocks, world inventories/entities, and online players).")
                sender.sendMessage("${ChatColor.YELLOW}${stats.legacyBlocksFound}${ChatColor.GRAY} IE1 block records need migration.")
                sender.sendMessage("${ChatColor.YELLOW}${stats.legacyItemsFound}${ChatColor.GRAY} IE1 inventory items need migration.")
                sender.sendMessage("${ChatColor.GRAY}Unloaded chunks are checked automatically when they load.")
            }

            "migrate" -> {
                val stats = service.scanLoaded(true)
                sender.sendMessage("${ChatColor.GREEN}IE2 migration pass complete.")
                sender.sendMessage("${ChatColor.GRAY}Blocks: ${ChatColor.WHITE}${stats.blocksMigrated}/${stats.legacyBlocksFound}${ChatColor.GRAY}; Items: ${ChatColor.WHITE}${stats.itemsMigrated}/${stats.legacyItemsFound}${ChatColor.GRAY}; Failures: ${ChatColor.WHITE}${stats.blockFailures + stats.itemFailures}")
                sender.sendMessage("${ChatColor.YELLOW}After the first full migration pass, perform a clean server shutdown/restart and run /ie2 doctor scan again.")
            }

            "refresh" -> {
                if (sender !is Player) {
                    sender.sendMessage("${ChatColor.RED}Run this subcommand as a player to refresh that player's IE2 items/armor.")
                    return
                }
                val stats = service.refreshPlayer(sender)
                sender.sendMessage("${ChatColor.GREEN}Refreshed ${stats.refreshedItems + stats.itemsMigrated} IE/IE2 items in your inventory and ender chest.")
            }

            else -> sender.sendMessage("${ChatColor.RED}Usage: /ie2 doctor <status|scan|migrate|refresh>")
        }
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size != 1) return emptyList()
        val current = args[0].lowercase(Locale.ENGLISH)
        return listOf("status", "scan", "migrate", "refresh").filter { it.startsWith(current) }
    }

    private fun status(value: Boolean) = if (value) "${ChatColor.GREEN}enabled" else "${ChatColor.RED}disabled"
}
