#version 150

// One aurora curtain. uv.x runs along the curtain (in blocks / 30), uv.y from the bottom edge (0) to the top (1).
// Rays are noise stretched upwards; the bottom edge is sharp and bright, the top fades out slowly.
uniform float Time;
uniform float Seed;
uniform float Brightness;
uniform vec3 Color2;
uniform vec4 Params;   // x: ray sharpness, y: how fast it fades upwards, z: shimmer, w: unused

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float u = uv.x;
    float v = clamp(uv.y, 0.0, 1.0);
    float t = Time;

    // Slow sheet that decides where the curtain is bright, and fast thin rays inside it.
    float sheet = noise(vec2(u * 0.9 + Seed * 7.0, t * 0.05 + Seed));
    sheet = smoothstep(0.12, 0.65, sheet * 0.65 + noise(vec2(u * 2.1 - t * 0.08, Seed * 3.0 + t * 0.03)) * 0.45);
    float rays = noise(vec2(u * 7.0 + Seed * 11.0, t * 0.12 + v * 0.6))
               * 0.6 + noise(vec2(u * 17.0 - t * 0.1, t * 0.2 + Seed)) * 0.4;
    rays = pow(smoothstep(0.2, 0.9, rays), Params.x);

    float body = mix(0.45, 1.0, rays) * sheet;

    // Brighter rays reach higher; the bottom edge ramps up sharply, the top dissolves.
    float reach = Params.y / (0.45 + body);
    float prof = smoothstep(0.0, 0.06, v) * exp(-v * reach) * (1.0 - smoothstep(0.7, 1.0, v));

    float shimmer = 1.0 - Params.z * (0.5 + 0.5 * sin(t * 1.3 + u * 4.0 + Seed * 9.0));
    float a = body * prof * shimmer * vertexColor.a * 1.8;

    vec3 col = mix(vertexColor.rgb, Color2, pow(v, 0.65));
    col += vec3(0.2, 0.3, 0.2) * exp(-v * 18.0) * rays;

    if (a < 0.002) {
        discard;
    }
    fragColor = vec4(min(col * Brightness, vec3(1.4)), clamp(a * (0.5 + 0.5 * Brightness), 0.0, 1.0));
}
