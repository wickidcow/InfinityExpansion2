package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem

/**
 * Maps InfinityExpansion v1 ids to their InfinityExpansion2 replacements.
 *
 * Most IE1 ids are the IE2 id without the IE_ prefix. Renamed/tiered items and
 * the two dynamic item families (mob cards / quarry oscillators) need explicit
 * translation so old inventories and block records survive removal of IE1.
 */
object LegacyIdMapper {

    @Volatile
    private var postRegistrationMappingsEnabled = false

    private val explicit = linkedMapOf(
        // Renamed core materials / workbench
        "INFINITE_INGOT" to "IE_INFINITY_INGOT",
        "INFINITE_MACHINE_CIRCUIT" to "IE_INFINITY_MACHINE_CIRCUIT",
        "INFINITE_MACHINE_CORE" to "IE_INFINITY_MACHINE_CORE",
        "END_ESSENCE" to "IE_ENDER_ESSENCE",
        "INFINITY_FORGE" to "IE_INFINITY_WORKBENCH",

        // Legacy strainers
        "BASIC_STRAINER" to "IE_STRAINER_1",
        "ADVANCED_STRAINER" to "IE_STRAINER_2",
        "REINFORCED_STRAINER" to "IE_STRAINER_3",

        // Legacy three-tier cobble/farm/tree machines -> closest IE2 tier
        "BASIC_COBBLE_GEN" to "IE_COBBLESTONE_GENERATOR",
        "ADVANCED_COBBLE_GEN" to "IE_COBBLESTONE_GENERATOR_2",
        "INFINITY_COBBLE_GEN" to "IE_COBBLESTONE_GENERATOR_4",
        "BASIC_VIRTUAL_FARM" to "IE_VIRTUAL_FARM",
        "ADVANCED_VIRTUAL_FARM" to "IE_VIRTUAL_FARM_2",
        "INFINITY_VIRTUAL_FARM" to "IE_VIRTUAL_FARM_4",
        "BASIC_TREE_GROWER" to "IE_TREE_GROWER",
        "ADVANCED_TREE_GROWER" to "IE_TREE_GROWER_2",
        "INFINITY_TREE_GROWER" to "IE_TREE_GROWER_4",

        // Quarries
        "BASIC_QUARRY" to "IE_QUARRY",
        "ADVANCED_QUARRY" to "IE_QUARRY_2",
        "VOID_QUARRY" to "IE_QUARRY_3",
        "INFINITY_QUARRY" to "IE_QUARRY_4",

        // Upgraded production machines
        "INFINITE_VOID_HARVESTER" to "IE_VOID_HARVESTER_3",
        "INFINITY_CONSTRUCTOR" to "IE_SINGULARITY_CONSTRUCTOR_2",
        "INFINITY_DUST_EXTRACTOR" to "IE_DUST_EXTRACTOR_4",
        "INFINITY_INGOT_FORMER" to "IE_INGOT_FORMER_4",
        "BASIC_OBSIDIAN_GEN" to "IE_OBSIDIAN_GENERATOR",

        // Generators
        "HYDRO_GENERATOR" to "IE_HYDRO_GENERATOR",
        "REINFORCED_HYDRO_GENERATOR" to "IE_HYDRO_GENERATOR_2",
        "GEOTHERMAL_GENERATOR" to "IE_GEOTHERMAL_GENERATOR",
        "REINFORCED_GEOTHERMAL_GENERATOR" to "IE_GEOTHERMAL_GENERATOR_2",
        "BASIC_PANEL" to "IE_SOLAR_PANEL",
        "ADVANCED_PANEL" to "IE_SOLAR_PANEL_2",
        "CELESTIAL_PANEL" to "IE_SOLAR_PANEL_3",
        "VOID_PANEL" to "IE_VOID_PANEL",
        "INFINITE_PANEL" to "IE_INFINITY_PANEL",

        // Mob simulation
        "EMPTY_DATA_CARD" to "IE_MOB_DATA_CARD_EMPTY",
        "DATA_INFUSER" to "IE_MOB_DATA_INFUSER",

        // IE1 storage capacity equivalents. IE2 tier 1 remains the native 1,024 unit.
        "BASIC_STORAGE" to "IE_STORAGE_UNIT_2",
        "ADVANCED_STORAGE" to "IE_STORAGE_UNIT_3",
        "REINFORCED_STORAGE" to "IE_STORAGE_UNIT_4",
        "VOID_STORAGE" to "IE_STORAGE_UNIT_5",
        "INFINITY_STORAGE" to "IE_STORAGE_UNIT_6",
    )

