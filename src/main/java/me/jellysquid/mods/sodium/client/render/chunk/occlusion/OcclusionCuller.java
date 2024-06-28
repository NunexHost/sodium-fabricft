package me.jellysquid.mods.sodium.client.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.util.collections.DoubleBufferedQueue;
import me.jellysquid.mods.sodium.client.util.collections.ReadQueue;
import me.jellysquid.mods.sodium.client.util.collections.WriteQueue;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class OcclusionCuller {
    private final Long2ReferenceMap<RenderSection> sections;
    private final World world;

    private final DoubleBufferedQueue<RenderSection> queue = new DoubleBufferedQueue<>();

    // Pre-allocate a small buffer for enqueueing neighbors. This helps avoid allocations during the queue processing loop.
    private final RenderSection[] neighborBuffer = new RenderSection[6];

    public OcclusionCuller(Long2ReferenceMap<RenderSection> sections, World world) {
        this.sections = sections;
        this.world = world;
    }

    public void findVisible(Visitor visitor,
                            Viewport viewport,
                            float searchDistance,
                            boolean useOcclusionCulling,
                            int frame)
    {
        final var queues = this.queue;
        queues.reset();

        this.init(visitor, queues.write(), viewport, searchDistance, useOcclusionCulling, frame);

        while (queues.flip()) {
            processQueue(visitor, viewport, searchDistance, useOcclusionCulling, frame, queues.read(), queues.write());
        }
    }

    private void processQueue(Visitor visitor,
                               Viewport viewport,
                               float searchDistance,
                               boolean useOcclusionCulling,
                               int frame,
                               ReadQueue<RenderSection> readQueue,
                               WriteQueue<RenderSection> writeQueue)
    {
        RenderSection section;

        while ((section = readQueue.dequeue()) != null) {
            boolean visible = isSectionVisible(section, viewport, searchDistance);
            visitor.visit(section, visible);

            if (!visible) {
                continue;
            }

            int connections;

            {
                if (useOcclusionCulling) {
                    connections = VisibilityEncoding.getConnections(section.getVisibilityData(), section.getIncomingDirections());
                } else {
                    connections = GraphDirectionSet.ALL;
                }

                connections &= getOutwardDirections(viewport.getChunkCoord(), section);
            }

            visitNeighbors(writeQueue, section, connections, frame, viewport); // Pass viewport here
        }
    }

    private static boolean isSectionVisible(RenderSection section, Viewport viewport, float maxDistance) {
        return isWithinRenderDistance(viewport.getTransform(), section, maxDistance) && isWithinFrustum(viewport, section);
    }

    // This method is performance-critical, so we optimize it for speed.
    private void visitNeighbors(final WriteQueue<RenderSection> queue, RenderSection section, int outgoing, int frame, Viewport viewport) { // Add viewport as argument
        // Only traverse into neighbors which are actually present.
        // This avoids a null-check on each invocation to enqueue, and since the compiler will see that a null
        // is never encountered (after profiling), it will optimize it away.
        outgoing &= section.getAdjacentMask();

        // Check if there are any valid connections left, and if not, early-exit.
        if (outgoing == GraphDirectionSet.NONE) {
            return;
        }

        // This helps the compiler move the checks for some invariants upwards.
        int i = 0;

        if (GraphDirectionSet.contains(outgoing, GraphDirection.DOWN)) {
            this.neighborBuffer[i++] = section.adjacentDown;
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.UP)) {
            this.neighborBuffer[i++] = section.adjacentUp;
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.NORTH)) {
            this.neighborBuffer[i++] = section.adjacentNorth;
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.SOUTH)) {
            this.neighborBuffer[i++] = section.adjacentSouth;
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.WEST)) {
            this.neighborBuffer[i++] = section.adjacentWest;
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.EAST)) {
            this.neighborBuffer[i++] = section.adjacentEast;
        }

        // Visit all neighbors in the buffer.
        for (int j = 0; j < i; j++) {
            RenderSection neighbor = this.neighborBuffer[j];

            if (neighbor.getLastVisibleFrame() != frame) {
                neighbor.setLastVisibleFrame(frame);
                neighbor.setIncomingDirections(GraphDirectionSet.NONE);

                queue.enqueue(neighbor);
            }

            // Directly update the incoming directions on the neighbor, avoiding unnecessary object allocation.
            neighbor.addIncomingDirections(getOutwardDirections(viewport.getChunkCoord(), section));
        }
    }

    private static int getOutwardDirections(ChunkSectionPos origin, RenderSection section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.getX() ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.getX() ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.getY() ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.getY() ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.getZ() ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.getZ() ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    private static boolean isWithinRenderDistance(CameraTransform camera, RenderSection section, float maxDistance) {
        // origin point of the chunk's bounding box (in view space)
        int ox = section.getOriginX() - camera.intX;
        int oy = section.getOriginY() - camera.intY;
        int oz = section.getOriginZ() - camera.intZ;

        // coordinates of the point to compare (in view space)
        // this is the closest point within the bounding box to the center (0, 0, 0)
        float dx = nearestToZero(ox, ox + 16) - camera.fracX;
        float dy = nearestToZero(oy, oy + 16) - camera.fracY;
        float dz = nearestToZero(oz, oz + 16) - camera.fracZ;

        // vanilla's "cylindrical fog" algorithm
        // max(length(distance.xz), abs(distance.y))
        return (((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance);
    }

    @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
    private static int nearestToZero(int min, int max) {
        // this compiles to slightly better code than Math.min(Math.max(0, min), max)
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }

    // The bounding box of a chunk section must be large enough to contain all possible geometry within it. Block models
    // can extend outside a block volume by +/- 1.0 blocks on all axis. Additionally, we make use of a small epsilon
    // to deal with floating point imprecision during a frustum check (see GH#2132).
    private static final float CHUNK_SECTION_SIZE = 8.0f /* chunk bounds */ + 1.0f /* maximum model extent */ + 0.125f /* epsilon */;

    public static boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
                CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE);
    }

    private void init(Visitor visitor,
                      WriteQueue<RenderSection> queue,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.getY() < this.world.getBottomSectionCoord()) {
            // below the world
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.world.getBottomSectionCoord(), GraphDirection.DOWN);
        } else if (origin.getY() >= this.world.getTopSectionCoord()) {
            // above the world
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.world.getTopSectionCoord() - 1, GraphDirection.UP);
        } else {
            this.initWithinWorld(visitor, queue, viewport, useOcclusionCulling, frame);
        }
    }

    private void initWithinWorld(Visitor visitor, WriteQueue<RenderSection> queue, Viewport viewport, boolean useOcclusionCulling, int frame) {
        var origin = viewport.getChunkCoord();
        var section = this.getRenderSection(origin.getX(), origin.getY(), origin.getZ());

        if (section == null) {
            return;
        }

        section.setLastVisibleFrame(frame);
        section.setIncomingDirections(GraphDirectionSet.NONE);

        visitor.visit(section, true);

        int outgoing;

        if (useOcclusionCulling) {
            outgoing = VisibilityEncoding.getConnections(section.getVisibilityData());
        } else {
            outgoing = GraphDirectionSet.ALL;
        }

        visitNeighbors(queue, section, outgoing, frame, viewport); // Pass viewport here
    }

    // Enqueues sections that are inside the viewport using diamond spiral iteration to avoid sorting and ensure a
    // consistent order. Innermost layers are enqueued first. Within each layer, iteration starts at the northernmost
    // section and proceeds counterclockwise (N->W->S->E).
    private void initOutsideWorldHeight(WriteQueue<RenderSection> queue,
                                        Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        var origin = viewport.getChunkCoord();
        var radius = MathHelper.floor(searchDistance / 16.0f);

        // Layer 0
        this.tryVisitNode(queue, origin.getX(), height, origin.getZ(), direction, frame, viewport);

        // Complete layers, excluding layer 0
        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }

        // Incomplete layers
        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }
    }

    private void tryVisitNode(WriteQueue<RenderSection> queue, int x, int y, int z, int direction, int frame, Viewport viewport) {
        RenderSection section = this.getRenderSection(x, y, z);

        if (section == null || !isWithinFrustum(viewport, section)) {
            return;
        }

        visitNode(queue, section, GraphDirectionSet.of(direction), frame, viewport); // Pass viewport here
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sections.get(ChunkSectionPos.asLong(x, y, z));
    }

    // Method to enqueue a section and update its incoming directions.
    private void visitNode(WriteQueue<RenderSection> queue, RenderSection section, int incoming, int frame, Viewport viewport) { 
        if (section.getLastVisibleFrame() != frame) {
            section.setLastVisibleFrame(frame);
            section.setIncomingDirections(GraphDirectionSet.NONE);
            queue.enqueue(section);
        }

        section.addIncomingDirections(incoming);
    }

    public interface Visitor {
        void visit(RenderSection section, boolean visible);
    }
}
