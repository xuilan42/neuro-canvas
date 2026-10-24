#version 150

// A target drawn as a hologram: only a glowing rim (Fresnel) and horizontal scan bands, on the model's own silhouette.
uniform sampler2D Sampler0;
uniform float NcGameTime;
uniform float Brightness;
uniform float FeetY;
uniform float Alpha;
uniform vec3 Color1;
uniform vec3 Color2;

in vec3 viewPos;
in vec3 normal;
in vec3 worldRel;
in vec2 uv;
out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, uv);
    if (tex.a < 0.1) {
        discard;
    }
    float t = NcGameTime * 0.05;
    vec3 n = normalize(normal);
    vec3 viewDir = normalize(-viewPos);
    float fresnel = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    float scan = sin((worldRel.y - FeetY) * 10.0 - t * 5.0) * 0.5 + 0.5;
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float rim = fresnel;
    vec3 mono = vec3(lum) * Color1 * 1.6 + Color1 * 0.25;
    vec3 rgb = mix(tex.rgb, mono, 0.75);
    rgb = mix(rgb, Color2, clamp(rim, 0.0, 1.0));
    float a = (rim + 0.07) * (0.3 + 0.7 * scan) * Alpha;
    fragColor = vec4(min(rgb * Brightness, vec3(1.0)), clamp(a, 0.0, 1.0));
}
