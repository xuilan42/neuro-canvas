#version 150

// A soft round light: bright in the middle, falling off with exp(); used under the ring and around the crystal.
uniform float NcGameTime;
uniform float Speed;
uniform float Brightness;
uniform vec3 Color2;

in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float t = NcGameTime * 0.05 * Speed;
    float breathe = 0.9 + 0.1 * sin(t * 2.0);
    float a = (exp(-r * r * 5.0) * 0.8 + exp(-r * 2.4) * 0.3) * exp(-pow(r, 6.0) * 5.0) * breathe * vertexColor.a;
    if (a < 0.003) {
        discard;
    }
    vec3 col = mix(vertexColor.rgb, Color2, clamp(r, 0.0, 1.0));
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    fragColor = vec4(min(col * Brightness, vec3(1.0)), clamp(a + dither, 0.0, 1.0));
}
