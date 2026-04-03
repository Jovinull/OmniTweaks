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
- **TreeCapitator** — `PlayerBlockBreakEvents.AFTER`: checks log block + axe in hand, BFS 26-neighbor (3×3×3 cube) up to 200 blocks, calls `level.destroyBlock()` + `hurtAndBreak()`
- **OmniDrill** — `PlayerBlockBreakEvents.BEFORE`: checks `DataComponents.TOOL` + `player.pick()` for face detection, BFS 4-connected in the perpendicular plane within configured area (default 3×3, max 8×8), respects 6-block distance and durability ≥ 2
- **AutoShulker** — No event registration; `ItemEntityMixin` injects into `ItemEntity.playerTouch()` at HEAD (cancellable), delegates to `AutoShulkerModule.handlePickup()`. Iterates player inventory for Shulker Boxes, uses `DataComponents.CONTAINER` / `ItemContainerContents` (no direct NBT), merges then fills empty slots

### Adding a new module

1. Create `modules/<name>/<Name>Module.java` with a `register()` static method
2. Add the module ID string to `ModuleManager.AVAILABLE_MODULES`
3. Add a subcommand literal in `BaseCommand.registerCommands()`
4. Call `<Name>Module.register()` in `OmniTweaks.onInitialize()`
5. If Mixin-based: add the mixin class and declare it in `omnitweaks.mixins.json`

## Commit conventions

Conventional commits in Portuguese, title only — no body, no co-author signatures.
Examples: `feat: módulo X`, `fix: corrige Y`, `chore: atualiza Z`

## Branch protection

`main` tem branch protection ativa: **push direto é bloqueado**. A CI ("Verificação e build") precisa passar antes do merge.

Fluxo obrigatório para qualquer alteração:
1. Criar um branch (`git checkout -b <nome>`)
2. Commitar no branch
3. Push do branch (`git push -u origin <nome>`)
4. Abrir PR via `gh pr create`
5. Aguardar a CI passar e fazer merge
