#version 150

// The procedural sky of the Ambience module. Mode: 0 space, 1 black hole, 2 aurora, 3 synthwave, 4 dusk, 5 meteor shower. Every pixel turns
// into a direction in the world, so the sky stays where it is when the player turns; the background turns slowly with the time of day.
uniform mat4 InvProjMat;
uniform mat3 CamRot;
uniform float NcGameTime;
uniform float Mode;
uniform float HoleSize;
uniform vec3 HoleDir;
uniform vec3 Color1;
uniform vec3 Color2;
uniform float Brightness;
uniform float Stars;
uniform float Meteors;
uniform float Turn;

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

// Two layers of stars; each cell may hold one, with a tiny glow that twinkles. Stars is the density (0 = none).
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
        float s = step(1.0 - (l == 0 ? 0.06 : 0.035) * Stars, h) * exp(-dist * dist * (l == 0 ? 140.0 : 260.0));
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

// The sky turns around a tilted axis as the day goes on, the way the real one does.
vec3 turnDir(vec3 d) {
    vec3 k = normalize(vec3(0.0, 0.62, 0.78));
    float c = cos(Turn);
    float s = sin(Turn);
    return d * c + cross(k, d) * s + k * dot(k, d) * (1.0 - c);
}

// A shooting star: every few seconds a streak crosses a random part of the upper sky.
float meteor(vec3 d, float t, float seed) {
    float period = 5.0 + seed * 2.7;
    float ph = t / period + seed * 3.1;
    float id = floor(ph);
    float f = fract(ph);
    float life = 0.09;
    if (f > life) {
        return 0.0;
    }
    float lt = f / life;
    vec3 A = normalize(vec3(hash12(vec2(id, seed)) - 0.5, 0.25 + hash12(vec2(id + 7.0, seed)) * 0.6, hash12(vec2(id + 13.0, seed)) - 0.5));
    vec3 R = normalize(vec3(hash12(vec2(id + 3.0, seed)) - 0.5, hash12(vec2(id + 5.0, seed)) - 0.7, hash12(vec2(id + 9.0, seed)) - 0.5));
    vec3 dirv = normalize(cross(A, R));
    vec3 head = normalize(A + dirv * lt * 0.55);
    vec3 tail = normalize(A + dirv * max(lt - 0.22, 0.0) * 0.55);
    vec3 ba = head - tail;
    float h = clamp(dot(d - tail, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    float dist = length(d - tail - ba * h);
    float fade = sin(lt * 3.14159);
    return (exp(-dist * 320.0) * h + exp(-length(d - head) * 110.0) * 0.8) * fade;
}

vec3 meteors(vec3 d, float t, float amount) {
    if (amount < 0.01 || d.y < 0.0) {
        return vec3(0.0);
    }
    float m = 0.0;
    for (int i = 0; i < 3; i++) {
        m += meteor(d, t, 1.7 * float(i + 1));
    }
    return vec3(0.9, 0.95, 1.0) * m * amount * 2.2;
}

// Curtains of light: layered ribbons that sway, with vertical rays inside them.
vec3 aurora(vec3 d, float t) {
    if (d.y < 0.0) {
        return vec3(0.0);
    }
    vec2 u = d.xz / (d.y + 0.3) * 1.15;
    vec3 acc = vec3(0.0);
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float n = fbm2(vec2(u.x * 0.7 + t * 0.02 * (fk + 1.0), fk * 9.0 + u.y * 0.15));
        float curtain = exp(-pow((u.y - 0.9 - fk * 0.6 + (n - 0.5) * 2.4) * 1.9, 2.0));
        float rays = 0.55 + 0.45 * vnoise2(vec2(u.x * 16.0 + fk * 5.0, t * 0.5));
        vec3 c = mix(Color1, Color2, clamp(n * 1.4 + fk * 0.15, 0.0, 1.0));
        acc += c * curtain * rays * (0.95 - fk * 0.22);
    }
    return acc * (1.0 - exp(-d.y * 9.0)) * 1.5;
}

// Synthwave: a gradient from deep colour to a hot horizon and a huge striped sun.
vec3 synth(vec3 d, float t) {
    vec3 top = Color1 * 0.2;
    vec3 col = mix(Color2, top, clamp(pow(clamp(d.y, 0.0, 1.0), 0.45), 0.0, 1.0));
    col = mix(col, top * 0.4, clamp(-d.y * 6.0, 0.0, 1.0));
    vec3 S = normalize(vec3(HoleDir.x, 0.16, HoleDir.z));
    float ang = acos(clamp(dot(d, S), -1.0, 1.0));
    float R = 0.34;
    float rel = (d.y - S.y) / R;
    float gap = step(fract(rel * 6.0), clamp((0.25 - rel) * 0.9, 0.0, 0.8));
    float vis = rel < 0.25 ? 1.0 - gap : 1.0;
    float disc = exp(-pow(ang / R, 16.0)) * vis;
    vec3 sunCol = mix(vec3(1.0, 0.92, 0.35), vec3(1.0, 0.2, 0.6), clamp(0.5 - rel * 0.5, 0.0, 1.0));
    col += sunCol * disc * 1.7 + Color2 * exp(-ang * 2.6) * 0.55;
    col += stars(d, t) * clamp(d.y - 0.2, 0.0, 1.0);
    return col;
}

// Dusk: a warm gradient, a low sun and soft clouds lit from below.
vec3 dusk(vec3 d, float t) {
    float y = clamp(d.y, 0.0, 1.0);
    vec3 col = mix(Color2 * 1.1, Color1 * 0.45, pow(y, 0.5));
    vec3 S = normalize(vec3(HoleDir.x, 0.06, HoleDir.z));
    float ang = acos(clamp(dot(d, S), -1.0, 1.0));
    col += vec3(1.0, 0.55, 0.2) * exp(-ang * 3.2) * 0.9 + vec3(1.0, 0.85, 0.6) * exp(-pow(ang / 0.06, 8.0)) * 2.0;
    if (d.y > 0.0) {
        vec2 u = d.xz / (d.y + 0.25) * 1.4 + vec2(t * 0.01, 0.0);
        float n = fbm2(u * 1.3);
        float c = clamp((n - 0.45) * 3.0, 0.0, 1.0) * (1.0 - exp(-d.y * 8.0));
        vec3 cloud = mix(Color1 * 0.5, vec3(1.0, 0.6, 0.35), exp(-ang * 1.8));
        col = mix(col, cloud, c * 0.75);
    }
    col += stars(d, t) * clamp(d.y - 0.5, 0.0, 1.0) * 0.6;
    return col;
}

void main() {
    vec4 v = InvProjMat * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 d = normalize(CamRot * normalize(v.xyz / v.w));
    float t = NcGameTime;
    int mode = int(Mode + 0.5);
    vec3 col;
    if (mode == 1) {
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
        col = space(turnDir(d2), t) + meteors(d, t, Meteors);

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

        float shadow = exp(-pow(ang / (rs * 0.72), 12.0));
        col = col * (1.0 - shadow) + emit * (1.0 - shadow * 0.95);
        col += hot * exp(-ang / rs * 1.6) * 0.12;
    } else if (mode == 2) {
        col = space(turnDir(d), t) * 0.7 + aurora(d, t) + meteors(d, t, Meteors);
    } else if (mode == 3) {
        col = synth(d, t) + meteors(d, t, Meteors);
    } else if (mode == 4) {
        col = dusk(d, t) + meteors(d, t, Meteors);
    } else if (mode == 5) {
        col = space(turnDir(d), t) + meteors(d, t, max(Meteors, 1.0));
    } else {
        col = space(turnDir(d), t) + meteors(d, t, Meteors);
    }
    float dither = (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(col * Brightness + dither, 1.0);
}
