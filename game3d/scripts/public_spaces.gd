extends Node3D
## Low-cost interactive civic spaces for the starter city.
## Visual-only furnishings have no colliders so walking routes and access remain clear.

const Layout = preload("res://scripts/neighborhood_layout.gd")
const INTERACTION_DISTANCE: float = 2.4
var world: Node3D


static func public_spots() -> Array[Dictionary]:
    return [
        {"id":"park_bench", "type":"bench", "name":"Pocket Park Bench", "position":Vector3(-11.0, 0.0, -14.0)},
        {"id":"market_bench", "type":"bench", "name":"Market Walk Bench", "position":Vector3(-10.0, 0.0, -4.5)},
        {"id":"campus_bench", "type":"bench", "name":"Campus Way Bench", "position":Vector3(10.0, 0.0, -4.5)},
        {"id":"market_bin", "type":"bin", "name":"Market Waste Bin", "position":Vector3(-14.0, 0.0, -3.6)},
        {"id":"campus_bin", "type":"bin", "name":"Campus Waste Bin", "position":Vector3(14.0, 0.0, -3.6)},
        {"id":"hostel_bin", "type":"bin", "name":"Hostel Waste Bin", "position":Vector3(11.0, 0.0, 9.5)},
        {"id":"fruit_stall", "type":"stall", "name":"Fruit Stall", "position":Vector3(-24.0, 0.0, 10.0), "description":"Fresh market fruit from local vendors."},
        {"id":"fabric_stall", "type":"stall", "name":"Fabric Stall", "position":Vector3(-21.0, 0.0, 12.5), "description":"Colourful fabric and outfit ideas."},
        {"id":"craft_stall", "type":"stall", "name":"Craft Stall", "position":Vector3(-17.0, 0.0, 13.0), "description":"Handmade decorative craft displays."},
        {"id":"opportunity_board", "type":"board", "name":"Community Jobs Board", "position":Vector3(9.0, 0.0, -10.0)}
    ]


static func nearest_spot(location: Vector3, max_distance: float = INTERACTION_DISTANCE) -> Dictionary:
    if not location.is_finite() or absf(location.y) > 3.0:
        return {}
    var nearest: Dictionary = {}
    var best: float = max_distance
    for spot in public_spots():
        var site: Vector3 = spot["position"]
        var distance: float = Vector2(location.x - site.x, location.z - site.z).length()
        if distance < best:
            nearest = spot
            best = distance
    return nearest


func configure(game_world: Node3D) -> void:
    world = game_world
    name = "PublicSpaces"
    _build_pocket_park()
    for spot in public_spots():
        _build_spot(spot)


func _box(center: Vector3, size: Vector3, color: Color) -> void:
    world.call("_add_visual_box", self, center, size, color)


func _caption(text: String, site: Vector3, size: int = 30) -> void:
    var sign := Label3D.new()
    sign.text = text
    sign.position = site
    sign.font_size = size
    sign.pixel_size = 0.008
    sign.outline_size = 8
    sign.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    add_child(sign)


func _build_pocket_park() -> void:
    _box(Vector3(-11.0, 0.025, -14.2), Vector3(8.0, 0.035, 7.0), Color(0.26, 0.55, 0.30))
    _box(Vector3(-11.0, 0.055, -14.2), Vector3(1.4, 0.01, 7.0), Color(0.77, 0.69, 0.55))
    _caption("POCKET PARK", Vector3(-11.0, 1.9, -18.0))
    for x in [-14.3, -7.8]:
        _box(Vector3(x, 0.65, -16.0), Vector3(0.25, 1.3, 0.25), Color(0.40, 0.28, 0.19))
        _box(Vector3(x, 1.55, -16.0), Vector3(1.4, 1.4, 1.4), Color(0.18, 0.42, 0.24))


func _build_spot(spot: Dictionary) -> void:
    var site: Vector3 = spot["position"]
    var spot_name: String = str(spot["name"])
    match str(spot["type"]):
        "bench":
            _box(site + Vector3(0, 0.55, 0), Vector3(1.9, 0.16, 0.52), Color(0.49, 0.31, 0.17))
            _box(site + Vector3(0, 1.05, 0.26), Vector3(1.9, 0.9, 0.13), Color(0.49, 0.31, 0.17))
            for x in [-0.75, 0.75]:
                _box(site + Vector3(x, 0.26, 0.0), Vector3(0.13, 0.52, 0.13), Color(0.28, 0.29, 0.3))
        "bin":
            _box(site + Vector3(0, 0.43, 0), Vector3(0.5, 0.86, 0.5), Color(0.25, 0.40, 0.29))
            _box(site + Vector3(0, 0.9, 0), Vector3(0.62, 0.10, 0.62), Color(0.18, 0.22, 0.24))
        "stall":
            _box(site + Vector3(0, 0.68, 0), Vector3(2.1, 1.35, 1.4), Color(0.48, 0.33, 0.23))
            var accent: Color = Color(0.91, 0.59, 0.17)
            if str(spot["id"]) == "fabric_stall":
                accent = Color(0.32, 0.47, 0.77)
            elif str(spot["id"]) == "craft_stall":
                accent = Color(0.56, 0.42, 0.65)
            _box(site + Vector3(0, 2.1, 0), Vector3(2.5, 0.15, 1.8), accent)
            _caption(spot_name, site + Vector3(0, 2.8, 0))
        "board":
            _box(site + Vector3(0, 1.5, 0), Vector3(2.7, 1.55, 0.18), Color(0.18, 0.30, 0.40))
            for x in [-1.0, 1.0]:
                _box(site + Vector3(x, 0.8, 0), Vector3(0.15, 1.6, 0.15), Color(0.38, 0.28, 0.17))
            _caption("COMMUNITY JOBS", site + Vector3(0, 1.9, 0.14), 27)
    if str(spot["type"]) != "stall" and str(spot["type"]) != "board":
        # Signposts help users identify interactables without needing a minimap.
        _caption(spot_name, site + Vector3(0, 1.45, 0), 22)
