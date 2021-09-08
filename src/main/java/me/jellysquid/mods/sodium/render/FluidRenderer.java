package me.jellysquid.mods.sodium.render;

import me.jellysquid.mods.sodium.interop.fabric.SodiumRenderer;
import me.jellysquid.mods.sodium.model.quad.QuadColorizer;
import me.jellysquid.mods.sodium.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.util.DirectionUtil;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderHandlerRegistryImpl;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

public class FluidRenderer {
    private static final RenderMaterial WATER_MATERIAL = SodiumRenderer.INSTANCE
            .materialFinder()
            .blendMode(0, BlendMode.TRANSLUCENT)
            .find();

    private static final RenderMaterial LAVA_MATERIAL = SodiumRenderer.INSTANCE
            .materialFinder()
            .disableDiffuse(0, true)
            .emissive(0, true)
            .blendMode(0, BlendMode.SOLID)
            .disableAo(0, true)
            .disableColorIndex(0, true)
            .find();

    // TODO: allow this to be changed by vertex format
    // TODO: move fluid rendering to a separate render pass and control glPolygonOffset and glDepthFunc to fix this properly
    private static final float EPSILON = 0.001f;

    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();
    private final BiomeColorBlender biomeColorBlender;

    private final Sprite waterOverlaySprite;

    // Cached wrapper type that adapts FluidRenderHandler to support QuadColorProvider<FluidState>
    private final FabricFluidColorizerAdapter fabricColorProviderAdapter = new FabricFluidColorizerAdapter();

    public FluidRenderer(BiomeColorBlender biomeColorBlender) {
        this.biomeColorBlender = biomeColorBlender;
        this.waterOverlaySprite = ModelLoader.WATER_OVERLAY.getSprite();
    }

    private boolean isFluidExposed(BlockRenderView world, int x, int y, int z, Direction dir, Fluid fluid) {
        // Up direction is hard to test since it doesnt fill the block
        if(dir != Direction.UP) {
            BlockPos pos = this.scratchPos.set(x, y, z);
            BlockState blockState = world.getBlockState(pos);
            VoxelShape shape = blockState.getCullingShape(world, pos);
            if (VoxelShapes.isSideCovered(VoxelShapes.fullCube(), shape, dir.getOpposite())) {
                return false; // Fluid is in waterlogged block that self occludes
            }
        }

        BlockPos pos = this.scratchPos.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
        return !world.getFluidState(pos).getFluid().matchesType(fluid);
    }

    private boolean isSideExposed(BlockRenderView world, int x, int y, int z, Direction dir, float height) {
        BlockPos pos = this.scratchPos.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
        BlockState blockState = world.getBlockState(pos);

        if (blockState.isOpaque()) {
            VoxelShape shape = blockState.getCullingShape(world, pos);

            // Hoist these checks to avoid allocating the shape below
            if (shape == VoxelShapes.fullCube()) {
                // The top face always be inset, so if the shape above is a full cube it can't possibly occlude
                return dir == Direction.UP;
            } else if (shape.isEmpty()) {
                return true;
            }

            VoxelShape threshold = VoxelShapes.cuboid(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);

            return !VoxelShapes.isSideCovered(threshold, shape, dir);
        }

        return true;
    }

