# InfinityExpansion2 Legacy Compatibility Fork

This fork keeps the current InfinityExpansion2 rewrite while adding a **safe upgrade path from InfinityExpansion v1**, with **Slimefun Legacy as the primary compatibility target** and Paper **26.2** as the primary server target.

The upstream IE2 project intentionally warns that it is not a drop-in replacement for IE1. This fork adds the missing migration layer instead of expecting an old IE1 world to survive unmodified.

## What this fork adds

- IE1 -> IE2 **placed-block migration**. Old unprefixed Slimefun IDs are kept resolvable long enough to rewrite the persisted block record to the IE2 ID.
- IE1 -> IE2 **item migration** in player inventories, ender chests, loaded vanilla containers, Slimefun machine menus, dropped items, item frames/displays and entity equipment.
- Explicit mappings for IE1 IDs whose IE2 name changed, plus automatic `FOO -> IE_FOO` mapping for normal IDs.
- Dynamic migration for old `COW_DATA_CARD`-style MobSim cards and `QUARRY_OSCILLATOR_*` items.
- Filled IE1 Storage Unit migration, including the old serialized stored-item data and old storage capacities.
- Additional IE2 storage tiers matching IE1's 6,400 / 25,600 / 102,400 / 409,600 / 1.6b capacities.
- `/ie2 doctor` status, scan, migration and item/armor refresh commands.
- Mob Simulation power/output hardening: the Legacy power fix consumes chamber base power by default (optional upstream card-energy charging), with overflow-safe math, stacked-card product/XP handling, no XP-without-output, and no silent stack truncation.
- Modern IE2 item/armor template refresh on join while retaining durability, enchantments, armor trims and non-conflicting PDC.
- Runtime Slimefun storage-controller access is isolated behind reflection so the addon does not link directly to either the Legacy/Gugu or Core/United storage implementation package.
- Paper 26.2 build target using Java 25 tooling, with Java 21 plugin bytecode.
- GitHub CI, tagged release builds and an upstream-sync workflow that opens a review PR instead of overwriting fork-specific fixes.

## Safe IE1 server migration

**Back up the complete server first.** For the safest first pass, temporarily run **IE1 and this IE2 fork together**. The fork soft-depends on IE1, so IE1 can own its old IDs while the doctor rewrites loaded data to IE2 IDs.

1. Back up the world and Slimefun data/database.
2. Add this fork while IE1 is still installed and start the server.
3. Run `/ie2 doctor status`, then `/ie2 doctor scan`.
4. Visit/load the areas containing IE1 machines and storage. Loaded chunks are migrated automatically.
5. Run `/ie2 doctor migrate`, then `/ie2 doctor scan` again.
6. Perform a clean shutdown.
7. Remove IE1, keep this fork, and start again.
8. Run `/ie2 doctor scan`. Old records in chunks that were not previously loaded remain protected by compatibility aliases and are migrated when those chunks later load.

See [`docs/MIGRATION_IE1.md`](docs/MIGRATION_IE1.md) for the detailed procedure and limitations.

## Commands

- `/ie2 doctor status` — runtime/fork compatibility and alias status.
- `/ie2 doctor scan` — read-only scan of currently loaded block/item data.
- `/ie2 doctor migrate` — force a migration pass over currently loaded data.
- `/ie2 doctor refresh` — refresh the executing player's current IE2 items/armor from modern templates.

Permission: `infinityexpansion2.command.doctor` (OP by default).

## Runtime targets

| Target | Status in this fork |
|---|---|
| Paper 26.2 | **Primary CI/build target** |
| Slimefun Legacy | **Primary Slimefun target** |
| Gugu Slimefun | Compatibility path retained |
| Slimefun United | Common API/runtime bridge retained; depends on the United build supporting the server version |
| Slimefun Core/official-style forks | Common API/runtime bridge retained |
| Java | Plugin bytecode 21; Paper 26.2 server/build runtime requires Java 25 |

Compatibility with a Slimefun fork does not override that fork's own Minecraft/Paper version limits.

## Updating the fork

- `.github/workflows/ci.yml` builds and verifies Paper 26.2 + migration invariants.
- `.github/workflows/upstream-sync.yml` fetches `GuizhanCraft/InfinityExpansion2:master`, merges it into `automation/upstream-sync`, and opens a PR for review.
- `.github/workflows/release.yml` builds tagged releases.
- The old runtime self-updater is intentionally disabled so it cannot replace this fork with an upstream binary.

## Upstream project

This repository is based on [GuizhanCraft/InfinityExpansion2](https://github.com/GuizhanCraft/InfinityExpansion2), the Kotlin rewrite of Mooy1's original InfinityExpansion. Original credits and license are retained.
