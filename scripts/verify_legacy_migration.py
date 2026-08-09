#!/usr/bin/env python3
"""Static safety invariants for the IE1 -> IE2 compatibility layer."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
mapper = (root / "src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyIdMapper.kt").read_text()
service = (root / "src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyMigrationService.kt").read_text()
bridge = (root / "src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/SlimefunCompatibilityBridge.kt").read_text()
registry_listener = (root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/listeners/SlimefunRegistryListener.kt").read_text()
build = (root / "build.gradle.kts").read_text()
config = (root / "src/main/resources/config.yml").read_text()
mobsim = (root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/mobsim/MobSimulationChamber.kt").read_text()
wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text()
main_plugin = (root / "src/main/kotlin/net/guizhanss/infinityexpansion2/InfinityExpansion2.kt").read_text()

required_mappings = {
    '"INFINITE_MACHINE_CIRCUIT" to "IE_INFINITY_MACHINE_CIRCUIT"',
    '"INFINITE_MACHINE_CORE" to "IE_INFINITY_MACHINE_CORE"',
    '"END_ESSENCE" to "IE_ENDER_ESSENCE"',
    '"BASIC_STORAGE" to "IE_STORAGE_UNIT_2"',
    '"INFINITY_STORAGE" to "IE_STORAGE_UNIT_6"',
    '"EMPTY_DATA_CARD" to "IE_MOB_DATA_CARD_EMPTY"',
    '"DATA_INFUSER" to "IE_MOB_DATA_INFUSER"',
}
errors = [f"missing mapping: {m}" for m in sorted(required_mappings) if m not in mapper]
if 'sourceId.endsWith("_DATA_CARD")' not in mapper:
    errors.append("dynamic IE1 mob-card migration is missing")
if 'sourceId.startsWith("QUARRY_OSCILLATOR_")' not in mapper:
    errors.append("dynamic IE1 oscillator migration is missing")
if 'location.block.setType' in service:
    errors.append("migration must not change the physical Bukkit block material")
if 'data["stored"]' not in service or '"stored_amount"' not in service:
    errors.append("legacy block storage amount translation is missing")
if 'scanSlimefunMenus' not in service:
    errors.append("virtual Slimefun machine-menu item migration is missing")
if 'paperApiVersion=26.2.build.+' in build:
    errors.append("unexpected literal property syntax in Gradle source")
if 'orElse("26.2.build.+")' not in build:
    errors.append("Paper 26.2 is not the default compile target")
if 'jvmTarget = JvmTarget.JVM_21' not in build:
    errors.append("plugin bytecode target is not Java 21")
if 'gradle-9.3.0-bin.zip' not in wrapper:
    errors.append("Java 25 build requires the Gradle 9.3.0 wrapper")
if 'kotlin("jvm") version "2.3.21"' not in build:
    errors.append("Kotlin Gradle plugin must remain on the Java-25-capable 2.3.21 line")
if main_plugin.count('.version("2.3.21")') < 2:
    errors.append("runtime Kotlin stdlib/reflect versions must match Kotlin 2.3.21")
# AbstractAddon invokes autoUpdate() before enable(); this hook must never touch lateinit
# configService/instance-backed helpers or the plugin will fail during onEnable.
auto_update_body = main_plugin.split("override fun autoUpdate()", 1)[1].split("private fun setupListeners()", 1)[0]
if "configService" in auto_update_body or "log(" in auto_update_body:
    errors.append("autoUpdate lifecycle hook must remain config/instance-free before enable()")
if 'auto-update: false' not in config:
    errors.append("runtime upstream self-update must remain disabled")
if 'charge-card-energy: false' not in config:
    errors.append("MobSim Legacy power compatibility must default to base chamber energy only")
if 'mobSimChargeCardEnergy' not in mobsim or 'getEnergyConsumptionPerTick().toLong()' not in mobsim:
    errors.append("MobSim base-power compatibility path is missing")

if 'currentOwner != null && currentOwner.id == sourceId' not in mapper:
    errors.append("migration must not rewrite ids canonically owned by another addon")
if 'postRegistrationMappingsEnabled' not in mapper or 'if (!postRegistrationMappingsEnabled) return null' not in mapper:
    errors.append("post-registration generic migration gate is missing")
if 'LegacyIdMapper.enablePostRegistrationMappings()' not in service:
    errors.append("full alias installation must enable generic migration only after registration")
if 'fun resolvedStartupAliases()' not in mapper or '.filterValues { SlimefunItem.getById(it) != null }' not in mapper:
    errors.append("startup alias set must be restricted to explicit resolved IE1 ids")
if 'installStartupAliases()' not in bridge or 'LegacyIdMapper.resolvedStartupAliases()' not in bridge:
    errors.append("startup-safe alias installation path is missing")
if 'migrationService.installStartupAliases()' not in main_plugin:
    errors.append("plugin startup must install only startup-safe aliases")
if 'migrationService.installAliases()' in main_plugin:
    errors.append("plugin startup must not install the full generic alias set before other addons register")
if 'EventPriority.HIGHEST' not in registry_listener or 'fun installMigrationAliases' not in registry_listener:
    errors.append("post-registration alias installation must run after normal finalized-event registration")
if 'InfinityExpansion2.migrationService.installAliases()' not in registry_listener:
    errors.append("full alias set is not installed after addon registration finalizes")

if errors:
    print("Legacy migration verification failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)
print("Legacy migration verification passed.")
