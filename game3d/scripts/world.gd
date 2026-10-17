extends Node3D
## BLINK LIFE 3D: Testlab-only native Godot scene.
## Fictional local Game Cash only. No Supabase, payments, BLINK Coins, or cashout.

const Layout = preload("res://scripts/neighborhood_layout.gd")
const Details = preload("res://scripts/neighborhood_details.gd")

const Preferences = preload("res://scripts/player_preferences.gd")

const DayCycle = preload("res://scripts/day_cycle.gd")
const WeatherCycle = preload("res://scripts/weather_cycle.gd")
const WeatherVisuals = preload("res://scripts/weather_visuals.gd")
const NpcWorld = preload("res://scripts/npc_world.gd")
const PublicSpaces = preload("res://scripts/public_spaces.gd")
const SoloProgress = preload("res://scripts/solo_progress.gd")
const SoloHome = preload("res://scripts/solo_home.gd")
const STUDY_CARDS = [
    {"question": "Which number completes 7 + 5?",
     "choices": ["10", "12", "14"], "answer": 1,
     "note": "Seven and five make twelve."},
    {"question": "What is the next number: 2, 4, 6, ...?",
     "choices": ["7", "8", "10"], "answer": 1,
     "note": "The pattern increases by two."},
    {"question": "Which sentence uses the correct plural?",
     "choices": ["Two books", "Two book", "Two bookses"], "answer": 0,
     "note": "The regular plural is books."}
]

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
var home_wall_style: int = 0
var home_floor_style: int = 0
var home_plant_spot: int = 0
var home_plant_custom: bool = false
var home_plant_x: int = SoloHome.PLANT_DEFAULT_X
var home_plant_z: int = SoloHome.PLANT_DEFAULT_Z
var home_plant_rotation: int = 0
var home_furniture: Dictionary = SoloHome.FURNITURE_DEFAULTS.duplicate()
var home_layout_slots: Dictionary = {}
var home_light_mode: int = 0
var home_ceiling_style: int = 0
var home_tv_mode: int = 0
var study_card: int = 0
var study_answered: bool = false
var study_correct: int = 0
var decor_undo: Array[Dictionary] = []
var decor_redo: Array[Dictionary] = []
var progress_store: RefCounted = SoloProgress.new()
var places: Array[Dictionary] = []
var active_quiz: Dictionary = {}
var social: Node3D
var preferences := Preferences.new()
var controls_panel: PanelContainer
var touch_directions: Dictionary = {}
var app_focused: bool = true
var last_safe_position := Vector3(0, 0.15, 0)
var camera_distance: float = 13.0
var day_cycle := DayCycle.new()
var weather := WeatherCycle.new()
var weather_visuals: Node3D
var weather_label: Label
var residents: Node3D
var public_spaces: Node3D
var seated_at: String = ""
var seated_meshes: Array[Dictionary] = []
var solo_room: Node3D
var home_inside: bool = false
var home_return_position := Vector3.ZERO
var sunlight: DirectionalLight3D
var sky_settings: Environment
var clock_label: Label
var neighborhood: Node3D
var purchase_pending: bool = false
var previous_fps_limit: int = 0



