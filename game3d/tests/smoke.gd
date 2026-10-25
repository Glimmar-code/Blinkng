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
    _assert_true(places.size() == 7, "World should include Homes and six original destinations")

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
        _assert_true(bool(world.get("owns_plant")), "Market interaction should buy the desk plant")
        _assert_true(int(world.get("game_cash")) == 370, "Market purchase should cost 250 fictional GC")
        world.call("_interact")
        _assert_true(int(world.get("game_cash")) == 370, "Purchasing a second time must not charge again")

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
