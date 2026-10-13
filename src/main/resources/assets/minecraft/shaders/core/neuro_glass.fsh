#version 150

// Frosted glass panel for the menu. Sampler0 is a blurred copy of the screen. Opacity is the one setting that matters:
//   0 = clear glass (the blurred scene shows through almost untouched), 1 = nearly solid tinted glass.
// On top of that: a soft vertical sheen, a faint grain, an inner glow and a thin accent rim that is brightest at the
// top left corner and along the bottom centre. Rect is in framebuffer pixels with the origin at the bottom left.
uniform sampler2D Sampler0;
uniform float Time;
uniform float Opacity;
uniform float Scale;
uniform float Radius;
uniform vec4 Rect;
uniform vec2 FbSize;
uniform vec3 Tint;
uniform vec3 Accent;
uniform vec3 Accent2;

in vec2 uv;
out vec4 fragColor;

float roundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 size = Rect.zw;
    vec2 local = gl_FragCoord.xy - Rect.xy;
    float d = roundedBox(local - size * 0.5, size * 0.5, Radius);
    float mask = 1.0 - smoothstep(-1.0, 0.0, d);
    if (mask <= 0.0) {
        discard;
    }
    vec2 p = local / size;                     // 0..1, origin bottom left
    float inside = -d;                         // pixels from the edge, positive inside

    vec3 scene = texture(Sampler0, gl_FragCoord.xy / FbSize).rgb;
    float lum = dot(scene, vec3(0.299, 0.587, 0.114));
    vec3 frosted = mix(scene, vec3(lum), 0.35) * 0.9 + 0.025;
    float o = clamp(Opacity, 0.0, 1.0);
    // Darker, tinted glass towards the bottom, lighter towards the top, like light falling from above.
    vec3 tintTop = mix(Tint, Accent2, 0.10);
    vec3 tintBottom = Tint * 0.8;
    vec3 tint = mix(tintBottom, tintTop, p.y);
    vec3 col = mix(frosted, tint, o * 0.92);
    col = mix(col, tint, o * o * 0.4);

    // Sheen: a diagonal soft band plus a lift near the top edge.
    float bd = (p.x * 0.8 + (1.0 - p.y) * 0.6 - 0.55) * 3.2;
    float band = exp(-bd * bd);
    col += (0.035 + 0.05 * (1.0 - o)) * band;
    col += Accent * 0.05 * smoothstep(0.55, 1.0, p.y) * (0.4 + 0.6 * o);

    // Inner glow hugging the edge.
    float glowW = 16.0 * Scale;
    float inner = exp(-inside / glowW);
    vec3 rimCol = mix(Accent, Accent2, clamp(p.x * 0.8 + (1.0 - p.y) * 0.35, 0.0, 1.0));
    float bc = (p.x - 0.5) * 3.4;
    float bottomCentre = exp(-bc * bc) * exp(-p.y * 14.0);
    float topLeft = exp(-length(vec2(p.x * 1.4, (1.0 - p.y) * 2.2)) * 3.0);
    float weight = 0.28 + 0.9 * bottomCentre + 0.7 * topLeft;
    col += rimCol * inner * 0.16 * weight;

    // The rim line itself.
    float line = 1.0 - smoothstep(0.0, 1.6 * Scale, inside);
    float lineA = (0.22 + 0.78 * clamp(weight, 0.0, 1.0)) * (0.55 + 0.45 * sin(Time * 0.6 + p.x * 3.0));
    col = mix(col, rimCol + 0.25, line * lineA * 0.85);

    // Grain keeps big flat areas from banding.
    col += (hash(gl_FragCoord.xy + fract(Time)) - 0.5) * 0.018;

    fragColor = vec4(clamp(col, 0.0, 1.0), mask);
}