func _ready() -> void:
    previous_fps_limit = Engine.max_fps
    preferences.read_settings()
    var recovered: Dictionary = progress_store.call("load_state")
    game_cash = int(recovered["cash"])
    completed_jobs = int(recovered["jobs"])
    owns_plant = bool(recovered["plant"])
    home_wall_style = int(recovered["wall_style"])
    home_floor_style = int(recovered["floor_style"])
    home_plant_spot = int(recovered["plant_spot"])
    home_plant_custom = bool(recovered["plant_custom"])
    home_plant_x = int(recovered["plant_x"])
    home_plant_z = int(recovered["plant_z"])
    home_plant_rotation = int(recovered["plant_rotation"])
    for key in SoloHome.FURNITURE_DEFAULTS:
        home_furniture[key] = int(recovered[key])
    home_layout_slots = recovered["layout_slots"].duplicate(true)
    home_light_mode = int(recovered["light_mode"])
    home_ceiling_style = int(recovered["ceiling_style"])
    home_tv_mode = int(recovered["tv_mode"])
    camera_distance = preferences.camera_distance
    day_cycle.enabled = preferences.day_cycle_enabled
    day_cycle.day_minutes = preferences.day_minutes
    weather.mode = preferences.weather_mode
    _create_lights()
    _create_city()
    weather_visuals = WeatherVisuals.new()
    add_child(weather_visuals)
    var details := Details.new()
    add_child(details)
    details.configure(self)
    neighborhood = details
    public_spaces = PublicSpaces.new()
    add_child(public_spaces)
    public_spaces.call("configure", self)
    residents = NpcWorld.new()
    add_child(residents)
    _create_player()
    _create_camera()
    _create_hud()
    if not str(progress_store.get("warning")).is_empty():
        hud_status.text = str(progress_store.get("warning"))
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
        weather.advance(delta)
    _update_daylight()
    if weather_visuals != null and player != null:
        weather_visuals.call("update_weather", player.global_position, weather.condition(), delta if app_focused else 0.0)
    if weather_label != null:
        weather_label.text = "Fictional local weather: " + WeatherCycle.label_for(weather.condition())
    if residents != null and player != null and app_focused:
        residents.call("update_residents", player.global_position, float(day_cycle.sample()["hour"]), weather.condition(), preferences.battery_saver, delta)
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
    var nearby_public: Dictionary = _nearest_public_spot()
    var nearby: Dictionary = _nearest_place()
    if home_inside:
        hud_location.text = "Your solo room • INTERACT to decorate • F to leave"
    elif not seated_at.is_empty():
        hud_location.text = "Seated at %s • INTERACT or F to stand" % seated_at
    elif not nearby_public.is_empty():
        hud_location.text = "%s • INTERACT" % str(nearby_public["name"])
    elif nearby.is_empty():
        hud_location.text = "Explore the 3D city — approach a marked building"
    else:
        hud_location.text = "%s • %s • INTERACT" % [nearby["name"], nearby["address"]]


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
    if player.is_on_floor() and absf(player.global_position.x) < 25.0 and absf(player.global_position.z) < 19.0:
        last_safe_position = player.global_position


func _movement_blocked() -> bool:
    if not app_focused or not seated_at.is_empty():
        return true
    if hud_modal != null and hud_modal.visible:
        return true
    if controls_panel != null and controls_panel.visible:
        return true
    if social != null and social.get("panel") != null and social.get("panel").visible:
        return true
    var focused: Control = get_viewport().gui_get_focus_owner()
    return focused is LineEdit or focused is TextEdit


func _recover_if_needed() -> void:
    var position: Vector3 = player.global_position
    if home_inside:
        if not position.is_finite() or position.y < 27.0 or position.y > 40.0 or absf(position.x) > 5.0 or absf(position.z) > 5.0:
            player.global_position = solo_room.call("spawn_position")
            player.velocity = Vector3.ZERO
            _stop_touch_axis()
            hud_status.text = "Returned to your safe starter room."
        return
    if not position.is_finite() or position.y < -3.0 or position.y > 12.0 or absf(position.x) > 28.0 or absf(position.z) > 22.0:
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
            if home_inside and not hud_modal.visible and not controls_panel.visible:
                _exit_solo_home()
                return
            if not seated_at.is_empty():
                _leave_seat()
                return
            if controls_panel != null and controls_panel.visible:
                controls_panel.hide()
            elif hud_modal != null and hud_modal.visible:
                _close_quiz()
            elif social != null and social.get("panel").visible:
                social.get("panel").hide()
            else:
                get_tree().quit()
            _stop_touch_axis()
            return
        if not seated_at.is_empty() and event.keycode == KEY_F:
            _leave_seat()
            return
        if home_inside and event.keycode == KEY_F:
            _exit_solo_home()
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

    weather_label = Label.new()
    weather_label.name = "WeatherStatus"
    weather_label.position = Vector2(18, 108)
    weather_label.add_theme_font_size_override("font_size", 14)
    hud.add_child(weather_label)

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
        if hud_modal.visible or (social != null and social.get("panel").visible):
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
    var weather_heading := Label.new()
    weather_heading.text = "Local weather preview (visual only, no penalties)"
    rows.add_child(weather_heading)
    var weather_choice := OptionButton.new()
    weather_choice.name = "WeatherMode"
    for caption in ["Automatic cycle", "Clear", "Light rain", "Tropical rain"]:
        weather_choice.add_item(caption)
    weather_choice.select(WeatherCycle.MODES.find(preferences.weather_mode))
    rows.add_child(weather_choice)
    weather_choice.item_selected.connect(_set_weather_mode)
    var save_help := Label.new()
    save_help.text = "Solo jobs and desk plant save locally after confirmation.\nThey never become BLINK Coins or shared online rewards."
    rows.add_child(save_help)
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


