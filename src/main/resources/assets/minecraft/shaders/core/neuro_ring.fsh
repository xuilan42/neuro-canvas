#version 150

uniform float NcGameTime;
uniform float Speed;
uniform float Brightness;
uniform vec3 Color2;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float t = NcGameTime * 0.05 * Speed;
    float ring = abs(length(p) - 0.62);
    float c = cos(t * 2.0);
    float s = sin(t * 2.0);
    vec2 rotUv = mat2(c, -s, s, c) * p;
    float gaps = sin(atan(rotUv.y, rotUv.x) * 4.0) * 0.5 + 0.5;
    float bloom = exp(-ring * 20.0) * gaps;
    float haze = exp(-ring * 9.0) * 0.14;
    float core = clamp(0.5 - (ring - 0.018) / max(fwidth(ring), 1e-4), 0.0, 1.0) * gaps;
    vec3 col = mix(vertexColor.rgb, Color2, 0.5 + 0.5 * sin(atan(p.y, p.x) * 2.0 + t * 2.0));
    col = mix(col, vec3(1.0), core * 0.5);
    float a = (core + bloom * 0.85 + haze) * vertexColor.a;
    if (a < 0.003) {
        discard;
    }
    fragColor = vec4(min(col * Brightness, vec3(1.0)), clamp(a, 0.0, 1.0));
}
