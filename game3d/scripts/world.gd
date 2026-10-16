extends Node3D
## BLINK LIFE 3D: Testlab-only native Godot scene.
## Fictional local Game Cash only. No Supabase, payments, BLINK Coins, or cashout.

const Layout = preload("res://scripts/neighborhood_layout.gd")
const Details = preload("res://scripts/neighborhood_details.gd")

const Preferences = preload("res://scripts/player_preferences.gd")

const DayCycle = preload("res://scripts/day_cycle.gd")

const WALK_SPEED: float = 5.0
const INTERACT_DISTANCE: float = 5.1
const WORLD_STEP: float = Layout.GRID_METRES

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
var futa_landmarks: Array[Dictionary] = []
var selected_waypoint: Dictionary = {}
var map_panel: PanelContainer
var map_view: Control
var active_quiz: Dictionary = {}
var social: Node3D
var preferences := Preferences.new()
var controls_panel: PanelContainer
var touch_directions: Dictionary = {}
var app_focused: bool = true
var last_safe_position := Vector3(0, 0.15, 0)
var camera_distance: float = 13.0
var day_cycle := DayCycle.new()
var sunlight: DirectionalLight3D
var sky_settings: Environment
var clock_label: Label
var neighborhood: Node3D
var purchase_pending: bool = false
var previous_fps_limit: int = 0



func _ready() -> void:
    previous_fps_limit = Engine.max_fps
    preferences.read_settings()
    camera_distance = preferences.camera_distance
    day_cycle.enabled = preferences.day_cycle_enabled
    day_cycle.day_minutes = preferences.day_minutes
    _create_lights()
    _create_city()
    var details := Details.new()
    add_child(details)
    details.configure(self)
    neighborhood = details
    _create_futa_district()
    _create_player()
    _create_camera()
    _create_hud()
    _apply_graphics()
    # Instantiate network UI only inside the already-launched Godot game.
    # Connections are opt-in: no socket opens until HOST LAN / JOIN LAN.
    var social_script: Script = load("res://scripts/social_world.gd")
    social = social_script.new()
    social.name = "SocialWorld"
    add_child(social)
    social.call("configure", self, player)


func _process(delta: float) -> void:
    if app_focused:
        day_cycle.advance(delta)
    _update_daylight()
    if player == null or follow_camera == null:
        return

    var radians: float = deg_to_rad(orbit_angle)
    var desired: Vector3 = player.global_position + Vector3(
        sin(radians) * camera_distance, camera_distance * 0.846, cos(radians) * camera_distance
    )
    if preferences.reduced_motion:
        follow_camera.global_position = desired
    else:
        follow_camera.global_position = follow_camera.global_position.lerp(desired, 1.0 - exp(-delta * 6.0))
    follow_camera.look_at(player.global_position + Vector3.UP * 1.25)

    hud_cash.text = "Game Cash: %d GC   |   Jobs: %d" % [game_cash, completed_jobs]
    var nearby: Dictionary = _nearest_place()
    if nearby.is_empty():
        hud_location.text = "Explore the 3D city — approach a marked building"
    else:
        if str(nearby.get("kind", "")) == "futa":
            hud_location.text = "%s • INTERACT" % nearby["name"]
        else:
            hud_location.text = "%s • %s • INTERACT" % [nearby["name"], nearby["address"]]
    if not selected_waypoint.is_empty():
        var destination: Vector3 = selected_waypoint["entry"]
        var distance: int = roundi(Vector2(
            destination.x - player.global_position.x,
            destination.z - player.global_position.z
        ).length())
        hud_location.text += "  |  %s: %d game units" % [selected_waypoint["name"], distance]


func _physics_process(delta: float) -> void:
    if player == null:
        return
    # Recover only offline. Network-authorized movement/home ownership stays
    # with SocialWorld, so local rescue cannot become a teleport exploit.
    if social == null or not bool(social.get("is_online")):
        _recover_if_needed()
    # Approved passengers follow their driver's networked vehicle.
    # Driving remains a client-side visual preview; the host validates moves.
    if social != null and bool(social.call("is_local_passenger")):
        player.velocity = Vector3.ZERO
        player.global_position = social.call("passenger_position")
        return

    var direction := Vector2.ZERO
    var blocked: bool = _movement_blocked()
    if blocked:
        _stop_touch_axis()
        player.velocity.x = 0.0
        player.velocity.z = 0.0
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

    direction = Vector2.ZERO if blocked else direction.limit_length(1.0)
    var speed: float = WALK_SPEED * preferences.movement_scale
    if social != null and bool(social.call("is_local_driver")):
        speed *= 1.6
    var horizontal: Vector2 = Preferences.horizontal_velocity(
        Vector2(player.velocity.x, player.velocity.z), direction, speed, delta)
    player.velocity.x = horizontal.x
    player.velocity.z = horizontal.y
    if not player.is_on_floor():
        player.velocity.y -= 18.0 * delta
    else:
        player.velocity.y = 0.0

    if direction.length_squared() > 0.001:
        player.rotation.y = lerp_angle(player.rotation.y, atan2(-direction.x, -direction.y), minf(delta * 12.0, 1.0))
    player.move_and_slide()
    if player.is_on_floor() and _is_play_area(player.global_position):
        last_safe_position = player.global_position