func _set_weather_mode(index: int) -> void:
    if index < 0 or index >= WeatherCycle.MODES.size():
        return
    weather.mode = WeatherCycle.MODES[index]
    preferences.weather_mode = weather.mode
    _save_preferences()


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
    if home_inside or player == null or absf(player.global_position.y) > 3.0:
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
    return nearest


func _nearest_public_spot() -> Dictionary:
    if home_inside or player == null or public_spaces == null:
        return {}
    return PublicSpaces.nearest_spot(player.global_position)


func _interact_public_spot(spot: Dictionary) -> void:
    match str(spot["type"]):
        "bench":
            if social != null and bool(social.get("is_online")):
                hud_status.text = "Public seating is currently available in offline preview only."
                return
            _sit_on_bench(str(spot["name"]))
        "bin":
            hud_status.text = "You found a public waste bin. Help keep the city clean."
        "stall":
            hud_status.text = "%s • %s (Preview only; no purchases.)" % [spot["name"], spot["description"]]
        "board":
            _open_community_board()


func _sit_on_bench(bench_name: String) -> void:
    if player == null or not seated_at.is_empty():
        return
    _stop_touch_axis()
    seated_at = bench_name
    seated_meshes.clear()
    for part in player.get_children():
        if part is MeshInstance3D:
            var visual: MeshInstance3D = part
            seated_meshes.append({"node": visual, "transform": visual.transform})
            visual.position.y -= 0.25
            if visual.position.y < 0.30:
                visual.rotation.x = deg_to_rad(-60.0)
                visual.position.z -= 0.15
    player.velocity = Vector3.ZERO
    hud_status.text = "Relaxing on %s. INTERACT or F to stand." % bench_name


func _leave_seat() -> void:
    if seated_at.is_empty():
        return
    for saved in seated_meshes:
        var visual: MeshInstance3D = saved["node"]
        if is_instance_valid(visual):
            visual.transform = saved["transform"]
    seated_meshes.clear()
    seated_at = ""
    if player != null:
        player.velocity = Vector3.ZERO
    hud_status.text = "Back on your feet."


func _open_community_board() -> void:
    _stop_touch_axis()
    for child in hud_modal.get_children():
        hud_modal.remove_child(child)
        child.queue_free()
    var rows := VBoxContainer.new()
    hud_modal.add_child(rows)
    var heading := Label.new()
    heading.text = "Community opportunities (fictional Game Cash)"
    rows.add_child(heading)
    for opportunity in ["Café • cashier task: 120 GC", "Tech hub • tech support: 180 GC", "Delivery depot • dispatch: 150 GC"]:
        var item := Label.new()
        item.text = opportunity
        rows.add_child(item)
    var directions := Label.new()
    directions.text = "Walk to a listed building to take a job. No automatic payouts."
    directions.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    rows.add_child(directions)
    var close := _new_button("Close board")
    rows.add_child(close)
    close.pressed.connect(_close_quiz)
    hud_modal.show()


func _interact() -> void:
    if home_inside:
        if not _movement_blocked():
            _show_home_decor()
        return
    if not seated_at.is_empty():
        _leave_seat()
        return
    if _movement_blocked():
        return
    var public_spot: Dictionary = _nearest_public_spot()
    if not public_spot.is_empty():
        _interact_public_spot(public_spot)
        return
    var place: Dictionary = _nearest_place()
    if place.is_empty():
        var greeting: String = ""
        if residents != null:
            greeting = str(residents.call("nearby_greeting", player.global_position))
        hud_status.text = greeting if not greeting.is_empty() else "Move closer to a building or NPC before interacting."
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
            if social != null and bool(social.get("is_online")):
                social.call("open_social")
                hud_status.text = "Social homes: visiting requires the owner's approval."
            else:
                _show_home_choice()
        _:
            hud_status.text = "Campus unlocked. More missions will follow."



