extends Control
## 2D in-game campus plan for the same fictional low-poly BLINK LIFE world.
## Draws original vector paths and building footprints; not a licensed map layer.
## Player marker is derived from Godot scene position, never phone GPS.

signal waypoint_selected(landmark: Dictionary)

const LEFT_X: float = -170.0
const RIGHT_X: float = 0.0
const TOP_Z: float = -145.0
const BOTTOM_Z: float = 1.0
const HORIZONTAL_ROADS := [-25.0, -65.0, -105.0]
const VERTICAL_ROADS := [-24.0, -65.0, -105.0, -142.0]

var landmarks: Array[Dictionary] = []
var avatar: Node3D
var update_seconds: float = 0.0


func configure(sites: Array[Dictionary], player: Node3D) -> void:
    landmarks = sites
    avatar = player
    custom_minimum_size = Vector2(312, 170)
    mouse_filter = Control.MOUSE_FILTER_STOP
    queue_redraw()


func _world_to_canvas(point: Vector3) -> Vector2:
    var width := maxf(size.x, 312.0) - 24.0
    var height := maxf(size.y, 170.0) - 20.0
    var u := clampf((point.x - LEFT_X) / (RIGHT_X - LEFT_X), 0.0, 1.0)
    var v := clampf((point.z - TOP_Z) / (BOTTOM_Z - TOP_Z), 0.0, 1.0)
    return Vector2(12.0 + u * width, 10.0 + v * height)


func _category_color(category: String) -> Color:
    match category:
        "academic":
            return Color("#80b7ed")
        "residential":
            return Color("#dc9483")
        "sport":
            return Color("#91cf7c")
        "commercial":
            return Color("#e2c476")
        "job":
            return Color("#f4ad58")
        "service":
            return Color("#efe0cb")
        "transport":
            return Color("#afcfd2")
        _:
            return Color("#e3e4e0")


func _draw() -> void:
    draw_rect(Rect2(Vector2.ZERO, size), Color("#263f38"), true)
    var campus_outline := Rect2(
        _world_to_canvas(Vector3(LEFT_X, 0, TOP_Z)),
        _world_to_canvas(Vector3(RIGHT_X, 0, BOTTOM_Z)) - _world_to_canvas(Vector3(LEFT_X, 0, TOP_Z))
    )
    draw_rect(campus_outline, Color("#81a481"), false, 1.5)
    for z in HORIZONTAL_ROADS:
        draw_line(_world_to_canvas(Vector3(-169, 0, z)),
            _world_to_canvas(Vector3(-3, 0, z)), Color("#74858b"), 4.0)
    for x in VERTICAL_ROADS:
        draw_line(_world_to_canvas(Vector3(x, 0, -143)),
            _world_to_canvas(Vector3(x, 0, -2)), Color("#74858b"), 4.0)
    # Map houses beyond the academic blocks, matching the generated lodges.
    for index in range(13):
        var x := -162.0 + float(index) * 12.1
        var building_at := _world_to_canvas(Vector3(x, 0, -139))
        draw_rect(Rect2(building_at - Vector2(2, 1.5), Vector2(4, 3)),
            Color("#c49c81"), true)
    for site in landmarks:
        var dot := _world_to_canvas(site["position"])
        draw_rect(Rect2(dot - Vector2(3, 3), Vector2(6, 6)),
            _category_color(str(site["category"])), true)
        draw_rect(Rect2(dot - Vector2(3, 3), Vector2(6, 6)),
            Color("#0b1417"), false, 1.0)
    if avatar != null:
        var player_dot := _world_to_canvas(avatar.global_position)
        draw_circle(player_dot, 5.0, Color("#f5f2e7"))
        draw_circle(player_dot, 2.5, Color("#274f97"))


func _process(delta: float) -> void:
    if not visible:
        return
    update_seconds += delta
    if update_seconds >= 0.25:
        update_seconds = 0.0
        queue_redraw()


func _gui_input(event: InputEvent) -> void:
    var press_at := Vector2.ZERO
    if event is InputEventMouseButton:
        if not event.pressed or event.button_index != MOUSE_BUTTON_LEFT:
            return
        press_at = event.position
    elif event is InputEventScreenTouch:
        if not event.pressed:
            return
        press_at = event.position
    else:
        return
    var distance := 13.0
    var nearest: Dictionary = {}
    for site in landmarks:
        var current := _world_to_canvas(site["position"]).distance_to(press_at)
        if current < distance:
            distance = current
            nearest = site
    if not nearest.is_empty():
        waypoint_selected.emit(nearest)
        accept_event()
