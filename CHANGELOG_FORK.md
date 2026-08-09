## Legacy runtime hotfix 5 - cross-addon Slimefun ID ownership

- Fixed IE1 migration aliases pre-claiming legitimate Slimefun IDs from other addons during startup.
- Startup now installs only explicit historical InfinityExpansion v1 aliases; generic `IE_` prefix aliases wait until Slimefun addon registration has finalized.
- Added canonical-owner protection so the migration scanner will not rewrite an item/block ID that is currently owned by another registered addon.
- Prevents known collisions such as ExoticGarden `ENDER_ESSENCE` versus IE2 `IE_ENDER_ESSENCE` and ExtraTools `COBBLESTONE_GENERATOR` versus IE2 `IE_COBBLESTONE_GENERATOR`.
- Slimefun's duplicate-ID protection remains intact; this fix does not weaken core conflict detection.

# Fork changelog

## Legacy compatibility foundation

- Added IE1 persisted block-ID aliasing and permanent IE1 -> IE2 record migration.
- Added migration of IE1 items across players, containers, Slimefun menus and loaded entities.
- Added renamed/tiered ID translations plus dynamic old MobSim card and quarry oscillator mappings.
- Added IE1 filled-storage PDC conversion and capacity-equivalent IE2 Storage Unit tiers.
- Added `/ie2 doctor status|scan|migrate|refresh`.
- Added current IE2 item/armor refresh preserving durability, enchantments, trims and non-conflicting PDC.
- Preserved the Legacy Mob Simulation power fix (base chamber power by default), and hardened output transaction/stacked-card behavior.
- Set Paper 26.2 as the primary compile/run target with Java 25 build tooling and Java 21 addon bytecode.
- Disabled runtime upstream JAR replacement.
- Added CI, release and reviewable upstream-sync GitHub Actions workflows.