func _show_home_choice() -> void:
    _stop_touch_axis()
    _close_quiz()
    for child in hud_modal.get_children():
        hud_modal.remove_child(child)
        child.queue_free()
    var choices := VBoxContainer.new()
    hud_modal.add_child(choices)
    var title := Label.new()
    title.text = "Homes • Private solo demo or approved multiplayer"
    title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    choices.add_child(title)
    var solo := _new_button("Enter free starter room (offline)")
    choices.add_child(solo)
    solo.pressed.connect(_enter_solo_home)
    var online := _new_button("Open multiplayer homes")
    choices.add_child(online)
    online.pressed.connect(func():
        _close_quiz()
        if social != null:
            social.call("open_social"))
    var leave := _new_button("Stay in city")
    choices.add_child(leave)
    leave.pressed.connect(_close_quiz)
    hud_modal.show()


func _refresh_home_camera() -> void:
    if follow_camera == null or player == null:
        return
    var radians: float = deg_to_rad(orbit_angle)
    follow_camera.global_position = player.global_position + Vector3(
        sin(radians) * camera_distance, camera_distance * 0.846, cos(radians) * camera_distance
    )
    follow_camera.look_at(player.global_position + Vector3.UP * 1.25)


func _enter_solo_home() -> void:
    if player == null or home_inside:
        return
    if social != null and bool(social.get("is_online")):
        hud_status.text = "Shared rooms use server-approved entry; solo mode is unavailable."
        _close_quiz()
        return
    if str(_nearest_place().get("id", "")) != "homes":
        hud_status.text = "Approach the Homes entrance to open your solo room."
        _close_quiz()
        return
    _close_quiz()
    _stop_touch_axis()
    home_return_position = player.global_position
    if solo_room == null:
        solo_room = SoloHome.new()
        add_child(solo_room)
        solo_room.call("configure")
    solo_room.call("update_owned_decor", owns_plant)
    solo_room.call("apply_decoration", home_wall_style, home_floor_style,
        home_plant_spot, home_plant_custom, home_plant_x, home_plant_z,
        home_plant_rotation, home_furniture)
    solo_room.call("apply_light_mode", home_light_mode)
    solo_room.call("apply_ceiling_style", home_ceiling_style)
    solo_room.call("apply_tv_mode", home_tv_mode)
    study_card = 0
    study_answered = false
    study_correct = 0
    decor_undo.clear()
    decor_redo.clear()
    home_inside = true
    player.global_position = solo_room.call("spawn_position")
    player.velocity = Vector3.ZERO
    last_safe_position = player.global_position
    _refresh_home_camera()
    hud_status.text = "Welcome home. Your desk plant appears here after a solo purchase."


func _exit_solo_home() -> void:
    if not home_inside:
        return
    _close_quiz()
    _stop_touch_axis()
    home_inside = false
    player.global_position = home_return_position
    player.velocity = Vector3.ZERO
    last_safe_position = player.global_position
    if solo_room != null:
        solo_room.queue_free()
        solo_room = null
    _refresh_home_camera()
    hud_status.text = "Returned to the city. Your solo progress stays saved."



func _home_save_snapshot() -> Dictionary:
    return {"version": 1, "cash": game_cash, "jobs": completed_jobs, "plant": owns_plant,
        "wall_style": home_wall_style, "floor_style": home_floor_style,
        "plant_spot": home_plant_spot, "plant_custom": home_plant_custom,
        "plant_x": home_plant_x, "plant_z": home_plant_z,
        "plant_rotation": home_plant_rotation,
        "chair_x": int(home_furniture["chair_x"]),
        "chair_z": int(home_furniture["chair_z"]),
        "chair_rotation": int(home_furniture["chair_rotation"]),
        "table_x": int(home_furniture["table_x"]),
        "table_z": int(home_furniture["table_z"]),
        "table_rotation": int(home_furniture["table_rotation"]),
        "bed_layout": int(home_furniture["bed_layout"]),
        "wardrobe_layout": int(home_furniture["wardrobe_layout"]),
        "layout_slots": home_layout_slots.duplicate(true), "light_mode": home_light_mode,
        "ceiling_style": home_ceiling_style, "tv_mode": home_tv_mode}


