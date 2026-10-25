#version 150

// The procedural sky of the Ambience module. Mode: 0 space, 1 black hole, 2 aurora, 3 synthwave, 4 dusk, 5 meteor shower, 6 Milky Way, 7 eclipse, 8 planet, 9 nebula. Every pixel turns
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

// The colour of a glowing body of the given temperature in kelvin (a well known curve fit), brightest channel near 1.
vec3 blackbody(float kelvin) {
    float t = clamp(kelvin, 1000.0, 40000.0) / 100.0;
    float r = t <= 66.0 ? 1.0 : 1.292936 * pow(max(t - 60.0, 1.0), -0.1332047);
    float g = t <= 66.0 ? 0.390082 * log(t) - 0.631841 : 1.129891 * pow(max(t - 60.0, 1.0), -0.0755148);
    float b = t >= 66.0 ? 1.0 : (t <= 19.0 ? 0.0 : 0.543207 * log(max(t - 10.0, 1.0)) - 1.196254);
    return clamp(vec3(r, g, b), 0.0, 1.0);
}

// A soft layer of airglow and haze that hugs the horizon.
vec3 horizonGlow(vec3 d, vec3 tint) {
    float h = clamp(d.y, 0.0, 1.0);
    return tint * exp(-h * 9.0) * 0.22 + vec3(0.02, 0.03, 0.05) * exp(-h * 3.0);
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
        float tk = hash13(id + 9.0);
        vec3 tint = blackbody(mix(3200.0, 11000.0, tk * tk) + step(0.97, tk) * 12000.0);
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
        float x = u.y - 0.9 - fk * 0.6 + (n - 0.5) * 2.4;
        float curtain = x < 0.0 ? exp(-pow(x * 3.4, 2.0)) : exp(-x * 2.1);
        float rays = 0.55 + 0.45 * vnoise2(vec2(u.x * 16.0 + fk * 5.0, t * 0.5));
        vec3 c = mix(mix(Color1, Color2, clamp(x * 1.6 + 0.6, 0.0, 1.0)), vec3(0.95, 0.2, 0.3), clamp(x * 1.2 - 0.3, 0.0, 1.0) * 0.55);
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


// A real night sky: the galactic band with dark lanes and a bright bulge, thousands of faint stars inside it.
vec3 milkyWay(vec3 d, float t) {
    vec3 gn = normalize(vec3(0.7, 0.5, -0.5));
    float gp = dot(d, gn);
    float band = exp(-gp * gp * 28.0);
    vec3 gc = normalize(cross(gn, vec3(0.0, 1.0, 0.2)));
    float bulge = exp(-pow(acos(clamp(dot(d, gc), -1.0, 1.0)) * 1.6, 2.0));
    float dens = fbm3(d * 5.0 + 3.0) * 0.8 + fbm3(d * 12.0) * 0.35;
    float lane = 1.0 - 0.85 * clamp((fbm3(d * 9.0 + 11.0) - 0.45) * 3.0, 0.0, 1.0) * band;
    vec3 col = mix(vec3(0.6, 0.7, 1.0), vec3(1.0, 0.85, 0.65), clamp(bulge * 1.4, 0.0, 1.0)) * band * (0.25 + dens) * lane * (0.55 + 1.3 * bulge);
    col += vec3(0.9, 0.9, 1.0) * pow(hash13(floor(d * 260.0)), 40.0) * band * 1.2;
    return vec3(0.003, 0.005, 0.012) + col * (0.5 + 0.5 * mix(Color1, Color2, 0.5)) + stars(d, t);
}

// A total eclipse: a black disc, the thin bright edge of the sun and a corona of streamers, with a sunset glow all around the horizon.
vec3 eclipse(vec3 d, float t) {
    vec3 S = normalize(HoleDir);
    float ang = acos(clamp(dot(d, S), -1.0, 1.0));
    float R = HoleSize * 0.5;
    vec3 T = normalize(cross(S, vec3(0.0, 1.0, 0.0)));
    vec3 B = cross(S, T);
    float az = atan(dot(d, B), dot(d, T));
    float streamers = 0.55 + 0.45 * fbm2(vec2(az * 3.0 + t * 0.02, ang * 9.0));
    float corona = exp(-max(ang - R, 0.0) / (R * 1.1)) * streamers;
    float ring = exp(-abs(ang - R * 1.03) * 260.0 / max(R * 4.0, 0.1));
    float disc = exp(-pow(ang / R, 24.0));
    vec3 sky = mix(Color2 * 0.28, vec3(0.008, 0.012, 0.03), pow(clamp(d.y, 0.0, 1.0), 0.4));
    sky += vec3(1.0, 0.45, 0.2) * exp(-abs(d.y) * 9.0) * 0.4;
    vec3 col = sky + (mix(vec3(1.0), Color2, 0.35) * corona * 1.5 + vec3(1.0, 0.95, 0.85) * ring * 2.4) * (1.0 - disc);
    return col * (1.0 - disc) + stars(d, t) * 0.5 * (1.0 - disc);
}

// A ringed planet and a moon hanging in dark space; HoleSize is the planet's size, HoleDir its place.
vec3 planet(vec3 d, float t) {
    vec3 C = normalize(HoleDir);
    vec3 T = normalize(cross(C, vec3(0.0, 1.0, 0.0)));
    vec3 B = cross(C, T);
    vec3 col = space(turnDir(d), t) * 0.6;
    float rp = HoleSize * 1.15;
    vec2 q = vec2(dot(d, T), dot(d, B));
    float tilt = 0.45;
    vec2 qr = vec2(q.x * cos(tilt) - q.y * sin(tilt), q.x * sin(tilt) + q.y * cos(tilt));
    float r = length(q) / rp;
    vec3 L = normalize(vec3(-0.6, 0.45, 0.65));
    if (dot(d, C) > 0.0) {
        if (r < 1.0) {
            vec3 n = vec3(q / rp, sqrt(max(1.0 - r * r, 0.0)));
            float diffuse = clamp(dot(n, L), 0.0, 1.0);
            float bands = 0.5 + 0.5 * sin(n.y * 16.0 + fbm2(n.xy * 4.0) * 3.5);
            vec3 surface = mix(Color1, Color2, bands) * (0.06 + 1.1 * diffuse);
            col = surface + mix(Color2, vec3(1.0), 0.5) * exp(-(1.0 - r) * 14.0) * (0.25 + 0.7 * diffuse);
        } else {
            col += mix(Color2, vec3(1.0), 0.5) * exp(-(r - 1.0) * 22.0) * 0.45;
        }
        float ringR = length(vec2(qr.x, qr.y / 0.24)) / rp;
        float ringBody = exp(-pow((ringR - 1.85) / 0.45, 2.0)) * (0.55 + 0.45 * sin(ringR * 38.0));
        float visible = qr.y < 0.0 ? 1.0 : (r < 1.0 ? 0.0 : 1.0);
        col = mix(col, mix(vec3(0.85, 0.78, 0.65), Color2, 0.3) * (0.45 + 0.6 * clamp(dot(vec3(0.0, 1.0, 0.0), L) + 0.5, 0.0, 1.0)), clamp(ringBody * visible * 0.85, 0.0, 1.0));
    }
    vec3 M = normalize(C + T * HoleSize * 2.3 + B * HoleSize * 0.9);
    float am = acos(clamp(dot(d, M), -1.0, 1.0));
    float rm = HoleSize * 0.32;
    if (am < rm) {
        vec2 mq = vec2(dot(d, T) - dot(M, T), dot(d, B) - dot(M, B)) / rm;
        vec3 n = vec3(mq, sqrt(max(1.0 - dot(mq, mq), 0.0)));
        float crater = 0.75 + 0.25 * fbm2(mq * 5.0);
        col = vec3(0.78, 0.76, 0.72) * crater * (0.04 + 1.2 * clamp(dot(n, L), 0.0, 1.0));
    }
    return col;
}

// Gas clouds twisted by noise, with hot knots; a sky made of nebula.
vec3 nebulaSky(vec3 d, float t) {
    vec3 q = d * 2.2;
    vec3 w = vec3(fbm3(q + vec3(0.0, t * 0.004, 0.0)), fbm3(q + 5.2), fbm3(q + 9.1));
    float n = fbm3(q * 1.8 + 2.5 * w);
    float n2 = fbm3(q * 3.5 - 1.7 * w + 7.0);
    vec3 col = mix(Color1, Color2, clamp(n2 * 1.3, 0.0, 1.0)) * pow(clamp(n * 1.15 - 0.12, 0.0, 1.0), 2.6) * 1.5;
    col += vec3(1.0, 0.75, 0.55) * pow(clamp(n * n2 * 2.0 - 0.5, 0.0, 1.0), 3.0) * 0.8;
    return vec3(0.004, 0.005, 0.014) + col + stars(d, t) * 0.9;
}

void main() {
    vec4 v = InvProjMat * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 d = normalize(CamRot * normalize(v.xyz / v.w));
    float t = NcGameTime;
    int mode = int(Mode + 0.5);
    vec3 col;
    if (mode == 10) {
        // Only the northern lights, added over the sky that is already there (the blend of neuro_skyadd.json does the adding).
        vec3 lights = aurora(d, t) * Brightness;
        float a = clamp(max(lights.r, max(lights.g, lights.b)), 0.0, 1.0);
        fragColor = vec4(lights, a);
        return;
    }
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
        float disk = exp(-max(rr - 1.0, 0.0) * heat) * (1.0 - exp(-max(rr - 0.45, 0.0) * 14.0)) * exp(-pow(rr / 3.6, 6.0));
        // The gas cools as r^(-3/4) from the inner edge, and the side moving toward us is shifted bluer and much brighter.
        float beta = 0.32 * qr.x / (length(qr) + 1e-4);
        float g = 1.0 / (1.0 - beta);
        vec3 hot = blackbody(10500.0 * pow(max(rr, 1.0), -0.75) * g);
        vec3 emit = hot * disk * swirl * pow(g, 3.0) * 1.3;
        emit = 1.0 - exp(-emit * 1.4);

        // The far side of the disk bent over and under the hole, and the thin photon ring right at its edge.
        float arc = exp(-abs(length(q) / rs - 1.28) * 4.5) * exp(-pow(abs(qr.x) / (rs * 1.7), 3.0));
        emit += vec3(1.0, 0.5, 0.15) * arc * 1.1;
        float photon = exp(-abs(length(q) / rs - 0.74) * 22.0);
        emit += vec3(1.0, 0.85, 0.6) * photon * 1.4;

        float shadow = exp(-pow(ang / (rs * 0.72), 12.0));
        // The near half of the disk passes in front of the shadow; the far half is hidden by it.
        float hidden = qr.y < 0.0 ? 0.0 : shadow * 0.95;
        col = col * (1.0 - shadow) + emit * (1.0 - hidden);
        col += hot * exp(-ang / rs * 1.6) * 0.12;
    } else if (mode == 2) {
        col = space(turnDir(d), t) * 0.7 + aurora(d, t) + horizonGlow(d, Color2 * 0.5) + meteors(d, t, Meteors);
    } else if (mode == 3) {
        col = synth(d, t) + meteors(d, t, Meteors);
    } else if (mode == 4) {
        col = dusk(d, t) + meteors(d, t, Meteors);
    } else if (mode == 5) {
        col = space(turnDir(d), t) + meteors(d, t, max(Meteors, 1.0));
    } else if (mode == 6) {
        col = milkyWay(turnDir(d), t) + horizonGlow(d, mix(Color1, Color2, 0.5) * 0.6) + meteors(d, t, Meteors);
    } else if (mode == 7) {
        col = eclipse(d, t) + meteors(d, t, Meteors * 0.3);
    } else if (mode == 8) {
        col = planet(d, t) + meteors(d, t, Meteors);
    } else if (mode == 9) {
        col = nebulaSky(turnDir(d), t) + meteors(d, t, Meteors);
    } else {
        col = space(turnDir(d), t) + horizonGlow(d, mix(Color1, Color2, 0.5) * 0.5) + meteors(d, t, Meteors);
    }
    float dither = (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(col * Brightness + dither, 1.0);
}
