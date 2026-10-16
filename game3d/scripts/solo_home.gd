extends Node3D
## A small offline starter room above the shared city block.
## This is NOT an online property, rentable unit or public multiplayer interior.
## Only simple low-poly meshes and bounded physical walls are built.

const ROOM_WIDTH: float = 8.8
const ROOM_DEPTH: float = 8.8
const HEIGHT: float = 2.8
const SPAWN_LOCAL: Vector3 = Vector3(0.0, 0.23, 1.25)
const WALL_COLORS = [Color(0.80, 0.84, 0.85), Color(0.78, 0.70, 0.61), Color(0.55, 0.69, 0.75)]
const FLOOR_COLORS = [Color(0.71, 0.61, 0.48), Color(0.46, 0.46, 0.48), Color(0.68, 0.59, 0.47)]
const PLANT_SPOTS = [Vector3(3.0, 0.95, -2.5), Vector3(0.0, 0.25, -3.65), Vector3(3.2, 0.25, 2.6)]
const PLANT_DEFAULT_X: int = 0
const PLANT_DEFAULT_Z: int = 3
const GRID_LIMIT: int = 3
const GRID_SPACING: float = 1.0
const HISTORY_LIMIT: int = 20
const FURNITURE_DEFAULTS = {"chair_x": 2, "chair_z": -1, "chair_rotation": 0, "table_x": 2, "table_z": 2, "table_rotation": 0}
var desk_plant: Node3D
var movable_chair: Node3D
var movable_table: Node3D
var floor_visual: MeshInstance3D
var wall_visuals: Array[MeshInstance3D] = []


func configure() -> void:
    name = "SoloStarterRoom"
    position = Vector3(0, 30.0, 0)
    floor_visual = _solid_box("Floor", Vector3(0, -0.15, 0), Vector3(ROOM_WIDTH, 0.3, ROOM_DEPTH), FLOOR_COLORS[0])
    wall_visuals.append(_solid_box("North wall", Vector3(0, HEIGHT / 2.0, -4.4), Vector3(8.8, HEIGHT, 0.25), WALL_COLORS[0]))
    wall_visuals.append(_solid_box("South wall", Vector3(0, HEIGHT / 2.0, 4.4), Vector3(8.8, HEIGHT, 0.25), WALL_COLORS[0]))
    wall_visuals.append(_solid_box("East wall", Vector3(4.4, HEIGHT / 2.0, 0), Vector3(0.25, HEIGHT, 8.8), WALL_COLORS[0]))
    wall_visuals.append(_solid_box("West wall", Vector3(-4.4, HEIGHT / 2.0, 0), Vector3(0.25, HEIGHT, 8.8), WALL_COLORS[0]))
    _box(self, Vector3(-2.5, 0.42, -2.5), Vector3(2.15, 0.45, 2.8), Color(0.29, 0.32, 0.39))
    _box(self, Vector3(-2.5, 0.70, -2.5), Vector3(1.9, 0.18, 2.55), Color(0.84, 0.81, 0.67))
    _box(self, Vector3(-2.5, 0.9, -3.35), Vector3(1.55, 0.25, 0.5), Color(0.47, 0.62, 0.70))
    _box(self, Vector3(2.3, 0.72, -2.5), Vector3(2.2, 0.15, 1.0), Color(0.50, 0.34, 0.25))
    for x in [1.35, 3.25]:
        for z in [-2.8, -2.2]:
            _box(self, Vector3(x, 0.33, z), Vector3(0.12, 0.65, 0.12), Color(0.38, 0.27, 0.22))
    movable_chair = Node3D.new()
    movable_chair.name = "MovableChair"
    add_child(movable_chair)
    _box(movable_chair, Vector3(0, 0.42, 0), Vector3(0.85, 0.15, 0.8), Color(0.53, 0.40, 0.29))
    _box(movable_chair, Vector3(0, 0.78, 0.4), Vector3(0.85, 0.73, 0.13), Color(0.53, 0.40, 0.29))
    movable_table = Node3D.new()
    movable_table.name = "MovableTable"
    add_child(movable_table)
    _box(movable_table, Vector3(0, 0.8, 0), Vector3(1.15, 0.15, 0.95), Color(0.45, 0.32, 0.25))
    for leg_x in [-0.45, 0.45]:
        for leg_z in [-0.34, 0.34]:
            _box(movable_table, Vector3(leg_x, 0.35, leg_z), Vector3(0.12, 0.70, 0.12), Color(0.37, 0.27, 0.21))
    _box(self, Vector3(-3.55, 1.2, 1.8), Vector3(0.65, 2.4, 1.45), Color(0.49, 0.40, 0.34))
    _box(self, Vector3(0.0, 1.8, -4.25), Vector3(2.1, 1.0, 0.05), Color(0.46, 0.68, 0.78))
    _box(self, Vector3(0.0, 1.8, -4.19), Vector3(0.08, 1.1, 0.06), Color(0.92, 0.92, 0.90))
    _caption("YOUR SOLO STARTER ROOM", Vector3(0, 2.5, -3.9))
    _caption("BED", Vector3(-2.5, 1.5, -2.5), 21)
    _caption("STUDY DESK", Vector3(2.3, 1.8, -2.5), 21)
    var light := OmniLight3D.new()
    light.name = "InteriorLight"
    light.position = Vector3(0, 2.5, 0)
    light.light_color = Color(1.0, 0.89, 0.72)
    light.omni_range = 9
    light.light_energy = 1.0
    light.shadow_enabled = false
    add_child(light)
    desk_plant = Node3D.new()
    desk_plant.name = "DeskPlant"
    add_child(desk_plant)
    _box(desk_plant, Vector3(0, 0.16, 0), Vector3(0.34, 0.33, 0.34), Color(0.54, 0.32, 0.22))
    _box(desk_plant, Vector3(0, 0.48, 0), Vector3(0.55, 0.5, 0.55), Color(0.19, 0.48, 0.24))
    desk_plant.position = PLANT_SPOTS[0]
    desk_plant.visible = false
    apply_decoration(0, 0, 0)