func _show_home_decor() -> void:
    if not home_inside or hud_modal == null:
        return
    _stop_touch_axis()
    _close_quiz()
    for child in hud_modal.get_children():
        hud_modal.remove_child(child)
        child.queue_free()
    var scroll := ScrollContainer.new()
    scroll.custom_minimum_size = Vector2(508, 278)
    scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    hud_modal.add_child(scroll)
    var rows := VBoxContainer.new()
    scroll.add_child(rows)
    rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    var heading := Label.new()
    heading.text = "Free solo-room decoration • saved locally"
    rows.add_child(heading)
    var study := _new_button("Study desk • free quick practice")
    rows.add_child(study)
    study.pressed.connect(_show_study_panel)
    var lighting := _new_button("Lighting: " + ["Warm", "Bright", "Dim"][home_light_mode])
    rows.add_child(lighting)
    lighting.pressed.connect(_cycle_home_light)
    var ceiling := _new_button("Ceiling accents: " + ["Ivory", "Blue-grey", "Sand"][home_ceiling_style])
    rows.add_child(ceiling)
    ceiling.pressed.connect(_cycle_home_ceiling)
    var home_screen := _new_button("Room screen: " + ["Off", "Nature still", "Campus notice"][home_tv_mode])
    rows.add_child(home_screen)
    home_screen.pressed.connect(_cycle_home_tv)
    var wall := _new_button("Wall palette %d/3" % (home_wall_style + 1))
    rows.add_child(wall)
    wall.pressed.connect(_cycle_home_decoration.bind("wall_style"))
    var floor := _new_button("Floor colour %d/3" % (home_floor_style + 1))
    rows.add_child(floor)
    floor.pressed.connect(_cycle_home_decoration.bind("floor_style"))
    var spot := _new_button("Plant preset %d/3" % (home_plant_spot + 1))
    spot.disabled = not owns_plant
    rows.add_child(spot)
    spot.pressed.connect(_cycle_home_decoration.bind("plant_spot"))
    var caption := Label.new()
    caption.text = "Plant floor grid: (%d, %d) • rotation: %d°" % [
        home_plant_x, home_plant_z, home_plant_rotation * 90]
    rows.add_child(caption)
    var move_plant := _new_button("Start floor-grid placement" if not home_plant_custom else "Use floor-grid placement")
    move_plant.disabled = not owns_plant
    rows.add_child(move_plant)
    move_plant.pressed.connect(_home_move_plant.bind(0, 0))
    for choices in [
        [["← Move", -1, 0], ["Move →", 1, 0]],
        [["↑ Move", 0, -1], ["Move ↓", 0, 1]]
    ]:
        var row := HBoxContainer.new()
        rows.add_child(row)
        for choice in choices:
            var button := _new_button(str(choice[0]), 200)
            button.disabled = not owns_plant
            row.add_child(button)
            button.pressed.connect(_home_move_plant.bind(int(choice[1]), int(choice[2])))
    var rotate := _new_button("Rotate plant 90°")
    rotate.disabled = not owns_plant
    rows.add_child(rotate)
    rotate.pressed.connect(_home_rotate_plant)
    for kind in ["chair", "table"]:
        var title := Label.new()
        title.text = "%s grid (%d, %d) • angle %d°" % [
            kind.capitalize(), int(home_furniture[kind + "_x"]),
            int(home_furniture[kind + "_z"]), int(home_furniture[kind + "_rotation"]) * 90]
        rows.add_child(title)
        var actions := HBoxContainer.new()
        rows.add_child(actions)
        for choice in [["←", -1, 0], ["→", 1, 0], ["↑", 0, -1], ["↓", 0, 1]]:
            var arrow := _new_button(kind.capitalize() + " " + str(choice[0]), 108)
            actions.add_child(arrow)
            arrow.pressed.connect(_home_move_furniture.bind(kind, int(choice[1]), int(choice[2])))
        var turn := _new_button("Rotate %s 90°" % kind)
        rows.add_child(turn)
        turn.pressed.connect(_home_rotate_furniture.bind(kind))
    var home_layout_title := Label.new()
    home_layout_title.text = "Bed and wardrobe • curated safe placements"
    rows.add_child(home_layout_title)
    for fixture in ["bed", "wardrobe"]:
        var fixture_button := _new_button("%s position %d/3" % [
            fixture.capitalize(), int(home_furniture[fixture + "_layout"]) + 1])
        fixture_button.pressed.connect(_cycle_home_fixture.bind(fixture))
        rows.add_child(fixture_button)
    var plans_label := Label.new()
    plans_label.text = "Room layouts (sets bed, wardrobe, chair and table)"
    rows.add_child(plans_label)
    for index in range(SoloHome.ROOM_PLANS.size()):
        var preset: Dictionary = SoloHome.ROOM_PLANS[index]
        var plan := _new_button(str(preset["name"]))
        rows.add_child(plan)
        plan.pressed.connect(_apply_room_plan.bind(index))
    var custom_heading := Label.new()
    custom_heading.text = "My saved layouts (2 local slots)"
    rows.add_child(custom_heading)
    for index in range(1, 3):
        var slot_key: String = "slot_%d" % index
        var actions := HBoxContainer.new()
        rows.add_child(actions)
        var save := _new_button("Save layout %d" % index, 210)
        save.pressed.connect(_save_home_layout_slot.bind(slot_key))
        actions.add_child(save)
        var load := _new_button("Apply layout %d" % index, 210)
        load.disabled = not home_layout_slots.has(slot_key)
        load.pressed.connect(_load_home_layout_slot.bind(slot_key))
        actions.add_child(load)
    var history_row := HBoxContainer.new()
    rows.add_child(history_row)
    var undo := _new_button("Undo", 210)
    undo.disabled = decor_undo.is_empty()
    undo.pressed.connect(_undo_home_decor)
    history_row.add_child(undo)
    var redo := _new_button("Redo", 210)
    redo.disabled = decor_redo.is_empty()
    redo.pressed.connect(_redo_home_decor)
    history_row.add_child(redo)
    var note := Label.new()
    note.text = "Free starter furniture: chair + table + owned plant.\nOccupied grid cells and your doorway stay clear."
    rows.add_child(note)
    var close := _new_button("Done • F to leave room")
    rows.add_child(close)
    close.pressed.connect(_close_quiz)
    hud_modal.show()


