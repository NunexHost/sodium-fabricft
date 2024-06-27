package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.DrawCommandList;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.util.BitwiseMath;
import org.lwjgl.system.MemoryUtil;

import java.util.Iterator;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final MultiDrawBatch batch;

    private final SharedQuadIndexBuffer sharedIndexBuffer;

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
    }

    @Override
    public void render(ChunkRenderMatrices matrices,
                   CommandList commandList,
                   ChunkRenderListIterable renderLists,
                   TerrainRenderPass renderPass,
                   CameraTransform camera) {
    super.begin(renderPass);

    boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;

    ChunkShaderInterface shader = this.activeProgram.getInterface();
    shader.setProjectionMatrix(matrices.projection());
    shader.setModelViewMatrix(matrices.modelView());

    Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());

    while (iterator.hasNext()) {
        ChunkRenderList renderList = iterator.next();

        var region = renderList.getRegion();
        var storage = region.getStorage(renderPass);

        if (storage == null) {
            continue;
        }

        fillCommandBuffer(this.batch, region, storage, renderList, camera, renderPass, useBlockFaceCulling);

        if (this.batch.isEmpty()) {
            continue;
        }

        this.sharedIndexBuffer.ensureCapacity(commandList, this.batch.getIndexBufferSize());

        var tessellation = this.prepareTessellation(commandList, region);

        setModelMatrixUniforms(shader, region, camera);
        executeDrawBatch(commandList, tessellation, this.batch);
    }

    super.end(renderPass);
}

private static void fillCommandBuffer(MultiDrawBatch batch,
                                      RenderRegion renderRegion,
                                      SectionRenderDataStorage renderDataStorage,
                                      ChunkRenderList renderList,
                                      CameraTransform camera,
                                      TerrainRenderPass pass,
                                      boolean useBlockFaceCulling) {
    batch.clear();

    var iterator = renderList.sectionsWithGeometryIterator(pass.isReverseOrder());

    if (iterator == null) {
        return;
    }

    int originX = renderRegion.getChunkX();
    int originY = renderRegion.getChunkY();
    int originZ = renderRegion.getChunkZ();

    int[] planes = new int[ModelQuadFacing.COUNT];

    while (iterator.hasNext()) {
        int sectionIndex = iterator.nextByteAsInt();

        int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
        int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
        int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

        var pMeshData = renderDataStorage.getDataPointer(sectionIndex);

        int slices;

        if (useBlockFaceCulling) {
            slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
        } else {
            slices = ModelQuadFacing.ALL;
        }

        slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

        if (slices != 0) {
            addDrawCommands(batch, pMeshData, slices);
        }
    }
}

private static void addDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
    final var pBaseVertex = batch.pBaseVertex;
    final var pElementCount = batch.pElementCount;

    int size = batch.size;

    for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
        MemoryUtil.memPutInt(pBaseVertex + (size << 2), SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing));
        MemoryUtil.memPutInt(pElementCount + (size << 2), SectionRenderDataUnsafe.getElementCount(pMeshData, facing));

        size += (mask >> facing) & 1;
    }

    batch.size = size;
}
