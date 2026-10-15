extends Node3D
## BLINK LIFE 3D: Testlab-only native Godot scene.
## Fictional local Game Cash only. No Supabase, payments, BLINK Coins, or cashout.

const WALK_SPEED: float = 5.0
const INTERACT_DISTANCE: float = 5.1
const WORLD_STEP: float = 6.0

var player: CharacterBody3D
var follow_camera: Camera3D
var hud_cash: Label
var hud_location: Label
var hud_status: Label
var hud_modal: PanelContainer
var touch_axis: Vector2 = Vector2.ZERO
var orbit_angle: float = 40.0
var game_cash: int = 500
var completed_jobs: int = 0
var owns_plant: bool = false
var places: Array[Dictionary] = []
var active_quiz: Dictionary = {}


func _ready() -> void:
    _create_lights()
    _create_city()
    _create_player()
    _create_camera()
    _create_hud()


func _process(delta: float) -> void:
    if player == null or follow_camera == null:
        return

    var radians: float = deg_to_rad(orbit_angle)
    var desired: Vector3 = player.global_position + Vector3(
        sin(radians) * 13.0, 11.0, cos(radians) * 13.0
    )
    follow_camera.global_position = follow_camera.global_position.lerp(
        desired, minf(delta * 6.0, 1.0)
    )
    follow_camera.look_at(player.global_position + Vector3.UP * 1.25)

    hud_cash.text = "Game Cash: %d GC   |   Jobs: %d" % [game_cash, completed_jobs]
    var nearby: Dictionary = _nearest_place()
    if nearby.is_empty():
        hud_location.text = "Explore the 3D city — approach a marked building"
    else:
        hud_location.text = "%s • Press INTERACT" % str(nearby["name"])


func _physics_process(delta: float) -> void:
    if player == null:
        return

    var direction := Vector2.ZERO
    if Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT):
        direction.x -= 1.0
    if Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT):
        direction.x += 1.0
    if Input.is_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP):
        direction.y -= 1.0
    if Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN):
        direction.y += 1.0

    if touch_axis != Vector2.ZERO:
        direction = touch_axis

    direction = direction.normalized()
    player.velocity.x = direction.x * WALK_SPEED
    player.velocity.z = direction.y * WALK_SPEED
    if not player.is_on_floor():
        player.velocity.y -= 18.0 * delta
    else:
        player.velocity.y = 0.0

    if direction.length_squared() > 0.001:
        player.rotation.y = atan2(-direction.x, -direction.y)
    player.move_and_slide()


func _unhandled_key_input(event: InputEvent) -> void:
    if event is InputEventKey and event.pressed and not event.echo:
        if event.keycode == KEY_Q:
            _orbit(-35.0)
        elif event.keycode == KEY_E:
            _orbit(35.0)
        elif event.keycode == KEY_F:
            _interact()
        elif event.keycode == KEY_ESCAPE:
            if hud_modal != null and hud_modal.visible:
                _close_quiz()
            else:
                get_tree().quit()


func _create_lights() -> void:
    var sunlight := DirectionalLight3D.new()
    sunlight.rotation_degrees = Vector3(-55.0, 25.0, 0.0)
    sunlight.light_energy = 1.2
    sunlight.shadow_enabled = true
    add_child(sunlight)

    var environment := WorldEnvironment.new()
    var settings := Environment.new()
    settings.background_mode = Environment.BG_COLOR
    settings.background_color = Color(0.72, 0.82, 0.92)
    settings.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    settings.ambient_light_color = Color(0.93, 0.94, 0.97)
    settings.ambient_light_energy = 0.6
    environment.environment = settings
    add_child(environment)


func _make_material(tint: Color) -> StandardMaterial3D:
    var material := StandardMaterial3D.new()
    material.albedo_color = tint
    material.roughness = 0.85
    return material


