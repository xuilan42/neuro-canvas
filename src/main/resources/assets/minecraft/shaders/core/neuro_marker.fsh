#version 150

uniform float NcGameTime;
uniform float Speed;
uniform float Brightness;
uniform vec3 Color2;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

uniform vec2 Box;   // halfSize size of the frame inside the quad, in quad units (-1..1)

float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

void main() {
    float t = NcGameTime * 0.05 * Speed;
    vec2 p = (uv * 2.0 - 1.0) * (1.0 + sin(t * 5.0) * 0.05);
    vec2 halfSize = Box;
    float frame = abs(sdBox(p, halfSize)) - 0.02;
    float crossD = min(abs(p.x), abs(p.y));
    float cutoff = min(halfSize.x, halfSize.y) * 0.5;
    float corner = max(frame, cutoff - crossD);
    float d = max(corner, 0.0);
    float bloom = exp(-d * 15.0);
    float core = clamp(0.5 - corner / max(fwidth(corner), 1e-4), 0.0, 1.0);
    vec3 col = mix(vertexColor.rgb, Color2, clamp(0.5 + 0.5 * dot(normalize(p + 1e-4), vec2(0.7, 0.7)), 0.0, 1.0));
    col = mix(col, vec3(1.0), core * 0.45);
    float a = (core + bloom * 0.7) * vertexColor.a;
    if (a < 0.003) {
        discard;
    }
    fragColor = vec4(min(col * Brightness, vec3(1.0)), clamp(a, 0.0, 1.0));
}
