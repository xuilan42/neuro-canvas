#version 150

// A procedural night sky. Mode 1 = space with a black hole, 2 = space only. Every pixel turns into a direction in the world, so the sky stays
// where it is when the player turns and the black hole sits at a fixed place in it.
uniform mat4 InvProjMat;
uniform mat3 CamRot;
uniform float NcGameTime;
uniform float Mode;
uniform float HoleSize;
uniform vec3 HoleDir;
uniform vec3 Color1;
uniform vec3 Color2;

in vec2 uv;
out vec4 fragColor;

float hash13(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise3(p);
        p = p * 2.03 + 7.7;
        a *= 0.5;
    }
    return v;
}

float vnoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise2(p);
        p = p * 2.1 + 3.3;
        a *= 0.5;
    }
    return v;
}

// Two layers of stars; each cell may hold one, with a tiny glow that twinkles.
vec3 stars(vec3 d, float t) {
    vec3 col = vec3(0.0);
    for (int l = 0; l < 2; l++) {
        float density = l == 0 ? 70.0 : 130.0;
        vec3 p = d * density;
        vec3 id = floor(p);
        vec3 f = fract(p) - 0.5;
        float h = hash13(id + float(l) * 17.0);
        vec3 off = (vec3(hash13(id + 1.0), hash13(id + 2.0), hash13(id + 3.0)) - 0.5) * 0.6;
        float dist = length(f - off);
        float s = step(l == 0 ? 0.94 : 0.965, h) * exp(-dist * dist * (l == 0 ? 140.0 : 260.0));
        s *= 0.65 + 0.35 * sin(t * 2.5 + h * 60.0);
        vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.65), hash13(id + 9.0));
        col += tint * s * (l == 0 ? 1.6 : 0.9);
    }
    return col;
}

vec3 space(vec3 d, float t) {
    float n = fbm3(d * 2.4 + vec3(t * 0.003, 0.0, 0.0));
    float m = fbm3(d * 4.8 - 3.1);
    vec3 neb = mix(Color1, Color2, m) * pow(clamp(n, 0.0, 1.0), 2.6) * 0.75;
    float band = exp(-pow(dot(d, normalize(vec3(0.3, 0.9, 0.2))) * 3.2, 2.0));
    neb += mix(Color2, Color1, 0.4) * band * fbm3(d * 7.0) * 0.28;
    return vec3(0.004, 0.006, 0.016) + neb + stars(d, t);
}

void main() {
    vec4 v = InvProjMat * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 d = normalize(CamRot * normalize(v.xyz / v.w));
    float t = NcGameTime;
    vec3 col;
    if (Mode < 1.5) {
        vec3 C = normalize(HoleDir);
        vec3 T = normalize(cross(C, vec3(0.0, 1.0, 0.0)));
        vec3 B = cross(C, T);
        float ang = acos(clamp(dot(d, C), -1.0, 1.0));
        vec2 q = vec2(dot(d, T), dot(d, B));
        vec2 dirp = length(q) > 1e-5 ? normalize(q) : vec2(1.0, 0.0);
        float rs = HoleSize;

        // Gravitational lensing: what is seen at the angle ang comes from the angle ang - rs^2 / ang (it flips inside the ring).
        float src = ang - rs * rs / max(ang, 0.03 * rs);
        vec3 d2 = C * cos(src) + (T * dirp.x + B * dirp.y) * sin(src);
        col = space(d2, t);

        // The accretion disk: a flattened, tilted ring of hot gas, brighter on the side that moves toward us.
        float tilt = 0.32;
        vec2 qr = vec2(q.x * cos(tilt) - q.y * sin(tilt), q.x * sin(tilt) + q.y * cos(tilt));
        vec2 qd = vec2(qr.x, qr.y / 0.2);
        float rr = length(qd) / rs;
        float heat = 1.1;
        float swirl = 0.65 + 0.7 * fbm2(vec2(atan(qd.y, qd.x) * 2.5 + t * 0.5, rr * 4.0));
        float disk = exp(-max(rr - 1.0, 0.0) * heat) * (1.0 - exp(-max(rr - 0.85, 0.0) * 14.0)) * exp(-pow(rr / 3.6, 6.0));
        float doppler = 1.0 + 0.75 * qr.x / (length(qr) + 1e-4);
        vec3 hot = mix(vec3(1.0, 0.92, 0.75), vec3(1.0, 0.36, 0.06), clamp((rr - 1.0) / 2.2, 0.0, 1.0));
        vec3 emit = hot * disk * swirl * doppler * 2.2;

        // The far side of the disk bent over and under the hole, and the thin photon ring right at its edge.
        float arc = exp(-abs(length(q) / rs - 1.28) * 4.5) * exp(-pow(abs(qr.x) / (rs * 1.7), 3.0));
        emit += vec3(1.0, 0.5, 0.15) * arc * 1.1;
        float photon = exp(-abs(length(q) / rs - 0.74) * 22.0);
        emit += vec3(1.0, 0.85, 0.6) * photon * 1.4;

        // The shadow of the hole: black, with a hard but not aliased edge.
        float shadow = exp(-pow(ang / (rs * 0.72), 12.0));
        col = col * (1.0 - shadow) + emit * (1.0 - shadow * 0.95);
        // A faint glow around everything.
        col += hot * exp(-ang / rs * 1.6) * 0.12;
    } else {
        col = space(d, t);
    }
    float dither = (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(col + dither, 1.0);
}