func _apply_home_decor(candidate: Dictionary) -> bool:
    if not home_inside or solo_room == null or (social != null and bool(social.get("is_online"))):
        return false
    if SoloProgress.valid_state(candidate).is_empty():
        return false
    # The decor editor may only change layout fields; undo cannot rewind
    # currency, job count or ownership even if future activities are added.
    if int(candidate["cash"]) != game_cash or int(candidate["jobs"]) != completed_jobs or bool(candidate["plant"]) != owns_plant:
        return false
    if not bool(progress_store.call("save_state", candidate)):
        hud_status.text = "Decoration not saved: " + str(progress_store.get("warning"))
        return false
    # No visual or currency changes happen before a successful disk commit.
    home_wall_style = int(candidate["wall_style"])
    home_floor_style = int(candidate["floor_style"])
    home_plant_spot = int(candidate["plant_spot"])
    home_plant_custom = bool(candidate["plant_custom"])
    home_plant_x = int(candidate["plant_x"])
    home_plant_z = int(candidate["plant_z"])
    home_plant_rotation = int(candidate["plant_rotation"])
    for key in SoloHome.FURNITURE_DEFAULTS:
        home_furniture[key] = int(candidate[key])
    home_layout_slots = candidate["layout_slots"].duplicate(true)
    home_light_mode = int(candidate["light_mode"])
    home_ceiling_style = int(candidate["ceiling_style"])
    home_tv_mode = int(candidate["tv_mode"])
    solo_room.call("apply_light_mode", home_light_mode)
    solo_room.call("apply_ceiling_style", home_ceiling_style)
    solo_room.call("apply_tv_mode", home_tv_mode)
    solo_room.call("apply_decoration", home_wall_style, home_floor_style,
        home_plant_spot, home_plant_custom, home_plant_x, home_plant_z,
        home_plant_rotation, home_furniture)
    hud_status.text = "Free solo room changes saved."
    return true


func _commit_home_decor(candidate: Dictionary) -> void:
    var previous: Dictionary = _home_save_snapshot()
    if previous == candidate:
        return
    if not _apply_home_decor(candidate):
        hud_status.text = "Decoration cannot overlap furniture or block the doorway."
        return
    decor_undo.append(previous)
    if decor_undo.size() > SoloHome.HISTORY_LIMIT:
        decor_undo.pop_front()
    decor_redo.clear()
    _show_home_decor()


func _cycle_home_decoration(field: String) -> void:
    if field not in ["wall_style", "floor_style", "plant_spot"]:
        return
    if field == "plant_spot" and not owns_plant:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate[field] = (int(candidate[field]) + 1) % 3
    if field == "plant_spot":
        candidate["plant_custom"] = false
    _commit_home_decor(candidate)


