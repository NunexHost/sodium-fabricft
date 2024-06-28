package me.jellysquid.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;

import java.util.*;

public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists = new ObjectArrayList<>();
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);

    private final int frame;

    public VisibleChunkCollector(int frame) {
        this.frame = frame;

        // Initialize the rebuild lists to avoid redundant allocations
        for (var type : ChunkUpdateType.values()) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    @Override
    public void visit(RenderSection section, boolean visible) {
        RenderRegion region = section.getRegion();
        ChunkRenderList renderList = region.getRenderList();

        // Optimization 1: Early exit if the section is not visible and has no pending updates
        if (!visible && section.getPendingUpdate() == null) {
            return;
        }

        // Optimization 2: Use a single check for both render list initialization and adding the section
        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);
            this.sortedRenderLists.add(renderList);
        }

        // Optimization 3: Only add the section if it is visible and has render objects
        if (visible && section.getFlags() != 0) {
            renderList.add(section);
        }

        // Optimization 4: Avoid redundant allocations for the rebuild lists
        addToRebuildLists(section);
    }

    private void addToRebuildLists(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        if (type != null && section.getBuildCancellationToken() == null) {
            Queue<RenderSection> queue = this.sortedRebuildLists.get(type);

            // Optimization 5: Use a conditional add instead of checking the queue size first
            if (queue.size() < type.getMaximumQueueSize()) {
                queue.add(section);
            }
        }
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }

    public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
        return this.sortedRebuildLists;
    }
}
