package com.itemmap.manager;

/**
 * Holds all per-frame settings. Stored server-side in FrameManager,
 * synced to all clients so everyone sees the same display.
 */
public class FrameData {

    public enum DisplayMode { FLAT_2D, RENDER_3D, SPIN_3D }

    public final long entityId;

    public DisplayMode mode;
    public float       spinSpeed;
    public float       scale;
    public float       padPct;
    public boolean     glowing;
    public String      label;
    public int         bgColor;
    public String      customImageId;
    public boolean     invisible;

    // client-only, not persisted
    public transient float spinAngle;

    public FrameData(long entityId) {
        this.entityId      = entityId;
        this.mode          = DisplayMode.FLAT_2D;
        this.spinSpeed     = 2.0f;
        this.scale         = 1.0f;
        this.padPct        = 0f;
        this.glowing       = false;
        this.label         = null;
        this.bgColor       = 0;
        this.customImageId = null;
        this.invisible     = false;
        this.spinAngle     = 0f;
    }

    public FrameData copy() {
        FrameData c        = new FrameData(entityId);
        c.mode             = this.mode;
        c.spinSpeed        = this.spinSpeed;
        c.scale            = this.scale;
        c.padPct           = this.padPct;
        c.glowing          = this.glowing;
        c.label            = this.label;
        c.bgColor          = this.bgColor;
        c.customImageId    = this.customImageId;
        c.invisible        = this.invisible;
        return c;
    }
}
