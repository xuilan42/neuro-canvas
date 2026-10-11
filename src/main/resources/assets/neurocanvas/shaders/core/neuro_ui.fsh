#version 150

// Effects drawn over or behind a rounded rectangle of the menu:
//   Mode 1 = CRT scanlines with a slow bright band, 2 = moving neon grid, 3 = flowing aurora gradient.
// Rect is in framebuffer pixels with the origin at the bottom left, like gl_FragCoord.
uniform float Mode;
uniform float Time;
uniform float Strength;
uniform float Scale;
uniform float Radius;
uniform vec4 Rect;
uniform vec3 Accent;
uniform vec3 Accent2;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 size = Rect.zw;
    vec2 local = gl_FragCoord.xy - Rect.xy;
    float d = roundedBox(local - size * 0.5, size * 0.5, Radius);
    float mask = 1.0 - smoothstep(-1.0, 0.0, d);
    if (mask <= 0.0) {
        discard;
    }
    vec2 uvp = local / size;
    vec3 col = vec3(0.0);
    float a = 0.0;

    if (Mode < 1.5) {
        float line = 0.5 + 0.5 * sin(local.y / (1.5 * Scale) * 3.14159265);
        float flicker = 0.93 + 0.07 * sin(Time * 37.0 + local.y * 0.01);
        a = (0.08 + 0.2 * (1.0 - line)) * flicker;
        float edge = smoothstep(0.0, 0.3, min(min(uvp.x, 1.0 - uvp.x), min(uvp.y, 1.0 - uvp.y)));
        a += (1.0 - edge) * 0.28;
        col = vec3(0.0, 0.015, 0.0);
        float y = fract(uvp.y * 0.7 - Time * 0.07);
        float band = smoothstep(0.0, 0.03, y) * (1.0 - smoothstep(0.03, 0.14, y));
        col = mix(col, Accent, band * 0.6);
        a += band * 0.07;
    } else if (Mode < 2.5) {
        vec2 g = abs(fract((local + vec2(0.0, Time * 14.0 * Scale)) / (22.0 * Scale)) - 0.5);
        float lines = smoothstep(0.455, 0.5, max(g.x, g.y));
        col = mix(Accent, Accent2, uvp.x);
        a = lines * 0.45 * (0.35 + 0.65 * (1.0 - uvp.y));
    } else {
        float t = Time * 0.15;
        vec2 p = uvp * vec2(size.x / size.y, 1.0) * 1.6;
        float n = sin(p.x * 3.0 + t * 2.0) + sin(p.y * 4.0 - t * 3.0 + sin(p.x * 2.0 + t)) + sin((p.x + p.y) * 2.5 + t * 1.7);
        n = n / 3.0 * 0.5 + 0.5;
        col = mix(Accent, Accent2, n);
        a = (0.16 + 0.26 * n) * (1.0 - uvp.y * 0.55);
    }
    fragColor = vec4(col, clamp(a * Strength, 0.0, 1.0) * mask);
}
