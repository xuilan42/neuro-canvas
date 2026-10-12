#version 150

// One shader for the mod's world visuals. Mode 0 = glowing line, 1 = box face with a glowing rim,
// 2 = flat ring (signed distance), 3 = soft ribbon. Colours come from the vertices.
uniform float Mode;
uniform float Glow;
uniform float Time;
uniform vec4 Params;   // x: line core width / ring half thickness, y: ring centre radius, z: animation speed
uniform vec3 Color2;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    int mode = int(Mode + 0.5);
    vec3 rgb = vertexColor.rgb;
    float a;
    if (mode == 0) {
        float s = abs(uv.y);
        float core = 1.0 - smoothstep(Params.x * 0.55, Params.x, s);
        float h = max(s - Params.x * 0.55, 0.0) / max(1.0 - Params.x * 0.55, 0.0001);
        float halo = exp(-h * h * 9.0) * (1.0 - smoothstep(0.7, 1.0, s)) * Glow;
        a = clamp(core + halo * 0.55, 0.0, 1.0);
        rgb = rgb * (0.85 + 0.6 * core);
    } else if (mode == 1) {
        float e = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float rim = 1.0 - smoothstep(0.0, 0.18, e);
        float scan = 0.5 + 0.5 * sin(uv.y * 7.0 - Time * 1.5);
        a = 0.85 + 1.2 * rim * Glow + 0.1 * scan;
        rgb = rgb * (1.0 + 0.5 * rim * Glow);
    } else if (mode == 2) {
        float r = length(uv);
        float d = abs(r - Params.y);
        float t = max(Params.x, 0.001);
        float band = 1.0 - smoothstep(0.0, t, d);
        float core = 1.0 - smoothstep(0.0, t * 0.4, d);
        float halo = exp(-d / (t * 1.5)) * Glow;
        a = clamp(band * 0.8 + core * 0.5 + halo * 0.3, 0.0, 1.0) * (1.0 - smoothstep(0.96, 1.0, r));
        float ang = atan(uv.y, uv.x);
        rgb = mix(rgb, Color2, 0.5 + 0.5 * sin(ang * 2.0 + Time * Params.z));
    } else {
        float s = abs(uv.y);
        a = 1.0 - smoothstep(0.35, 1.0, s);
        rgb = rgb * (0.9 + 0.4 * (1.0 - s));
    }
    a *= vertexColor.a;
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(min(rgb, vec3(1.0)), clamp(a, 0.0, 1.0));
}