func _is_play_area(position: Vector3) -> bool:
    if absf(position.x) < 28.0 and absf(position.z) < 22.0:
        return true
    # Campus is west of the small original district. Keep private interiors
    # on the positive-X side out of this public-world collision/recovery area.
    return position.x >= -170.0 and position.x <= 0.0 and position.z >= -145.0 and position.z <= 0.0


func _movement_blocked() -> bool:
    if not app_focused:
        return true
    if hud_modal != null and hud_modal.visible:
        return true
    if controls_panel != null and controls_panel.visible:
        return true
    if map_panel != null and map_panel.visible:
        return true
    if social != null and social.get("panel") != null and social.get("panel").visible:
        return true
    var focused: Control = get_viewport().gui_get_focus_owner()
    return focused is LineEdit or focused is TextEdit


func _recover_if_needed() -> void:
    var position: Vector3 = player.global_position
    if not position.is_finite() or position.y < -3.0 or not _is_play_area(position):
        player.global_position = last_safe_position + Vector3.UP * 0.1
        player.velocity = Vector3.ZERO
        _stop_touch_axis()
        hud_status.text = "Returned to safe ground."


func _notification(what: int) -> void:
    if what == NOTIFICATION_APPLICATION_FOCUS_OUT or what == NOTIFICATION_APPLICATION_PAUSED:
        app_focused = false
        _stop_touch_axis()
    elif what == NOTIFICATION_APPLICATION_FOCUS_IN or what == NOTIFICATION_APPLICATION_RESUMED:
        app_focused = true


func _unhandled_key_input(event: InputEvent) -> void:
    if event is InputEventKey and event.pressed and not event.echo:
        if event.keycode == KEY_ESCAPE:
            if controls_panel != null and controls_panel.visible:
                controls_panel.hide()
            elif map_panel != null and map_panel.visible:
                map_panel.hide()
            elif hud_modal != null and hud_modal.visible:
                _close_quiz()
            elif social != null and social.get("panel").visible:
                social.get("panel").hide()
            else:
                get_tree().quit()
            _stop_touch_axis()
            return
        if _movement_blocked():
            return
        if event.keycode == KEY_Q:
            _orbit(-35.0)
        elif event.keycode == KEY_E:
            _orbit(35.0)
        elif event.keycode == KEY_F:
            _interact()
        elif event.keycode == KEY_EQUAL or event.keycode == KEY_KP_ADD:
            _zoom(-1.0)
        elif event.keycode == KEY_MINUS or event.keycode == KEY_KP_SUBTRACT:
            _zoom(1.0)


func _create_lights() -> void:
    sunlight = DirectionalLight3D.new()
    sunlight.rotation_degrees = Vector3(-55.0, 25.0, 0.0)
    sunlight.light_energy = 1.2
    sunlight.shadow_enabled = true
    add_child(sunlight)

    var environment := WorldEnvironment.new()
    var settings := Environment.new()
    sky_settings = settings
    settings.background_mode = Environment.BG_COLOR
    settings.background_color = Color(0.72, 0.82, 0.92)
    settings.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    settings.ambient_light_color = Color(0.93, 0.94, 0.97)
    settings.ambient_light_energy = 0.6
    environment.environment = settings
    add_child(environment)


func _update_daylight() -> void:
    if sunlight == null or sky_settings == null:
        return
    var frame: Dictionary = day_cycle.sample()
    sunlight.rotation_degrees = frame["sun_rotation"]
    sunlight.light_energy = frame["sun_energy"]
    sunlight.light_color = frame["sun_color"]
    sky_settings.background_color = frame["sky_color"]
    sky_settings.ambient_light_energy = frame["ambient_energy"]
    if neighborhood != null and player != null:
        neighborhood.call("update_lighting", player.global_position, float(frame["daylight"]), preferences.battery_saver)
    if clock_label != null:
        var minutes: int = int(float(frame["hour"]) * 60.0) % 1440
        clock_label.text = "Local scene time %02d:%02d" % [minutes / 60, minutes % 60]


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
    return Layout.position_for(col, row)