func update_owned_decor(has_plant: bool) -> void:
    if desk_plant != null:
        desk_plant.visible = has_plant


## Bounded solo-only furniture layout: chair, table and optional plant.
## Every check uses each item's rotated footprint, plus an entry clearance.
static func _half_size(kind: String, rotation: int) -> Vector2:
    var base: Vector2 = Vector2(0.45, 0.43)
    if kind == "table":
        base = Vector2(0.62, 0.52)
    elif kind == "plant":
        base = Vector2(0.30, 0.30)
    if rotation % 2 == 1:
        return Vector2(base.y, base.x)
    return base


static func _rect_overlaps(a: Vector2, a_half: Vector2, b: Vector2, b_half: Vector2) -> bool:
    return absf(a.x - b.x) < a_half.x + b_half.x + 0.12 and absf(a.y - b.y) < a_half.y + b_half.y + 0.12


static func _fixed_clear(kind: String, position: Vector2, rotation: int = 0) -> bool:
    var half: Vector2 = _half_size(kind, rotation)
    if absf(position.x) + half.x > 4.0 or absf(position.y) + half.y > 4.0:
        return false
    var reserved: Array[Dictionary] = [
        {"center": Vector2(-2.5, -2.5), "half": Vector2(1.075, 1.4)},
        {"center": Vector2(2.3, -2.5), "half": Vector2(1.10, 0.50)},
        {"center": Vector2(-3.55, 1.8), "half": Vector2(0.325, 0.725)},
        {"center": Vector2(0.0, 1.25), "half": Vector2(0.38, 0.38)}
    ]
    for obstacle in reserved:
        if _rect_overlaps(position, half, obstacle["center"], obstacle["half"]):
            return false
    return true


static func floor_cell_allowed(x: int, z: int) -> bool:
    if absi(x) > GRID_LIMIT or absi(z) > GRID_LIMIT:
        return false
    return _fixed_clear("plant", Vector2(float(x), float(z)))


