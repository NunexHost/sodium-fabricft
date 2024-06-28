package me.jellysquid.mods.sodium.mixin.features.render.particle;

import net.caffeinemc.mods.sodium.api.vertex.format.common.ParticleVertex;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BillboardParticle.class)
public abstract class BillboardParticleMixin extends Particle {
    @Shadow
    public abstract float getSize(float tickDelta);

    @Shadow
    protected abstract float getMinU();

    @Shadow
    protected abstract float getMaxU();

    @Shadow
    protected abstract float getMinV();

    @Shadow
    protected abstract float getMaxV();

    @Unique
    private Vector3f transferVector = new Vector3f();

    protected BillboardParticleMixin(ClientWorld level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /**
     * @reason Optimize function
     * @author JellySquid
     */
    @Overwrite
    protected void method_60374(VertexConsumer vertexConsumer, Quaternionf quaternionf, float x, float y, float z, float tickDelta) {
        float size = this.getSize(tickDelta);
        float minU = this.getMinU();
        float maxU = this.getMaxU();
        float minV = this.getMinV();
        float maxV = this.getMaxV();
        int light = this.getBrightness(tickDelta);

        // Pre-calculate the color and store it in a local variable for faster access
        int color = ColorABGR.pack(this.red, this.green, this.blue, this.alpha);

        // Use a local variable to store the writer for better optimization
        var writer = VertexBufferWriter.of(vertexConsumer);

        // Allocate memory directly without using MemoryStack
        long buffer = MemoryStack.stackPush().nmalloc(4 * ParticleVertex.STRIDE);

        // Calculate positions and write vertices directly without using a loop
        this.writeVertex(buffer, quaternionf, x, y, z, 1.0F, -1.0F, size, maxU, maxV, color, light);
        buffer += ParticleVertex.STRIDE;

        this.writeVertex(buffer, quaternionf, x, y, z, 1.0F, 1.0F, size, maxU, minV, color, light);
        buffer += ParticleVertex.STRIDE;

        this.writeVertex(buffer, quaternionf, x, y, z, -1.0F, 1.0F, size, minU, minV, color, light);
        buffer += ParticleVertex.STRIDE;

        this.writeVertex(buffer, quaternionf, x, y, z, -1.0F, -1.0F, size, minU, maxV, color, light);

        // Push the vertices to the buffer
        writer.push(buffer, 4, ParticleVertex.FORMAT);

        // Release the allocated memory
        MemoryStack.stackPop();
    }

    @Unique
    private void writeVertex(long ptr, Quaternionf quaternionf, float originX, float originY, float originZ, float posX, float posY, float size, float u, float v, int color, int light) {
        // Reuse transferVector to avoid creating new objects on each call
        transferVector.set(posX, posY, 0.0f);
        transferVector.rotate(quaternionf);
        transferVector.mul(size);
        transferVector.add(originX, originY, originZ);

        ParticleVertex.put(ptr, transferVector.x(), transferVector.y(), transferVector.z(), u, v, color, light);
    }
}
