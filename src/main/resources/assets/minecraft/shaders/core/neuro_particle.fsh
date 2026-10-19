#version 150

// Particle shapes drawn procedurally on a quad whose corners are (-1,-1) .. (1,1).
// Shape: 0 glow (firefly), 1 star, 2 sparkle, 3 dot, 4 ring, 5 diamond, 6 heart, 7 snowflake.
uniform float Shape;
uniform float Brightness;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

float edgeFade(float r) {
    return 1.0 - smoothstep(0.88, 1.0, r);
}

float shapeMask(int shape, vec2 p) {
    float r = length(p);
    float a = atan(p.y, p.x);
    if (shape == 8) {
        // A soft puff of smoke: a round falloff whose edge is eaten away by a few overlapping waves.
        float n = 0.5 + 0.22 * sin(p.x * 4.3 + 1.7) * sin(p.y * 3.9 - 0.6) + 0.18 * sin((p.x + p.y) * 6.1 + 2.4) + 0.1 * sin(p.x * 9.0 - p.y * 7.3);
        float body = exp(-r * r * 3.4) * (0.55 + 0.9 * n);
        return clamp(body, 0.0, 1.0) * (1.0 - smoothstep(0.62, 1.0, r));
    }
    if (shape == 0) {
        float halo = exp(-r * r * 5.0);
        float core = exp(-r * r * 42.0);
        return clamp(halo * 0.65 + core, 0.0, 1.0) * edgeFade(r);
    }
    if (shape == 1) {
        float phi = abs(mod(a - 1.5707963 + 0.6283185, 1.2566371) - 0.6283185);
        vec2 q = r * vec2(cos(phi), sin(phi));
        vec2 tip = vec2(0.96, 0.0);
        vec2 inner = 0.42 * vec2(cos(0.6283185), sin(0.6283185));
        vec2 e = inner - tip;
        vec2 n = normalize(vec2(e.y, -e.x));
        return (1.0 - smoothstep(-0.045, 0.045, dot(q - tip, n))) * (0.85 + 0.15 * (1.0 - smoothstep(0.0, 0.6, r)));
    }
    if (shape == 2) {
        vec2 q = abs(p);
        float flare = max(exp(-q.x * 13.0) * exp(-q.y * 1.7), exp(-q.y * 13.0) * exp(-q.x * 1.7));
        float core = exp(-r * r * 20.0);
        return clamp(flare + core * 0.8, 0.0, 1.0) * edgeFade(r);
    }
    if (shape == 3) {
        return (1.0 - smoothstep(0.8, 0.98, r));
    }
    if (shape == 4) {
        return (1.0 - smoothstep(0.05, 0.17, abs(r - 0.72))) * 1.0;
    }
    if (shape == 5) {
        float d = abs(p.x) * 0.9 + abs(p.y) * 1.1;
        float body = (1.0 - smoothstep(0.82, 0.92, d));
        return body * (0.75 + 0.25 * (1.0 - smoothstep(0.0, 0.7, d)));
    }
    if (shape == 6) {
        vec2 h = vec2(p.x * 1.4, p.y * 1.4 + 0.2);
        float q = h.x * h.x + h.y * h.y - 1.0;
        float f = q * q * q - h.x * h.x * h.y * h.y * h.y;
        // fwidth turns the implicit value into roughly one pixel of edge softness everywhere along the outline.
        float soft = clamp(0.5 - f / max(fwidth(f), 1e-5), 0.0, 1.0);
        return soft * (0.85 + 0.15 * (1.0 - smoothstep(0.0, 0.9, r)));
    }
    float af = abs(mod(a + 0.5235988, 1.0471976) - 0.5235988);
    vec2 l = r * vec2(cos(af), sin(af));
    float arm = (1.0 - smoothstep(0.02, 0.075, l.y)) * step(l.x, 0.95);
    vec2 dir = vec2(0.5, 0.8660254);
    vec2 v1 = l - vec2(0.38, 0.0);
    float b1 = (1.0 - smoothstep(0.015, 0.06, length(v1 - dir * clamp(dot(v1, dir), 0.0, 0.3))));
    vec2 v2 = l - vec2(0.62, 0.0);
    float b2 = (1.0 - smoothstep(0.015, 0.06, length(v2 - dir * clamp(dot(v2, dir), 0.0, 0.2))));
    float hub = (1.0 - smoothstep(0.08, 0.16, r));
    return clamp(arm + b1 + b2 + hub, 0.0, 1.0);
}

void main() {
    float mask = shapeMask(int(Shape + 0.5), uv);
    if (mask <= 0.003) {
        discard;
    }
    vec3 color = vertexColor.rgb * (0.9 + 0.6 * pow(mask, 3.0));
    fragColor = vec4(min(color * Brightness, vec3(1.0)), clamp(mask * vertexColor.a * (0.5 + 0.5 * Brightness), 0.0, 1.0));
}
