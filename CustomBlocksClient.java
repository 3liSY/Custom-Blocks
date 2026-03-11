{
  "schemaVersion": 1,
  "id": "customblocks",
  "version": "3.0.0",
  "name": "Custom Blocks",
  "description": "Create custom textured blocks using PNG images EASILY - no coding needed.",
  "authors": [ "3liSY and Claude AI" ],
  "icon": "pack.png",
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main":   [ "com.customblocks.CustomBlocksMod" ],
    "client": [ "com.customblocks.client.CustomBlocksClient" ]
  },
  "mixins": [],
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-api":   "*",
    "minecraft":    "~1.21.1"
  }
}