func _add_visual_box(parent: Node3D, local_center: Vector3, dimensions: Vector3, tint: Color) -> void:
    var visual := MeshInstance3D.new()
    var geometry := BoxMesh.new()
    geometry.size = dimensions
    visual.mesh = geometry
    visual.material_override = _make_material(tint)
    visual.position = local_center
    parent.add_child(visual)


func _add_static_box(label: String, center: Vector3, dimensions: Vector3, tint: Color) -> void:
    var body := StaticBody3D.new()
    body.name = label
    body.position = center
    add_child(body)

    _add_visual_box(body, Vector3.ZERO, dimensions, tint)

    var shape := BoxShape3D.new()
    shape.size = dimensions
    var collider := CollisionShape3D.new()
    collider.shape = shape
    body.add_child(collider)


func _location_position(col: int, row: int) -> Vector3:
    return Vector3((col - 3) * WORLD_STEP, 0.0, (row - 2) * WORLD_STEP)


func _create_city() -> void:
    _add_static_box(
        "Grass foundation", Vector3(0, -0.3, 0),
        Vector3(55, 0.6, 42), Color(0.37, 0.55, 0.41)
    )
    # Roads are overlays, not obstacles.
    _add_visual_box(self, Vector3(0, 0.012, 0), Vector3(52, 0.025, 4.6), Color(0.35, 0.40, 0.46))
    _add_visual_box(self, Vector3(0, 0.013, 0), Vector3(4.6, 0.028, 39), Color(0.35, 0.40, 0.46))

    places = [
        {"name": "Campus", "col": 3, "row": 0, "height": 5.1, "color": Color(0.45, 0.51, 0.59)},
        {"name": "Café", "col": 0, "row": 1, "height": 3.1, "color": Color(0.75, 0.48, 0.36)},
        {"name": "Tech hub", "col": 6, "row": 1, "height": 4.4, "color": Color(0.40, 0.53, 0.67)},
        {"name": "Market", "col": 0, "row": 3, "height": 3.2, "color": Color(0.79, 0.65, 0.39)},
        {"name": "Delivery depot", "col": 2, "row": 4, "height": 3.2, "color": Color(0.50, 0.51, 0.59)},
        {"name": "Hostel", "col": 6, "row": 4, "height": 5.3, "color": Color(0.63, 0.55, 0.47)}
    ]

    for place in places:
        var location: Vector3 = _location_position(int(place["col"]), int(place["row"]))
        var height: float = float(place["height"])
        _add_static_box(
            str(place["name"]), location + Vector3(0, height / 2.0, 0),
            Vector3(4.2, height, 4.2), place["color"]
        )
        _add_visual_box(
            self, location + Vector3(0, height + 0.15, 0),
            Vector3(4.5, 0.3, 4.5), Color(0.25, 0.30, 0.35)
        )

    # Low-poly trees and streetscape.
    for coords in [Vector2i(1, 0), Vector2i(5, 0), Vector2i(4, 4), Vector2i(1, 4)]:
        var site := _location_position(coords.x, coords.y)
        _add_visual_box(self, site + Vector3(0, 0.6, 0), Vector3(0.32, 1.2, 0.32), Color(0.40, 0.28, 0.20))
        _add_visual_box(self, site + Vector3(0, 1.65, 0), Vector3(2.2, 1.8, 2.2), Color(0.20, 0.44, 0.27))


