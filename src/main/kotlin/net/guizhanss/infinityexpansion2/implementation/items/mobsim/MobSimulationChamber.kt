package net.guizhanss.infinityexpansion2.implementation.items.mobsim

import io.github.schntgaispock.slimehud.util.HudBuilder
import io.github.schntgaispock.slimehud.waila.HudRequest
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.libraries.dough.inventory.InvUtils
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import net.guizhanss.guizhanlib.kt.minecraft.extensions.isAir
import net.guizhanss.guizhanlib.kt.slimefun.extensions.isSlimefunItem
import net.guizhanss.guizhanlib.kt.slimefun.utils.getBlockMenu
import net.guizhanss.guizhanlib.kt.slimefun.utils.getInt
import net.guizhanss.guizhanlib.kt.slimefun.utils.setInt
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.api.mobsim.MobDataCardProps
import net.guizhanss.infinityexpansion2.core.IERegistry
import net.guizhanss.infinityexpansion2.core.items.annotations.HudProvider
import net.guizhanss.infinityexpansion2.core.items.attributes.CustomWikiItem
import net.guizhanss.infinityexpansion2.core.items.attributes.EnergyTickingConsumer
import net.guizhanss.infinityexpansion2.core.items.attributes.InformationalRecipeDisplayItem
import net.guizhanss.infinityexpansion2.core.menu.MenuLayout
import net.guizhanss.infinityexpansion2.implementation.items.machines.abstracts.AbstractTickingMachine
import net.guizhanss.infinityexpansion2.utils.items.GuiItems
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import java.util.logging.Level
import kotlin.math.floor
import kotlin.random.Random

