extends SceneTree

# Headless regression checks for BLINK LIFE's native Godot 3D scene.
# This is a runtime smoke test, not a substitute for actual Android hardware testing.

var failed: bool = false


func _assert_true(condition: bool, explanation: String) -> void:
    if not condition:
        failed = true
        printerr("BLINK LIFE SMOKE FAILURE: %s" % explanation)


func _run() -> void:
    var packed: PackedScene = load("res://scenes/main.tscn") as PackedScene
    _assert_true(packed != null, "Main 3D scene must import")
    if packed == null:
        quit(1)
        return

    var world: Node3D = packed.instantiate() as Node3D
    _assert_true(world != null, "Main scene must instantiate as Node3D")
    if world == null:
        quit(1)
        return
    root.add_child(world)
    await process_frame
    await physics_frame

    var player: CharacterBody3D = world.get_node_or_null("Player") as CharacterBody3D
    var camera: Camera3D = world.get_node_or_null("OrbitCamera") as Camera3D
    _assert_true(player != null, "Native 3D scene must contain player physics body")
    _assert_true(camera != null, "Native 3D scene must contain a perspective camera")
    if player != null:
        _assert_true(player.get_child_count() >= 4, "Player should have real 3D visual geometry")
    if camera != null:
        _assert_true(camera.current, "Perspective camera must be the active scene camera")

    var places: Array = world.get("places")
    _assert_true(places.size() == 7, "Original seven businesses must stay unchanged")
    var landmarks: Array = world.get("futa_landmarks")
    _assert_true(landmarks.size() == 19, "FUTA-inspired map must contain 19 named landmarks")
    var name_keys: Dictionary = {}
    var position_keys: Dictionary = {}
    for site in landmarks:
        _assert_true(site.has("entry") and site.has("position"), "Each new building needs a waypoint and walkable frontage")
        _assert_true(str(site.get("kind", "")) == "futa", "New destinations must be clearly marked fictional")
        _assert_true(not name_keys.has(site["name"]), "Campus landmark names must be unique")
        name_keys[site["name"]] = true
        var p: Vector3 = site["position"]
        var coordinate := "%s:%s" % [p.x, p.z]
        _assert_true(not position_keys.has(coordinate), "Campus building coordinates must be unique")
        position_keys[coordinate] = true
    _assert_true(world.get_node_or_null("FutaGameGround") is StaticBody3D, "Campus requires solid physical ground")
    _assert_true(world.get_node_or_null("FutaEasternGround") is StaticBody3D, "Campus south-east roads need contiguous ground")
    _assert_true(world.get_node_or_null("CampusLibrary") is StaticBody3D, "Campus library must have 3D collision")
    var map_panel: Control = world.get("map_panel")
    _assert_true(map_panel != null and not map_panel.visible, "Campus map must start closed")
    if map_panel != null:
        map_panel.show()
        _assert_true(bool(world.call("_movement_blocked")), "Map overlay must block avatar motion")
        map_panel.hide()
    if landmarks.size() > 0:
        world.call("_select_waypoint", landmarks[0])
        _assert_true(world.get("selected_waypoint")["name"] == landmarks[0]["name"], "Selecting map waypoints must persist the target")
        _assert_true(not world.get("map_panel").visible, "Destination selection closes the map overlay")

    var details: Node = world.get_node_or_null("NeighborhoodDetails")
    _assert_true(details != null, "Shared neighborhood details must be present")
    if details != null:
        for place in places:
            _assert_true(details.get_node_or_null("Address_" + str(place["id"])) != null, "Every destination needs its visible address")
        _assert_true(details.get_node_or_null("Street_market_walk") != null, "Market Walk must have a street sign")
        _assert_true(details.get_node_or_null("Street_campus_way") != null, "Campus Way must have a street sign")
    if player != null:
        var layout: Script = load("res://scripts/neighborhood_layout.gd")
        for place in places:
            player.global_position = layout.entrance_for(place) + Vector3.UP * 0.15
            _assert_true(world.call("_nearest_place").get("id", "") == place["id"], "Every approach must select the correct destination")
        player.global_position = Vector3(0, 0.15, 0)
        _assert_true(world.call("_nearest_place").is_empty(), "The road intersection is not a building interaction")
        player.global_position = Vector3(-18, 8, -3.25)
        _assert_true(world.call("_nearest_place").is_empty(), "Interaction should not reach between floors")
        player.global_position = Vector3.ZERO

    var initial_cash: int = int(world.get("game_cash"))
    _assert_true(initial_cash == 500, "Prototype Game Cash balance should start at 500")

    world.call("_orbit", 35.0)
    _assert_true(absf(float(world.get("orbit_angle")) - 75.0) < 0.001, "Camera rotation should work")

    world.call("_set_touch_axis", Vector2.RIGHT)
    _assert_true(world.get("touch_axis") == Vector2.RIGHT, "Touch movement input must be accepted")
    world.call("_stop_touch_axis")
    _assert_true(world.get("touch_axis") == Vector2.ZERO, "Releasing input should stop movement")

    if player != null:
        player.global_position = Vector3(-18.0, 0.5, -6.0)
        world.call("_interact")
        var quiz: Dictionary = world.get("active_quiz")
        _assert_true(not quiz.is_empty(), "Café interaction should open an interactive job")
        _assert_true(bool(world.get("hud_modal").visible), "Job answers should be accessible in the game")
        world.call("_submit_quiz", 0)
        _assert_true(int(world.get("game_cash")) == 500, "Wrong answers must not pay Game Cash")
        world.call("_submit_quiz", 1)
        _assert_true(int(world.get("game_cash")) == 620, "Correct café answer must award 120 fictional GC")
        _assert_true(int(world.get("completed_jobs")) == 1, "Completed job count must increment")
        _assert_true(world.get("active_quiz").is_empty(), "Completed job should close the quiz")

        player.global_position = Vector3(-18.0, 0.5, 6.0)
        world.call("_interact")
        _assert_true(not bool(world.get("owns_plant")), "Preview must not buy without confirmation")
        _assert_true(int(world.get("game_cash")) == 620, "Preview must not charge")
        world.call("_close_quiz")
        world.call("_confirm_plant_purchase")
        _assert_true(int(world.get("game_cash")) == 620, "Cancelled purchase cannot be confirmed later")
        world.call("_interact")
        world.set("game_cash", 100)
        world.call("_confirm_plant_purchase")
        _assert_true(int(world.get("game_cash")) == 100, "Confirmation must recheck available funds")
        _assert_true(not bool(world.get("owns_plant")), "Insufficient funds must not grant the item")
        world.set("game_cash", 620)
        world.call("_interact")
        world.call("_confirm_plant_purchase")
        _assert_true(bool(world.get("owns_plant")), "Confirmed market purchase should grant the plant")
        world.call("_confirm_plant_purchase")
        _assert_true(int(world.get("game_cash")) == 370, "Market purchase should cost 250 fictional GC")
        world.call("_interact")
        _assert_true(int(world.get("game_cash")) == 370, "Purchasing a second time must not charge again")

    # Controls must never move an avatar while UI input owns the interaction.
    world.call("_set_touch_axis", Vector2.RIGHT)
    world.call("_set_touch_axis", Vector2.UP)
    _assert_true(float(world.get("touch_axis").length()) <= 1.001, "Diagonal touch must be normalized")
    world.call("_release_touch_axis", Vector2.RIGHT)
    _assert_true(world.get("touch_axis") == Vector2.UP, "Releasing one finger preserves the other direction")
    world.call("_notification", MainLoop.NOTIFICATION_APPLICATION_FOCUS_OUT)
    _assert_true(world.get("touch_axis") == Vector2.ZERO, "Focus loss must clear touch")
    _assert_true(bool(world.call("_movement_blocked")), "Unfocused gameplay must be blocked")
    world.call("_notification", MainLoop.NOTIFICATION_APPLICATION_FOCUS_IN)
    var panel: Control = world.get("controls_panel")
    panel.show()
    world.call("_set_touch_axis", Vector2.RIGHT)
    _assert_true(world.get("touch_axis") == Vector2.ZERO, "Settings must block movement input")
    panel.hide()
    var cash_before_cancel: int = int(world.get("game_cash"))
    player.global_position = Vector3(-18.0, 0.5, -6.0)
    world.call("_interact")
    _assert_true(bool(world.call("_movement_blocked")), "Quiz must block movement input")
    world.call("_close_quiz")
    _assert_true(int(world.get("game_cash")) == cash_before_cancel, "Cancel must never pay rewards")
    if player != null:
        world.set("last_safe_position", Vector3(0, 0.15, 0))
        player.global_position = Vector3(0, -10, 0)
        world.call("_recover_if_needed")
        _assert_true(player.global_position.y > 0, "Falling off the map must recover offline")
        _assert_true(player.velocity == Vector3.ZERO, "Recovery must clear velocity")

    var cycle: RefCounted = world.get("day_cycle")
    cycle.set("enabled", true)
    cycle.set("hour", 12.0)
    world.call("_update_daylight")
    _assert_true(float(world.get("sunlight").light_energy) > 1.0, "Shared scene must apply daylight")
    cycle.set("hour", 0.0)
    world.call("_update_daylight")
    _assert_true(is_zero_approx(float(world.get("sunlight").light_energy)), "Shared scene must apply night lighting")
    cycle.set("hour", 8.0)

    var preferences: RefCounted = world.get("preferences")
    var previous_saver: bool = preferences.get("battery_saver")
    var previous_shadows: bool = preferences.get("shadows_enabled")
    var lights: Array = world.get("neighborhood").get("lamp_lights")
    player.global_position = Vector3(-11, 0.15, 3.5)
    cycle.set("hour", 0.0)
    preferences.set("battery_saver", false)
    preferences.set("shadows_enabled", true)
    world.call("_apply_graphics")
    _assert_true(Engine.max_fps == 60, "Standard mode caps rendering at 60 FPS")
    _assert_true(bool(world.get("sunlight").shadow_enabled), "Standard mode respects enabled shadows")
    var visible_lamps: int = 0
    for light in lights:
        if light.visible:
            visible_lamps += 1
    _assert_true(visible_lamps > 0 and visible_lamps <= 2, "Night lighting must use at most two nearby lamps")
    cycle.set("hour", 12.0)
    world.call("_update_daylight")
    for light in lights:
        _assert_true(not light.visible, "Daylight must disable street lamps")
    cycle.set("hour", 0.0)
    preferences.set("battery_saver", true)
    world.call("_apply_graphics")
    _assert_true(Engine.max_fps == 30, "Saver caps rendering at 30 FPS")
    _assert_true(not bool(world.get("sunlight").shadow_enabled), "Saver disables expensive shadows")
    for light in lights:
        _assert_true(not light.visible, "Saver disables all dynamic street lights")
    preferences.set("battery_saver", previous_saver)
    preferences.set("shadows_enabled", previous_shadows)
    cycle.set("hour", 8.0)
    world.call("_apply_graphics")
    player.global_position = Vector3.ZERO

    # Test each new job and interaction without changing BLINK Coins or real funds.
    if player != null:
        player.global_position = Vector3(-13.0, 0.15, -43.8)
        _assert_true(world.call("_nearest_place").get("name", "") == "FUTA Campus Café", "Café entrance must be reachable")
        world.call("_interact")
        _assert_true(not world.get("active_quiz").is_empty(), "Campus café starts a local quiz")
        world.call("_submit_quiz", 0)
        _assert_true(int(world.get("game_cash")) == 370, "Wrong campus café answer earns zero cash")
        world.call("_submit_quiz", 1)
        _assert_true(int(world.get("game_cash")) == 510, "Correct campus café quiz earns 140 fictional GC")
        player.global_position = Vector3(-154.0, 0.15, -117.8)
        _assert_true(world.call("_nearest_place").get("name", "") == "Campus Delivery Office", "Courier entrance must be reachable")
        world.call("_interact")
        world.call("_submit_quiz", 0)
        _assert_true(int(world.get("game_cash")) == 670, "Courier task earns 160 fictional GC")
        _assert_true(int(world.get("completed_jobs")) == 3, "Both campus jobs increment completed jobs")
        player.global_position = Vector3(-85.0, 0.15, -43.8)
        world.call("_interact")
        _assert_true(world.get("active_quiz").is_empty(), "Campus library has no surprise purchases")
        _assert_true("Campus Library" in str(world.get("hud_status").text), "Campus library interaction identifies its name")
        _assert_true(bool(world.call("_is_play_area", player.global_position)), "Offline map recovery must accept campus positions")
        world.set("last_safe_position", player.global_position)
        world.call("_recover_if_needed")
        _assert_true(player.global_position.x == -85.0, "Navigable campus walk must not teleport player away")
        player.global_position = Vector3(-240.0, 0.15, -43.8)
        world.call("_recover_if_needed")
        _assert_true(player.global_position.x > -100.0, "Out-of-map movement must recover to last safe ground")
        player.global_position = Vector3.ZERO
        world.set("last_safe_position", Vector3(0, 0.15, 0))

    # The game must remain offline until a player explicitly chooses Host/Join.
    var social: Node = world.get_node_or_null("SocialWorld")
    _assert_true(social != null, "Social LAN module must be embedded in native scene")
    if social != null:
        _assert_true(not bool(social.get("is_online")), "Social session must start offline")
        _assert_true(social.get("people").is_empty(), "No fake players should appear offline")
        _assert_true(social.has_method("open_social"), "Homes must open social controls")
        _assert_true(social.has_method("_host_lan"), "LAN host flow must exist")
        _assert_true(social.has_method("_join_lan"), "LAN join flow must exist")
        _assert_true(social.has_method("_server_action"), "Host must authorize social actions")
        _assert_true(social.has_method("_allowed_position"), "Server must validate player positions")
        social.call("_host_lan")
        _assert_true(bool(social.get("is_online")), "Trusted local multiplayer host should start")
        var profiles: Dictionary = social.get("people")
        _assert_true(profiles.size() == 1, "Hosting starts with exactly one real player")
        _assert_true(bool(social.call("_allowed_position", 1, Vector3(-85, 0.5, -49))), "LAN players can visit campus")
        _assert_true(bool(social.call("_allowed_position", 1, Vector3(-13, 0.5, -123))), "LAN players can reach eastern campus")
        _assert_true(not bool(social.call("_allowed_position", 1, Vector3(-240, 0.5, -49))), "LAN rejects out-of-map positions")
        _assert_true(not bool(social.call("_allowed_position", 1, Vector3(69, 0.5, 32))), "Campus map cannot bypass other player's private home")
        social.call("_server_action", 1, "car", 0)
        var vehicles: Dictionary = social.get("cars")
        _assert_true(vehicles.has(1), "Host should be able to spawn a virtual car")
        social.call("_server_action", 1, "location", 0)
        profiles = social.get("people")
        _assert_true(bool(profiles[1]["map_opt_in"]), "Virtual location sharing requires explicit opt in")
        social.call("_server_action", 1, "home", 0)
        _assert_true(player.global_position.x > 50.0, "My Home must teleport into a physical 3D interior")
        social.call("_server_action", 1, "city", 0)
        _assert_true(player.global_position.x < 20.0, "Return to City must restore gameplay")
        social.call("_disconnect")
        _assert_true(not bool(social.get("is_online")), "Disconnect must return to offline mode")

    # Give the 3D meshes and physics resources time to leave the scene tree
    # before stopping the headless runtime.
    world.queue_free()
    await process_frame
    await process_frame

    if failed:
        quit(1)
    else:
        print("BLINK LIFE native 3D scene smoke tests passed.")
        quit(0)


func _initialize() -> void:
    call_deferred("_run")

