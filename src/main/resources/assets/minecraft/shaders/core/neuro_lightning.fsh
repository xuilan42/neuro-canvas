#version 150

uniform float NcGameTime;
uniform float Speed;
uniform float Brightness;
uniform vec3 Color2;

in vec2 uv;
in vec4 vertexColor;
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
        p = p * 2.03 + 7.1;
        a *= 0.5;
    }
    return v * 1.07;
}

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float t = NcGameTime * 0.05 * Speed;
    float bloom = 0.0;
    float hot = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float amp = 0.2 / (1.0 + fi * 0.4);
        float offset = (fbm(vec2(p.y * 3.0 + fi * 17.3, t * 15.0 + fi * 3.7)) - 0.5) * 2.0 * amp;
        float spread = (i == 0 ? 0.0 : (i == 1 ? 0.3 : -0.34)) * p.y;
        float lineDist = abs(p.x - offset - spread);
        float w = i == 0 ? 1.0 : 0.55;
        bloom += (exp(-lineDist * 50.0) + exp(-lineDist * 12.0) * 0.18) * w;
        hot += exp(-lineDist * 160.0) * w;
    }
    float flicker = 0.3 + 0.7 * vnoise(vec2(t * 20.0, 3.1));
    float fade = exp(-pow(abs(p.y), 4.0) * 2.5);
    bloom *= flicker * fade;
    hot *= flicker * fade;
    vec3 col = mix(vertexColor.rgb, Color2, 0.5 + 0.5 * p.y);
    col = mix(col, vec3(1.0), clamp(hot, 0.0, 1.0));
    float a = bloom * vertexColor.a;
    if (a < 0.003) {
        discard;
    }
    fragColor = vec4(min(col * Brightness, vec3(1.0)), clamp(a, 0.0, 1.0));
}
