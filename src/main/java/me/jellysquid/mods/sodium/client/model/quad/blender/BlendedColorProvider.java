package me.jellysquid.mods.sodium.client.model.quad.blender;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public abstract class BlendedColorProvider<T> implements ColorProvider<T> {
    @Override
    public void getColors(WorldSlice view, BlockPos pos, T state, ModelQuadView quad, int[] output) {
        // Pre-calculate block position offsets for optimization
        final int blockX = pos.getX();
        final int blockY = pos.getY();
        final int blockZ = pos.getZ();

        // Reuse interpolation values for all vertices
        float interpX, interpY, interpZ;

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            // Offset the position by -0.5f to align smooth blending with flat blending.
            interpX = quad.getX(vertexIndex) - 0.5f;
            interpY = quad.getY(vertexIndex) - 0.5f;
            interpZ = quad.getZ(vertexIndex) - 0.5f;

            output[vertexIndex] = this.getVertexColor(view, blockX, blockY, blockZ, interpX, interpY, interpZ);
        }
    }

    private int getVertexColor(WorldSlice world, int blockX, int blockY, int blockZ, float interpX, float interpY, float interpZ) {
        // Floor the positions here to always get the largest integer below the input
        // as negative values by default round toward zero when casting to an integer.
        // Which would cause negative ratios to be calculated in the interpolation later on.
        final int intX = MathHelper.floor(interpX);
        final int intY = MathHelper.floor(interpY);
        final int intZ = MathHelper.floor(interpZ);

        // Integer component of position vector
        final int worldIntX = blockX + intX;
        final int worldIntY = blockY + intY;
        final int worldIntZ = blockZ + intZ;

        // Retrieve the color values for each neighboring block
        // Use an array for efficient access
        final int[] colors = new int[8];
        colors[0] = this.getColor(world, worldIntX + 0, worldIntY + 0, worldIntZ + 0);
        colors[1] = this.getColor(world, worldIntX + 0, worldIntY + 0, worldIntZ + 1);
        colors[2] = this.getColor(world, worldIntX + 0, worldIntY + 1, worldIntZ + 0);
        colors[3] = this.getColor(world, worldIntX + 0, worldIntY + 1, worldIntZ + 1);
        colors[4] = this.getColor(world, worldIntX + 1, worldIntY + 0, worldIntZ + 0);
        colors[5] = this.getColor(world, worldIntX + 1, worldIntY + 0, worldIntZ + 1);
        colors[6] = this.getColor(world, worldIntX + 1, worldIntY + 1, worldIntZ + 0);
        colors[7] = this.getColor(world, worldIntX + 1, worldIntY + 1, worldIntZ + 1);

        // Linear interpolation across the Z-axis
        int z0, z1;
        if (colors[0] != colors[1]) {
            z0 = ColorMixer.mix(colors[0], colors[1], interpZ - intZ);
        } else {
            z0 = colors[0];
        }
        if (colors[4] != colors[5]) {
            z1 = ColorMixer.mix(colors[4], colors[5], interpZ - intZ);
        } else {
            z1 = colors[4];
        }

        // Linear interpolation across the X-axis
        int x0;
        if (z0 != z1) {
            x0 = ColorMixer.mix(z0, z1, interpX - intX);
        } else {
            x0 = z0;
        }

        // Linear interpolation across the Y-axis (assuming Y is the vertical axis)
        int x1;
        if (colors[2] != colors[3]) {
            x1 = ColorMixer.mix(colors[2], colors[3], interpZ - intZ);
        } else {
            x1 = colors[2];
        }
        if (colors[6] != colors[7]) {
            x1 = ColorMixer.mix(colors[6], colors[7], interpZ - intZ);
        } else {
            x1 = colors[6];
        }
        
        int finalColor;
        if (x0 != x1) {
            finalColor = ColorMixer.mix(x0, x1, interpY - intY);
        } else {
            finalColor = x0;
        }

        return ColorARGB.toABGR(finalColor);
    }

    protected abstract int getColor(WorldSlice world, int x, int y, int z);
}
