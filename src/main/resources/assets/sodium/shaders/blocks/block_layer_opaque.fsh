#version 330 core

#import <sodium:include/fog.glsl>

in vec4 v_Color;
in vec2 v_TexCoord;
in float v_FragDistance;

in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

uniform sampler2D u_BlockTex;

uniform vec4 u_FogColor;
uniform float u_FogStart;
uniform float u_FogEnd;

out vec4 fragColor;

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    // Combine per-vertex color and ambient occlusion "shade"
    vec3 finalColor = diffuseColor.rgb * v_Color.rgb * v_Color.a;

    fragColor = _linearFog(vec4(finalColor, diffuseColor.a), v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}
