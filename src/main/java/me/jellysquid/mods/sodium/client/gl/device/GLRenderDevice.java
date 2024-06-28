package me.jellysquid.mods.sodium.client.gl.device;

import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.buffer.*;
import me.jellysquid.mods.sodium.client.gl.functions.DeviceFunctions;
import me.jellysquid.mods.sodium.client.gl.state.GlStateTracker;
import me.jellysquid.mods.sodium.client.gl.sync.GlFence;
import me.jellysquid.mods.sodium.client.gl.tessellation.*;
import me.jellysquid.mods.sodium.client.gl.util.EnumBitField;
import net.minecraft.client.render.BufferRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public class GLRenderDevice implements RenderDevice {
    private final GlStateTracker stateTracker = new GlStateTracker();
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();

    private final DeviceFunctions functions = new DeviceFunctions(this);

    private boolean isActive;
    private GlTessellation activeTessellation;

    @Override
    public CommandList createCommandList() {
        GLRenderDevice.this.checkDeviceActive();

        return this.commandList;
    }

    @Override
    public void makeActive() {
        if (this.isActive) {
            return;
        }

        BufferRenderer.reset();

        this.stateTracker.clear();
        this.isActive = true;
    }

    @Override
    public void makeInactive() {
        if (!this.isActive) {
            return;
        }

        this.stateTracker.clear();
        this.isActive = false;
    }

    @Override
    public GLCapabilities getCapabilities() {
        // No GL capabilities class in OpenGL 2.
        // You may need to implement a custom class to check for certain features.
        return null;
    }

    @Override
    public DeviceFunctions getDeviceFunctions() {
        return this.functions;
    }

    private void checkDeviceActive() {
        if (!this.isActive) {
            throw new IllegalStateException("Tried to access device from unmanaged context");
        }
    }

    private class ImmediateCommandList implements CommandList {
        private final GlStateTracker stateTracker;

        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
        }

        @Override
        public void bindVertexArray(GlVertexArray array) {
            // Vertex Arrays are not supported in OpenGL 2.
            // You'll have to manage vertex attributes manually.
        }

        @Override
        public void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            GL15.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), byteBuffer, usage.getId());
            glBuffer.setSize(byteBuffer.remaining());
        }

        @Override
        public void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            // Copy buffer subdata is not supported in OpenGL 2.
            // You may need to use alternative methods to achieve this.
        }

        @Override
        public void bindBuffer(GlBufferTarget target, GlBuffer buffer) {
            if (this.stateTracker.makeBufferActive(target, buffer)) {
                GL15.glBindBuffer(target.getTargetParameter(), buffer.handle());
            }
        }

        @Override
        public void unbindVertexArray() {
            // Vertex Arrays are not supported in OpenGL 2.
            // You'll have to manage vertex attributes manually.
        }

        @Override
        public void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

            GL15.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), bufferSize, usage.getId());
            buffer.setSize(bufferSize);
        }

        @Override
        public void deleteBuffer(GlBuffer buffer) {
            if (buffer.getActiveMapping() != null) {
                this.unmap(buffer.getActiveMapping());
            }

            this.stateTracker.notifyBufferDeleted(buffer);

            int handle = buffer.handle();
            buffer.invalidateHandle();

            GL15.glDeleteBuffers(handle);
        }

        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {
            // Vertex Arrays are not supported in OpenGL 2.
            // You'll have to manage vertex attributes manually.
        }

        @Override
        public void flush() {
            // NO-OP
        }

        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            GLRenderDevice.this.activeTessellation = tessellation;
            GLRenderDevice.this.activeTessellation.bind(GLRenderDevice.this.commandList);

            return GLRenderDevice.this.drawCommandList;
        }

        @Override
        public void deleteTessellation(GlTessellation tessellation) {
            tessellation.delete(this);
        }

        @Override
        public GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            // Buffer mapping is not supported in OpenGL 2.
            // You may need to use alternative methods to access buffer data.
            return null;
        }

        @Override
        public void unmap(GlBufferMapping map) {
            // Buffer mapping is not supported in OpenGL 2.
            // You may need to use alternative methods to access buffer data.
        }

        @Override
        public void flushMappedRange(GlBufferMapping map, int offset, int length) {
            // Buffer mapping is not supported in OpenGL 2.
            // You may need to use alternative methods to access buffer data.
        }

        @Override
        public GlFence createFence() {
            // Fences are not supported in OpenGL 2.
            // You may need to use alternative synchronization mechanisms.
            return null;
        }

        private void checkMapDisposed(GlBufferMapping map) {
            if (map.isDisposed()) {
                throw new IllegalStateException("Buffer mapping is already disposed");
            }
        }

        @Override
        public GlMutableBuffer createMutableBuffer() {
            return new GlMutableBuffer();
        }

        @Override
        public GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags) {
            GlImmutableBuffer buffer = new GlImmutableBuffer(flags);

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
            // Buffer storage is not supported in OpenGL 2.
            // You can use glBufferData instead.
            GL15.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), bufferSize, flags.getBitField());

            return buffer;
        }

        @Override
        public GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
            // You'll have to manage the tessellation process manually in OpenGL 2.
            // Consider creating a custom tessellation implementation.
            return null;
        }
    }

    private class ImmediateDrawCommandList implements DrawCommandList {
        public ImmediateDrawCommandList() {

        }

        @Override
        public void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType) {
            GlPrimitiveType primitiveType = GLRenderDevice.this.activeTessellation.getPrimitiveType();

            // MultiDrawElementsBaseVertex is not supported in OpenGL 2.
            // You may need to use a loop for drawing multiple elements.
            for (int i = 0; i < batch.size(); ++i) {
                GL11.glDrawElements(primitiveType.getId(), batch.pElementCount[i], indexType.getFormatId(),
                        batch.pElementPointer + i * indexType.getSizeInBytes());
                GL11.glDrawArrays(primitiveType.getId(), batch.pBaseVertex[i], batch.pElementCount[i]);
            }
        }

        @Override
        public void endTessellating() {
            GLRenderDevice.this.activeTessellation.unbind(GLRenderDevice.this.commandList);
            GLRenderDevice.this.activeTessellation = null;
        }

        @Override
        public void flush() {
            if (GLRenderDevice.this.activeTessellation != null) {
                this.endTessellating();
            }
        }
    }
}
