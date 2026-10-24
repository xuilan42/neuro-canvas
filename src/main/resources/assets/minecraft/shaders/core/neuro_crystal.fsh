#version 150

uniform float NcGameTime;
uniform float Speed;
uniform float Brightness;
uniform vec3 Color2;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

// Colour goes toward white where the light is intense (a neon tube is white in the middle and coloured at its edge); a little dither hides banding.
vec3 neon(vec3 col, float a) {
    return mix(col, vec3(1.0), clamp(a * a * 0.7, 0.0, 1.0));
}

float dither() {
    return (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
}


// The outer glow of a crystal: every face is a triangle with barycentric uv, the glow follows its edges.
void main() {
    float e = min(min(uv.x, uv.y), 1.0 - uv.x - uv.y);
    float bloom = exp(-e * 16.0);
    float inner = exp(-e * 4.0) * 0.18;
    vec3 col = mix(vertexColor.rgb, Color2, clamp(uv.y * 1.5, 0.0, 1.0));
    float a = (bloom + inner) * vertexColor.a;
    if (a < 0.003) {
        discard;
    }
    fragColor = vec4(min(neon(col, a) * Brightness, vec3(1.0)), clamp(a + dither(), 0.0, 1.0));
}
