#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;

out vec3 viewPos;
out vec3 normal;
out vec3 worldRel;
out vec2 uv;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    normal = mat3(ModelViewMat) * Normal;
    worldRel = IViewRotMat * Position;
    uv = UV0;
}