func _create_player() -> void:
    player = CharacterBody3D.new()
    player.name = "Player"
    player.position = Vector3(0, 0.15, 0)
    player.floor_snap_length = 0.25
    add_child(player)

    var capsule := CapsuleShape3D.new()
    capsule.radius = 0.34
    capsule.height = 1.65
    var collider := CollisionShape3D.new()
    collider.position = Vector3(0, 0.87, 0)
    collider.shape = capsule
    player.add_child(collider)

    _add_visual_box(player, Vector3(0, 0.80, 0), Vector3(0.63, 0.86, 0.40), Color(0.10, 0.14, 0.21))
    _add_visual_box(player, Vector3(-0.16, 0.23, 0), Vector3(0.23, 0.45, 0.32), Color(0.22, 0.25, 0.32))
    _add_visual_box(player, Vector3(0.16, 0.23, 0), Vector3(0.23, 0.45, 0.32), Color(0.22, 0.25, 0.32))

    var head := MeshInstance3D.new()
    var sphere := SphereMesh.new()
    sphere.radius = 0.32
    sphere.height = 0.64
    head.mesh = sphere
    head.material_override = _make_material(Color(0.88, 0.62, 0.45))
    head.position = Vector3(0, 1.55, 0)
    player.add_child(head)


func _create_camera() -> void:
    follow_camera = Camera3D.new()
    follow_camera.name = "OrbitCamera"
    follow_camera.current = true
    follow_camera.fov = 58.0
    follow_camera.position = Vector3(9, 11, 11)
    add_child(follow_camera)


func _new_button(caption: String, minimum_width: float = 56.0) -> Button:
    var button := Button.new()
    button.text = caption
    button.custom_minimum_size = Vector2(minimum_width, 52)
    button.focus_mode = Control.FOCUS_NONE
    return button


func _create_hud() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)

    var hud := Control.new()
    hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    layer.add_child(hud)

    hud_cash = Label.new()
    hud_cash.position = Vector2(18, 14)
    hud_cash.add_theme_font_size_override("font_size", 22)
    hud.add_child(hud_cash)

    hud_location = Label.new()
    hud_location.position = Vector2(18, 46)
    hud_location.add_theme_font_size_override("font_size", 16)
    hud.add_child(hud_location)

    hud_status = Label.new()
    hud_status.position = Vector2(18, 78)
    hud_status.text = "TESTLAB: all money is fictional. Walk using WASD/arrows or touch controls."
    hud_status.add_theme_font_size_override("font_size", 14)
    hud.add_child(hud_status)

    # Always offer a visible exit: Android's Godot instance runs in a
    # separate process, so quitting here returns the player to BLINK.
    var exit_button := _new_button("EXIT TO BLINK", 160)
    exit_button.anchor_left = 1.0
    exit_button.anchor_right = 1.0
    exit_button.offset_left = -195
    exit_button.offset_right = -12
    exit_button.offset_top = 14
    exit_button.offset_bottom = 66
    exit_button.pressed.connect(func(): get_tree().quit())
    hud.add_child(exit_button)

    var direction_bar := HBoxContainer.new()
    direction_bar.anchor_top = 1
    direction_bar.anchor_bottom = 1
    direction_bar.offset_left = 14
    direction_bar.offset_top = -72
    direction_bar.offset_right = 285
    direction_bar.offset_bottom = -10
    hud.add_child(direction_bar)

    var controls := [
        ["←", Vector2(-1, 0)], ["↑", Vector2(0, -1)],
        ["↓", Vector2(0, 1)], ["→", Vector2(1, 0)]
    ]
    for item in controls:
        var arrow := _new_button(str(item[0]))
        direction_bar.add_child(arrow)
        arrow.button_down.connect(_set_touch_axis.bind(item[1]))
        arrow.button_up.connect(_stop_touch_axis)

    var action_bar := HBoxContainer.new()
    action_bar.anchor_left = 1
    action_bar.anchor_right = 1
    action_bar.anchor_top = 1
    action_bar.anchor_bottom = 1
    action_bar.offset_left = -322
    action_bar.offset_top = -72
    action_bar.offset_right = -12
    action_bar.offset_bottom = -10
    hud.add_child(action_bar)

    var left_camera := _new_button("↶", 54)
    action_bar.add_child(left_camera)
    left_camera.pressed.connect(_orbit.bind(-35.0))

    var interact_button := _new_button("INTERACT", 118)
    action_bar.add_child(interact_button)
    interact_button.pressed.connect(_interact)

    var right_camera := _new_button("↷", 54)
    action_bar.add_child(right_camera)
    right_camera.pressed.connect(_orbit.bind(35.0))

    hud_modal = PanelContainer.new()
    hud_modal.anchor_left = 0.5
    hud_modal.anchor_right = 0.5
    hud_modal.anchor_top = 0.5
    hud_modal.anchor_bottom = 0.5
    hud_modal.offset_left = -260
    hud_modal.offset_right = 260
    hud_modal.offset_top = -145
    hud_modal.offset_bottom = 145
    hud_modal.visible = false
    hud.add_child(hud_modal)


