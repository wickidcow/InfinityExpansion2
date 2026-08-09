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
