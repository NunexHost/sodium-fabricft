package me.jellysquid.mods.sodium.client.render.immediate.model;

import me.jellysquid.mods.sodium.client.render.immediate.model.AABB;
import net.minecraft.util.math.Direction;
import org.joml.*;

import java.util.Set;

public class ModelCuboid {
    public final float x1, y1, z1;
    public final float x2, y2, z2;

    public final float u0, u1, u2, u3, u4, u5;
    public final float v0, v1, v2;

    private final int faces;

    public final boolean mirror;

    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();

    // Pre-calculated bounds for fast intersection checks
    private final AABB bounds = new AABB();

    public ModelCuboid(int u, int v,
                       float x1, float y1, float z1,
                       float sizeX, float sizeY, float sizeZ,
                       float extraX, float extraY, float extraZ,
                       boolean mirror,
                       float textureWidth, float textureHeight,
                       Set<Direction> renderDirections) {
        float x2 = x1 + sizeX;
        float y2 = y1 + sizeY;
        float z2 = z1 + sizeZ;

        x1 -= extraX;
        y1 -= extraY;
        z1 -= extraZ;

        x2 += extraX;
        y2 += extraY;
        z2 += extraZ;

        if (mirror) {
            float tmp = x2;
            x2 = x1;
            x1 = tmp;
        }

        this.x1 = x1 / 16.0f;
        this.y1 = y1 / 16.0f;
        this.z1 = z1 / 16.0f;

        this.x2 = x2 / 16.0f;
        this.y2 = y2 / 16.0f;
        this.z2 = z2 / 16.0f;

        var scaleU = 1.0f / textureWidth;
        var scaleV = 1.0f / textureHeight;

        this.u0 = scaleU * (u);
        this.u1 = scaleU * (u + sizeZ);
        this.u2 = scaleU * (u + sizeZ + sizeX);
        this.u3 = scaleU * (u + sizeZ + sizeX + sizeX);
        this.u4 = scaleU * (u + sizeZ + sizeX + sizeZ);
        this.u5 = scaleU * (u + sizeZ + sizeX + sizeZ + sizeX);

        this.v0 = scaleV * (v);
        this.v1 = scaleV * (v + sizeZ);
        this.v2 = scaleV * (v + sizeZ + sizeY);

        this.mirror = mirror;

        int faces = 0;

        for (var dir : renderDirections) {
            faces |= 1 << dir.ordinal();
        }

        this.faces = faces;

        // Pre-calculate bounds for fast intersection checks
        this.min.set(this.x1, this.y1, this.z1);
        this.max.set(this.x2, this.y2, this.z2);
        this.bounds.set(this.min, this.max);
    }

    public boolean shouldDrawFace(int quadIndex) {
        return (this.faces & (1 << quadIndex)) != 0;
    }

    /**
     * Checks if this cuboid intersects the given AABB.
     *
     * @param aabb The AABB to check against.
     * @return True if the cuboids intersect, false otherwise.
     */
    public boolean intersects(AABB aabb) {
        return this.bounds.intersects(aabb);
    }
}
