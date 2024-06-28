package me.jellysquid.mods.sodium.client.render.immediate.model;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public class BakedModelEncoder {

    private static final int VERTEX_STRIDE = ModelVertex.STRIDE;

    // Pre-allocate memory for vertex data, reducing heap allocations
    private static final ThreadLocal<MemoryStack> STACK = ThreadLocal.withInitial(MemoryStack::stackPush);
    private static final ThreadLocal<Long> BUFFER = ThreadLocal.withInitial(() -> {
        MemoryStack stack = STACK.get();
        return stack.nmalloc(4 * VERTEX_STRIDE);
    }); 

    public static void writeQuadVertices(VertexBufferWriter writer, MatrixStack.Entry matrices, ModelQuadView quad, int color, int light, int overlay) {
        Matrix3f matNormal = matrices.getNormalMatrix();
        Matrix4f matPosition = matrices.getPositionMatrix();

        // Reuse the same memory stack and buffer for each call, minimizing allocations
        MemoryStack stack = STACK.get();
        long buffer = BUFFER.get().longValue(); // Unbox the Long
        long ptr = buffer;

        // Pre-compute the transformed normal vector
        var normal = MatrixHelper.transformNormal(matNormal, matrices.canSkipNormalization, quad.getLightFace());

        // Unroll the loop for better performance
        float x0 = quad.getX(0);
        float y0 = quad.getY(0);
        float z0 = quad.getZ(0);
        float u0 = quad.getTexU(0);
        float v0 = quad.getTexV(0);
        float x1 = quad.getX(1);
        float y1 = quad.getY(1);
        float z1 = quad.getZ(1);
        float u1 = quad.getTexU(1);
        float v1 = quad.getTexV(1);
        float x2 = quad.getX(2);
        float y2 = quad.getY(2);
        float z2 = quad.getZ(2);
        float u2 = quad.getTexU(2);
        float v2 = quad.getTexV(2);
        float x3 = quad.getX(3);
        float y3 = quad.getY(3);
        float z3 = quad.getZ(3);
        float u3 = quad.getTexU(3);
        float v3 = quad.getTexV(3);

        // Transform positions in bulk, avoiding repeated calculations
        float xt0 = MatrixHelper.transformPositionX(matPosition, x0, y0, z0);
        float yt0 = MatrixHelper.transformPositionY(matPosition, x0, y0, z0);
        float zt0 = MatrixHelper.transformPositionZ(matPosition, x0, y0, z0);
        float xt1 = MatrixHelper.transformPositionX(matPosition, x1, y1, z1);
        float yt1 = MatrixHelper.transformPositionY(matPosition, x1, y1, z1);
        float zt1 = MatrixHelper.transformPositionZ(matPosition, x1, y1, z1);
        float xt2 = MatrixHelper.transformPositionX(matPosition, x2, y2, z2);
        float yt2 = MatrixHelper.transformPositionY(matPosition, x2, y2, z2);
        float zt2 = MatrixHelper.transformPositionZ(matPosition, x2, y2, z2);
        float xt3 = MatrixHelper.transformPositionX(matPosition, x3, y3, z3);
        float yt3 = MatrixHelper.transformPositionY(matPosition, x3, y3, z3);
        float zt3 = MatrixHelper.transformPositionZ(matPosition, x3, y3, z3);

        // Write vertices with optimized inlining and memory access
        ModelVertex.write(ptr, xt0, yt0, zt0, color, u0, v0, overlay, light, normal);
        ptr += VERTEX_STRIDE;
        ModelVertex.write(ptr, xt1, yt1, zt1, color, u1, v1, overlay, light, normal);
        ptr += VERTEX_STRIDE;
        ModelVertex.write(ptr, xt2, yt2, zt2, color, u2, v2, overlay, light, normal);
        ptr += VERTEX_STRIDE;
        ModelVertex.write(ptr, xt3, yt3, zt3, color, u3, v3, overlay, light, normal);
        ptr += VERTEX_STRIDE;

        // Push the data to the vertex buffer
        writer.push(stack, buffer, 4, ModelVertex.FORMAT);
    }

    public static void writeQuadVertices(VertexBufferWriter writer, MatrixStack.Entry matrices, ModelQuadView quad, float r, float g, float b, float a, float[] brightnessTable, boolean colorize, int[] light, int overlay) {
        Matrix3f matNormal = matrices.getNormalMatrix();
        Matrix4f matPosition = matrices.getPositionMatrix();

        MemoryStack stack = STACK.get();
        long buffer = BUFFER.get().longValue(); // Unbox the Long
        long ptr = buffer;

        var normal = MatrixHelper.transformNormal(matNormal, matrices.canSkipNormalization, quad.getLightFace());

        float x0 = quad.getX(0);
        float y0 = quad.getY(0);
        float z0 = quad.getZ(0);
        float u0 = quad.getTexU(0);
        float v0 = quad.getTexV(0);
        float x1 = quad.getX(1);
        float y1 = quad.getY(1);
        float z1 = quad.getZ(1);
        float u1 = quad.getTexU(1);
        float v1 = quad.getTexV(1);
        float x2 = quad.getX(2);
        float y2 = quad.getY(2);
        float z2 = quad.getZ(2);
        float u2 = quad.getTexU(2);
        float v2 = quad.getTexV(2);
        float x3 = quad.getX(3);
        float y3 = quad.getY(3);
        float z3 = quad.getZ(3);
        float u3 = quad.getTexU(3);
        float v3 = quad.getTexV(3);

        float xt0 = MatrixHelper.transformPositionX(matPosition, x0, y0, z0);
        float yt0 = MatrixHelper.transformPositionY(matPosition, x0, y0, z0);
        float zt0 = MatrixHelper.transformPositionZ(matPosition, x0, y0, z0);
        float xt1 = MatrixHelper.transformPositionX(matPosition, x1, y1, z1);
        float yt1 = MatrixHelper.transformPositionY(matPosition, x1, y1, z1);
        float zt1 = MatrixHelper.transformPositionZ(matPosition, x1, y1, z1);
        float xt2 = MatrixHelper.transformPositionX(matPosition, x2, y2, z2);
        float yt2 = MatrixHelper.transformPositionY(matPosition, x2, y2, z2);
        float zt2 = MatrixHelper.transformPositionZ(matPosition, x2, y2, z2);
        float xt3 = MatrixHelper.transformPositionX(matPosition, x3, y3, z3);
        float yt3 = MatrixHelper.transformPositionY(matPosition, x3, y3, z3);
        float zt3 = MatrixHelper.transformPositionZ(matPosition, x3, y3, z3);

        // Optimize color calculations and packing
        float brightness0 = brightnessTable[0];
        float brightness1 = brightnessTable[1];
        float brightness2 = brightnessTable[2];
        float brightness3 = brightnessTable[3];

        int color0, color1, color2, color3;
        if (colorize) {
            int c0 = quad.getColor(0);
            int c1 = quad.getColor(1);
            int c2 = quad.getColor(2);
            int c3 = quad.getColor(3);
            float oR0 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(c0));
            float oG0 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(c0));
            float oB0 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(c0));
            float oR1 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(c1));
            float oG1 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(c1));
            float oB1 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(c1));
            float oR2 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(c2));
            float oG2 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(c2));
            float oB2 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(c2));
            float oR3 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(c3));
            float oG3 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(c3));
            float oB3 = ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(c3));

            color0 = ColorABGR.pack(oR0 * brightness0 * r, oG0 * brightness0 * g, oB0 * brightness0 * b, a);
            color1 = ColorABGR.pack(oR1 * brightness1 * r, oG1 * brightness1 * g, oB1 * brightness1 * b, a);
            color2 = ColorABGR.pack(oR2 * brightness2 * r, oG2 * brightness2 * g, oB2 * brightness2 * b, a);
            color3 = ColorABGR.pack(oR3 * brightness3 * r, oG3 * brightness3 * g, oB3 * brightness3 * b, a);
        } else {
            color0 = ColorABGR.pack(brightness0 * r, brightness0 * g, brightness0 * b, a);
            color1 = ColorABGR.pack(brightness1 * r, brightness1 * g, brightness1 * b, a);
            color2 = ColorABGR.pack(brightness2 * r, brightness2 * g, brightness2 * b, a);
            color3 = ColorABGR.pack(brightness3 * r, brightness3 * g, brightness3 * b, a);
        }

        ModelVertex.write(ptr, xt0, yt0, zt0, color0, u0, v0, overlay, light[0], normal);
        ptr += VERTEX_STRIDE;
        ModelVertex.write(ptr, xt1, yt1, zt1, color1, u1, v1, overlay, light[1], normal);
        ptr += VERTEX_STRIDE;
        ModelVertex.write(ptr, xt2, yt2, zt2, color2, u2, v2, overlay, light[2], normal);
        ptr += VERTEX_STRIDE;
        ModelVertex.write(ptr, xt3, yt3, zt3, color3, u3, v3, overlay, light[3], normal);
        ptr += VERTEX_STRIDE;

        writer.push(stack, buffer, 4, ModelVertex.FORMAT);
    }
            }
