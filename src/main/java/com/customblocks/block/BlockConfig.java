package com.customblocks.block;

import com.customblocks.CustomBlocksMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.sound.BlockSoundGroup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class BlockConfig {

    public float hardness = 1.5f;
    public float resistance = 6.0f;
    public String sound = "stone";
    public int lightLevel = 0;
    public boolean toolRequired = true;

    public static BlockConfig load(File blockFolder) {
        BlockConfig cfg = new BlockConfig();
        File settingsFile = new File(blockFolder, "settings.txt");
        if (!settingsFile.exists()) return cfg;

        try {
            Properties props = new Properties();
            props.load(Files.newInputStream(settingsFile.toPath()));
            if (props.containsKey("hardness"))      cfg.hardness     = Float.parseFloat(props.getProperty("hardness").trim());
            if (props.containsKey("resistance"))    cfg.resistance   = Float.parseFloat(props.getProperty("resistance").trim());
            if (props.containsKey("sound"))         cfg.sound        = props.getProperty("sound").trim().toLowerCase();
            if (props.containsKey("light"))         cfg.lightLevel   = Integer.parseInt(props.getProperty("light").trim());
            if (props.containsKey("tool_required")) cfg.toolRequired = Boolean.parseBoolean(props.getProperty("tool_required").trim());
        } catch (IOException | NumberFormatException e) {
            CustomBlocksMod.LOGGER.warn("[CustomBlocks] Bad settings.txt in {}: {}", blockFolder.getName(), e.getMessage());
        }

        return cfg;
    }

    public AbstractBlock.Settings applyTo(AbstractBlock.Settings settings) {
        settings = settings.strength(hardness, resistance);
        settings = settings.sounds(resolveSound());
        settings = settings.luminance(state -> lightLevel);
        if (toolRequired) settings = settings.requiresTool();
        return settings;
    }

    private BlockSoundGroup resolveSound() {
        return switch (sound) {
            case "wood"   -> BlockSoundGroup.WOOD;
            case "gravel" -> BlockSoundGroup.GRAVEL;
            case "grass"  -> BlockSoundGroup.GRASS;
            case "metal"  -> BlockSoundGroup.METAL;
            case "glass"  -> BlockSoundGroup.GLASS;
            case "wool"   -> BlockSoundGroup.WOOL;
            case "sand"   -> BlockSoundGroup.SAND;
            case "snow"   -> BlockSoundGroup.SNOW;
            default       -> BlockSoundGroup.STONE;
        };
    }
}
