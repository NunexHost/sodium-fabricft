package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferMapFlags;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.util.EnumBitField;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class SharedQuadIndexBuffer {
    private static final int ELEMENTS_PER_PRIMITIVE = 6;
    private static final int VERTICES_PER_PRIMITIVE = 4;

    private final GlMutableBuffer buffer;
    private final IndexType indexType;

    // Using a fixed size buffer to avoid unnecessary allocations and memory management overhead.
    private final int maxPrimitives;

    public SharedQuadIndexBuffer(CommandList commandList, IndexType indexType) {
        this.indexType = indexType;
        this.maxPrimitives = indexType.getMaxPrimitiveCount(); // Set the maximum primitive count based on the chosen index type

    public void ensureCapacity(CommandList commandList, int elementCount) {
        if (elementCount > this.indexType.getMaxElementCount()) {
            throw new IllegalArgumentException("Tried to reserve storage for more vertices in this buffer than it can hold");
        }

        int primitiveCount = elementCount / ELEMENTS_PER_PRIMITIVE;
            
        // Create the buffer with the maximum capacity
        this.buffer = commandList.createMutableBuffer();
        allocateStorage(commandList);
        createIndexBuffer(commandList);
    }

    private void allocateStorage(CommandList commandList) {
        var bufferSize = this.maxPrimitives * this.indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;
        commandList.allocateStorage(this.buffer, bufferSize, GlBufferUsage.STATIC_DRAW);
    }

    // Pre-generate the index buffer at initialization for better performance.
    private void createIndexBuffer(CommandList commandList) {
        var mapped = commandList.mapBuffer(this.buffer, 0, this.buffer.getSize(),
                EnumBitField.of(GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.UNSYNCHRONIZED));
        this.indexType.createIndexBuffer(mapped.getMemoryBuffer(), this.maxPrimitives);
        commandList.unmap(mapped);
    }

    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.buffer);
    }

    public GlIndexType getIndexFormat() {
        return this.indexType.getFormat();
    }

    public IndexType getIndexType() {
        return this.indexType;
    }

    // No need for `ensureCapacity` anymore as the buffer has a fixed size.
    // Use `maxPrimitives` to check if the desired primitive count is within the buffer's capacity.

    public enum IndexType {
        SHORT(GlIndexType.UNSIGNED_SHORT, 64 * 1024) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                ShortBuffer shortBuffer = byteBuffer.asShortBuffer();

                // Optimized for loop for index buffer generation
                for (int i = 0; i < primitiveCount * ELEMENTS_PER_PRIMITIVE; i += ELEMENTS_PER_PRIMITIVE) {
                    shortBuffer.put(i + 0, (short) (i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 0));
                    shortBuffer.put(i + 1, (short) (i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 1));
                    shortBuffer.put(i + 2, (short) (i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 2));
                    shortBuffer.put(i + 3, (short) (i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 2));
                    shortBuffer.put(i + 4, (short) (i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 3));
                    shortBuffer.put(i + 5, (short) (i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 0));
                }
            }
        },
        INTEGER(GlIndexType.UNSIGNED_INT, Integer.MAX_VALUE) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                IntBuffer intBuffer = byteBuffer.asIntBuffer();

                // Optimized for loop for index buffer generation
                for (int i = 0; i < primitiveCount * ELEMENTS_PER_PRIMITIVE; i += ELEMENTS_PER_PRIMITIVE) {
                    intBuffer.put(i + 0, i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 0);
                    intBuffer.put(i + 1, i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 1);
                    intBuffer.put(i + 2, i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 2);
                    intBuffer.put(i + 3, i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 2);
                    intBuffer.put(i + 4, i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 3);
                    intBuffer.put(i + 5, i / ELEMENTS_PER_PRIMITIVE * VERTICES_PER_PRIMITIVE + 0);
                }
            }
        };

        public static final IndexType[] VALUES = IndexType.values();

        private final GlIndexType format;
        private final int maxElementCount;

        IndexType(GlIndexType format, int maxElementCount) {
            this.format = format;
            this.maxElementCount = maxElementCount;
        }

        public abstract void createIndexBuffer(ByteBuffer buffer, int primitiveCount);

        public int getBytesPerElement() {
            return this.format.getStride();
        }

        public GlIndexType getFormat() {
            return this.format;
        }

        public int getMaxPrimitiveCount() {
            return this.maxElementCount / 4;
        }

        public int getMaxElementCount() {
            return this.maxElementCount;
        }
    }
}
