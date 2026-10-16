extends RefCounted
## BLINK LIFE: original FUTA-inspired *fictional* play district.
## This is not a survey, navigation map, or an exact reproduction of FUTA.
## No external map tiles, photos, coordinates, GPS, or copyrighted assets.
## Geometry is intentionally lightweight, deterministic and shared by Android/Windows.

const MAP_ID := "futa_inspired_v1"
const MAP_TITLE := "FUTA-Inspired Campus & Community"
const CAMPUS_MIN_X := -170.0
const CAMPUS_MAX_X := -2.0
const CAMPUS_MIN_Z := -145.0
const CAMPUS_MAX_Z := 1.0

# All roads are navigable surface overlays, never blocking collision walls.
const HORIZONTAL_ROADS := [-25.0, -65.0, -105.0]
const VERTICAL_ROADS := [-24.0, -65.0, -105.0, -142.0]

# Coordinates are FICTIONAL game units, not real latitude/longitude.
# Every landmark has a distinct, walkable doorway on the adjacent open ground.
const LANDMARKS := [
    {"name": "FUTA-inspired Main Gate", "x": -45.0, "z": -13.0, "height": 3.0, "color": "#e3e4e0", "category": "gate", "info": "Enter the fictional campus neighbourhood."},
    {"name": "Campus Library", "x": -85.0, "z": -49.0, "height": 5.0, "color": "#7c9dbf", "category": "academic", "info": "Browse study opportunities and student activities."},
    {"name": "Senate & Administration", "x": -85.0, "z": -85.0, "height": 5.5, "color": "#c9a482", "category": "academic", "info": "The university administration landmark."},
    {"name": "Engineering Workshops", "x": -125.0, "z": -85.0, "height": 4.5, "color": "#7a91a7", "category": "academic", "info": "Visit the engineering district."},
    {"name": "Science Complex", "x": -45.0, "z": -85.0, "height": 4.8, "color": "#8eaccc", "category": "academic", "info": "Explore the science quarter."},
    {"name": "Computing Centre", "x": -45.0, "z": -49.0, "height": 5.2, "color": "#6e9abc", "category": "academic", "info": "Find tech and coding opportunities."},
    {"name": "Student Centre", "x": -85.0, "z": -39.0, "height": 3.5, "color": "#e2ac84", "category": "social", "info": "Meet friends in the campus centre."},
    {"name": "Postgraduate School", "x": -85.0, "z": -123.0, "height": 4.0, "color": "#9eb2b5", "category": "academic", "info": "Advanced studies district."},
    {"name": "North Hostels", "x": -126.0, "z": -123.0, "height": 5.0, "color": "#be786f", "category": "residential", "info": "Student accommodation area."},
    {"name": "West Hostels", "x": -125.0, "z": -49.0, "height": 5.0, "color": "#cc8875", "category": "residential", "info": "Walk through the hostel quarter."},
    {"name": "Sports Stadium", "x": -85.0, "z": -14.0, "height": 2.8, "color": "#79ad72", "category": "sport", "info": "Sports and community activities."},
    {"name": "Health Centre", "x": -45.0, "z": -123.0, "height": 3.3, "color": "#e6e0d4", "category": "service", "info": "Campus health services landmark."},
    {"name": "Agriculture Plots", "x": -13.0, "z": -123.0, "height": 2.5, "color": "#a1b78a", "category": "academic", "info": "Gardens and practical agriculture."},
    {"name": "Research Village", "x": -13.0, "z": -85.0, "height": 3.8, "color": "#9bc4b9", "category": "academic", "info": "Research and innovation district."},
    {"name": "FUTA Campus Café", "x": -13.0, "z": -49.0, "height": 3.0, "color": "#d3a36e", "category": "job", "info": "Take an optional café shift to earn fictional GC."},
    {"name": "Off-campus Market", "x": -154.0, "z": -49.0, "height": 3.0, "color": "#c5b07a", "category": "commercial", "info": "A row of local shops and stalls."},
    {"name": "Campus Bus Stop", "x": -13.0, "z": -14.0, "height": 2.2, "color": "#afc5c6", "category": "transport", "info": "Transit information for the game world."},
    {"name": "Community Housing", "x": -154.0, "z": -85.0, "height": 3.8, "color": "#c9907c", "category": "residential", "info": "Houses and shared courtyards."},
    {"name": "Campus Delivery Office", "x": -154.0, "z": -123.0, "height": 3.0, "color": "#899eb2", "category": "job", "info": "Take an optional safe courier shift."}
]

var _materials: Dictionary = {}


func _material(tint: Color) -> StandardMaterial3D:
    var key := tint.to_html()
    if _materials.has(key):
        return _materials[key]
    var material := StandardMaterial3D.new()
    material.albedo_color = tint
    material.roughness = 0.95
    _materials[key] = material
    return material


