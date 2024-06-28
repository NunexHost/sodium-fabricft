package me.jellysquid.mods.sodium.client.model.quad.properties;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.util.math.Direction;

public class ModelQuadFlags {
    /**
     * Indicates that the quad does not fully cover the given face for the model.
     */
    public static final int IS_PARTIAL = 0b001;

    /**
     * Indicates that the quad is parallel to its light face.
     */
    public static final int IS_PARALLEL = 0b010;

    /**
     * Indicates that the quad is aligned to the block grid.
     * This flag is only set if {@link #IS_PARALLEL} is set.
     */
    public static final int IS_ALIGNED = 0b100;

    /**
     * @return True if the bit-flag of {@link ModelQuadFlags} contains the given flag
     */
    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    /**
     * Calculates the properties of the given quad. This data is used later by the light pipeline in order to make
     * certain optimizations.
     */
    public static int getQuadFlags(ModelQuadView quad, Direction face) {
        // Calculate min/max bounds using a single loop
        float minX = quad.getX(0);
        float minY = quad.getY(0);
        float minZ = quad.getZ(0);

        float maxX = minX;
        float maxY = minY;
        float maxZ = minZ;

        for (int i = 1; i < 4; ++i) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        // Use a switch statement for clarity and potential optimization
        int flags = 0;

        switch (face.getAxis()) {
            case X:
                flags |= (minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F) ? IS_PARTIAL : 0;
                flags |= (minX == maxX) ? IS_PARALLEL : 0;
                flags |= (minX == maxX && minX < 0.0001f) ? IS_ALIGNED : 0;
                flags |= (minX == maxX && maxX > 0.9999F) ? IS_ALIGNED : 0;
                break;
            case Y:
                flags |= (minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F) ? IS_PARTIAL : 0;
                flags |= (minY == maxY) ? IS_PARALLEL : 0;
                flags |= (minY == maxY && minY < 0.0001f) ? IS_ALIGNED : 0;
                flags |= (minY == maxY && maxY > 0.9999F) ? IS_ALIGNED : 0;
                break;
            case Z:
                flags |= (minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F) ? IS_PARTIAL : 0;
                flags |= (minZ == maxZ) ? IS_PARALLEL : 0;
                flags |= (minZ == maxZ && minZ < 0.0001f) ? IS_ALIGNED : 0;
                flags |= (minZ == maxZ && maxZ > 0.9999F) ? IS_ALIGNED : 0;
                break;
        }

        return flags;
    }
}