func _cycle_home_ceiling() -> void:
    if not home_inside:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate["ceiling_style"] = (home_ceiling_style + 1) % 3
    _commit_home_decor(candidate)


func _cycle_home_tv() -> void:
    if not home_inside:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate["tv_mode"] = (home_tv_mode + 1) % 3
    _commit_home_decor(candidate)


func _cycle_home_light() -> void:
    if not home_inside:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate["light_mode"] = (home_light_mode + 1) % 3
    _commit_home_decor(candidate)



## Optional educational mini-activity, entirely local and reward-free.
func _show_study_panel() -> void:
    if not home_inside or hud_modal == null:
        return
    _stop_touch_axis()
    _close_quiz()
    for child in hud_modal.get_children():
        hud_modal.remove_child(child)
        child.queue_free()
    var rows := VBoxContainer.new()
    hud_modal.add_child(rows)
    var heading := Label.new()
    heading.text = "Study desk • practice %d/%d" % [study_card + 1, STUDY_CARDS.size()]
    rows.add_child(heading)
    var card: Dictionary = STUDY_CARDS[study_card]
    var prompt := Label.new()
    prompt.text = str(card["question"])
    prompt.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    rows.add_child(prompt)
    for option in range(card["choices"].size()):
        var answer := _new_button(str(card["choices"][option]))
        answer.disabled = study_answered
        rows.add_child(answer)
        answer.pressed.connect(_submit_study_answer.bind(option))
    if study_answered:
        var note := Label.new()
        note.text = str(card["note"])
        note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
        rows.add_child(note)
        var next := _new_button("Next question")
        rows.add_child(next)
        next.pressed.connect(_next_study_card)
    var count := Label.new()
    count.text = "Correct this visit: %d" % study_correct
    rows.add_child(count)
    var close := _new_button("Back to room controls")
    rows.add_child(close)
    close.pressed.connect(_show_home_decor)
    hud_modal.show()


func _submit_study_answer(choice: int) -> bool:
    if not home_inside or study_answered:
        return false
    var card: Dictionary = STUDY_CARDS[study_card]
    if choice < 0 or choice >= card["choices"].size():
        return false
    study_answered = true
    var correct: bool = choice == int(card["answer"])
    if correct:
        study_correct += 1
    hud_status.text = "Correct practice answer!" if correct else "Try the next one."
    _show_study_panel()
    return correct


func _next_study_card() -> void:
    if not home_inside or not study_answered:
        return
    study_card = (study_card + 1) % STUDY_CARDS.size()
    study_answered = false
    _show_study_panel()


func _home_move_plant(delta_x: int, delta_z: int) -> void:
    if not owns_plant or not home_inside:
        return
    if absi(delta_x) + absi(delta_z) > 1:
        return
    var candidate: Dictionary = _home_save_snapshot()
    var x: int = home_plant_x + delta_x
    var z: int = home_plant_z + delta_z
    if not SoloHome.floor_cell_allowed(x, z):
        hud_status.text = "That plant position is blocked by a wall or furniture."
        return
    candidate["plant_custom"] = true
    candidate["plant_x"] = x
    candidate["plant_z"] = z
    _commit_home_decor(candidate)


func _home_rotate_plant() -> void:
    if not owns_plant or not home_inside:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate["plant_rotation"] = (home_plant_rotation + 1) % 4
    _commit_home_decor(candidate)


func _home_move_furniture(kind: String, dx: int, dz: int) -> void:
    if kind not in ["chair", "table"] or not home_inside:
        return
    if absi(dx) + absi(dz) != 1:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate[kind + "_x"] = int(candidate[kind + "_x"]) + dx
    candidate[kind + "_z"] = int(candidate[kind + "_z"]) + dz
    _commit_home_decor(candidate)


func _home_rotate_furniture(kind: String) -> void:
    if kind not in ["chair", "table"] or not home_inside:
        return
    var candidate: Dictionary = _home_save_snapshot()
    candidate[kind + "_rotation"] = (int(candidate[kind + "_rotation"]) + 1) % 4
    _commit_home_decor(candidate)


func _cycle_home_fixture(kind: String) -> void:
    if kind not in ["bed", "wardrobe"] or not home_inside:
        return
    var candidate: Dictionary = _home_save_snapshot()
    var key: String = kind + "_layout"
    var options: Array = SoloHome.BED_PLACEMENTS if kind == "bed" else SoloHome.WARDROBE_PLACEMENTS
    candidate[key] = (int(candidate[key]) + 1) % options.size()
    _commit_home_decor(candidate)


