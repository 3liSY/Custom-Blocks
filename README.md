# Custom Blocks Ultimate v10 — Fabric 1.21.1

> **512 custom textured blocks** — image editor, chest GUI, animated textures, glow shader, per-face textures, custom sounds, permissions, recycle bin, undo, templates, HTTP pack server.

---

## Build

```bash
# Requirements: Java 21, internet access for Gradle

# 1. Download the Gradle wrapper JAR (one-time setup)
mkdir -p gradle/wrapper
curl -L "https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar" \
  -o gradle/wrapper/gradle-wrapper.jar

# 2. Build
chmod +x gradlew && ./gradlew build

# 3. Output
ls build/libs/
# → Custom-Blocks-Ultimate-v10-10.0.0.jar  ← drop this in /mods/
```

**Fabric requirements:**
- Fabric Loader ≥ 0.16.7
- Fabric API ≥ 0.108.0+1.21.1
- Java 21
- Yarn 1.21.1+build.3

TwelveMonkeys (WebP/GIF/BMP/TIFF/ICO support) is **bundled** — no extra installs needed.

---

## Drop-In Install

```
mods/
├── fabric-api-0.108.0+1.21.1.jar
└── Custom-Blocks-Ultimate-v10-10.0.0.jar
```

On first run, the mod creates:
```
config/customblocks/
├── blocks.json          ← block metadata
├── permissions.json     ← access control
├── templates.json       ← custom templates
└── pack-v10.zip         ← generated resource pack
```

**Players must use the resource pack** to see custom textures. Serve it via:
```
/cb packurl            → get the HTTP URL
/cb exportpack v10     → regenerate the ZIP
```

---

## Key Bindings

| Key | Action |
|-----|--------|
| **F6** | Open Custom Blocks GUI (B Screen) |

---

## Commands

All commands work as `/customblock` or `/cb`:

### Block Creation
| Command | Description |
|---------|-------------|
| `/cb createurl <id> <name> <url>` | Create block from image URL |
| `/cb template <templateId> <newId> <name>` | Create from preset template |
| `/cb templates` | List all 15 built-in templates |

### Block Management
| Command | Description |
|---------|-------------|
| `/cb delete <id>` | Delete block (sends to recycle bin) |
| `/cb rename <id> <newname>` | Rename display name |
| `/cb give <id> [player] [count]` | Give block items |
| `/cb list` | List all blocks |
| `/cb info <id>` | Detailed block info |

### Properties
| Command | Description |
|---------|-------------|
| `/cb glow <id> <0-15>` | Set light emission |
| `/cb hardness <id> <1-5>` | Set hardness (1=soft 0.3, 5=unbreakable) |
| `/cb sound <id> <type>` | Set sound preset (stone/wood/grass/metal/glass/sand/wool/gravel) |
| `/cb face <id> <face> <url>` | Set per-face texture (top/bottom/north/south/east/west) |
| `/cb animfps <id> <1-20>` | Set animation speed |

### Recycle Bin
| Command | Description |
|---------|-------------|
| `/cb recycle` | List recycle bin (50 slots) |
| `/cb recycle restore <id>` | Restore deleted block |
| `/cb recycle purge <id>` | Permanently delete from recycle |
| `/cb recycle clear` | Empty recycle bin |

### Undo / History
| Command | Description |
|---------|-------------|
| `/cb undo` | Undo last action (20 steps) |
| `/cb history` | Show undo depth |

### Resource Pack
| Command | Description |
|---------|-------------|
| `/cb packurl` | Get HTTP URL for resource pack |
| `/cb exportpack [version]` | Export pack ZIP + serve via HTTP |
| `/cb atlas` | Rebuild resource pack immediately |

### Admin
| Command | Description |
|---------|-------------|
| `/cb reload` | Reload permissions.json + templates.json |
| `/cb browse` | Open GUI help |

---

## GUI — B Screen (F6)

