#version 150

// Depth-based volumetric fog. The distance and world position of every pixel are rebuilt from the depth buffer,
// then the fog is the optical depth of a medium whose density falls off exponentially with height:
//   amount = c * exp(-b * y0) * (1 - exp(-b * dy * t)) / (b * dy)        fog = 1 - exp(-amount)
// (the integral of c * exp(-b * y) along the view ray).

uniform sampler2D Sampler0;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform vec3 CamPos;
uniform vec3 FogColor;
uniform vec3 SunDir;
uniform vec3 SunColor;
// x density, y height falloff, z fog height, w start distance
uniform vec4 Params0;
// x max opacity, y sun glow, z sky blend, w 1 = plain distance fog
uniform vec4 Params1;

in vec2 uv;
out vec4 fragColor;

void main() {
    float depth = texture(Sampler0, uv).r;
    bool sky = depth >= 0.9999;

    vec4 ndc = vec4(uv * 2.0 - 1.0, sky ? 1.0 : depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProj * ndc;
    view.xyz /= view.w;
    vec3 rd = normalize((InvView * vec4(view.xyz, 0.0)).xyz);
    float dist = sky ? 512.0 : length(view.xyz);

    float c = Params0.x;
    float b = Params0.y;
    float start = Params0.w;
    float t = max(dist - start, 0.0);

    float amount;
    if (Params1.w > 0.5) {
        amount = c * t;
    } else {
        float y0 = CamPos.y + rd.y * start - Params0.z;
        float k = b * rd.y;
        float ramp = abs(k) < 1e-4 ? t : (1.0 - exp(clamp(-t * k, -20.0, 20.0))) / k;
        amount = c * exp(clamp(-y0 * b, -20.0, 20.0)) * ramp;
    }
    float fog = 1.0 - exp(-max(amount, 0.0));

    float sunDot = max(dot(rd, SunDir), 0.0);
    float glow = pow(sunDot, 8.0) * Params1.y * clamp(SunDir.y * 4.0 + 0.4, 0.0, 1.0);
    vec3 color = mix(FogColor, SunColor, clamp(glow, 0.0, 1.0));

    float alpha = fog * Params1.x * (sky ? Params1.z : 1.0);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
