#version 150

// A creature's own model drawn with a changed surface. Mode 0 ice (frost climbs from the feet), 1 dissolve (the body wears away from the feet
// up, with a glowing edge), 2 tint (the colours drift toward Color1). Progress runs 0..1; Height is the height of the creature.
uniform sampler2D Sampler0;
uniform float NcGameTime;
uniform float Brightness;
uniform float FeetY;
uniform float Height;
uniform float Mode;
uniform float Progress;
uniform float Alpha;
uniform vec3 Color1;
uniform vec3 Color2;

in vec3 viewPos;
in vec3 normal;
in vec3 worldRel;
in vec3 worldNormal;
in vec2 uv;
out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    vec4 tex = texture(Sampler0, uv);
    if (tex.a < 0.1) {
        discard;
    }
    float h = clamp((worldRel.y - FeetY) / max(Height, 0.1), 0.0, 1.2);
    float shade = 0.62 + 0.38 * clamp(dot(normalize(worldNormal), normalize(vec3(0.3, 0.9, 0.35))) * 0.5 + 0.5, 0.0, 1.0);
    vec3 rgb = tex.rgb * shade;
    float a = Alpha;
    int mode = int(Mode + 0.5);
    float n = vnoise(worldRel * 16.0);
    if (mode == 0) {
        float cover = clamp((Progress * 1.5 - h) * 4.0 + (n - 0.5) * 0.8, 0.0, 1.0);
        vec3 view = normalize(-viewPos);
        float rim = pow(1.0 - max(dot(normalize(normal), view), 0.0), 2.5);
        float sparkle = pow(vnoise(worldRel * 60.0 + NcGameTime * 0.4), 14.0) * 3.0;
        vec3 ice = mix(Color1, Color2, 0.35 + 0.35 * n) * (0.55 + 0.6 * shade) + vec3(rim * 0.5) + vec3(sparkle);
        rgb = mix(rgb, ice, cover);
    } else if (mode == 1) {
        float cut = Progress * 1.5 - 0.1;
        float edge = (h + n * 0.4) - cut;
        if (edge < 0.0) {
            discard;
        }
        float glow = exp(-edge * 14.0);
        rgb = mix(rgb, mix(Color1, Color2, clamp(edge * 8.0, 0.0, 1.0)) * 2.2, clamp(glow * 1.2, 0.0, 1.0));
    } else {
        vec3 view = normalize(-viewPos);
        float rim = pow(1.0 - max(dot(normalize(normal), view), 0.0), 2.0);
        rgb = mix(rgb, Color1 * (0.7 + 0.8 * rim), clamp(Progress * 0.85, 0.0, 1.0)) + Color2 * rim * Progress * 0.8;
    }
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    fragColor = vec4(min(rgb * Brightness, vec3(1.0)), clamp(a + dither, 0.0, 1.0));
}
