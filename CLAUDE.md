# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

```bash
# Build the mod JAR
./gradlew build

# Check code formatting (required to pass CI)
./gradlew spotlessCheck

# Auto-fix formatting issues
./gradlew spotlessApply

# Clean build outputs
./gradlew clean
```

There are no automated tests — CI validates only that Spotless formatting passes and the project compiles.

**After editing any Java or `.gradle` file, always run `./gradlew build` locally before committing.** The pre-commit hook only checks formatting — it does not compile. The CI build runs only after the push, so a commit with broken code will reach the remote before being caught.

## Critical: Minecraft 26.1.1 and the no-remap setup

This mod targets **Minecraft 26.1.1**, the first release under Mojang's new versioning scheme (no "1." prefix, released 2026). MC 26.1.1 ships **without bytecode obfuscation**, which changes the entire Gradle/Loom configuration:

- **Plugin ID must be** `net.fabricmc.fabric-loom` (full ID, NOT the `fabric-loom` alias) — this activates Loom's no-remap mode
- **`loom { noIntermediateMappings() }`** is required — there is no `mappings` dependency line
- **`implementation`** is used for all dependencies (not `modImplementation` — that configuration doesn't exist in no-remap mode)
- **No Yarn mappings exist** for 26.1.1; all class/method names are Mojang's native names directly

All Java source uses **Mojang native names** — e.g., `ServerPlayer`, `ServerLevel`, `BlockPos`, `ItemStack`, `DataComponents`, `PlayerBlockBreakEvents`, `EquipmentSlot.MAINHAND`, etc. Do not use Yarn names.

Requires **Java 25** (`--release 25`). The `omnitweaks.mixins.json` still declares `"compatibilityLevel": "JAVA_21"` — this is a known discrepancy that doesn't affect compilation.

## Code formatting (Spotless)

Import order enforced: `java` → `javax` → blank → `net.minecraft` → `net.fabricmc` → `com.jovinull`

All files must: trim trailing whitespace, end with a newline, have no unused imports.

Run `./gradlew spotlessApply` before committing if you edited Java or `.gradle` files.

## Architecture

The mod follows a **module system** pattern where each feature is independently togglable per player at runtime.

### Core flow

```
OmniTweaks.onInitialize()
  └── new ModuleManager()          // in-memory state store (ConcurrentHashMap, no persistence)
  └── BaseCommand.register()       // registers /omnitweaks and /ot alias via Brigadier
  └── TreeCapitatorModule.register() // registers Fabric AFTER block break event
  └── OmniDrillModule.register()    // registers Fabric BEFORE block break event
  // AutoShulkerModule has no register() — it's driven entirely by ItemEntityMixin
```

### ModuleManager (`core/ModuleManager.java`)

Central state: `Map<UUID, Set<String>>` for enabled modules, `Map<UUID, int[]>` for OmniDrill area config. Module IDs: `"autoshulker"`, `"treecapitator"`, `"omnidrill"`. All modules start **disabled** for every player; state resets on server restart.

### Command system (`commands/BaseCommand.java`)

Single root command `/omnitweaks` (alias `/ot`) with literal subcommands per module. OmniDrill accepts optional `<largura> <altura>` (1–8 each) to set area and activate. All other modules are simple toggles. Uses `ctx.getSource().getPlayerOrException()` — commands require a player context.

### Module pattern

Each module (except AutoShulker) registers a Fabric API event listener:
- **TreeCapitator** — `PlayerBlockBreakEvents.AFTER`: checks log block + axe in hand, BFS 26-neighbor (3×3×3 cube) up to 200 blocks, calls `level.destroyBlock()` + `hurtAndBreak()` + `awardStat(Stats.BLOCK_MINED)`
- **OmniDrill** — `PlayerBlockBreakEvents.BEFORE`: checks `DataComponents.TOOL` + `player.pick()` for face detection, BFS in a 3D volume (width × height × depth, default 3×3×3, max 8×8×8) expanding laterally and into the wall via `face.getOpposite()`, respects 6-block distance and durability ≥ 2; yields to TreeCapitator when both are active and block is a log; awards `Stats.BLOCK_MINED` for each extra block
- **AutoShulker** — No event registration; `ItemEntityMixin` injects into `ItemEntity.playerTouch()` at HEAD (cancellable), delegates to `AutoShulkerModule.handlePickup()`. Iterates player inventory for Shulker Boxes, uses `DataComponents.CONTAINER` / `ItemContainerContents` (no direct NBT), merges then fills empty slots
- **QuickDump** — `UseBlockCallback.EVENT` (sneak + right-click on any container block); intercepts only when player holds a Shulker Box in main hand and is sneaking; transfers items into the target `Container` block entity (merge phase then empty slots, respects `canPlaceItem`); updates `DataComponents.CONTAINER` in-place on the Shulker Box; returns `InteractionResult.SUCCESS_SERVER` to cancel vanilla (no GUI opens)

### Adding a new module

1. Create `modules/<name>/<Name>Module.java` with a `register()` static method
2. Add the module ID string to `ModuleManager.AVAILABLE_MODULES`
3. Add a subcommand literal in `BaseCommand.registerCommands()`
4. Call `<Name>Module.register()` in `OmniTweaks.onInitialize()`
5. If Mixin-based: add the mixin class and declare it in `omnitweaks.mixins.json`

## GitHub workflow

`gh` CLI is at `/c/Program Files/GitHub CLI/gh` (not in PATH on this machine — use the full path).

**After every `git push` of a feature/fix branch, immediately create a PR with `gh pr create`.** Never leave a pushed branch without a PR. The CI workflow ("Verificação e build") must pass before merging; direct pushes to `main` are blocked.

```bash
"/c/Program Files/GitHub CLI/gh" pr create --title "..." --body "..."
```

**After creating the PR, always return to `main` and pull** so the local working tree is clean and up to date for the next task:

```bash
git checkout main && git pull
```

Exception: if the CI fails and the branch needs a fix, stay on the branch to fix it. Once the fix is pushed, return to `main`.

## Commit conventions

Conventional commits in Portuguese, title only — no body, no co-author signatures.
Examples: `feat: módulo X`, `fix: corrige Y`, `chore: atualiza Z`
