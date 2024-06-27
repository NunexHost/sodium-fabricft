// The position of the vertex around the model origin
vec3 _vert_position;

// The block texture coordinate of the vertex
vec2 _vert_tex_diffuse_coord;

// The light texture coordinate of the vertex
ivec2 _vert_tex_light_coord;

// The color of the vertex
vec4 _vert_color;

// The index of the draw command which this vertex belongs to
uint _draw_id;

// The material bits for the primitive
uint _material_params;

#ifdef USE_VERTEX_COMPRESSION
// Input vertex attributes
in uvec4 a_PosId;
in vec4 a_Color;
in vec2 a_TexCoord;
in ivec2 a_LightCoord;

#if !defined(VERT_POS_SCALE) || !defined(VERT_POS_OFFSET) || !defined(VERT_TEX_SCALE)
#error "Vertex compression scales not defined"
#endif

// Constants for bit manipulation
const uint DRAW_ID_MASK = 0xFF00u;
const uint MATERIAL_PARAMS_MASK = 0xFFu;

// Function to initialize vertex attributes
void _vert_init() {
    // Calculate vertex position
    _vert_position = vec3(a_PosId.xyz) * VERT_POS_SCALE + VERT_POS_OFFSET;

    // Calculate texture coordinates
    _vert_tex_diffuse_coord = a_TexCoord * VERT_TEX_SCALE;
    _vert_tex_light_coord = a_LightCoord;

    // Extract draw ID and material parameters
    uint posIdW = a_PosId.w;
    _draw_id = (posIdW & DRAW_ID_MASK) >> 8u;
    _material_params = posIdW & MATERIAL_PARAMS_MASK;

    // Assign vertex color
    _vert_color = a_Color;
}

#else
#error "Vertex compression must be enabled"
#endif