func _apply_room_plan(index: int) -> void:
    if not home_inside or index < 0 or index >= SoloHome.ROOM_PLANS.size():
        return
    var candidate: Dictionary = _home_save_snapshot()
    var plan: Dictionary = SoloHome.ROOM_PLANS[index]
    for key in SoloHome.FURNITURE_DEFAULTS:
        candidate[key] = int(plan.get(key, SoloHome.FURNITURE_DEFAULTS[key]))
    # Plants are put back in the existing display preset to keep room plans
    # safe without requiring the user to own any paid cosmetics.
    candidate["plant_custom"] = false
    candidate["plant_spot"] = 0
    _commit_home_decor(candidate)


func _save_home_layout_slot(slot: String) -> void:
    if not home_inside or not SoloProgress.SLOT_NAMES.has(slot):
        return
    var candidate: Dictionary = _home_save_snapshot()
    var bookmark: Dictionary = {}
    for key in SoloProgress.DECOR_KEYS:
        bookmark[key] = candidate[key]
    var slots: Dictionary = candidate["layout_slots"].duplicate(true)
    slots[slot] = bookmark
    candidate["layout_slots"] = slots
    # Saving a bookmark does not change the scene and does not consume
    # undo history; money and item ownership are left untouched.
    if _apply_home_decor(candidate):
        hud_status.text = "Your private decoration bookmark was saved."
        _show_home_decor()


func _load_home_layout_slot(slot: String) -> void:
    if not home_inside or not SoloProgress.SLOT_NAMES.has(slot):
        return
    if not home_layout_slots.has(slot):
        return
    var candidate: Dictionary = _home_save_snapshot()
    var layout: Dictionary = home_layout_slots[slot]
    for key in SoloProgress.DECOR_KEYS:
        candidate[key] = layout[key]
    # Loading a layout only affects cosmetics. The current wallet and
    # ownership never come from a bookmarked object.
    _commit_home_decor(candidate)


func _undo_home_decor() -> void:
    if not home_inside or decor_undo.is_empty():
        return
    var previous: Dictionary = decor_undo.pop_back()
    var current: Dictionary = _home_save_snapshot()
    previous["layout_slots"] = home_layout_slots.duplicate(true)
    if not _apply_home_decor(previous):
        decor_undo.append(previous)
        return
    decor_redo.append(current)
    _show_home_decor()


func _redo_home_decor() -> void:
    if not home_inside or decor_redo.is_empty():
        return
    var next: Dictionary = decor_redo.pop_back()
    var current: Dictionary = _home_save_snapshot()
    next["layout_slots"] = home_layout_slots.duplicate(true)
    if not _apply_home_decor(next):
        decor_redo.append(next)
        return
    decor_undo.append(current)
    if decor_undo.size() > SoloHome.HISTORY_LIMIT:
        decor_undo.pop_front()
    _show_home_decor()


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
    if social != null and bool(social.get("is_online")):
        hud_status.text = "Solo market purchases are disabled during a shared session."
        _close_quiz()
        return
    var after_purchase: Dictionary = _home_save_snapshot()
    after_purchase["cash"] = game_cash - 250
    after_purchase["plant"] = true
    if not bool(progress_store.call("save_state", after_purchase)):
        hud_status.text = "Purchase not completed: " + str(progress_store.get("warning"))
        _close_quiz()
        return
    game_cash -= 250
    owns_plant = true
    hud_status.text = "Saved: purchased a desk plant for 250 fictional GC."
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

    if social != null and bool(social.get("is_online")):
        hud_status.text = "Solo rewards are disabled in shared sessions; no online currency is awarded."
        _close_quiz()
        return
    var payout: int = int(active_quiz["payout"])
    var after_job: Dictionary = _home_save_snapshot()
    after_job["cash"] = game_cash + payout
    after_job["jobs"] = completed_jobs + 1
    if not bool(progress_store.call("save_state", after_job)):
        hud_status.text = "Job reward not saved: " + str(progress_store.get("warning"))
        return
    game_cash += payout
    completed_jobs += 1
    hud_status.text = "Saved: earned %d fictional Game Cash." % payout
    _close_quiz()


func _close_quiz() -> void:
    purchase_pending = false
    active_quiz = {}
    hud_modal.visible = false

