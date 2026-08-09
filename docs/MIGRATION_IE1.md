# InfinityExpansion v1 -> InfinityExpansion2 migration

## Why old placed blocks can break

InfinityExpansion v1 persisted its Slimefun block IDs without the IE2 `IE_` prefix. IE2 registers a different item ID for the replacement. If IE1 is removed before old block records are translated, Slimefun can encounter an ID that no installed addon owns. This fork installs temporary aliases and then rewrites loaded block records to the real IE2 ID.

The migration is **idempotent**: an `IE_` record is already modern and is ignored on later passes.

## Recommended migration sequence

1. Make an offline backup of the world, plugin configs, Slimefun storage/database and player data.
2. Start once with both IE1 and this IE2 fork installed.
3. Run `/ie2 doctor status`.
4. Run `/ie2 doctor scan`.
5. Load the regions containing IE1 machines. A pregenerator may be used if you already use one, but this fork intentionally does not force-load an entire world itself.
6. Run `/ie2 doctor migrate`.
7. Run `/ie2 doctor scan` again and review the console for migration failures.
8. Cleanly stop the server so Slimefun can flush persistent data.
9. Remove IE1.
10. Start with this fork and run `/ie2 doctor scan` again.

Unloaded chunks do not need to be mass-loaded before IE1 is removed: legacy aliases keep recognized old IDs resolvable, and the chunk-load migration rewrites them when the chunk is encountered later.

## What is migrated

### Slimefun block records

The doctor preserves the old block's Slimefun key/value data and menu contents, recreates the record with the target IE2 ID, and restores the stored data/items. It **does not change the physical Bukkit block Material**, because doing that during a persistence migration can destroy vanilla block state or trigger unrelated block behavior.

For IE1 Storage Units, block key `stored` is translated to IE2's `stored_amount` key.

### Items

Migration covers:

- player inventory, armor/offhand and ender chest;
- loaded vanilla container inventories;
- Slimefun block menus being migrated;
- existing/spawned item entities;
- item frames and item displays;
- entity equipment;
- nested container/shulker items exposed through `BlockStateMeta`;
- bundle contents;
- virtual inventories from loaded Slimefun block menus, including valid IE2 machines that still contain IE1 items.

Fresh IE2 templates are used so modern item metadata/display/attributes are restored. Durability, player enchantments, armor trim and non-conflicting persistent data are preserved.

### Filled IE1 Storage Units

IE1 item-form Storage Units used PDC keys named `item` and `stored`, with the stored ItemStack serialized through an older YAML representation. The compatibility bridge decodes those primitives without requiring InfinityLib, then writes IE2's native `StorageCache` PDC.

The fork adds IE2 tiers matching IE1 capacities:

| IE1 | IE2 migration target | Capacity |
|---|---|---:|
| BASIC_STORAGE | IE_STORAGE_UNIT_2 | 6,400 |
| ADVANCED_STORAGE | IE_STORAGE_UNIT_3 | 25,600 |
| REINFORCED_STORAGE | IE_STORAGE_UNIT_4 | 102,400 |
| VOID_STORAGE | IE_STORAGE_UNIT_5 | 409,600 |
| INFINITY_STORAGE | IE_STORAGE_UNIT_6 | 1,600,000,000 |

## Special ID translations

Most items use `OLD_ID -> IE_OLD_ID`. Renamed families include:

- `INFINITE_MACHINE_CIRCUIT -> IE_INFINITY_MACHINE_CIRCUIT`
- `INFINITE_MACHINE_CORE -> IE_INFINITY_MACHINE_CORE`
- `END_ESSENCE -> IE_ENDER_ESSENCE`
- `BASIC/ADVANCED/REINFORCED_STRAINER -> IE_STRAINER_1/2/3`
- old three-tier cobble/farm/tree machines -> nearest IE2 tier;
- old generator names -> corresponding IE2 generator tiers;
- old storage names -> capacity-equivalent IE2 storage tiers;
- `EMPTY_DATA_CARD -> IE_MOB_DATA_CARD_EMPTY`
- `DATA_INFUSER -> IE_MOB_DATA_INFUSER`
- `<MOB>_DATA_CARD -> IE_MOB_DATA_CARD_<MOB>` dynamically;
- `QUARRY_OSCILLATOR_<RESOURCE> -> IE_OSCILLATOR_<RESOURCE>` dynamically.

## Doctor interpretation

A clean scan of the **currently loaded** world reports zero recognized IE1 block records and zero recognized IE1 item stacks. This is not a claim that never-loaded chunks contain none; those chunks are checked when they load.

Any migration exception is logged with the source/target ID and, for blocks, the location. Do not delete the backup until the server has completed at least one clean shutdown/restart after IE1 removal.