    /** Returns an existing IE2 id for a legacy id, or null when no safe mapping exists. */
    fun targetFor(sourceId: String?): String? {
        if (sourceId.isNullOrBlank() || sourceId.startsWith("IE_")) return null

        /*
         * Never migrate an id that is canonically owned by another registered addon.
         * IE2's temporary compatibility aliases intentionally point an old key at an
         * IE_ item whose real id is different, so those remain migratable.
         */
        val currentOwner = SlimefunItem.getById(sourceId)
        if (currentOwner != null && currentOwner.id == sourceId) return null

        explicit[sourceId]?.let { target ->
            if (SlimefunItem.getById(target) != null) return target
        }

        // Do not guess generic aliases until addon registration has completed. Before that
        // another addon may still be about to claim this un-prefixed id canonically.
        if (!postRegistrationMappingsEnabled) return null

        // IE1 dynamic cards were COW_DATA_CARD, ZOMBIE_DATA_CARD, ...
        if (sourceId.endsWith("_DATA_CARD") && sourceId != "EMPTY_DATA_CARD") {
            val mob = sourceId.removeSuffix("_DATA_CARD")
            val target = "IE_MOB_DATA_CARD_$mob"
            if (SlimefunItem.getById(target) != null) return target
        }

        // IE1 dynamic oscillators were QUARRY_OSCILLATOR_DIAMOND, ...
        if (sourceId.startsWith("QUARRY_OSCILLATOR_")) {
            val resource = sourceId.removePrefix("QUARRY_OSCILLATOR_")
            val target = "IE_OSCILLATOR_$resource"
            if (SlimefunItem.getById(target) != null) return target
        }

        // The common migration path: IE1 FOO -> IE2 IE_FOO.
        val prefixed = "IE_$sourceId"
        return prefixed.takeIf { SlimefunItem.getById(it) != null }
    }

    fun enablePostRegistrationMappings() {
        postRegistrationMappingsEnabled = true
    }

    /**
     * Startup-safe aliases are restricted to the explicit IE1 ids we know historically
     * belonged to InfinityExpansion. Generic IE_ prefix aliases are deliberately omitted
     * here because another addon may legitimately register the un-prefixed id later in
     * the same server startup.
     */
    fun resolvedStartupAliases(): Map<String, String> = explicit
        .filterValues { SlimefunItem.getById(it) != null }

    /** All aliases that can currently be resolved to a registered IE2 item. */
    fun resolvedAliases(): Map<String, String> {
        val aliases = LinkedHashMap<String, String>()

        explicit.forEach { (oldId, newId) ->
            if (SlimefunItem.getById(newId) != null) aliases[oldId] = newId
        }

        val registryIds = registeredIds()
        registryIds.filter { it.startsWith("IE_") }.forEach { newId ->
            aliases.putIfAbsent(newId.removePrefix("IE_"), newId)

            if (newId.startsWith("IE_MOB_DATA_CARD_") && newId != "IE_MOB_DATA_CARD_EMPTY") {
                aliases.putIfAbsent("${newId.removePrefix("IE_MOB_DATA_CARD_")}_DATA_CARD", newId)
            }
            if (newId.startsWith("IE_OSCILLATOR_")) {
                aliases.putIfAbsent("QUARRY_OSCILLATOR_${newId.removePrefix("IE_OSCILLATOR_")}", newId)
            }
        }

        return aliases
    }

    private fun registeredIds(): Set<String> = runCatching {
        val slimefunClass = Class.forName("io.github.thebusybiscuit.slimefun4.implementation.Slimefun")
        val registryObject = slimefunClass.getMethod("getRegistry").invoke(null)
        val getter = registryObject.javaClass.methods.firstOrNull {
            it.name == "getSlimefunItemIds" && it.parameterCount == 0
        }
        @Suppress("UNCHECKED_CAST")
        (getter?.invoke(registryObject) as? Map<String, *>)?.keys.orEmpty()
    }.getOrDefault(emptySet())

    fun explicitMappings(): Map<String, String> = explicit.toMap()
}
