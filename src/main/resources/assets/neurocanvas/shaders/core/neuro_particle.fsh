#version 150

// Particle shapes drawn procedurally on a quad whose corners are (-1,-1) .. (1,1).
// Shape: 0 glow (firefly), 1 star, 2 sparkle, 3 dot, 4 ring, 5 diamond, 6 heart, 7 snowflake.
uniform float Shape;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

float edgeFade(float r) {
    return 1.0 - smoothstep(0.88, 1.0, r);
}

float shapeMask(int shape, vec2 p) {
    float r = length(p);
    float a = atan(p.y, p.x);
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
        return smoothstep(0.045, -0.045, dot(q - tip, n)) * (0.85 + 0.15 * smoothstep(0.6, 0.0, r));
    }
    if (shape == 2) {
        vec2 q = abs(p);
        float flare = max(exp(-q.x * 13.0) * exp(-q.y * 1.7), exp(-q.y * 13.0) * exp(-q.x * 1.7));
        float core = exp(-r * r * 20.0);
        return clamp(flare + core * 0.8, 0.0, 1.0) * edgeFade(r);
    }
    if (shape == 3) {
        return smoothstep(0.98, 0.8, r);
    }
    if (shape == 4) {
        return smoothstep(0.17, 0.05, abs(r - 0.72)) * 1.0;
    }
    if (shape == 5) {
        float d = abs(p.x) * 0.9 + abs(p.y) * 1.1;
        float body = smoothstep(0.92, 0.82, d);
        return body * (0.75 + 0.25 * smoothstep(0.7, 0.0, d));
    }
    if (shape == 6) {
        vec2 h = vec2(p.x * 1.4, p.y * 1.4 + 0.2);
        float q = h.x * h.x + h.y * h.y - 1.0;
        float f = q * q * q - h.x * h.x * h.y * h.y * h.y;
        // fwidth turns the implicit value into roughly one pixel of edge softness everywhere along the outline.
        float soft = clamp(0.5 - f / max(fwidth(f), 1e-5), 0.0, 1.0);
        return soft * (0.85 + 0.15 * smoothstep(0.9, 0.0, r));
    }
    float af = abs(mod(a + 0.5235988, 1.0471976) - 0.5235988);
    vec2 l = r * vec2(cos(af), sin(af));
    float arm = smoothstep(0.075, 0.02, l.y) * step(l.x, 0.95);
    vec2 dir = vec2(0.5, 0.8660254);
    vec2 v1 = l - vec2(0.38, 0.0);
    float b1 = smoothstep(0.06, 0.015, length(v1 - dir * clamp(dot(v1, dir), 0.0, 0.3)));
    vec2 v2 = l - vec2(0.62, 0.0);
    float b2 = smoothstep(0.06, 0.015, length(v2 - dir * clamp(dot(v2, dir), 0.0, 0.2)));
    float hub = smoothstep(0.16, 0.08, r);
    return clamp(arm + b1 + b2 + hub, 0.0, 1.0);
}

void main() {
    float mask = shapeMask(int(Shape + 0.5), uv);
    if (mask <= 0.003) {
        discard;
    }
    vec3 color = vertexColor.rgb * (0.9 + 0.6 * pow(mask, 3.0));
    fragColor = vec4(min(color, vec3(1.0)), mask * vertexColor.a);
}
