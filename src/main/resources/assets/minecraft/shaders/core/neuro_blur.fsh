#version 150

// One direction of a separable gaussian blur (5 taps using the linear-sampling trick, covers 9 texels).
uniform sampler2D Sampler0;
uniform vec2 Direction;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, uv).rgb * 0.2270270270;
    c += (texture(Sampler0, uv + Direction * 1.3846153846).rgb + texture(Sampler0, uv - Direction * 1.3846153846).rgb) * 0.3162162162;
    c += (texture(Sampler0, uv + Direction * 3.2307692308).rgb + texture(Sampler0, uv - Direction * 3.2307692308).rgb) * 0.0702702703;
    fragColor = vec4(c, 1.0);
}
