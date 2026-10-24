#version 150

// The enchantment shimmer of armour and items. The game's own glint layer draws the geometry (same depth test, no depth writes) and the
// texture matrix scrolls and scales the coordinates; this shader only decides the colour. Pattern: 0 galaxy, 1 rainbow, 2 glitch, 3 flow.
uniform sampler2D Sampler0;
uniform float GameTime;
uniform float Pattern;
uniform float Speed;
uniform float Scale;
uniform float Strength;
uniform vec3 Color1;
uniform vec3 Color2;

in vec2 texCoord0;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + 11.7;
        a *= 0.5;
    }
    return v;
}

vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}

void main() {
    float t = GameTime * 1200.0 * Speed;
    vec2 uv = texCoord0 * Scale;
    vec3 col;
    float a;
    int mode = int(Pattern + 0.5);
    if (mode == 0) {
        // Galaxy: a slow nebula of the two colours with twinkling stars on top.
        float n = fbm(uv * 3.0 + vec2(t * 0.05, -t * 0.03));
        float n2 = fbm(uv * 6.0 - vec2(t * 0.04, t * 0.02) + 4.0);
        col = mix(Color1, Color2, n) * (0.35 + 1.1 * n2);
        vec2 cell = floor(uv * 26.0);
        float star = pow(hash(cell), 36.0) * (0.6 + 0.4 * sin(t * 3.0 + hash(cell + 3.0) * 30.0));
        col += vec3(1.0) * star * 2.5;
        a = clamp(0.35 + n * 0.6 + star, 0.0, 1.0);
    } else if (mode == 1) {
        float hue = uv.x * 0.45 + uv.y * 0.3 + t * 0.12;
        col = hsv(hue, 0.8, 1.0);
        a = 0.85;
    } else if (mode == 2) {
        // Glitch: rows slide sideways at random moments, colours split into red and blue, scanlines run through.
        float row = floor(uv.y * 18.0);
        float tick = floor(t * 7.0);
        float jump = step(0.82, hash(vec2(row, tick))) * (hash(vec2(row + 1.0, tick)) - 0.5) * 0.6;
        float x = uv.x + jump;
        float band = 0.5 + 0.5 * sin(x * 14.0 + t * 2.0);
        float split = step(0.9, hash(vec2(tick, 5.0))) * 0.04;
        float r = 0.5 + 0.5 * sin((x + split) * 14.0 + t * 2.0);
        float b = 0.5 + 0.5 * sin((x - split) * 14.0 + t * 2.0);
        col = mix(Color1, Color2, band) + vec3(r * 0.35, 0.0, b * 0.35) * step(0.9, hash(vec2(tick, 5.0)));
        float scan = 0.65 + 0.35 * sin(uv.y * 90.0 - t * 12.0);
        col *= scan;
        a = 0.45 + 0.55 * step(0.75, hash(vec2(row, tick + 9.0))) + 0.25 * band;
    } else {
        // Flow: the two colours move into each other.
        float k = sin(t * 1.4 + uv.x * 6.0 + sin(uv.y * 5.0 + t * 0.7) * 1.5) * 0.5 + 0.5;
        col = mix(Color1, Color2, k);
        a = 0.55 + 0.4 * k;
    }
    // The game's glint texture adds a little grain of its own.
    float grain = dot(texture(Sampler0, texCoord0).rgb, vec3(0.333));
    col *= 0.7 + 0.6 * grain;
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    fragColor = vec4(min(col * Strength, vec3(4.0)), clamp(a * Strength * 0.6 + dither, 0.0, 1.0));
}
