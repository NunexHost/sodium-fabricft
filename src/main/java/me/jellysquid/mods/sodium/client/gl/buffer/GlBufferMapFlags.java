package me.jellysquid.mods.sodium.client.gl.buffer;

import me.jellysquid.mods.sodium.client.gl.util.EnumBit;
import org.lwjgl.opengl.GL11;

/**
 * This enum cannot be ported to OpenGL 2.1 because it relies on features introduced in later versions.
 *
 * OpenGL 2.1 does not support the concept of mapping buffers for direct access.
 *
 * Therefore, the functionality of this enum is not available in OpenGL 2.1.
 *
 * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glMapBuffer.xhtml">glMapBuffer</a>
 */
public enum GlBufferMapFlags implements EnumBit {
    // READ(GL30C.GL_MAP_READ_BIT),
    // WRITE(GL30C.GL_MAP_WRITE_BIT),
    // PERSISTENT(GL44C.GL_MAP_PERSISTENT_BIT),
    // INVALIDATE_BUFFER(GL30C.GL_MAP_INVALIDATE_BUFFER_BIT),
    // INVALIDATE_RANGE(GL30C.GL_MAP_INVALIDATE_RANGE_BIT),
    // EXPLICIT_FLUSH(GL30C.GL_MAP_FLUSH_EXPLICIT_BIT),
    // COHERENT(GL44C.GL_MAP_COHERENT_BIT),
    // UNSYNCHRONIZED(GL33C.GL_MAP_UNSYNCHRONIZED_BIT);

    // No support for buffer mapping in OpenGL 2.1

    private final int bit;

    GlBufferMapFlags(int bit) {
        this.bit = bit;
    }

    @Override
    public int getBits() {
        return this.bit;
    }
}
