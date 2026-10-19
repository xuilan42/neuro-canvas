#version 150

// Full-screen menu backgrounds drawn over the theme gradient. Mode picks the style (1..11); the result is colour plus alpha.
uniform float Mode;
uniform float Time;
uniform float Strength;
uniform float Scale;
uniform float Radius;
uniform vec4 Rect;
uniform vec3 Accent;
uniform vec3 Accent2;

out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
vec2 hash2(vec2 p) { return vec2(hash(p), hash(p + 17.3)); }

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
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

void main() {
    vec2 size = Rect.zw;
    vec2 uv = (gl_FragCoord.xy - Rect.xy) / size;
    vec2 p = (uv - 0.5) * vec2(size.x / size.y, 1.0);
    float t = Time;
    vec3 col = vec3(0.0);
    float a = 0.0;
    int m = int(Mode + 0.5);

    if (m == 1) { // mesh blobs
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            vec2 c = vec2(sin(t * 0.20 + fi * 2.0), cos(t * 0.17 + fi * 1.3)) * vec2(0.55, 0.38);
            float w = exp(-dot(p - c, p - c) * 3.2);
            col += mix(Accent, Accent2, fi / 3.0) * w;
            a += w * 0.55;
        }
        col /= max(a / 0.55, 0.001);
    } else if (m == 2) { // aurora curtains
        float n = fbm(vec2(p.x * 2.0 + t * 0.05, t * 0.1));
        float band = smoothstep(0.32, 0.0, abs(p.y - (n - 0.5) * 0.9 + 0.12));
        float n2 = fbm(vec2(p.x * 3.0 - t * 0.07, t * 0.13 + 5.0));
        band += 0.7 * smoothstep(0.26, 0.0, abs(p.y - (n2 - 0.5) * 0.8 - 0.15));
        col = mix(Accent, Accent2, uv.x + 0.2 * sin(t * 0.2));
        a = band * 0.55 * (0.4 + 0.6 * uv.y);
    } else if (m == 3) { // domain-warped smoke
        vec2 q = vec2(fbm(p * 1.6 + t * 0.04), fbm(p * 1.6 + 1.7 - t * 0.03));
        float f = fbm(p * 1.6 + 2.0 * q);
        col = mix(Accent, Accent2, q.x);
        a = f * f * 1.1;
    } else if (m == 4) { // parallax starfield
        for (int l = 0; l < 3; l++) {
            float fl = float(l);
            float cell = (34.0 + fl * 26.0) * Scale;
            vec2 g = (gl_FragCoord.xy + vec2(t * (6.0 + fl * 8.0) * Scale, 0.0)) / cell;
            vec2 id = floor(g);
            vec2 f = fract(g) - 0.5;
            vec2 h = hash2(id + fl * 31.0);
            float d = length(f - (h - 0.5) * 0.6);
            float r = 0.05 + 0.05 * h.x;
            float s = smoothstep(r, 0.0, d) * (0.6 + 0.4 * sin(t * 2.5 + h.y * 30.0));
            s *= step(0.35, h.y);
            col += mix(Accent, vec3(1.0), 0.5 + 0.5 * h.x) * s;
            a += s * 0.9;
        }
        col /= max(a, 0.001);
    } else if (m == 5) { // synthwave grid
        float horizon = 0.12;
        float y = horizon - p.y;
        if (y > 0.0) {
            float z = 0.35 / y;
            vec2 gv = vec2(p.x * z, z + t * 0.6);
            vec2 gl = abs(fract(gv) - 0.5);
            float line = smoothstep(0.46, 0.5, max(gl.x, gl.y));
            a = line * exp(-z * 0.18) * 0.7;
            col = mix(Accent, Accent2, clamp(z * 0.1, 0.0, 1.0));
        } else {
            float glow = exp(-(p.y - horizon) * 3.0);
            float sun = smoothstep(0.26, 0.24, length(p - vec2(0.0, horizon + 0.2)));
            col = mix(Accent2, Accent, clamp((p.y - horizon) * 3.0, 0.0, 1.0));
            a = glow * 0.35 + sun * 0.4;
        }
    } else if (m == 6) { // bokeh
        for (int l = 0; l < 2; l++) {
            float fl = float(l);
            float cell = (150.0 + fl * 90.0) * Scale;
            vec2 g = gl_FragCoord.xy / cell;
            g.y -= t * (0.04 + 0.03 * fl);
            vec2 id = floor(g);
            vec2 f = fract(g);
            vec2 h = hash2(id + fl * 9.0);
            float d = length(f - (0.2 + 0.6 * h));
            float r = 0.18 + 0.18 * h.x;
            float disc = smoothstep(r, r * 0.85, d) * 0.22 + smoothstep(0.02, 0.0, abs(d - r)) * 0.25;
            disc *= step(0.3, h.y);
            col += mix(Accent, Accent2, h.x) * disc;
            a += disc;
        }
        col /= max(a, 0.001);
    } else if (m == 7) { // metaballs
        float s = 0.0;
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            vec2 c = vec2(sin(t * (0.21 + 0.05 * fi) + fi * 2.1), cos(t * (0.17 + 0.04 * fi) + fi * 1.7)) * vec2(0.5, 0.33);
            s += (0.012 + 0.004 * fi) / dot(p - c, p - c);
        }
        float body = smoothstep(1.0, 1.2, s);
        col = mix(Accent, Accent2, clamp(s * 0.25, 0.0, 1.0));
        a = body * 0.4 + smoothstep(0.5, 1.0, s) * 0.12;
    } else if (m == 8) { // voronoi cells
        vec2 g = p * 5.0;
        vec2 id = floor(g);
        vec2 f = fract(g);
        float d1 = 8.0;
        float d2 = 8.0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 o = vec2(float(i), float(j));
                vec2 h = hash2(id + o);
                vec2 pt = o + 0.5 + 0.45 * sin(t * 0.5 + 6.2831 * h);
                float d = length(pt - f);
                if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
            }
        }
        float edge = smoothstep(0.07, 0.0, d2 - d1);
        col = mix(Accent, Accent2, uv.x);
        a = edge * 0.5 + (1.0 - d1) * 0.08;
    } else if (m == 9) { // dot grid with a pulse
        vec2 f = fract(gl_FragCoord.xy / (22.0 * Scale)) - 0.5;
        float dot_ = smoothstep(0.16, 0.1, length(f));
        float w = 0.5 + 0.5 * sin(length(p) * 6.0 - t * 1.4);
        col = mix(Accent, Accent2, w);
        a = dot_ * (0.12 + 0.5 * w * w);
    } else if (m == 10) { // diagonal stripes
        float s = fract((uv.x * size.x / size.y + uv.y) * 5.0 - t * 0.08);
        float band = smoothstep(0.0, 0.5, s) * smoothstep(1.0, 0.5, s);
        col = mix(Accent, Accent2, uv.y);
        a = band * 0.2;
    } else { // waves
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float y = 0.22 + 0.08 * fi + 0.045 * sin(p.x * (3.0 + fi) + t * (0.4 + 0.2 * fi) + fi);
            float mk = smoothstep(0.012, 0.0, uv.y - y);
            col += mix(Accent, Accent2, fi / 3.0) * mk;
            a += mk * 0.14;
        }
        col /= max(a / 0.14, 1.0);
    }
    fragColor = vec4(col, clamp(a * Strength, 0.0, 1.0));
}
