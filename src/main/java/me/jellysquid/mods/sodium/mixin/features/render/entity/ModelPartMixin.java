package me.jellysquid.mods.sodium.mixin.features.render.entity;

import me.jellysquid.mods.sodium.client.model.ModelCuboidAccessor;
import me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer;
import me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid;
import me.jellysquid.mods.sodium.client.render.immediate.model.ModelPartData;
import me.jellysquid.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public class ModelPartMixin implements ModelPartData {
    @Shadow
    public float pivotX;
    @Shadow
    public float pivotY;
    @Shadow
    public float pivotZ;

    @Shadow
    public float xScale;
    @Shadow
    public float yScale;
    @Shadow
    public float zScale;

    @Shadow
    public float yaw;
    @Shadow
    public float pitch;
    @Shadow
    public float roll;

    @Shadow
    public boolean visible;
    @Shadow
    public boolean hidden;

    @Mutable
    @Shadow
    @Final
    private List<ModelPart.Cuboid> cuboids;

    @Mutable
    @Shadow
    @Final
    private Map<String, ModelPart> children;

    @Unique
    private ModelPart[] sodium$children;

    @Unique
    private ModelCuboid[] sodium$cuboids;

    @Unique
    private boolean sodium$needsUpdate; // Flag to track changes requiring cuboid updates

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(List<ModelPart.Cuboid> cuboids, Map<String, ModelPart> children, CallbackInfo ci) {
        this.sodium$needsUpdate = true;

        var copies = new ModelCuboid[cuboids.size()];

        // Copy cuboids to avoid modifying the original list
        for (int i = 0; i < cuboids.size(); i++) {
            var accessor = (ModelCuboidAccessor) cuboids.get(i);
            copies[i] = accessor.sodium$copy();
        }

        this.sodium$cuboids = copies;
        this.sodium$children = children.values()
                .toArray(ModelPart[]::new);

        // Make the collections immutable to prevent accidental modifications
        this.cuboids = Collections.unmodifiableList(this.cuboids);
        this.children = Collections.unmodifiableMap(this.children);
    }

    // Inject into any method that modifies cuboid data
    @Inject(method = {"setPivot", "setRotation", "setScale", "setHidden"}, at = @At("RETURN"))
    private void onModelPartUpdate(CallbackInfo ci) {
        this.sodium$needsUpdate = true;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    private void onRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(vertices);

        if (writer == null) {
            return;
        }

        ci.cancel();

        // Update cuboid data if necessary
        if (this.sodium$needsUpdate) {
            this.updateCuboids();
            this.sodium$needsUpdate = false;
        }

        // Render using the immediate mode renderer
        EntityRenderer.render(matrices, writer, (ModelPart) (Object) this, light, overlay, ColorARGB.toABGR(color));
    }

    // Update cuboid data based on current model part properties
    private void updateCuboids() {
        for (int i = 0; i < this.sodium$cuboids.length; i++) {
            var cuboid = this.sodium$cuboids[i];
            var originalCuboid = this.cuboids.get(i);

            // Apply transformations from the model part to the cuboid
            cuboid.setPivot(originalCuboid.origin.x * (1.0f / 16.0f), originalCuboid.origin.y * (1.0f / 16.0f), originalCuboid.origin.z * (1.0f / 16.0f));
            cuboid.setSize(originalCuboid.size.x * (1.0f / 16.0f), originalCuboid.size.y * (1.0f / 16.0f), originalCuboid.size.z * (1.0f / 16.0f));
            cuboid.setOffset(originalCuboid.offset.x * (1.0f / 16.0f), originalCuboid.offset.y * (1.0f / 16.0f), originalCuboid.offset.z * (1.0f / 16.0f));
            cuboid.setRotation(originalCuboid.rotation.x, originalCuboid.rotation.y, originalCuboid.rotation.z);
            cuboid.setMirror(originalCuboid.mirror);

            // Apply scaling and rotation from the model part
            cuboid.scale(this.xScale, this.yScale, this.zScale);
            cuboid.rotate(this.roll, this.yaw, this.pitch);
            cuboid.translate(this.pivotX * (1.0f / 16.0f), this.pivotY * (1.0f / 16.0f), this.pivotZ * (1.0f / 16.0f));
        }
    }

    /**
     * @author JellySquid
     * @reason Apply transform more quickly
     */
    @Overwrite
    public void rotate(MatrixStack matrixStack) {
        // No need to apply transformations here, they are already baked into the cuboids
    }

    @Override
    public ModelCuboid[] getCuboids() {
        return this.sodium$cuboids;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public boolean isHidden() {
        return this.hidden;
    }

    @Override
    public ModelPart[] getChildren() {
        return this.sodium$children;
    }
}