func _visual(parent: Node3D, center: Vector3, size: Vector3, tint: Color) -> void:
    var mesh := MeshInstance3D.new()
    var cube := BoxMesh.new()
    cube.size = size
    mesh.mesh = cube
    mesh.material_override = _material(tint)
    mesh.position = center
    parent.add_child(mesh)


func _solid(parent: Node3D, label: String, center: Vector3, size: Vector3, tint: Color) -> void:
    var body := StaticBody3D.new()
    body.name = label
    body.position = center
    parent.add_child(body)
    _visual(body, Vector3.ZERO, size, tint)
    var shape := BoxShape3D.new()
    shape.size = size
    var collision := CollisionShape3D.new()
    collision.shape = shape
    body.add_child(collision)


func _ground_and_roads(parent: Node3D) -> void:
    var ground_width := CAMPUS_MAX_X - CAMPUS_MIN_X
    var ground_length := CAMPUS_MAX_Z - CAMPUS_MIN_Z
    var center_x := (CAMPUS_MIN_X + CAMPUS_MAX_X) / 2.0
    var center_z := (CAMPUS_MIN_Z + CAMPUS_MAX_Z) / 2.0
    _solid(parent, "FutaGameGround", Vector3(center_x, -0.31, center_z),
        Vector3(ground_width, 0.62, ground_length), Color("#568351"))
    var road_color := Color("#4c565e")
    for z in HORIZONTAL_ROADS:
        _visual(parent, Vector3(center_x, 0.015, z), Vector3(ground_width - 4, 0.03, 4.5), road_color)
    for x in VERTICAL_ROADS:
        _visual(parent, Vector3(x, 0.018, center_z), Vector3(4.5, 0.035, ground_length - 6), road_color)
    # Visible footpath linking the existing starter neighbourhood to campus.
    _visual(parent, Vector3(-14.0, 0.035, -10.0), Vector3(48.0, 0.035, 1.5), Color("#b7ae98"))
    # Simple pedestrian markings near campus entrance.
    for i in range(4):
        _visual(parent, Vector3(-38.0 + i * 1.4, 0.052, -24.0),
            Vector3(0.8, 0.025, 2.7), Color("#f2eddd"))


func _campus_buildings(parent: Node3D) -> Array[Dictionary]:
    var destinations: Array[Dictionary] = []
    for site in LANDMARKS:
        var name: String = site["name"]
        var x: float = site["x"]
        var z: float = site["z"]
        var height: float = site["height"]
        var tint := Color.from_string(str(site["color"]), Color.GRAY)
        var footprint := Vector3(7.0, height, 7.0)
        if str(site["category"]) == "gate":
            footprint = Vector3(7.0, height, 2.8)
        elif str(site["category"]) == "sport":
            footprint = Vector3(12.0, 0.5, 7.0)
        _solid(parent, name.replace(" ", ""), Vector3(x, footprint.y / 2.0, z), footprint, tint)
        if str(site["category"]) != "sport":
            _visual(parent, Vector3(x, footprint.y + 0.15, z),
                Vector3(footprint.x + 0.5, 0.3, footprint.z + 0.5), Color("#424c53"))
        destinations.append({
            "name": name,
            "position": Vector3(x, 0.0, z),
            "kind": "futa",
            "category": site["category"],
            "description": site["info"],
        })
    return destinations


func _surroundings(parent: Node3D) -> void:
    # Repeating homes, green buffers and courtyards: no procedural randomness,
    # keeping the same layout on Windows and Android and across game sessions.
    for index in range(13):
        var x := -162.0 + float(index) * 12.1
        _solid(parent, "SouthLodge%02d" % index, Vector3(x, 1.6, -139.0),
            Vector3(4.5, 3.2, 5.0),
            Color("#b98f7d") if index % 2 == 0 else Color("#d5b194"))
        _visual(parent, Vector3(x, 3.32, -139.0),
            Vector3(4.9, 0.25, 5.4), Color("#685f5e"))
    for row in range(5):
        for column in range(9):
            var x := -161.0 + float(column) * 18.0
            var z := -16.0 - float(row) * 27.0
            var clear := true
            for road_x in VERTICAL_ROADS:
                if absf(x - road_x) < 4.0:
                    clear = false
            for road_z in HORIZONTAL_ROADS:
                if absf(z - road_z) < 4.0:
                    clear = false
            for site in LANDMARKS:
                if Vector2(x - float(site["x"]), z - float(site["z"])).length() < 10.0:
                    clear = false
            if not clear:
                continue
            _visual(parent, Vector3(x, 0.65, z), Vector3(0.38, 1.3, 0.38), Color("#785c40"))
            _visual(parent, Vector3(x, 1.78, z), Vector3(2.8, 1.7, 2.8), Color("#387c44"))


func build(parent: Node3D) -> Array[Dictionary]:
    _ground_and_roads(parent)
    var destinations := _campus_buildings(parent)
    _surroundings(parent)
    return destinations
