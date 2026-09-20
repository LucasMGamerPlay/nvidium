# Nvidium (persistente)

Fork of [Nvidium](https://github.com/MCRcortex/nvidium) with **disk-persistent Sodium/Nvidium meshes**. Explored terrain survives unload, quit, and rejoin without keeping vanilla chunks in RAM.

This is **not** vanilla Nvidium. There is no far LOD in this fork (it was tried and removed). Distant terrain is the same full Sodium mesh, kept on GPU and/or reloaded from the world folder.

[![Modrinth](https://img.shields.io/modrinth/dt/nvidium?logo=modrinth)](https://modrinth.com/mod/nvidium)

Nvidium is an alternate rendering backend for Sodium. It uses NVIDIA mesh shaders to draw large amounts of terrain at playable framerates.

**Requires Sodium and an NVIDIA GTX 1600 series or newer (Turing+).**

## This fork

- **Minecraft** 26.3, **Sodium** 0.9.2, **Fabric**.
- Branch: `persistente` on [LucasMGamerPlay/nvidium](https://github.com/LucasMGamerPlay/nvidium).
- License: LGPL-3.0 (same as upstream). [Distant Horizons](https://modrinth.com/mod/distanthorizons) and [Voxy](https://modrinth.com/mod/voxy) can sit next to this jar as **separate** mods (see below). Voxy is ARR — this fork never copies it.

### What is stored

Sodium/Nvidium section meshes are written to:

```
Singleplayer: <save>/nvidium-persistent/<dimension>/
Multiplayer:  <game>/nvidium-persistent/mp/<server-name>_<ip>/<dimension>/
```

Format is compact quads + zstd (`VERSION` 3). Typical cache for a radius-32 import is tens of thousands of sections.

On join, meshes stream back to the GPU. Vanilla/Sodium can unload the chunk; the GPU copy stays until `Region Keep Distance` / VRAM eviction.

Block break/place remeshes in Sodium and overwrites that section in the store. F3 `Disk` line shows `dirty` (store rewrites) and `edits` (block-change events).

### Options (Sodium → Nvidium)

| Option | Meaning |
| --- | --- |
| **Disk persistence** | Save/load meshes from the world folder. Reload renderer after toggling. |
| **Region Keep Distance** | How far to keep GPU terrain after Sodium unloads. Vanilla / N chunks / Keep All. |

### Distant Horizons / Voxy (far LOD)

This fork does **not** copy those mods. Voxy is All-Rights-Reserved. Distant Horizons is LGPL and already has server LOD streaming (DH 3.3.0 for Minecraft 26.3).

When `distanthorizons` or `voxy` is loaded, Nvidium:

- Stops stretching fog out to Keep All (so the LOD mod can draw the horizon).
- Caps GPU keep at 32 chunks so full Sodium meshes do not cover the LOD ring.
- Still **saves** full meshes to disk; walking back remeshes/reloads detail.

Install DH (or Voxy) on the **server** if that mod supports it and you want LOD without walking. `/nvidium persist import` remains singleplayer-only.

F3 `Keep` shows `DH coexist`, `Voxy coexist`, or `DH+Voxy coexist`. Prefer **one** LOD mod, not both at once.

### Multiplayer

The client still persists whatever chunks the server sends (walk, elytra, spectator). Rejoin the same server and the explored horizon reloads from disk. `/nvidium persist import` is **singleplayer only** (it has to generate chunks). There is no server-side distant-terrain stream in this fork.

A server resource pack counts as a pack change (`pack.sig`) and will wipe that server's cache.

F3 `Disk` shows `mp/<server>/minecraft_overworld` vs `sp/minecraft_overworld`.

### Commands (singleplayer)

| Command | Meaning |
| --- | --- |
| `/nvidium persist import [radius]` | Generate and mesh chunks in a square radius (default 64, min 8, max 256). Teleports through the area so Sodium can mesh. May bump render distance to 12 for the job, then restore it unless you changed it yourself. |
| `/nvidium persist import all` | Mesh chunks that already exist in region files. Does not generate new terrain. |
| `/nvidium persist stop` | Cancel a running import. |
| `/nvidium persist status` | Progress line. |
| `/nvidium persist wipe` | Delete the persist folder for the current world or server. Leave and rejoin. |

Radius 32 is ~4225 chunks and takes a few minutes. Progress is on the action bar and on the F3 `Disk` line.

### F3

Look for `Using nvidium renderer` and a `Disk:` line:

```
Disk: 28482 sections, 12MB zstd+quads, wrQ: 0, ldQ: 0, loaded: …, dirty: …, edits: …
```

### Wipe / reset cache

1. `/nvidium persist wipe`, or delete `saves/<world>/nvidium-persistent/` by hand.
2. Leave the world and rejoin (or restart).

Old `VERSION` 1–2 files are ignored as corrupt; the store rewrites as you explore.

### Test profile

Use the Modrinth profile `Nvidium` only. If you add Voxy or Distant Horizons, F3 should show `coexist`.

Suggested checks:

1. Explore or `/nvidium persist import 32`, quit, rejoin — horizon should return from disk.
2. Break/place a block — F3 `edits` and `dirty` should increase; the hole/block must persist after rejoin.
3. Nether / End (separate store folders).
4. Resource pack change: the cache stores atlas UVs. A pack or block-atlas size change writes a new `pack.sig` and **wipes** `nvidium-persistent` for that dimension so old UVs are not drawn. Re-explore or `/nvidium persist import` after switching packs.
5. Bobby off + low render distance: persisted GPU mesh should still draw past vanilla RD if Keep Distance is high.

Nether and End use their own folders under `nvidium-persistent/` (`minecraft_the_nether`, `minecraft_the_end`). Run import in that dimension.

### Out of scope (this fork)

Built-in far LOD (use Distant Horizons for that), Iris-over-persistent-terrain, entities, NeoForge.

## Upstream

Vanilla Nvidium docs and issues: [MCRcortex/nvidium](https://github.com/MCRcortex/nvidium).
