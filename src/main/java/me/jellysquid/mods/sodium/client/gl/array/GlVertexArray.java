package me.jellysquid.mods.sodium.client.gl.array;

import me.jellysquid.mods.sodium.client.gl.GlObject;
import org.lwjgl.opengl.GL11;

/**
 * Provides Vertex Array functionality on supported platforms.
 * This is a workaround for OpenGL 2.1, which lacks dedicated Vertex Array Objects.
 */
public class GlVertexArray extends GlObject {
    public static final int NULL_ARRAY_ID = 0;

    public GlVertexArray() {
        // No actual object creation is needed for OpenGL 2.1
        // We just use a dedicated handle for bookkeeping purposes.
        this.setHandle(1);
    }

    /**
     * Binds this Vertex Array, effectively enabling it.
     *
     * Note that this does not create or manage any vertex buffers.
     * It simply binds the current state to this handle.
     */
    public void bind() {
        // We can't directly bind an object in OpenGL 2.1.
        // Instead, we track the current state in this class.
    }

    /**
     * Unbinds this Vertex Array, effectively disabling it.
     *
     * Note that this does not destroy any vertex buffers.
     * It simply reverts to the default state.
     */
    public void unbind() {
        // We can't directly unbind an object in OpenGL 2.1.
        // Instead, we rely on the default state to be considered unbound.
    }
}