```
┌─────────────────────────────────────────────────────┐
│ [All] [⭐Favs] [🗑Recycle] [📋Templates] [✨Special] │ ← Tabs (left)
├──────┬──────────────────────────────┬────────────────┤
│ Tabs │  🔍 Search bar               │  + Create      │
│      │  ┌────┬────┬────┬────┐      │  📦 Chest View │
│      │  │ 🟥 │ 🟦 │ 🟩 │ 🟧 │      │  ──────────── │
│      │  │blk1│blk2│blk3│blk4│      │ [Selected:    │
│      │  └────┴────┴────┴────┘      │  my_block]    │
│      │  ... grid scrolls ...       │ Give×1 Give×64│
│      │                              │ 🖼 Retexture  │
│      │                              │ ⚙ Properties  │
│      │                              │ ✎ Pic Editor  │
│      │                              │ ☆ Favorite    │
│      │                              │ ✖ Delete      │
├──────┴──────────────────────────────┴────────────────┤
│  ⬇ Drop image here to create block (Drop Zone)       │
└──────────────────────────────────────────────────────┘
```

**Grid interactions:**
- Click = select block
- Scroll = navigate pages

**Create panel:**
- Fill ID, Name, URL → Create
- Or click "✎ Pic Editor" to open the image editor

**Properties panel (⚙):**
- `−` / `+` buttons: adjust glow (0-15)
- Hardness: Soft / Norm / Hard / Max / Unbr
- Sound: stone / wood / grass / metal / glass / sand / wool / gravel

---

## Chest Browse GUI (📦 Chest View)

9 tabs: A-E / F-J / K-O / P-T / U-Z / 0-9 / ⭐ Favs / 🔍 All / 🗑 Recycle

- **Left click** = give ×1
- **Shift+click** = give ×64
- **Right click** = toggle favorite ★
- **Scroll** = page through blocks

---

## Image Editor (✎ Pic Editor)

```
┌─────────────────────────────────────────────────────┐
│ ✎ Image Editor — create                             │
│ URL: [https://…/texture.png              ] [Load]   │
│ ID:  [block_id        ] Name: [My Block  ]          │
│ [⟺ H] [⟷ V] [↻90] [↻180] [↻270]                   │
│ Output Size: 16px  ─────●────────────────           │
│ Brightness: 0      ──────────●───────────           │
│ Contrast: 1.0      ──────────●───────────           │
│                              ┌──────────┐           │
│                              │ PREVIEW  │           │
│                              │  [IMG]   │           │
│                              └──────────┘           │
│              [✔ Apply]   [✖ Cancel]                 │
└─────────────────────────────────────────────────────┘
```

**Features:**
- Load from URL or drag-drop image file onto Minecraft window
- Crop (set top/right/bottom/left trim in pixels)
- Output size: 16–256px (snaps to 16px multiples)
- Brightness: −100 to +100
- Contrast: 0.1× to 3.0×
- Flip horizontal / vertical
- Rotate 90° / 180° / 270°
- Live preview updates as you drag sliders
- "Apply" sends processed image to server

---

## Drag & Drop

Drag any image file from your desktop/file manager **directly onto the Minecraft window**.

- If the Custom Blocks GUI (F6) is open → opens Image Editor with the dropped image
- If Image Editor is open → loads the image immediately
- If no CB screen is open → opens Image Editor automatically

Supports: **PNG, JPEG, GIF, BMP, WebP, TIFF, ICO** (via bundled TwelveMonkeys)

---

## HTTP Pack Server

The mod runs a built-in HTTP server on port 8080:

```
http://<server-ip>:8080/pack-v10.zip
```

Use this in `server.properties`:
```properties
resource-pack=http://YOUR_SERVER_IP:8080/pack-v10.zip
resource-pack-prompt=Custom Blocks v10
```

The pack auto-updates whenever you create/edit blocks. Players already connected get an in-game toast notification.

---

## Animated Textures

Add animation frames via the GUI or:
1. Use Image Editor to load frames
2. Set FPS: `/cb animfps <id> <1-20>`
3. Frames are stored as a sprite sheet; `.mcmeta` is auto-generated

**Spec:** 2–32 frames, 1–20 FPS.

---

## Per-Face Textures

Set different textures for each face:
```
/cb face my_block top    https://example.com/top.png
/cb face my_block bottom https://example.com/bottom.png
/cb face my_block north  https://example.com/north.png
/cb face my_block south  https://example.com/south.png
/cb face my_block east   https://example.com/east.png
/cb face my_block west   https://example.com/west.png
```

---

## Random Texture Variants

