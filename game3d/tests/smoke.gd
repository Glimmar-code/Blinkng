extends SceneTree

# Headless regression checks for BLINK LIFE's native Godot 3D scene.
# This is a runtime smoke test, not a substitute for actual Android hardware testing.

const SoloProgress = preload("res://scripts/solo_progress.gd")
const TEST_SAVE: String = "user://blink_life_scene_smoke_save.json"
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
    var demo_progress := SoloProgress.new()
    demo_progress.save_path = TEST_SAVE
    demo_progress.backup_path = TEST_SAVE + ".bak"
    demo_progress.temp_path = TEST_SAVE + ".tmp"
    for file in [demo_progress.save_path, demo_progress.backup_path, demo_progress.temp_path]:
        if FileAccess.file_exists(file):
            DirAccess.remove_absolute(ProjectSettings.globalize_path(file))
    world.set("progress_store", demo_progress)
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
    _assert_true(places.size() == 7, "World should include Homes and six original destinations")

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

    var weather_fx: Node3D = world.get_node_or_null("WeatherVisuals") as Node3D
    _assert_true(weather_fx != null, "Local weather scene must be present")
    if weather_fx != null:
        var rain_node: MultiMeshInstance3D = weather_fx.get_node_or_null("Rain") as MultiMeshInstance3D
        _assert_true(rain_node != null, "Weather renderer must batch drops")
        if rain_node != null:
            var local_weather: RefCounted = world.get("weather")
            local_weather.set("mode", "heavy")
            world.call("_process", 0.10)
            _assert_true(rain_node.visible and rain_node.multimesh.visible_instance_count == 80, "Tropical weather shows capped rainfall")
            local_weather.set("mode", "clear")
            world.call("_process", 0.10)
            _assert_true(not rain_node.visible and rain_node.multimesh.visible_instance_count == 0, "Clear weather hides rainfall immediately")
            local_weather.set("mode", "auto")

    var npc_world: Node3D = world.get_node_or_null("Residents") as Node3D
    _assert_true(npc_world != null, "Local fictional residents must be present")
    if npc_world != null:
        _assert_true(npc_world.get_child_count() == 8, "Scene contains eight original NPC actors")
        var npc_ada: Node3D = npc_world.get_node_or_null("NPC_ada") as Node3D
        _assert_true(npc_ada != null, "The first recurring resident has a stable NPC ID")
        if npc_ada != null:
            var npc_nameplate: Label3D = npc_ada.get_node_or_null("NPCName") as Label3D
            _assert_true(npc_nameplate != null and npc_nameplate.text.begins_with("NPC"), "NPC labels must be distinguishable from real players")
            npc_world.call("update_residents", npc_ada.global_position, 8.0, "light", false, 0.25)
            var umbrella: Node3D = npc_ada.get_node_or_null("RainUmbrella") as Node3D
            _assert_true(umbrella != null and umbrella.visible, "A walking NPC carries an umbrella in light rain")
            _assert_true(not str(npc_world.call("nearby_greeting", npc_ada.global_position)).is_empty(), "Resident conversations remain text-accessible")
            npc_world.call("update_residents", npc_ada.global_position, 8.0, "clear", true, 0.25)
            _assert_true(umbrella != null and not umbrella.visible, "NPC umbrellas retract in clear weather")
            var visible_count: int = 0
            for actor in npc_world.get_children():
                if actor.visible:
                    visible_count += 1
            _assert_true(visible_count <= 4, "Battery-saving mode caps visible residents at four")

    var spaces: Node3D = world.get_node_or_null("PublicSpaces") as Node3D
    _assert_true(spaces != null, "Public amenities should exist as 3D geometry")
    var PublicSpaceData: Script = load("res://scripts/public_spaces.gd")
    if player != null and PublicSpaceData != null:
        var public_locations: Array[Dictionary] = PublicSpaceData.public_spots()
        var bench: Dictionary = public_locations[0]
        player.global_position = bench["position"] + Vector3.UP * 0.15
        var first_body: MeshInstance3D = player.get_child(1) as MeshInstance3D
        var before_pose: Transform3D = first_body.transform
        world.call("_interact")
        _assert_true(not str(world.get("seated_at")).is_empty(), "The park bench should be usable in offline preview")
        _assert_true(bool(world.call("_movement_blocked")), "Sitting must block movement until standing")
        _assert_true(not first_body.transform.is_equal_approx(before_pose), "Sitting must visibly change the character pose")
        world.call("_interact")
        _assert_true(str(world.get("seated_at")).is_empty(), "Interact again must stand up")
        _assert_true(first_body.transform.is_equal_approx(before_pose), "Standing must restore the original avatar pose")

        var board: Dictionary = public_locations[public_locations.size() - 1]
        player.global_position = board["position"] + Vector3.UP * 0.15
        var before_board_cash: int = int(world.get("game_cash"))
        world.call("_interact")
        _assert_true(world.get("hud_modal").visible, "Community opportunities must open a readable panel")
        _assert_true(int(world.get("game_cash")) == before_board_cash, "Viewing opportunities cannot create currency")
        world.call("_close_quiz")
        player.global_position = public_locations[6]["position"] + Vector3.UP * 0.15
        world.call("_interact")
        _assert_true(int(world.get("game_cash")) == before_board_cash, "Browsing stalls cannot charge or mint currency")
        _assert_true(str(world.get("hud_status").text).contains("Fruit Stall"), "The selected stall should show its own identity")
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


    # The free starter room is a playable, opt-in SOLO interior, never a
    # replacement for the host-authorized property visiting system.
    if player != null:
        var layout_home: Script = load("res://scripts/neighborhood_layout.gd")
        var home_place: Dictionary = {}
        for destination in world.get("places"):
            if str(destination["id"]) == "homes":
                home_place = destination
        _assert_true(not home_place.is_empty(), "Homes destination must exist")
        if not home_place.is_empty():
            var outside_home: Vector3 = layout_home.entrance_for(home_place) + Vector3.UP * 0.15
            player.global_position = outside_home
            world.call("_interact")
            _assert_true(bool(world.get("hud_modal").visible), "Homes offers a choice between solo and multiplayer")
            _assert_true(not bool(world.get("home_inside")), "A menu must not teleport without consent")
            world.call("_enter_solo_home")
            _assert_true(bool(world.get("home_inside")), "Solo starter room opens when player chooses it")
            var room: Node3D = world.get("solo_room")
            _assert_true(room != null and room.get_node_or_null("Floor") != null, "Starter home has a physical floor")
            _assert_true(room != null and room.get_node_or_null("North wall") != null, "Starter home has real walls")
            _assert_true(player.global_position.y > 28.0, "Solo room places player in a distinct reachable interior")
            _assert_true(bool(room.get_node("DeskPlant").visible), "Purchased plant decor appears in the starter room")
            _assert_true(world.call("_nearest_place").is_empty(), "Cannot trigger outside businesses from a room")
            _assert_true(world.call("_nearest_public_spot").is_empty(), "Cannot trigger city props from a room")
            _assert_true(player.velocity == Vector3.ZERO, "Entering a home resets movement")
            player.global_position = Vector3(30, 0, 0)
            world.call("_recover_if_needed")
            _assert_true(bool(world.get("home_inside")) and player.global_position.y > 28.0, "Indoor fall/out-of-bounds recovers indoors")
            var wall_visual: MeshInstance3D = room.get("wall_visuals")[0]
            var floor_visual: MeshInstance3D = room.get("floor_visual")
            var initial_wall: Color = (wall_visual.material_override as StandardMaterial3D).albedo_color
            var initial_floor: Color = (floor_visual.material_override as StandardMaterial3D).albedo_color
            var plant_start: Vector3 = room.get_node("DeskPlant").position
            world.call("_interact")
            _assert_true(bool(world.get("hud_modal").visible), "Interact indoors opens decoration controls")
            _assert_true(bool(world.get("home_inside")), "Decoration controls cannot accidentally exit room")
            world.call("_close_quiz")
            world.call("_cycle_home_decoration", "wall_style")
            world.call("_cycle_home_decoration", "floor_style")
            world.call("_cycle_home_decoration", "plant_spot")
            _assert_true(int(world.get("home_wall_style")) == 1, "Free wall palette cycle should apply")
            _assert_true(int(world.get("home_floor_style")) == 1, "Free floor palette cycle should apply")
            _assert_true(int(world.get("home_plant_spot")) == 1, "Owned plant has a safe alternate location")
            _assert_true((wall_visual.material_override as StandardMaterial3D).albedo_color != initial_wall, "Walls visibly change palette")
            _assert_true((floor_visual.material_override as StandardMaterial3D).albedo_color != initial_floor, "Floor visibly changes palette")
            _assert_true(room.get_node("DeskPlant").position != plant_start, "Plant can be repositioned without moving player")
            var decor_checkpoint: Dictionary = demo_progress.load_state()
            _assert_true(int(decor_checkpoint["wall_style"]) == 1 and int(decor_checkpoint["floor_style"]) == 1, "Wall and floor choices survive disk reload")
            _assert_true(int(decor_checkpoint["plant_spot"]) == 1, "Plant placement survives disk reload")
            _assert_true(int(decor_checkpoint["cash"]) == 370, "No currency charged for free home decoration")

            var preset_position: Vector3 = room.get_node("DeskPlant").position
            world.call("_home_move_plant", 0, 0)
            _assert_true(bool(world.get("home_plant_custom")), "Explicit floor-grid mode becomes active")
            _assert_true(int(world.get("home_plant_z")) == 3, "The starting floor grid is safe and deterministic")
            var new_floor_position: Vector3 = room.get_node("DeskPlant").position
            _assert_true(new_floor_position != preset_position, "Floor-grid mode visibly relocates the plant")
            world.call("_home_move_plant", -1, 0)
            _assert_true(int(world.get("home_plant_x")) == -1, "Grid moves one metre left into a safe cell")
            var moved_position: Vector3 = room.get_node("DeskPlant").position
            world.call("_home_move_plant", 0, 1)
            _assert_true(room.get_node("DeskPlant").position == moved_position, "An out-of-bounds grid move is rejected without jumping")
            var unsafe_layout: Dictionary = world.call("_home_save_snapshot")
            unsafe_layout["plant_x"] = -3
            unsafe_layout["plant_z"] = -3
            _assert_true(not bool(world.call("_apply_home_decor", unsafe_layout)), "Furniture-overlap layout is rejected before saving")
            _assert_true(room.get_node("DeskPlant").position == moved_position, "Rejected overlap cannot repaint or move furniture")
            world.call("_home_rotate_plant")
            _assert_true(int(world.get("home_plant_rotation")) == 1, "Plant orientation cycles in ninety-degree steps")
            _assert_true(is_equal_approx(room.get_node("DeskPlant").rotation.y, PI / 2.0), "Plant visibly rotates on scene")
            world.call("_undo_home_decor")
            _assert_true(int(world.get("home_plant_rotation")) == 0, "Undo restores previous rotation")
            world.call("_undo_home_decor")
            _assert_true(int(world.get("home_plant_x")) == 0, "Second undo restores previous position")
            world.call("_redo_home_decor")
            _assert_true(int(world.get("home_plant_x")) == -1, "Redo reapplies one safe placement change")
            _assert_true(int(demo_progress.load_state()["plant_x"]) == -1, "Redo persists to local disk")
            world.call("_home_rotate_plant")
            _assert_true((world.get("decor_redo") as Array).is_empty(), "New edits clear stale redo history")
            _assert_true(int(world.get("game_cash")) == 370, "Undo/redo/rotation cannot create or charge currency")
            world.call("_cycle_home_decoration", "invalid")
            _assert_true(int(world.get("home_wall_style")) == 1, "Unknown decoration requests do nothing")
            world.call("_close_quiz")
            world.call("_exit_solo_home")
            _assert_true(not bool(world.get("home_inside")), "Explicit Exit safely leaves solo room")
            _assert_true(player.global_position.distance_to(outside_home) < 0.01, "Solo room returns to original home entrance")
            world.call("_enter_solo_home")
            var reopened_room: Node3D = world.get("solo_room")
            _assert_true(bool(world.get("home_inside")), "Players can reopen the free room")
            _assert_true(reopened_room.get_node("DeskPlant").position != plant_start, "Re-entering keeps saved plant placement")
            var reopened_floor: MeshInstance3D = reopened_room.get("floor_visual")
            _assert_true((reopened_floor.material_override as StandardMaterial3D).albedo_color != initial_floor, "Re-entering restores saved floor palette")
            world.call("_exit_solo_home")
            _assert_true(int(world.get("game_cash")) == 370 and bool(world.get("owns_plant")), "Entering homes cannot change saved money or decoration")
            _assert_true(not bool(world.get_node("SocialWorld").get("is_online")), "Solo room must never connect to multiplayer")

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

    var saved_demo: Dictionary = demo_progress.load_state()
    _assert_true(int(saved_demo["cash"]) == 370, "Solo job and purchase balance must persist on disk")
    _assert_true(int(saved_demo["jobs"]) == 1, "Completed solo job count must persist on disk")
    _assert_true(bool(saved_demo["plant"]), "Purchased desk plant must persist between launches")
    for file in [demo_progress.save_path, demo_progress.backup_path, demo_progress.temp_path]:
        if FileAccess.file_exists(file):
            DirAccess.remove_absolute(ProjectSettings.globalize_path(file))
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