static func layout_allowed(layout: Dictionary) -> bool:
    var footprints: Array[Dictionary] = []
    for kind in ["chair", "table"]:
        var x: int = int(layout.get(kind + "_x", FURNITURE_DEFAULTS[kind + "_x"]))
        var z: int = int(layout.get(kind + "_z", FURNITURE_DEFAULTS[kind + "_z"]))
        var turn: int = int(layout.get(kind + "_rotation", 0))
        if abs(x) > GRID_LIMIT or abs(z) > GRID_LIMIT or turn < 0 or turn > 3:
            return false
        var point := Vector2(float(x), float(z))
        if not _fixed_clear(kind, point, turn):
            return false
        footprints.append({"point": point, "half": _half_size(kind, turn)})
    if bool(layout.get("plant_custom", false)):
        var px: int = int(layout.get("plant_x", 0))
        var pz: int = int(layout.get("plant_z", PLANT_DEFAULT_Z))
        if not floor_cell_allowed(px, pz):
            return false
        footprints.append({"point": Vector2(float(px), float(pz)),
            "half": _half_size("plant", 0)})
    # The owned plant's THREE pre-existing presets include raised desktop
    # positions; they intentionally do not occupy the new floor grid.
    for i in footprints.size():
        for j in range(i + 1, footprints.size()):
            if _rect_overlaps(footprints[i]["point"], footprints[i]["half"],
                    footprints[j]["point"], footprints[j]["half"]):
                return false
    return true


func apply_decoration(wall_style: int, floor_style: int, plant_spot: int,
        plant_custom: bool = false, plant_x: int = PLANT_DEFAULT_X,
        plant_z: int = PLANT_DEFAULT_Z, plant_rotation: int = 0,
        furniture: Dictionary = {}) -> bool:
    if wall_style < 0 or wall_style >= WALL_COLORS.size():
        return false
    if floor_style < 0 or floor_style >= FLOOR_COLORS.size():
        return false
    if plant_spot < 0 or plant_spot >= PLANT_SPOTS.size():
        return false
    if plant_rotation < 0 or plant_rotation >= 4:
        return false
    var layout: Dictionary = FURNITURE_DEFAULTS.duplicate()
    layout.merge(furniture, true)
    layout["plant_custom"] = plant_custom
    layout["plant_x"] = plant_x
    layout["plant_z"] = plant_z
    if not layout_allowed(layout):
        return false
    for wall in wall_visuals:
        _repaint(wall, WALL_COLORS[wall_style])
    _repaint(floor_visual, FLOOR_COLORS[floor_style])
    if desk_plant != null:
        desk_plant.position = Vector3(float(plant_x) * GRID_SPACING, 0.15,
            float(plant_z) * GRID_SPACING) if plant_custom else PLANT_SPOTS[plant_spot]
        desk_plant.rotation.y = deg_to_rad(float(plant_rotation) * 90.0)
    if movable_chair != null:
        movable_chair.position = Vector3(float(layout["chair_x"]), 0, float(layout["chair_z"]))
        movable_chair.rotation.y = deg_to_rad(float(layout["chair_rotation"]) * 90.0)
    if movable_table != null:
        movable_table.position = Vector3(float(layout["table_x"]), 0, float(layout["table_z"]))
        movable_table.rotation.y = deg_to_rad(float(layout["table_rotation"]) * 90.0)
    return true


func _repaint(mesh: MeshInstance3D, color: Color) -> void:
    if mesh == null:
        return
    var material: StandardMaterial3D = mesh.material_override as StandardMaterial3D
    if material != null:
        material.albedo_color = color


func spawn_position() -> Vector3:
    return global_position + SPAWN_LOCAL


func _box(parent: Node3D, center: Vector3, size: Vector3, color: Color) -> MeshInstance3D:
    var mesh := BoxMesh.new()
    mesh.size = size
    var visual := MeshInstance3D.new()
    visual.mesh = mesh
    visual.position = center
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = 0.82
    visual.material_override = material
    parent.add_child(visual)
    return visual


func _solid_box(node_name: String, center: Vector3, size: Vector3, color: Color) -> MeshInstance3D:
    var body := StaticBody3D.new()
    body.name = node_name
    body.position = center
    add_child(body)
    var visual: MeshInstance3D = _box(body, Vector3.ZERO, size, color)
    var collision := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = size
    collision.shape = shape
    body.add_child(collision)
    return visual


func _caption(message: String, center: Vector3, size: int = 28) -> void:
    var label := Label3D.new()
    label.text = message
    label.position = center
    label.font_size = size
    label.pixel_size = 0.009
    label.outline_size = 8
    label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    add_child(label)