Add up to 8 random texture variants (Minecraft randomly picks one when placing):
- GUI: Properties → "Add Random Variant" (drag image or paste URL)
- Generated blockstate uses weighted model selection

---

## Custom Sounds

Upload `.ogg` files via the GUI Properties panel:
- **Break sound** — played when the block is broken
- **Place sound** — played when placed
- **Step sound** — played when walking on it

Alternatively use built-in sound presets: `stone` / `wood` / `grass` / `metal` / `glass` / `sand` / `wool` / `gravel`

---

## Permissions

`config/customblocks/permissions.json`:
```json
{
  "default": "browse",
  "opLevel": 2,
  "players": {
    "AdminPlayer": "admin",
    "BuilderPlayer": "edit",
    "ShopKeeper": "give"
  },
  "groups": {}
}
```

| Level | Can do |
|-------|--------|
| `browse` | Open GUI, see blocks |
| `give` | Give blocks to self |
| `edit` | Create, delete, rename, retexture, set properties |
| `admin` | All of the above + reload config, clear recycle, export pack |

Reload without restart: `/cb reload`

---

## Templates (15 built-in)

| ID | Name | Glow | Sound |
|----|------|------|-------|
| `neon_red` | Neon Red | 15 | glass |
| `neon_blue` | Neon Blue | 15 | glass |
| `neon_green` | Neon Green | 15 | glass |
| `neon_yellow` | Neon Yellow | 12 | glass |
| `neon_pink` | Neon Pink | 13 | glass |
| `wood_oak` | Oak Plank | 0 | wood |
| `stone_smooth` | Smooth Stone | 0 | stone |
| `metal_iron` | Iron Panel | 0 | metal |
| `glass_clear` | Clear Glass | 0 | glass |
| `sand_yellow` | Desert Sand | 0 | sand |
| `wool_white` | White Wool | 0 | wool |
| `grass_green` | Grass Top | 0 | grass |
| `portal_swirl` | Portal Swirl | 11 | glass |
| `lava_glow` | Lava Glow | 15 | stone |
| `diamond_ore` | Diamond Ore | 0 | stone |

Usage: `/cb template neon_red my_neon_block "Red Neon Light"`

Add custom templates in `config/customblocks/templates.json`.

---

## Technical Notes

- **512 slots** registered at startup — no registry mismatches on reload
- **Drip-feed sync**: textures sent 4/tick after 3s join delay — no kick risk
- **Undo**: 20 steps, metadata only (textures re-read from disk on restore)
- **Recycle bin**: 50 deleted blocks, newest first; restores to original slot if free
- **Pack format**: 34 (Minecraft 1.21.1)
- **Thread safety**: All slot mutations are ConcurrentHashMap-safe; texture downloads use virtual threads
- **Graceful errors**: All failures show in-game toast / chat message, never crash

---

## File Structure (source)

```
src/main/java/com/customblocks/
├── CustomBlocksMod.java          ← Server init, 512 block registration, drip-feed
├── SlotManager.java              ← Data store: slots, recycle, undo, persistence
├── PermissionManager.java        ← permissions.json
├── TemplateManager.java          ← 15 built-in + custom templates
├── ResourcePackExporter.java     ← ZIP builder: models, states, animations, sounds
├── PackHttpServer.java           ← HTTP server on :8080
├── block/SlotBlock.java          ← Block with dynamic hardness/sound
├── item/ColorSquareItem.java     ← Color square items
├── command/CustomBlockCommand.java ← All /cb commands
├── network/
│   ├── FullSyncPayload.java      ← S2C full metadata sync
│   ├── SlotUpdatePayload.java    ← S2C single block update
│   └── ImageEditPayload.java     ← C2S image upload
├── util/ImageProcessor.java      ← Server-side image processing (TwelveMonkeys)
└── client/
    ├── CustomBlocksClient.java   ← Client init, packet handlers, drag-drop, keybind
    ├── ClientSlotData.java       ← Client-side metadata mirror + favorites
    ├── texture/TextureCache.java ← Dynamic texture upload/cache/invalidate
    └── gui/
        ├── CustomBlocksScreen.java  ← B Screen (main GUI)
        ├── ChestBrowseScreen.java   ← Chest-style 9-tab browse
        └── ImageEditorScreen.java   ← Pic editor: crop/brightness/contrast/flip/rotate
```
