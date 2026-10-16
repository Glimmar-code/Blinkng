extends Node3D
## Lightweight original procedural streetscape, shared by Android and Windows.

const Layout = preload("res://scripts/neighborhood_layout.gd")
var world: Node3D
var lamp_lights: Array[OmniLight3D] = []
var lamp_materials: Array[StandardMaterial3D] = []


func configure(game_world: Node3D) -> void:
    world = game_world
    name = "NeighborhoodDetails"
    _build_paths()
    _build_frontages()
    _build_street_signs()
    _build_lamps()


func _box(center: Vector3, size: Vector3, color: Color) -> void:
    world.call("_add_visual_box", self, center, size, color)


func _label(caption: String, center: Vector3, font_size: int = 36) -> Label3D:
    var label := Label3D.new()
    label.text = caption
    label.position = center
    label.font_size = font_size
    label.pixel_size = 0.008
    label.outline_size = 8
    label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    add_child(label)
    return label


func _build_paths() -> void:
    for path in Layout.footpaths():
        _box(path["center"], path["size"], Color(0.69, 0.65, 0.57))
    # Flush crossings do not add collision obstacles or interfere with wheelchairs.
    for z in [-4.1, 4.1]:
        for x in range(-2, 3):
            _box(Vector3(x * 0.85, 0.083, z), Vector3(0.4, 0.015, 1.0), Color(0.91, 0.9, 0.8))
    for x in [-7.0, 7.0]:
        for z in range(-2, 3):
            _box(Vector3(x, 0.083, z * 0.85), Vector3(1.0, 0.015, 0.4), Color(0.91, 0.9, 0.8))
    for x in range(-24, 25, 4):
        if abs(x) > 8:
            _box(Vector3(x, 0.04, 0), Vector3(1.5, 0.015, 0.09), Color(0.87, 0.8, 0.46))


func _build_frontages() -> void:
    for place in Layout.places():
        var site: Vector3 = Layout.position_for(int(place["col"]), int(place["row"]))
        var facing: float = 1.0 if site.z < 0.0 else -1.0
        var front_z: float = site.z + facing * 2.12
        var height: float = float(place["height"])
        # Door is a clearly marked interaction frontage, not a fake enterable room.
        _box(Vector3(site.x, 1.1, front_z), Vector3(1.0, 2.2, 0.04), Color(0.18, 0.25, 0.29))
        _box(Vector3(site.x + 0.34, 1.0, front_z + facing * 0.04), Vector3(0.06, 0.14, 0.05), Color(0.8, 0.72, 0.42))
        var awning_color: Color = Color(0.25, 0.43, 0.33) if str(place["district"]) == "Residential Quarter" else Color(0.72, 0.48, 0.25)
        _box(Vector3(site.x, 2.5, front_z + facing * 0.35), Vector3(3.3, 0.12, 0.9), awning_color)
        for x_offset in [-1.4, 1.4]:
            _box(Vector3(site.x + x_offset, 1.65, front_z), Vector3(0.7, 0.7, 0.06), Color(0.39, 0.61, 0.66))
            if height > 4.0:
                _box(Vector3(site.x + x_offset, 3.7, front_z), Vector3(0.7, 0.7, 0.06), Color(0.39, 0.61, 0.66))
        var sign: Label3D = _label("%s\n%s" % [place["name"], place["address"]], Vector3(site.x, height + 1.0, site.z), 32)
        sign.name = "Address_" + str(place["id"])
        sign.set_meta("place_id", place["id"])
    # A modest campus landmark, with no extra lights or physics bodies.
    _box(Vector3(4.8, 1.2, -14), Vector3(0.5, 2.4, 0.5), Color(0.73, 0.7, 0.6))
    _box(Vector3(4.8, 2.6, -14), Vector3(1.2, 0.4, 1.2), Color(0.83, 0.66, 0.31))


func _build_street_signs() -> void:
    var sites: Array[Vector3] = [Vector3(-8.5, 0, 3.9), Vector3(3.9, 0, -8.5)]
    for i in range(Layout.STREETS.size()):
        var site: Vector3 = sites[i]
        _box(site + Vector3(0, 1.1, 0), Vector3(0.1, 2.2, 0.1), Color(0.24, 0.28, 0.3))
        var sign: Label3D = _label(str(Layout.STREETS[i]["name"]), site + Vector3(0, 2.5, 0), 28)
        sign.name = "Street_" + str(Layout.STREETS[i]["id"])


func _build_lamps() -> void:
    for x in [-23.0, -11.0, 11.0, 23.0]:
        for z in [-4.0, 4.0]:
            var site := Vector3(x, 0, z)
            _box(site + Vector3(0, 1.65, 0), Vector3(0.12, 3.3, 0.12), Color(0.25, 0.28, 0.3))
            _box(site + Vector3(0, 3.35, 0), Vector3(0.5, 0.16, 0.5), Color(0.95, 0.85, 0.58))
            var bulb := MeshInstance3D.new()
            var mesh := BoxMesh.new()
            mesh.size = Vector3(0.38, 0.08, 0.38)
            bulb.mesh = mesh
            bulb.position = site + Vector3(0, 3.24, 0)
            var material := StandardMaterial3D.new()
            material.albedo_color = Color(0.95, 0.85, 0.58)
            material.emission_enabled = true
            material.emission = Color(1.0, 0.75, 0.4)
            bulb.material_override = material
            lamp_materials.append(material)
            add_child(bulb)
            var light := OmniLight3D.new()
            light.position = site + Vector3(0, 3.05, 0)
            light.omni_range = 6.0
            light.light_color = Color(1.0, 0.8, 0.55)
            light.shadow_enabled = false
            light.visible = false
            lamp_lights.append(light)
            add_child(light)


func update_lighting(observer: Vector3, daylight: float, battery_saver: bool) -> void:
    var intensity: float = clampf(1.0 - daylight, 0.0, 1.0)
    for material in lamp_materials:
        material.emission_energy_multiplier = intensity * 1.5
    # At most two nearby, shadow-free point lights; saving mode keeps just bulbs.
    var candidates: Array[int] = []
    if not battery_saver and intensity > 0.01:
        for i in range(lamp_lights.size()):
            if observer.distance_squared_to(lamp_lights[i].global_position) < 144.0:
                candidates.append(i)
        candidates.sort_custom(func(a: int, b: int):
            return observer.distance_squared_to(lamp_lights[a].global_position) < observer.distance_squared_to(lamp_lights[b].global_position))
    for i in range(lamp_lights.size()):
        lamp_lights[i].visible = candidates.find(i) >= 0 and candidates.find(i) < 2
        lamp_lights[i].light_energy = intensity * 1.2

