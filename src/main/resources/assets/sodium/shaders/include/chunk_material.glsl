const uint MATERIAL_USE_MIP_OFFSET = 0u;
const uint MATERIAL_ALPHA_CUTOFF_OFFSET = 1u;

float _material_mip_bias(uint material) {
    // Se MATERIAL_USE_MIP_OFFSET é 1, então shift = 0, caso contrário, shift = -4
    uint shift = ((material >> MATERIAL_USE_MIP_OFFSET) & 1u) * 4u;
    return -float(shift);
}

float _material_alpha_cutoff(uint material) {
    // Inline dos valores ALPHA_CUTOFF
    const float alphaCutoff[4] = float[4](0.0, 0.1, 0.5, 1.0);
    return alphaCutoff[(material >> MATERIAL_ALPHA_CUTOFF_OFFSET) & 3u];
}