func _create_city() -> void:
    _add_static_box(
        "Grass foundation", Vector3(0, -0.3, 0),
        Layout.FOUNDATION_SIZE, Color(0.37, 0.55, 0.41)
    )
    # Roads are overlays, not obstacles.
    _add_visual_box(self, Vector3(0, 0.012, 0), Vector3(52, 0.025, 4.6), Color(0.35, 0.40, 0.46))
    _add_visual_box(self, Vector3(0, 0.013, 4.5), Vector3(4.6, 0.028, 27), Color(0.35, 0.40, 0.46))

    places = Layout.places()

    for place in places:
        var location: Vector3 = _location_position(int(place["col"]), int(place["row"]))
        var height: float = float(place["height"])
        _add_static_box(
            str(place["name"]), location + Vector3(0, height / 2.0, 0),
            Vector3(Layout.BUILDING_WIDTH, height, Layout.BUILDING_WIDTH), place["color"]
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


## Loaded only when BLINK LIFE opens, never when normal BLINK pages load.
func _create_futa_district() -> void:
    var map_script: Script = load("res://scripts/futa_map.gd")
    var map_builder: RefCounted = map_script.new()
    futa_landmarks = map_builder.call("build", self)
    places.append_array(futa_landmarks)


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
    _create_map_hud(hud)

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
        arrow.button_up.connect(_release_touch_axis.bind(item[1]))

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
    _create_controls_panel(hud)


## Navigation uses fictional in-game coordinates, not real-world GPS.
func _create_map_hud(hud: Control) -> void:
    var map_button := _new_button("CAMPUS MAP", 145)
    map_button.position = Vector2(18, 115)
    hud.add_child(map_button)

    map_panel = PanelContainer.new()
    map_panel.position = Vector2(18, 175)
    map_panel.custom_minimum_size = Vector2(335, 0)
    map_panel.visible = false
    hud.add_child(map_panel)
    map_button.pressed.connect(func():
        if (controls_panel != null and controls_panel.visible) or (hud_modal != null and hud_modal.visible):
            return
        if social != null and social.get("panel") != null and social.get("panel").visible:
            return
        map_panel.visible = not map_panel.visible
        _stop_touch_axis()
    )

    var layout := VBoxContainer.new()
    map_panel.add_child(layout)
    var title := Label.new()
    title.text = "FUTA-inspired GAME map (fictional layout)"
    title.add_theme_font_size_override("font_size", 15)
    layout.add_child(title)
    var hint := Label.new()
    hint.text = "Tap a map marker or choose a named destination below."
    layout.add_child(hint)

    var view_script: Script = load("res://scripts/futa_minimap.gd")
    map_view = view_script.new()
    map_view.name = "CampusPlanView"
    layout.add_child(map_view)
    map_view.call("configure", futa_landmarks, player)
    map_view.connect("waypoint_selected", _select_waypoint)

    var scroll := ScrollContainer.new()
    scroll.custom_minimum_size = Vector2(315, 138)
    layout.add_child(scroll)
    var destinations := VBoxContainer.new()
    scroll.add_child(destinations)
    for landmark in futa_landmarks:
        var item := _new_button(str(landmark["name"]), 302)
        destinations.add_child(item)
        item.pressed.connect(_select_waypoint.bind(landmark))
    var clear := _new_button("CLEAR WAYPOINT")
    layout.add_child(clear)
    clear.pressed.connect(func():
        selected_waypoint = {}
        map_panel.visible = false
    )


func _select_waypoint(landmark: Dictionary) -> void:
    selected_waypoint = landmark.duplicate()
    map_panel.visible = false
    hud_status.text = "Waypoint selected: %s. This is a fictional game location." % landmark["name"]
    _stop_touch_axis()


func _set_touch_axis(axis: Vector2) -> void:
    if _movement_blocked():
        return
    touch_directions[axis] = true
    _refresh_touch_axis()


func _release_touch_axis(axis: Vector2) -> void:
    touch_directions.erase(axis)
    _refresh_touch_axis()


func _refresh_touch_axis() -> void:
    touch_axis = Vector2.ZERO
    for axis in touch_directions:
        touch_axis += axis
    touch_axis = touch_axis.limit_length(1.0)


func _stop_touch_axis() -> void:
    touch_directions.clear()
    touch_axis = Vector2.ZERO


func _zoom(amount: float) -> void:
    camera_distance = clampf(camera_distance + amount, 8.0, 20.0)
    preferences.camera_distance = camera_distance
    _save_preferences()


func _save_preferences() -> void:
    if preferences.write_settings() != OK:
        hud_status.text = "Settings apply now, but could not be saved on this device."


func _create_controls_panel(hud: Control) -> void:
    var toggle := _new_button("CONTROLS", 160)
    toggle.anchor_left = 1.0
    toggle.anchor_right = 1.0
    toggle.offset_left = -195
    toggle.offset_right = -12
    toggle.offset_top = 76
    toggle.offset_bottom = 128
    hud.add_child(toggle)
    controls_panel = PanelContainer.new()
    controls_panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
    controls_panel.offset_left = -210
    controls_panel.offset_right = 210
    controls_panel.offset_top = -245
    controls_panel.offset_bottom = 245
    hud.add_child(controls_panel)
    controls_panel.hide()
    toggle.pressed.connect(func():
        if hud_modal.visible or (map_panel != null and map_panel.visible) or (social != null and social.get("panel").visible):
            return
        controls_panel.visible = not controls_panel.visible
        _stop_touch_axis())
    var rows := VBoxContainer.new()
    var scroll := ScrollContainer.new()
    scroll.custom_minimum_size = Vector2(420, 490)
    scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    controls_panel.add_child(scroll)
    scroll.add_child(rows)
    rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    var title := Label.new()
    title.text = "Movement and camera"
    rows.add_child(title)
    var speed_label := Label.new()
    speed_label.text = "Walking speed (50–100%)"
    rows.add_child(speed_label)
    var speed := HSlider.new()
    speed.name = "WalkingSpeed"
    speed.min_value = 0.5
    speed.max_value = 1.0
    speed.step = 0.05
    speed.value = preferences.movement_scale
    speed.custom_minimum_size = Vector2(360, 44)
    rows.add_child(speed)
    speed.value_changed.connect(func(value: float):
        preferences.movement_scale = value
        _save_preferences())
    var reduced := CheckButton.new()
    reduced.text = "Reduced camera motion"
    reduced.button_pressed = preferences.reduced_motion
    reduced.custom_minimum_size.y = 48
    rows.add_child(reduced)
    reduced.toggled.connect(func(enabled: bool):
        preferences.reduced_motion = enabled
        _save_preferences())
    var zoom_row := HBoxContainer.new()
    rows.add_child(zoom_row)
    var nearer := _new_button("Zoom in (+)", 160)
    var farther := _new_button("Zoom out (−)", 160)
    zoom_row.add_child(nearer)
    zoom_row.add_child(farther)
    nearer.pressed.connect(_zoom.bind(-1.0))
    farther.pressed.connect(_zoom.bind(1.0))
    clock_label = Label.new()
    rows.add_child(clock_label)
    var cycle := CheckButton.new()
    cycle.text = "Day/night cycle (local visuals)"
    cycle.button_pressed = day_cycle.enabled
    cycle.custom_minimum_size.y = 48
    rows.add_child(cycle)
    cycle.toggled.connect(func(enabled: bool):
        day_cycle.enabled = enabled
        preferences.day_cycle_enabled = enabled
        _save_preferences())
    var duration_label := Label.new()
    duration_label.text = "Day length: %d minutes" % int(day_cycle.day_minutes)
    rows.add_child(duration_label)
    var duration := HSlider.new()
    duration.min_value = 10
    duration.max_value = 60
    duration.step = 5
    duration.value = day_cycle.day_minutes
    duration.custom_minimum_size.y = 44
    rows.add_child(duration)
    duration.value_changed.connect(func(value: float):
        day_cycle.day_minutes = value
        preferences.day_minutes = value
        duration_label.text = "Day length: %d minutes" % int(value)
        _save_preferences())
    var graphics := Label.new()
    graphics.text = "Graphics (saved on this device)"
    rows.add_child(graphics)
    var battery := CheckButton.new()
    battery.text = "Battery saver · 30 FPS target"
    battery.button_pressed = preferences.battery_saver
    battery.custom_minimum_size.y = 48
    rows.add_child(battery)
    battery.toggled.connect(func(enabled: bool):
        preferences.battery_saver = enabled
        _apply_graphics()
        _save_preferences())
    var shadows := CheckButton.new()
    shadows.text = "Sun shadows (standard mode)"
    shadows.button_pressed = preferences.shadows_enabled
    shadows.custom_minimum_size.y = 48
    rows.add_child(shadows)
    shadows.toggled.connect(func(enabled: bool):
        preferences.shadows_enabled = enabled
        _apply_graphics()
        _save_preferences())
    var graphics_help := Label.new()
    graphics_help.text = "Standard: 60 FPS target. Saver disables shadows\nand nearby lamp lights; bulbs remain visible."
    rows.add_child(graphics_help)
    var close := _new_button("Done")
    rows.add_child(close)
    close.pressed.connect(func(): controls_panel.hide())


func _apply_graphics() -> void:
    Engine.max_fps = 30 if preferences.battery_saver else 60
    if sunlight != null:
        sunlight.shadow_enabled = preferences.shadows_enabled and not preferences.battery_saver
    _update_daylight()


func _exit_tree() -> void:
    Engine.max_fps = previous_fps_limit


func _orbit(degrees: float) -> void:
    orbit_angle = fposmod(orbit_angle + degrees, 360.0)


func _nearest_place() -> Dictionary:
    # Use the closest frontage, not array order or a point inside the building.
    # Vertical separation prevents selecting a city business from another level.
    if player == null or absf(player.global_position.y) > 3.0:
        return {}
    var nearest: Dictionary = {}
    var best_distance: float = 2.8
    for place in places:
        var entry: Vector3 = Layout.entrance_for(place)
        var distance: float = Vector2(player.global_position.x - entry.x,
            player.global_position.z - entry.z).length()
        if distance < best_distance:
            nearest = place
            best_distance = distance
    for site in futa_landmarks:
        var entry: Vector3 = site["entry"]
        var distance: float = Vector2(player.global_position.x - entry.x,
            player.global_position.z - entry.z).length()
        if distance < best_distance:
            nearest = site
            best_distance = distance
    return nearest


func _interact() -> void:
    if _movement_blocked():
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
                _preview_plant_purchase()
        "Hostel":
            hud_status.text = "Your room has a desk plant." if owns_plant else "Your room is empty. Visit the market."
        "Homes":
            if social != null:
                social.call("open_social")
            hud_status.text = "Social homes: visiting requires the owner's approval."
        "FUTA Campus Café":
            _start_quiz("Campus café", "Three meals cost 70 GC each. A guest pays 250 GC. Change?", ["30 GC", "40 GC", "50 GC"], 1, 140)
        "Campus Delivery Office":
            _start_quiz("Campus courier", "What is the safest way to deliver a parcel?", ["Follow the marked path", "Speed through crossings", "Skip the destination"], 0, 160)
        _:
            if str(place.get("kind", "")) == "futa":
                hud_status.text = "%s: %s" % [place["name"], place["description"]]
            else:
                hud_status.text = "Campus unlocked. More missions will follow."


func _preview_plant_purchase() -> void:
    _stop_touch_axis()
    purchase_pending = true
    for child in hud_modal.get_children():
        hud_modal.remove_child(child)
        child.queue_free()
    var rows := VBoxContainer.new()
    hud_modal.add_child(rows)
    var label := Label.new()
    label.text = "Desk plant · cosmetic\nTotal: 250 fictional GC\nBalance: %d GC · After purchase: %d GC" % [game_cash, game_cash - 250]
    rows.add_child(label)
    var buy := _new_button("Buy for 250 GC")
    rows.add_child(buy)
    buy.pressed.connect(_confirm_plant_purchase)
    var cancel := _new_button("Cancel")
    rows.add_child(cancel)
    cancel.pressed.connect(_close_quiz)
    hud_modal.show()


func _confirm_plant_purchase() -> void:
    if not purchase_pending:
        return
    # Consume the pending intent before mutations to prevent double charging.
    purchase_pending = false
    var place: Dictionary = _nearest_place()
    if owns_plant or game_cash < 250 or place.get("name", "") != "Market":
        hud_status.text = "Purchase unavailable. Return to the market and check your balance."
        _close_quiz()
        return
    game_cash -= 250
    owns_plant = true
    hud_status.text = "Purchased a desk plant for 250 fictional GC."
    _close_quiz()


func _start_quiz(title: String, prompt: String, choices: Array[String], answer: int, payout: int) -> void:
    _stop_touch_axis()
    purchase_pending = false
    active_quiz = {"answer": answer, "payout": payout}
    for child in hud_modal.get_children():
        hud_modal.remove_child(child)
        child.queue_free()

    var content := VBoxContainer.new()
    hud_modal.add_child(content)

    var title_label := Label.new()
    title_label.text = "%s · Reward: %d GC" % [title, payout]
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
    purchase_pending = false
    active_quiz = {}
    hud_modal.visible = false

