extends Node3D
## Capped procedural rain/cloud visual preview. No weather API, GPS, or gameplay penalties.
## MultiMesh batches raindrops without creating 80 independent scene nodes.

const LIGHT_DROPS: int = 24
const HEAVY_DROPS: int = 80
var rain: MultiMeshInstance3D
var clouds: Array[Node3D] = []
var clock: float = 0.0
var update_accumulator: float = 0.0
var last_count: int = -1


static func budget_for(condition_name: String) -> int:
    match condition_name:
        "light":
            return LIGHT_DROPS
        "heavy":
            return HEAVY_DROPS
        _:
            return 0


func _ready() -> void:
    name = "WeatherVisuals"
    var drops := BoxMesh.new()
    drops.size = Vector3(0.035, 0.46, 0.035)
    var raindrop_material := StandardMaterial3D.new()
    raindrop_material.albedo_color = Color(0.71, 0.85, 0.96, 0.72)
    raindrop_material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    raindrop_material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    drops.material = raindrop_material
    var pool := MultiMesh.new()
    pool.transform_format = MultiMesh.TRANSFORM_3D
    pool.mesh = drops
    pool.instance_count = HEAVY_DROPS
    pool.visible_instance_count = 0
    rain = MultiMeshInstance3D.new()
    rain.name = "Rain"
    rain.multimesh = pool
    rain.visible = false
    add_child(rain)

    var cloud_material := StandardMaterial3D.new()
    cloud_material.albedo_color = Color(0.69, 0.73, 0.77)
    cloud_material.roughness = 1.0
    for i in range(2):
        var group := Node3D.new()
        group.name = "Cloud_%d" % i
        add_child(group)
        clouds.append(group)
        for part in range(3):
            var block := BoxMesh.new()
            block.size = Vector3(3.5 - float(part) * 0.4, 0.55, 2.2)
            var piece := MeshInstance3D.new()
            piece.mesh = block
            piece.material_override = cloud_material
            piece.position = Vector3(float(part - 1) * 1.7, sin(float(part)) * 0.25, 0.0)
            group.add_child(piece)


func update_weather(center: Vector3, condition_name: String, delta: float) -> void:
    if rain == null or not center.is_finite():
        return
    var step: float = clampf(delta, 0.0, 0.25) if is_finite(delta) else 0.0
    clock = fposmod(clock + step, 120.0)
    position = Vector3(center.x, 0.0, center.z)
    for i in range(clouds.size()):
        clouds[i].position = Vector3(
            -16.0 + fposmod(clock * 0.5 + float(i) * 13.0, 32.0),
            12.0 + float(i) * 1.5,
            -7.0 + float(i) * 14.0
        )

    var count: int = budget_for(condition_name)
    rain.visible = count > 0
    rain.multimesh.visible_instance_count = count
    update_accumulator += step
    if count == 0:
        last_count = 0
        return
    if count == last_count and update_accumulator < 0.05:
        return
    update_accumulator = 0.0
    last_count = count
    var fall_speed: float = 12.0 if condition_name == "heavy" else 8.0
    for i in range(count):
        var x: float = -8.0 + float((i * 37) % 79) * 16.0 / 79.0
        var z: float = -8.0 + float((i * 19) % 83) * 16.0 / 83.0
        var y: float = 1.5 + fposmod(float(i) * 0.37 - clock * fall_speed, 8.0)
        rain.multimesh.set_instance_transform(
            i, Transform3D(Basis.IDENTITY, Vector3(x, y, z))
        )
