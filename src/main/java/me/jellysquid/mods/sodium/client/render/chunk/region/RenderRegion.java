package me.jellysquid.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.Map;

/**
 * Represents a 8x4x8 region of chunks which are processed and rendered together.
 */
public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    protected static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    protected static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    protected static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final StagingBuffer stagingBuffer;
    private final int x, y, z;

    private final ChunkRenderList renderList;

    // Use a fixed-size array for sections instead of a HashMap for better performance
    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    // Optimized section count tracking with a single int variable
    private int sectionCount;

    // Store section render data using a Map for easy access and management
    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new Reference2ReferenceOpenHashMap<>();
    // Resources for this region, including geometry and tessellation data
    private DeviceResources resources;

    public RenderRegion(int x, int y, int z, StagingBuffer stagingBuffer) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.stagingBuffer = stagingBuffer;
        this.renderList = new ChunkRenderList(this);
    }

    public static long key(int x, int y, int z) {
        return ChunkSectionPos.asLong(x, y, z);
    }

    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public void delete(CommandList commandList) {
        // Optimized deletion of resources:
        // - Delete section render data in one go
        // - Delete resources if they exist
        for (var storage : this.sectionRenderData.values()) {
            storage.delete();
        }
        this.sectionRenderData.clear();

        if (this.resources != null) {
            this.resources.delete(commandList);
            this.resources = null;
        }

        // Clear the sections array in one go
        Arrays.fill(this.sections, null);
    }

    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return this.sectionRenderData.get(pass);
    }

    public SectionRenderDataStorage createStorage(TerrainRenderPass pass) {
        var storage = this.sectionRenderData.get(pass);

        if (storage == null) {
            this.sectionRenderData.put(pass, storage = new SectionRenderDataStorage());
        }

        return storage;
    }

    // Optimize refresh by deleting tessellations only if they exist
    public void refresh(CommandList commandList) {
        if (this.resources != null) {
            this.resources.deleteTessellations(commandList);
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.onBufferResized();
        }
    }

    // Use a single integer for sectionCount instead of a HashMap for better performance
    public void addSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();

        // Optimized check for existing section using a single index access
        if (this.sections[sectionIndex] != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        // Optimized section addition with a single index access
        this.sections[sectionIndex] = section;
        this.sectionCount++;
    }

    // Optimized section removal with single index access
    public void removeSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();

        // Optimized check for existing section using a single index access
        if (this.sections[sectionIndex] == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (this.sections[sectionIndex] != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        // Optimized removal of meshes from storage
        for (var storage : this.sectionRenderData.values()) {
            storage.removeMeshes(sectionIndex);
        }

        // Optimized section removal with a single index access
        this.sections[sectionIndex] = null;
        this.sectionCount--;
    }

    public RenderSection getSection(int id) {
        return this.sections[id];
    }

    public DeviceResources getResources() {
        return this.resources;
    }

    // Optimized resource creation by creating only when needed
    public DeviceResources createResources(CommandList commandList) {
        if (this.resources == null) {
            this.resources = new DeviceResources(commandList, this.stagingBuffer);
        }

        return this.resources;
    }

    // Optimized update by deleting resources only if they should be deleted
    public void update(CommandList commandList) {
        if (this.resources != null && this.resources.shouldDelete()) {
            this.resources.delete(commandList);
            this.resources = null;
        }
    }

    public ChunkRenderList getRenderList() {
        return this.renderList;
    }

    public static class DeviceResources {
        // Use a dedicated buffer arena for geometry data
        private final GlBufferArena geometryArena;
        // Use a single tessellation object for the region
        private GlTessellation tessellation;

        public DeviceResources(CommandList commandList, StagingBuffer stagingBuffer) {
            int stride = ChunkMeshFormats.COMPACT.getVertexFormat().getStride();
            // Allocate enough memory for all sections in the region
            this.geometryArena = new GlBufferArena(commandList, REGION_SIZE * 756, stride, stagingBuffer);
        }

        // Optimized tessellation update: delete old tessellation if it exists
        public void updateTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
            }

            this.tessellation = tessellation;
        }

        public GlTessellation getTessellation() {
            return this.tessellation;
        }

        // Optimized deletion of tessellations: delete only if it exists
        public void deleteTessellations(CommandList commandList) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
                this.tessellation = null;
            }
        }

        public GlBuffer getVertexBuffer() {
            return this.geometryArena.getBufferObject();
        }

        // Optimized deletion of resources: delete tessellation and buffer arena
        public void delete(CommandList commandList) {
            this.deleteTessellations(commandList);
            this.geometryArena.delete(commandList);
        }

        public GlBufferArena getGeometryArena() {
            return this.geometryArena;
        }

        public boolean shouldDelete() {
            // Check if the buffer arena is empty for optimized deletion
            return this.geometryArena.isEmpty();
        }
    }
}
