package me.jellysquid.mods.sodium.client.render.immediate.model;

import org.joml.Vector3f;

public class AABB {
    private final Vector3f min;
    private final Vector3f max;

    public AABB() {
        this.min = new Vector3f();
        this.max = new Vector3f();
    }

    public AABB(Vector3f min, Vector3f max) {
        this.min = min;
        this.max = max;
    }

    public void set(Vector3f min, Vector3f max) {
        this.min.set(min);
        this.max.set(max);
    }

    /**
     * Checks if this AABB intersects another AABB.
     *
     * @param other The other AABB to check against.
     * @return True if the AABBs intersect, false otherwise.
     */
    public boolean intersects(AABB other) {
        return !(other.min.x > this.max.x ||
                other.max.x < this.min.x ||
                other.min.y > this.max.y ||
                other.max.y < this.min.y ||
                other.min.z > this.max.z ||
                other.max.z < this.min.z);
    }

    // Getters
    public Vector3f getMin() {
        return min;
    }

    public Vector3f getMax() {
        return max;
    }
}