    public void render(BlockRenderView world, FluidState fluidState, BlockPos pos, RenderContext ctx) {
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        Fluid fluid = fluidState.getFluid();

        boolean sfUp = this.isFluidExposed(world, posX, posY, posZ, Direction.UP, fluid);
        boolean sfDown = this.isFluidExposed(world, posX, posY, posZ, Direction.DOWN, fluid) &&
                this.isSideExposed(world, posX, posY, posZ, Direction.DOWN, 0.8888889F);
        boolean sfNorth = this.isFluidExposed(world, posX, posY, posZ, Direction.NORTH, fluid);
        boolean sfSouth = this.isFluidExposed(world, posX, posY, posZ, Direction.SOUTH, fluid);
        boolean sfWest = this.isFluidExposed(world, posX, posY, posZ, Direction.WEST, fluid);
        boolean sfEast = this.isFluidExposed(world, posX, posY, posZ, Direction.EAST, fluid);

        if (!sfUp && !sfDown && !sfEast && !sfWest && !sfNorth && !sfSouth) {
            return;
        }

        boolean isWater = fluidState.isIn(FluidTags.WATER);

        RenderMaterial material = isWater ? WATER_MATERIAL : LAVA_MATERIAL;

        QuadEmitter emitter = ctx.getEmitter();

        FluidRenderHandler handler = FluidRenderHandlerRegistryImpl.INSTANCE.getOverride(fluidState.getFluid());
        QuadColorizer<FluidState> colorizer = this.createColorProviderAdapter(handler);

        Sprite[] sprites = handler.getFluidSprites(world, pos, fluidState);

        float h1 = this.getCornerHeight(world, posX, posY, posZ, fluidState.getFluid());
        float h2 = this.getCornerHeight(world, posX, posY, posZ + 1, fluidState.getFluid());
        float h3 = this.getCornerHeight(world, posX + 1, posY, posZ + 1, fluidState.getFluid());
        float h4 = this.getCornerHeight(world, posX + 1, posY, posZ, fluidState.getFluid());

        float yOffset = sfDown ? EPSILON : 0.0F;

        if (sfUp && this.isSideExposed(world, posX, posY, posZ, Direction.UP, Math.min(Math.min(h1, h2), Math.min(h3, h4)))) {
            h1 -= EPSILON;
            h2 -= EPSILON;
            h3 -= EPSILON;
            h4 -= EPSILON;

            Vec3d velocity = fluidState.getVelocity(world, pos);

            Sprite sprite;
            float u1, u2, u3, u4;
            float v1, v2, v3, v4;

            if (velocity.x == 0.0D && velocity.z == 0.0D) {
                sprite = sprites[0];
                u1 = sprite.getFrameU(0.0D);
                v1 = sprite.getFrameV(0.0D);
                u2 = u1;
                v2 = sprite.getFrameV(16.0D);
                u3 = sprite.getFrameU(16.0D);
                v3 = v2;
                u4 = u3;
                v4 = v1;
            } else {
                sprite = sprites[1];
                float dir = (float) MathHelper.atan2(velocity.z, velocity.x) - (1.5707964f);
                float sin = MathHelper.sin(dir) * 0.25F;
                float cos = MathHelper.cos(dir) * 0.25F;
                u1 = sprite.getFrameU(8.0F + (-cos - sin) * 16.0F);
                v1 = sprite.getFrameV(8.0F + (-cos + sin) * 16.0F);
                u2 = sprite.getFrameU(8.0F + (-cos + sin) * 16.0F);
                v2 = sprite.getFrameV(8.0F + (cos + sin) * 16.0F);
                u3 = sprite.getFrameU(8.0F + (cos + sin) * 16.0F);
                v3 = sprite.getFrameV(8.0F + (cos - sin) * 16.0F);
                u4 = sprite.getFrameU(8.0F + (cos - sin) * 16.0F);
                v4 = sprite.getFrameV(8.0F + (-cos - sin) * 16.0F);
            }

            float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
            float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
            float s1 = (float) sprites[0].getWidth() / (sprites[0].getMaxU() - sprites[0].getMinU());
            float s2 = (float) sprites[0].getHeight() / (sprites[0].getMaxV() - sprites[0].getMinV());
            float s3 = 4.0F / Math.max(s2, s1);

            u1 = MathHelper.lerp(s3, u1, uAvg);
            u2 = MathHelper.lerp(s3, u2, uAvg);
            u3 = MathHelper.lerp(s3, u3, uAvg);
            u4 = MathHelper.lerp(s3, u4, uAvg);
            v1 = MathHelper.lerp(s3, v1, vAvg);
            v2 = MathHelper.lerp(s3, v2, vAvg);
            v3 = MathHelper.lerp(s3, v3, vAvg);
            v4 = MathHelper.lerp(s3, v4, vAvg);

            emitter.material(material);
            emitter.pos(0, 0.0f, h1, 0.0f).sprite(0, 0, u1, v1);
            emitter.pos(1, 0.0f, h2, 1.0F).sprite(1, 0, u2, v2);
            emitter.pos(2, 1.0F, h3, 1.0F).sprite(2, 0, u3, v3);
            emitter.pos(3, 1.0F, h4, 0.0f).sprite(3, 0, u4, v4);

            this.calculateQuadColors(emitter, world, pos, fluidState, colorizer);

            emitter.emit();

            if (fluidState.method_15756(world, this.scratchPos.set(posX, posY + 1, posZ))) {
                emitter.material(material);
                emitter.pos(3, 0.0f, h1, 0.0f).sprite(3, 0, u1, v1);
                emitter.pos(2, 0.0f, h2, 1.0F).sprite(2, 0, u2, v2);
                emitter.pos(1, 1.0F, h3, 1.0F).sprite(1, 0, u3, v3);
                emitter.pos(0, 1.0F, h4, 0.0f).sprite(0, 0, u4, v4);

                this.calculateQuadColors(emitter, world, pos, fluidState, colorizer);

                emitter.emit();
            }

        }

        if (sfDown) {
            Sprite sprite = sprites[0];

            float minU = sprite.getMinU();
            float maxU = sprite.getMaxU();
            float minV = sprite.getMinV();
            float maxV = sprite.getMaxV();

            emitter.material(material);
            emitter.pos(0, 0.0f, yOffset, 1.0F).sprite(0, 0, minU, maxV);
            emitter.pos(1, 0.0f, yOffset, 0.0f).sprite(1, 0, minU, minV);
            emitter.pos(2, 1.0F, yOffset, 0.0f).sprite(2, 0, maxU, minV);
            emitter.pos(3, 1.0F, yOffset, 1.0F).sprite(3, 0, maxU, maxV);

            this.calculateQuadColors(emitter, world, pos, fluidState, colorizer);

            emitter.emit();
        }

        for (Direction dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            float c1;
            float c2;
            float x1;
            float z1;
            float x2;
            float z2;

            switch (dir) {
                case NORTH:
                    if (!sfNorth) {
                        continue;
                    }

                    c1 = h1;
                    c2 = h4;
                    x1 = 0.0f;
                    x2 = 1.0F;
                    z1 = EPSILON;
                    z2 = z1;
                    break;
                case SOUTH:
                    if (!sfSouth) {
                        continue;
                    }

                    c1 = h3;
                    c2 = h2;
                    x1 = 1.0F;
                    x2 = 0.0f;
                    z1 = 1.0f - EPSILON;
                    z2 = z1;
                    break;
                case WEST:
                    if (!sfWest) {
                        continue;
                    }

                    c1 = h2;
                    c2 = h1;
                    x1 = EPSILON;
                    x2 = x1;
                    z1 = 1.0F;
                    z2 = 0.0f;
                    break;
                case EAST:
                    if (!sfEast) {
                        continue;
                    }

                    c1 = h4;
                    c2 = h3;
                    x1 = 1.0f - EPSILON;
                    x2 = x1;
                    z1 = 0.0f;
                    z2 = 1.0F;
                    break;
                default:
                    continue;
            }

            if (this.isSideExposed(world, posX, posY, posZ, dir, Math.max(c1, c2))) {
                int adjX = posX + dir.getOffsetX();
                int adjY = posY + dir.getOffsetY();
                int adjZ = posZ + dir.getOffsetZ();

                Sprite sprite = sprites[1];

                if (isWater) {
                    BlockPos posAdj = this.scratchPos.set(adjX, adjY, adjZ);
                    Block block = world.getBlockState(posAdj).getBlock();

                    if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
                        sprite = this.waterOverlaySprite;
                    }
                }

                float u1 = sprite.getFrameU(0.0D);
                float u2 = sprite.getFrameU(8.0D);
                float v1 = sprite.getFrameV((1.0F - c1) * 16.0F * 0.5F);
                float v2 = sprite.getFrameV((1.0F - c2) * 16.0F * 0.5F);
                float v3 = sprite.getFrameV(8.0D);

                emitter.material(material);
                emitter.pos(0, x2, c2, z2).sprite(0, 0, u2, v2);
                emitter.pos(1, x2, yOffset, z2).sprite(1, 0, u2, v3);
                emitter.pos(2, x1, yOffset, z1).sprite(2, 0, u1, v3);
                emitter.pos(3, x1, c1, z1).sprite(3, 0, u1, v1);

                this.calculateQuadColors(emitter, world, pos, fluidState, colorizer);

                emitter.emit();

                if (sprite != this.waterOverlaySprite) {
                    emitter.material(material);
                    emitter.pos(3, x2, c2, z2).sprite(0, 0, u2, v2);
                    emitter.pos(2, x2, yOffset, z2).sprite(1, 0, u2, v3);
                    emitter.pos(1, x1, yOffset, z1).sprite(2, 0, u1, v3);
                    emitter.pos(0, x1, c1, z1).sprite(3, 0, u1, v1);

                    this.calculateQuadColors(emitter, world, pos, fluidState, colorizer);

                    emitter.emit();
                }
            }
        }
    }

    private void calculateQuadColors(QuadEmitter quad, BlockRenderView world, BlockPos pos, FluidState fluidState, QuadColorizer<FluidState> handler) {
        int[] biomeColors = this.biomeColorBlender.getColors(world, pos, quad, handler, fluidState);

        for (int i = 0; i < 4; i++) {
            quad.spriteColor(i, 0, biomeColors != null ? biomeColors[i] : 0xFFFFFFFF);
        }
    }

    private QuadColorizer<FluidState> createColorProviderAdapter(FluidRenderHandler handler) {
        FabricFluidColorizerAdapter adapter = this.fabricColorProviderAdapter;
        adapter.setHandler(handler);

        return adapter;
    }

    private float getCornerHeight(BlockRenderView world, int x, int y, int z, Fluid fluid) {
        int samples = 0;
        float totalHeight = 0.0F;

        for (int i = 0; i < 4; ++i) {
            int x2 = x - (i & 1);
            int z2 = z - (i >> 1 & 1);

            if (world.getFluidState(this.scratchPos.set(x2, y + 1, z2)).getFluid().matchesType(fluid)) {
                return 1.0F;
            }

            BlockPos pos = this.scratchPos.set(x2, y, z2);

            BlockState blockState = world.getBlockState(pos);
            FluidState fluidState = blockState.getFluidState();

            if (fluidState.getFluid().matchesType(fluid)) {
                float height = fluidState.getHeight(world, pos);

                if (height >= 0.8F) {
                    totalHeight += height * 10.0F;
                    samples += 10;
                } else {
                    totalHeight += height;
                    ++samples;
                }
            } else if (!blockState.getMaterial().isSolid()) {
                ++samples;
            }
        }

        return totalHeight / (float) samples;
    }

    private static class FabricFluidColorizerAdapter implements QuadColorizer<FluidState> {
        private FluidRenderHandler handler;

        public void setHandler(FluidRenderHandler handler) {
            this.handler = handler;
        }

        @Override
        public int getColor(FluidState state, @Nullable BlockRenderView world, @Nullable BlockPos pos, int tintIndex) {
            if (this.handler == null) {
                return -1;
            }

            return this.handler.getFluidColor(world, pos, state);
        }
    }
}