func _set_touch_axis(axis: Vector2) -> void:
    touch_axis = axis


func _stop_touch_axis() -> void:
    touch_axis = Vector2.ZERO


func _orbit(degrees: float) -> void:
    orbit_angle = fposmod(orbit_angle + degrees, 360.0)


func _nearest_place() -> Dictionary:
    for place in places:
        var location: Vector3 = _location_position(int(place["col"]), int(place["row"]))
        var horizontal_distance: float = Vector2(
            player.global_position.x - location.x,
            player.global_position.z - location.z
        ).length()
        if horizontal_distance <= INTERACT_DISTANCE:
            return place
    return {}


func _interact() -> void:
    if hud_modal.visible:
        return
    var place: Dictionary = _nearest_place()
    if place.is_empty():
        hud_status.text = "Move closer to a building before interacting."
        return

    match str(place["name"]):
        "Café":
            _start_quiz("Café cashier", "Two snacks cost 80 GC each. You receive 200 GC. Change?", ["20 GC", "40 GC", "60 GC"], 1, 120)
        "Tech hub":
            _start_quiz("Tech support", "A laptop will not charge. What should you check first?", ["Power cable and outlet", "Delete photos", "Mute speakers"], 0, 180)
        "Delivery depot":
            _start_quiz("Delivery dispatch", "Which action makes a delivery safer?", ["Ignore the route", "Follow the marked route", "Race through traffic"], 1, 150)
        "Market":
            if owns_plant:
                hud_status.text = "You already purchased your virtual desk plant."
            elif game_cash < 250:
                hud_status.text = "A desk plant costs 250 fictional Game Cash."
            else:
                game_cash -= 250
                owns_plant = true
                hud_status.text = "Purchased a desk plant. Visit your hostel!"
        "Hostel":
            hud_status.text = "Your room has a desk plant." if owns_plant else "Your room is empty. Visit the market."
        _:
            hud_status.text = "Campus unlocked. More missions will follow."


func _start_quiz(title: String, prompt: String, choices: Array[String], answer: int, payout: int) -> void:
    active_quiz = {"answer": answer, "payout": payout}
    for child in hud_modal.get_children():
        child.queue_free()

    var content := VBoxContainer.new()
    hud_modal.add_child(content)

    var title_label := Label.new()
    title_label.text = title
    title_label.add_theme_font_size_override("font_size", 20)
    content.add_child(title_label)

    var question_label := Label.new()
    question_label.text = prompt
    question_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    content.add_child(question_label)

    for choice_id in range(choices.size()):
        var choice_button := _new_button(choices[choice_id])
        content.add_child(choice_button)
        choice_button.pressed.connect(_submit_quiz.bind(choice_id))

    var cancel := _new_button("Cancel")
    content.add_child(cancel)
    cancel.pressed.connect(_close_quiz)
    hud_modal.visible = true


func _submit_quiz(choice_id: int) -> void:
    if active_quiz.is_empty():
        return
    if choice_id != int(active_quiz["answer"]):
        hud_status.text = "Not quite — try another answer."
        return

    var payout: int = int(active_quiz["payout"])
    game_cash += payout
    completed_jobs += 1
    hud_status.text = "Job complete! Earned %d fictional Game Cash." % payout
    _close_quiz()


func _close_quiz() -> void:
    active_quiz = {}
    hud_modal.visible = false
