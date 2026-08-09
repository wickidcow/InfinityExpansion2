# Compatibility design

## Slimefun

Slimefun Legacy is the primary target. The fork compiles against the common Slimefun API surface and avoids compile-time references to the storage-controller implementation classes that differ between fork families.

At runtime, the compatibility bridge detects Legacy/Gugu-style or Core/United-style storage layouts and reflectively locates the loaded block-data controller/cache. Legacy-ID alias installation is also isolated behind the registry API so unsupported internals fail closed and are reported by `/ie2 doctor status`.

This preserves compatibility architecture across:

- Slimefun Legacy;
- Gugu Slimefun;
- Slimefun United;
- official/Core-style Slimefun forks.

A Slimefun fork still needs to support the Minecraft/Paper version being run. The IE2 addon cannot make an older Slimefun runtime compatible with Paper 26.2 by itself.

## Java / Paper

Paper 26.2 is the CI and run-paper target and is built with a Java 25 toolchain. The addon emits Java 21 bytecode to keep its own class-version floor at Java 21 for other supported server/fork combinations.

## Updating

The runtime binary self-updater is disabled. Upstream changes are merged through `.github/workflows/upstream-sync.yml` into a review branch. This makes migration mappings, reflection compatibility, MobSim power logic and modern item behavior explicit review points whenever upstream changes those areas.
