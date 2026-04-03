package com.customblocks.command;

import com.customblocks.*;
import com.customblocks.block.SlotBlock;
import com.customblocks.network.FullSyncPayload;
import com.customblocks.network.SlotUpdatePayload;
import com.customblocks.util.ImageProcessor;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CustomBlockCommand {

    private static final SuggestionProvider<ServerCommandSource> BLOCK_IDS =
            (ctx, b) -> { SlotManager.allCustomIds().forEach(b::suggest); return b.buildFuture(); };
    private static final SuggestionProvider<ServerCommandSource> SOUNDS =
            (ctx, b) -> { SlotManager.SOUND_KEYS.forEach(b::suggest); return b.buildFuture(); };
    private static final SuggestionProvider<ServerCommandSource> FACES =
            (ctx, b) -> { SlotManager.FACE_KEYS.forEach(b::suggest); return b.buildFuture(); };
    private static final SuggestionProvider<ServerCommandSource> RECYCLE_IDS =
            (ctx, b) -> { SlotManager.getRecycleBin().forEach(d -> b.suggest(d.customId)); return b.buildFuture(); };
    private static final SuggestionProvider<ServerCommandSource> TEMPLATE_IDS =
            (ctx, b) -> { TemplateManager.allTemplates().forEach(t -> b.suggest(t.id())); return b.buildFuture(); };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> {
            var root = build();
            dispatcher.register(root);
            dispatcher.register(CommandManager.literal("cb").redirect(
                    dispatcher.getRoot().getChild("customblock")));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("customblock")
            .requires(src -> true)
            .then(CommandManager.literal("createurl")
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .then(CommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(ctx -> cmdCreateUrl(ctx.getSource(),
                                StringArgumentType.getString(ctx,"id"),
                                StringArgumentType.getString(ctx,"name").replace("_"," "),
                                StringArgumentType.getString(ctx,"url")))))))
            .then(CommandManager.literal("delete")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .executes(ctx -> cmdDelete(ctx.getSource(), StringArgumentType.getString(ctx,"id")))))
            .then(CommandManager.literal("rename")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .then(CommandManager.argument("newname", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRename(ctx.getSource(),
                            StringArgumentType.getString(ctx,"id"),
                            StringArgumentType.getString(ctx,"newname").replace("_"," "))))))
            .then(CommandManager.literal("give")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .executes(ctx -> cmdGive(ctx.getSource(), StringArgumentType.getString(ctx,"id"), null, 1))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> cmdGive(ctx.getSource(),
                            StringArgumentType.getString(ctx,"id"),
                            EntityArgumentType.getPlayer(ctx,"player"), 1))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1,64))
                            .executes(ctx -> cmdGive(ctx.getSource(),
                                StringArgumentType.getString(ctx,"id"),
                                EntityArgumentType.getPlayer(ctx,"player"),
                                IntegerArgumentType.getInteger(ctx,"count")))))))
            .then(CommandManager.literal("glow")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .then(CommandManager.argument("level", IntegerArgumentType.integer(0,15))
                        .executes(ctx -> cmdGlow(ctx.getSource(),
                            StringArgumentType.getString(ctx,"id"),
                            IntegerArgumentType.getInteger(ctx,"level"))))))
            .then(CommandManager.literal("hardness")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .then(CommandManager.argument("level", IntegerArgumentType.integer(1,5))
                        .executes(ctx -> cmdHardness(ctx.getSource(),
                            StringArgumentType.getString(ctx,"id"),
                            IntegerArgumentType.getInteger(ctx,"level"))))))
            .then(CommandManager.literal("sound")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .then(CommandManager.argument("type", StringArgumentType.word()).suggests(SOUNDS)
                        .executes(ctx -> cmdSound(ctx.getSource(),
                            StringArgumentType.getString(ctx,"id"),
                            StringArgumentType.getString(ctx,"type"))))))
            .then(CommandManager.literal("face")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .then(CommandManager.argument("face", StringArgumentType.word()).suggests(FACES)
                        .then(CommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(ctx -> cmdFace(ctx.getSource(),
                                StringArgumentType.getString(ctx,"id"),
                                StringArgumentType.getString(ctx,"face"),
                                StringArgumentType.getString(ctx,"url")))))))
            .then(CommandManager.literal("animfps")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .then(CommandManager.argument("fps", IntegerArgumentType.integer(1,20))
                        .executes(ctx -> cmdAnimFps(ctx.getSource(),
                            StringArgumentType.getString(ctx,"id"),
                            IntegerArgumentType.getInteger(ctx,"fps"))))))
            .then(CommandManager.literal("packurl").executes(ctx -> cmdPackUrl(ctx.getSource())))
            .then(CommandManager.literal("exportpack")
                .executes(ctx -> cmdExportPack(ctx.getSource(),"v10"))
                .then(CommandManager.argument("version", StringArgumentType.word())
                    .executes(ctx -> cmdExportPack(ctx.getSource(), StringArgumentType.getString(ctx,"version")))))
            .then(CommandManager.literal("template")
                .then(CommandManager.argument("templateId", StringArgumentType.word()).suggests(TEMPLATE_IDS)
                    .then(CommandManager.argument("newId", StringArgumentType.word())
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> cmdTemplate(ctx.getSource(),
                                StringArgumentType.getString(ctx,"templateId"),
                                StringArgumentType.getString(ctx,"newId"),
                                StringArgumentType.getString(ctx,"name").replace("_"," ")))))))
            .then(CommandManager.literal("templates").executes(ctx -> cmdListTemplates(ctx.getSource())))
            .then(CommandManager.literal("recycle")
                .executes(ctx -> cmdListRecycle(ctx.getSource()))
                .then(CommandManager.literal("restore")
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RECYCLE_IDS)
                        .executes(ctx -> cmdRecycleRestore(ctx.getSource(), StringArgumentType.getString(ctx,"id")))))
                .then(CommandManager.literal("purge")
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RECYCLE_IDS)
                        .executes(ctx -> cmdRecyclePurge(ctx.getSource(), StringArgumentType.getString(ctx,"id")))))
                .then(CommandManager.literal("clear").executes(ctx -> cmdRecycleClear(ctx.getSource()))))
            .then(CommandManager.literal("undo").executes(ctx -> cmdUndo(ctx.getSource())))
            .then(CommandManager.literal("history").executes(ctx -> cmdHistory(ctx.getSource())))
            .then(CommandManager.literal("list").executes(ctx -> cmdList(ctx.getSource())))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(BLOCK_IDS)
                    .executes(ctx -> cmdInfo(ctx.getSource(), StringArgumentType.getString(ctx,"id")))))
            .then(CommandManager.literal("browse").executes(ctx -> cmdBrowse(ctx.getSource())))
            .then(CommandManager.literal("atlas").executes(ctx -> cmdAtlas(ctx.getSource())))
            .then(CommandManager.literal("reload").executes(ctx -> cmdReload(ctx.getSource())));
    }

    // ── Command implementations ───────────────────────────────────────────────

    private static int cmdCreateUrl(ServerCommandSource src, String id, String name, String url) {
        if (!checkEdit(src)) return 0;
        String cid = id.toLowerCase().replaceAll("[^a-z0-9_]","_");
        if (SlotManager.hasId(cid)) { src.sendMessage(Text.literal("§c[CB] ID '"+cid+"' already exists.")); return 0; }
        src.sendMessage(Text.literal("§7[CB] Downloading…"));
        Thread.ofVirtual().start(() -> {
            try {
                byte[] tex = ImageProcessor.downloadAndProcess(url.trim(), 16);
                src.getServer().execute(() -> {
                    SlotManager.SlotData d = SlotManager.assign(cid, name, tex);
                    if (d == null) { src.sendMessage(Text.literal("§c[CB] No free slots.")); return; }
                    SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
                    CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload(
                            "add", d.index, cid, name, tex, d.lightLevel, d.hardness, d.soundType));
                    src.sendMessage(Text.literal("§a[CB] Created '"+name+"' slot "+d.index+"."));
                });
            } catch (Exception e) {
                src.getServer().execute(() -> src.sendMessage(Text.literal("§c[CB] Error: "+e.getMessage())));
            }
        });
        return 1;
    }

    private static int cmdDelete(ServerCommandSource src, String id) {
        if (!checkEdit(src)) return 0;
        if (!SlotManager.hasId(id)) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        SlotManager.delete(id); SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
        CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload("delete",-1,id,null,null,0,0,"stone"));
        src.sendMessage(Text.literal("§a[CB] Deleted '"+id+"'."));
        return 1;
    }

    private static int cmdRename(ServerCommandSource src, String id, String newName) {
        if (!checkEdit(src)) return 0;
        if (!SlotManager.rename(id, newName)) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        SlotManager.saveAll();
        SlotManager.SlotData d = SlotManager.getById(id);
        CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload("rename",d.index,id,newName,null,d.lightLevel,d.hardness,d.soundType));
        src.sendMessage(Text.literal("§a[CB] Renamed to '"+newName+"'."));
        return 1;
    }

    private static int cmdGive(ServerCommandSource src, String id, ServerPlayerEntity target, int count) {
        ServerPlayerEntity p;
        try { p = target != null ? target : src.getPlayerOrThrow(); }
        catch (Exception e) { src.sendMessage(Text.literal("§c[CB] Specify a player.")); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        if (d == null) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        p.getInventory().insertStack(new ItemStack(CustomBlocksMod.SLOT_ITEMS[d.index], Math.min(count,64)));
        src.sendMessage(Text.literal("§a[CB] Gave "+count+"x '"+d.displayName+"'."));
        return 1;
    }

    private static int cmdGlow(ServerCommandSource src, String id, int level) {
        if (!checkEdit(src)) return 0;
        if (!SlotManager.setLight(id, level)) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        SlotManager.saveAll();
        SlotManager.SlotData d = SlotManager.getById(id);
        CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload("update",d.index,id,null,null,level,d.hardness,d.soundType));
        src.sendMessage(Text.literal("§a[CB] Glow="+level+" on '"+id+"'."));
        return 1;
    }

    private static int cmdHardness(ServerCommandSource src, String id, int level) {
        if (!checkEdit(src)) return 0;
        float h = switch(level){ case 1->0.3f; case 2->1.5f; case 3->3.0f; case 4->5.0f; default->-1f; };
        if (!SlotManager.setHardness(id,h)) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        SlotManager.saveAll(); src.sendMessage(Text.literal("§a[CB] Hardness="+level+" on '"+id+"'."));
        return 1;
    }

    private static int cmdSound(ServerCommandSource src, String id, String type) {
        if (!checkEdit(src)) return 0;
        if (!SlotManager.setSound(id,type)) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        SlotManager.saveAll(); src.sendMessage(Text.literal("§a[CB] Sound='"+type+"' on '"+id+"'."));
        return 1;
    }

    private static int cmdFace(ServerCommandSource src, String id, String face, String url) {
        if (!checkEdit(src)) return 0;
        if (!SlotManager.FACE_KEYS.contains(face)) { src.sendMessage(Text.literal("§c[CB] Bad face. Valid: "+SlotManager.FACE_KEYS)); return 0; }
        src.sendMessage(Text.literal("§7[CB] Downloading face…"));
        Thread.ofVirtual().start(() -> {
            try {
                byte[] tex = ImageProcessor.downloadAndProcess(url.trim(), 16);
                src.getServer().execute(() -> {
                    if (!SlotManager.setFaceTexture(id, face, tex)) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return; }
                    SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
                    SlotManager.SlotData d = SlotManager.getById(id);
                    CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload("update",d.index,id,null,null,d.lightLevel,d.hardness,d.soundType));
                    src.sendMessage(Text.literal("§a[CB] Face '"+face+"' set on '"+id+"'."));
                });
            } catch (Exception e) { src.getServer().execute(() -> src.sendMessage(Text.literal("§c[CB] "+e.getMessage()))); }
        });
        return 1;
    }

    private static int cmdAnimFps(ServerCommandSource src, String id, int fps) {
        if (!checkEdit(src)) return 0;
        SlotManager.SlotData d = SlotManager.getById(id);
        if (d == null) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        d.animFps = fps; SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
        src.sendMessage(Text.literal("§a[CB] AnimFPS="+fps+" on '"+id+"'."));
        return 1;
    }

    private static int cmdPackUrl(ServerCommandSource src) {
        String url = PackHttpServer.getUrl();
        src.sendMessage(Text.literal("§a[CB] Pack URL: §f"+url));
        return 1;
    }

    private static int cmdExportPack(ServerCommandSource src, String version) {
        if (!checkAdmin(src)) return 0;
        try {
            ResourcePackExporter.exportCurrentPackZip(new File("config/customblocks"), version);
            src.sendMessage(Text.literal("§a[CB] Exported pack-"+version+".zip → HTTP: "+PackHttpServer.getUrl()));
        } catch (Exception e) { src.sendMessage(Text.literal("§c[CB] "+e.getMessage())); }
        return 1;
    }

    private static int cmdTemplate(ServerCommandSource src, String tid, String newId, String name) {
        if (!checkEdit(src)) return 0;
        String cid = newId.toLowerCase().replaceAll("[^a-z0-9_]","_");
        if (SlotManager.hasId(cid)) { src.sendMessage(Text.literal("§c[CB] ID '"+cid+"' already exists.")); return 0; }
        SlotManager.SlotData d = TemplateManager.apply(tid, cid, name);
        if (d == null) { src.sendMessage(Text.literal("§c[CB] Unknown template or no slots: "+tid)); return 0; }
        SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
        CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload("add",d.index,cid,name,d.texture,d.lightLevel,d.hardness,d.soundType));
        src.sendMessage(Text.literal("§a[CB] Created '"+name+"' from template '"+tid+"'."));
        return 1;
    }

    private static int cmdListTemplates(ServerCommandSource src) {
        src.sendMessage(Text.literal("§6[CB] Templates ("+TemplateManager.allTemplates().size()+"):"));
        TemplateManager.allTemplates().forEach(t -> src.sendMessage(Text.literal(
                "§7  §e"+t.id()+" §7— "+t.description()+" (glow:"+t.lightLevel()+" snd:"+t.soundType()+")")));
        return 1;
    }

    private static int cmdListRecycle(ServerCommandSource src) {
        var bin = SlotManager.getRecycleBin();
        if (bin.isEmpty()) { src.sendMessage(Text.literal("§7[CB] Recycle bin empty.")); return 1; }
        src.sendMessage(Text.literal("§6[CB] Recycle ("+bin.size()+"/"+SlotManager.RECYCLE_SIZE+"):"));
        bin.forEach(d -> src.sendMessage(Text.literal("§7  "+d.customId+" — "+d.displayName)));
        return 1;
    }

    private static int cmdRecycleRestore(ServerCommandSource src, String id) {
        if (!checkEdit(src)) return 0;
        SlotManager.SlotData d = SlotManager.restoreFromRecycle(id);
        if (d == null) { src.sendMessage(Text.literal("§c[CB] Cannot restore '"+id+"'.")); return 0; }
        SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
        CustomBlocksMod.broadcastUpdate(src.getServer(), new SlotUpdatePayload("add",d.index,d.customId,d.displayName,d.texture,d.lightLevel,d.hardness,d.soundType));
        src.sendMessage(Text.literal("§a[CB] Restored '"+d.displayName+"'."));
        return 1;
    }

    private static int cmdRecyclePurge(ServerCommandSource src, String id) {
        if (!checkAdmin(src)) return 0;
        SlotManager.purgeRecycle(id); src.sendMessage(Text.literal("§a[CB] Purged '"+id+"'."));
        return 1;
    }

    private static int cmdRecycleClear(ServerCommandSource src) {
        if (!checkAdmin(src)) return 0;
        SlotManager.clearRecycle(); src.sendMessage(Text.literal("§a[CB] Recycle bin cleared."));
        return 1;
    }

    private static int cmdUndo(ServerCommandSource src) {
        if (!checkEdit(src)) return 0;
        if (!SlotManager.undo()) { src.sendMessage(Text.literal("§c[CB] Nothing to undo.")); return 0; }
        SlotManager.saveAll(); CustomBlocksMod.regenPack(src.getServer());
        List<FullSyncPayload.SlotEntry> meta = new ArrayList<>();
        SlotManager.allSlots().forEach(d -> meta.add(new FullSyncPayload.SlotEntry(d.index,d.customId,d.displayName,null,d.lightLevel,d.hardness,d.soundType)));
        FullSyncPayload syncPkt = new FullSyncPayload(meta, SlotManager.getTabIconTexture());
        src.getServer().getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, syncPkt));
        src.sendMessage(Text.literal("§a[CB] Undone. "+SlotManager.undoDepth()+" step(s) left."));
        return 1;
    }

    private static int cmdHistory(ServerCommandSource src) {
        src.sendMessage(Text.literal("§6[CB] Undo: "+SlotManager.undoDepth()+"/"+SlotManager.UNDO_DEPTH+" steps."));
        return 1;
    }

    private static int cmdList(ServerCommandSource src) {
        src.sendMessage(Text.literal("§6[CB] Blocks ("+SlotManager.usedSlots()+"/"+SlotManager.MAX_SLOTS+"):"));
        SlotManager.allSlots().forEach(d -> src.sendMessage(Text.literal(
                "§7  ["+d.index+"] §f"+d.customId+" §7— "+d.displayName+
                (d.lightLevel>0?" §e✦"+d.lightLevel:"")+
                (d.isAnimated()?" §b♦anim":"")+
                (d.hasRandom()?" §d±rnd":""))));
        return 1;
    }

    private static int cmdInfo(ServerCommandSource src, String id) {
        SlotManager.SlotData d = SlotManager.getById(id);
        if (d==null) { src.sendMessage(Text.literal("§c[CB] Not found: "+id)); return 0; }
        src.sendMessage(Text.literal("§6[CB] "+d.customId+" | §f"+d.displayName+" | slot "+d.index));
        src.sendMessage(Text.literal("  glow="+d.lightLevel+" hard="+d.hardness+" snd="+d.soundType+" unbr="+d.unbreakable));
        src.sendMessage(Text.literal("  faces="+d.faceTextures.keySet()+" anim="+d.animFrames.size()+"@"+d.animFps+"fps rand="+d.randomVariants.size()));
        return 1;
    }

    private static int cmdBrowse(ServerCommandSource src) {
        src.sendMessage(Text.literal("§a[CB] Press §fF6§a in-game to open the Custom Blocks GUI."));
        return 1;
    }

    private static int cmdAtlas(ServerCommandSource src) {
        if (!checkAdmin(src)) return 0;
        CustomBlocksMod.regenPack(src.getServer());
        src.sendMessage(Text.literal("§a[CB] Resource pack rebuilt."));
        return 1;
    }

    private static int cmdReload(ServerCommandSource src) {
        if (!checkAdmin(src)) return 0;
        PermissionManager.load(); TemplateManager.load();
        src.sendMessage(Text.literal("§a[CB] Reloaded permissions + templates."));
        return 1;
    }

    private static boolean checkEdit(ServerCommandSource src) {
        try {
            if (!PermissionManager.canEdit(src.getPlayerOrThrow())) {
                src.sendMessage(Text.literal("§c[CB] Need 'edit' permission.")); return false;
            }
            return true;
        } catch (Exception e) { return src.hasPermissionLevel(2); }
    }

    private static boolean checkAdmin(ServerCommandSource src) {
        try {
            if (!PermissionManager.isAdmin(src.getPlayerOrThrow())) {
                src.sendMessage(Text.literal("§c[CB] Need 'admin' permission.")); return false;
            }
            return true;
        } catch (Exception e) { return src.hasPermissionLevel(2); }
    }
}