@HudProvider
class MobSimulationChamber(
    itemGroup: ItemGroup,
    itemStack: SlimefunItemStack,
    recipeType: RecipeType,
    recipe: Array<out ItemStack?>,
    energyPerTick: Int,
) : AbstractTickingMachine(itemGroup, itemStack, recipeType, recipe, MenuLayout.SINGLE_INPUT, energyPerTick),
    EnergyTickingConsumer, InformationalRecipeDisplayItem, CustomWikiItem {

    override val wikiUrl = "mob-simulation/chamber"

    private val energyCapacitySetting =
        IntRangeSetting(
            this,
            "energy-capacity",
            1,
            (energyPerTick.toLong() * 1000L).coerceAtMost(MAX_SAFE_ENERGY.toLong()).toInt(),
            MAX_SAFE_ENERGY
        )

    init {
        addItemSetting(energyCapacitySetting)
    }

    override fun postRegister() {
        super.postRegister()

        if (getEnergyConsumptionPerTick() > capacity) {
            InfinityExpansion2.log(Level.WARNING, "Invalid item settings for $id:")
            InfinityExpansion2.log(
                Level.WARNING,
                "The base energy consumption is larger than energy capacity."
            )
            InfinityExpansion2.log(Level.WARNING, "Using default value now, please update the config.")
            energyPerTickSetting.update(energyPerTickSetting.defaultValue)
            energyCapacitySetting.update(energyCapacitySetting.defaultValue)
        }
    }

    override fun getCapacity() = energyCapacitySetting.value

    override fun onNewInstance(menu: BlockMenu, b: Block) {
        val l = b.location

        // xp button
        menu.replaceExistingItem(XP_SLOT, GuiItems.experience(0))
        menu.addMenuClickHandler(XP_SLOT) { p, _, _, _ ->
            val xp = l.getInt(XP_KEY)
            if (xp > 0) {
                p.giveExp(xp)
                p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                l.setInt(XP_KEY, 0)
                menu.replaceExistingItem(XP_SLOT, GuiItems.experience(0))
            }
            false
        }
    }

    override fun process(b: Block, menu: BlockMenu): Boolean {
        val l = b.location
        val (props, cardAmount) = menu.getDataCard(layout) ?: run {
            menu.setStatus { GuiItems.INVALID_INPUT }
            menu.setEnergyConsumption(0)
            return false
        }

        // When stacked cards are enabled, one card stack represents parallel simulations.
        val multiplier = if (InfinityExpansion2.configService.mobSimAllowStackedCard.value) cardAmount else 1

        // Legacy/Albion power compatibility.
        //
        // AbstractTickingMachine.tick() has already verified that the chamber has at least its
        // configured base charge before process() is called. Do not perform a second base/card
        // charge gate here in compatibility mode: the proven Albion MobSim patch deliberately
        // avoided that second gate because it can report NO_POWER on Slimefun forks even while
        // the surrounding energy network has ample stored power.
        //
        // charge-card-energy=false (default): preserve the previously tested compatibility
        // behavior. The superclass performs the base-power presence check and this process tick
        // removes one unit of local charge. This is intentionally conservative and prevents the
        // false "Insufficient Power" regression seen on Legacy/Gugu-style energy runtimes.
        //
        // charge-card-energy=true: opt back into upstream-style full card-energy charging.
        val chargeCardEnergy = InfinityExpansion2.configService.mobSimChargeCardEnergy.value
        val requestedEnergy = if (chargeCardEnergy) {
            getEnergyConsumptionPerTick().toLong() + props.energy.toLong() * multiplier.toLong()
        } else {
            getEnergyConsumptionPerTick().toLong()
        }

        val displayEnergy = requestedEnergy.coerceIn(1L, MAX_SAFE_ENERGY.toLong()).toInt()
        val energyToDrain = if (chargeCardEnergy) {
            if (
                requestedEnergy <= 0L ||
                requestedEnergy > capacity.toLong() ||
                requestedEnergy > MAX_SAFE_ENERGY.toLong() ||
                getCharge(menu.location).toLong() < requestedEnergy
            ) {
                menu.setStatus { GuiItems.NO_POWER }
                menu.setEnergyConsumption(0)
                return false
            }
            requestedEnergy.toInt()
        } else {
            LEGACY_COMPATIBILITY_DRAIN
        }

        val currentXp = l.getInt(XP_KEY)
        menu.setStatus { GuiItems.PRODUCING }
        menu.setEnergyConsumption(displayEnergy)
        menu.replaceExistingItem(XP_SLOT, GuiItems.experience(currentXp))

        val outputTicks = InfinityExpansion2.configService.mobSimInterval.value.toLong() * getCustomTickRate().toLong()
        if (outputTicks > 0L && tickCount.toLong() % outputTicks == 0L) {
            val drops = if (InfinityExpansion2.configService.mobSimLegacyOutput.value) {
                expandDrop(props.getRandomDrop(), multiplier)?.toMutableList()
            } else {
                val generated = mutableListOf<ItemStack>()
                var overflow = false
                props.drops.forEach { (item, chance) ->
                    if (Random.nextDouble() <= chance) {
                        val expanded = expandDrop(item, multiplier)
                        if (expanded == null || generated.size + expanded.size > MAX_OUTPUT_STACKS) {
                            overflow = true
                            return@forEach
                        }
                        generated.addAll(expanded)
                    }
                }
                if (overflow) null else generated
            }

            // Never silently truncate a large stacked-card output. If it cannot be represented or
            // does not fit, produce nothing, grant no XP, and consume no energy for this tick.
            if (drops == null || !InvUtils.fitAll(menu.toInventory(), drops.toTypedArray(), *outputSlots)) {
                menu.setStatus { GuiItems.NO_ROOM }
                return false
            }

            drops.forEach { menu.pushItem(it, *outputSlots) }

            val xp = floor(
                props.experience * InfinityExpansion2.configService.mobSimExpMultiplier.value * multiplier.toDouble()
            ).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
            val newXp = (currentXp.toLong() + xp.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            l.setInt(XP_KEY, newXp)
            menu.replaceExistingItem(XP_SLOT, GuiItems.experience(newXp))
        }

        removeCharge(l, energyToDrain)
        // handle custom energy consumption, so always return false
        return false
    }

    private fun expandDrop(item: ItemStack, multiplier: Int): List<ItemStack>? {
        if (multiplier <= 0 || item.type.isAir) return emptyList()
        val max = item.maxStackSize.coerceAtLeast(1)
        val total = item.amount.toLong() * multiplier.toLong()
        if (total <= 0L) return emptyList()
        val needed = (total + max - 1L) / max.toLong()
        if (needed > MAX_OUTPUT_STACKS.toLong()) return null

        var remaining = total
        return buildList(needed.toInt()) {
            while (remaining > 0L) {
                val amount = minOf(remaining, max.toLong()).toInt()
                add(item.clone().apply { setAmount(amount) })
                remaining -= amount.toLong()
            }
        }
    }

    private fun BlockMenu.setEnergyConsumption(value: Int = 0) {
        if (hasViewer()) {
            val item = if (value > 0) {
                GuiItems.energyConsumptionPerTick(value, getCustomTickRate())
            } else {
                ChestMenuUtils.getBackground()
            }
            replaceExistingItem(ENERGY_CONSUMPTION_SLOT, item)
        }
    }

    override fun getInfoItems() = listOf(
        GuiItems.tickRate(getCustomTickRate()),
        GuiItems.energyConsumptionPerTick(getEnergyConsumptionPerTick()),
        GuiItems.outputInterval(InfinityExpansion2.configService.mobSimInterval.value),
    )

    companion object {

        private const val ENERGY_CONSUMPTION_SLOT = 5
        private const val XP_SLOT = 8
        private const val XP_KEY = "xp"
        private const val MAX_SAFE_ENERGY = 2_000_000_000
        private const val LEGACY_COMPATIBILITY_DRAIN = 1
        private const val MAX_OUTPUT_STACKS = 4096

        /**
         * Get the data card from the menu input slot (the layout must be [MenuLayout.SINGLE_INPUT]).
         * If the input is invalid, return null.
         * Returns the [MobDataCardProps] and the amount of the card.
         */
        private fun BlockMenu.getDataCard(layout: MenuLayout): Pair<MobDataCardProps, Int>? {
            val input = getItemInSlot(layout.inputSlots[0])
            if (input.isAir() || !input.isSlimefunItem<MobDataCard>()) {
                return null
            }

            // check if input is a registered card
            val id = MobDataCard.getMobDataId(input) ?: return null
            val props = IERegistry.mobDataCards[id] ?: return null

            return props to input.amount
        }

        @Suppress("unused")
        fun hudHandler(request: HudRequest): String {
            val loc = request.location
            val menu = loc.getBlockMenu()
            val machine = request.slimefunItem as MobSimulationChamber

            val card = menu.getDataCard(machine.layout)
            return buildString {
                if (card != null) {
                    append("&b${card.first.name}")
                    append("&7 | ")
                }

                append(HudBuilder.formatEnergyStored(machine.getCharge(request.location), machine.capacity))
            }
        }
    }
}
