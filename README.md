<div align="center">

# ♾️⚙️ InfinityExpansion2 — Slimefun Legacy

**End-game machines, storage, Mob Simulation, extreme resources, and a safer IE1 → IE2 migration path.**

![Slimefun Legacy](https://img.shields.io/badge/Slimefun-Legacy-6bd425?style=for-the-badge)
![Paper 26.2](https://img.shields.io/badge/Paper-26.2-blue?style=for-the-badge)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)
![Maintained for AlbionMC.com](https://img.shields.io/badge/Maintained%20for-albionmc.com-7b68ee?style=for-the-badge)

</div>

> [!IMPORTANT]
> This is an **unofficial Slimefun Legacy compatibility fork** of InfinityExpansion2, developed for use on **albionmc.com**. It preserves the upstream IE2 rewrite while adding migration and safety work for established InfinityExpansion v1 worlds.

## ♾️ What does InfinityExpansion2 do?

InfinityExpansion2 is a large end-game Slimefun addon centered around advanced resources, high-tier machines, storage, automation, and Mob Simulation. It is the Kotlin rewrite in the Infinity Expansion project family.

This fork additionally focuses on helping existing servers move from **InfinityExpansion v1 (IE1)** to **InfinityExpansion2 (IE2)** without casually abandoning old item/block data.

## 🔄 IE1 → IE2 migration support

The maintained fork adds:

- placed-block migration from legacy IE1 IDs to IE2 IDs;
- item migration in player inventories, ender chests, vanilla containers, Slimefun machine menus, dropped items, displays/item frames, and entity equipment where supported;
- explicit mappings for renamed items plus normal `FOO → IE_FOO` compatibility mapping;
- migration for dynamic old MobSim cards and quarry oscillators;
- filled IE1 Storage Unit migration, including serialized stored-item data and old capacities;
- additional IE2 storage tiers matching historical IE1 capacity milestones;
- `/ie2 doctor` commands for status, scan, migration, and item/armor refresh;
- compatibility aliases for legacy records that are encountered after the first migration pass.

### Recommended migration process

1. Back up the complete server, worlds, Slimefun data/database, and plugin folders.
2. On a staging copy, run IE1 and this IE2 fork together for the initial migration pass where supported.
3. Run `/ie2 doctor status` and `/ie2 doctor scan`.
4. Load areas containing old IE1 machines/storage and run `/ie2 doctor migrate`.
5. Run another scan and perform a clean shutdown.
6. Remove IE1, keep IE2, restart, and scan again.
7. Test old storage, machines, Mob Simulation, items, armor, and unloaded/then-loaded chunks before production use.

See `docs/MIGRATION_IE1.md` for the detailed procedure and limitations.

## 🛡️ Slimefun Legacy maintenance

Additional compatibility/safety work includes:

- Slimefun Legacy as the primary Slimefun target;
- Paper 26.2 / Java 25 build/runtime support with Java 21 plugin bytecode;
- Mob Simulation power/output hardening and overflow-safe arithmetic;
- stacked-card product/XP handling and no XP-without-output behavior;
- item/armor template refresh while preserving durability, enchantments, trims, and non-conflicting persistent data;
- reflective isolation around Slimefun storage-controller implementation differences;
- CI, tagged releases, and upstream-sync review workflow;
- disabling the old runtime self-updater so it cannot replace this fork with an unrelated upstream binary.

## ❤️ Credits & project lineage

- **Mooy1** — original creator of **InfinityExpansion**, the project and gameplay lineage from which IE2 ultimately descends.
- **GuizhanCraft/InfinityExpansion2** — creator/maintainer community of the current Kotlin IE2 rewrite and the immediate upstream repository for this fork.
- **InfinityExpansion / InfinityExpansion2 contributors** — years of machines, storage, Mob Simulation, fixes, translations, and compatibility work.
- **Slimefun developers and contributors** — for the platform and addon APIs IE/IE2 extend.
- **wickidcow / Slimefun Legacy** — current IE1 migration, safety, and compatibility maintenance for modern servers and albionmc.com.

This repository preserves upstream credits and does not claim authorship of the original InfinityExpansion or InfinityExpansion2 work.

## 📜 GNU General Public License v3.0

InfinityExpansion2 is licensed under the **GNU General Public License v3.0 (GPLv3)**. See `LICENSE` for the complete terms.

If you distribute IE2 or a modified GPL-covered version, comply with GPLv3, including preserving applicable notices, identifying modified versions, licensing covered modified source under GPLv3, and making the required Corresponding Source available when distributing object code.

The software is provided **without warranty** as described by GPLv3.

## ⚖️ Independence & trademark notice

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

InfinityExpansion2, Slimefun Legacy, and this maintenance fork are independent community projects. They are not sponsored, endorsed, approved, or operated by Mojang Studios or Microsoft. Minecraft-related names, brands, and assets remain the property of their respective rights holders.

This repository is also not represented as an official release of Mooy1, GuizhanCraft, the original Slimefun developers, or other upstream contributors unless explicitly stated by those parties.

---

<div align="center">

**♾️ Preserve the old world. Build the next tier. ⚙️**

</div>
