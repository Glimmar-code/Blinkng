extends SceneTree

const Preferences = preload("res://scripts/player_preferences.gd")
const DayCycle = preload("res://scripts/day_cycle.gd")
const TEST_PATH: String = "user://blink_controls_test.cfg"
var failed: bool = false


func check(condition: bool, message: String) -> void:
    if not condition:
        failed = true
        printerr("SMOKE FAILURE: " + message)


func _initialize() -> void:
    var settings := Preferences.new()
    settings.camera_distance = 18.0
    settings.movement_scale = 0.65
    settings.reduced_motion = true
    check(settings.write_settings(TEST_PATH) == OK, "Preferences should save")
    var restored := Preferences.new()
    restored.read_settings(TEST_PATH)
    check(is_equal_approx(restored.camera_distance, 18.0), "Camera distance survives relaunch")
    check(is_equal_approx(restored.movement_scale, 0.65), "Walking speed survives relaunch")
    check(restored.reduced_motion, "Reduced motion survives relaunch")
    var corrupt := ConfigFile.new()
    corrupt.set_value("controls", "camera_distance", "bad")
    corrupt.set_value("controls", "movement_scale", INF)
    corrupt.set_value("controls", "reduced_motion", "true")
    corrupt.save(TEST_PATH)
    restored.read_settings(TEST_PATH)
    check(is_equal_approx(restored.camera_distance, 13.0), "Wrong-type zoom falls back")
    check(is_equal_approx(restored.movement_scale, 1.0), "Infinite speed falls back")
    check(not restored.reduced_motion, "Wrong-type boolean falls back")
    check(Preferences._bounded(-200, 8, 20, 13) == 8, "Zoom cannot pass near limit")
    check(Preferences._bounded(500, 8, 20, 13) == 20, "Zoom cannot pass far limit")
    var slow_frames := Vector2.ZERO
    var fast_frames := Vector2.ZERO
    for i in range(6):
        slow_frames = Preferences.horizontal_velocity(slow_frames, Vector2.RIGHT, 5.0, 1.0 / 30.0)
    for i in range(12):
        fast_frames = Preferences.horizontal_velocity(fast_frames, Vector2.RIGHT, 5.0, 1.0 / 60.0)
    check(slow_frames.is_equal_approx(fast_frames), "Acceleration agrees at 30 and 60 FPS")
    check(slow_frames.x > 0 and slow_frames.x < 5, "Acceleration must be gradual")
    var diagonal: Vector2 = Preferences.horizontal_velocity(Vector2.ZERO, Vector2(1, 1), 5.0, 0.1)
    check(diagonal.length() <= 5.0, "Diagonal input must not exceed speed cap")
    var stopped := Vector2(5, 0)
    for i in range(30):
        stopped = Preferences.horizontal_velocity(stopped, Vector2.ZERO, 5.0, 1.0 / 60.0)
    check(stopped.is_zero_approx(), "Braking must stop without drift")
    var cycle := DayCycle.new()
    cycle.hour = 12.0
    var noon: Dictionary = cycle.sample()
    cycle.hour = 0.0
    var midnight: Dictionary = cycle.sample()
    check(float(noon["sun_energy"]) > float(midnight["sun_energy"]), "Noon must be brighter than midnight")
    check(float(midnight["ambient_energy"]) >= 0.3, "Night must remain navigable")
    cycle.hour = 5.999
    var before_dawn: Color = cycle.sample()["sky_color"]
    cycle.hour = 6.001
    var after_dawn: Color = cycle.sample()["sky_color"]
    check(absf(before_dawn.r - after_dawn.r) < 0.01, "Dawn must not flash")
    cycle.hour = 23.99
    cycle.advance(60.0)
    check(cycle.hour >= 0.0 and cycle.hour < 2.0, "Midnight must wrap safely")
    cycle.day_minutes = 10.0
    cycle.hour = 8.0
    cycle.advance(600.0)
    check(is_equal_approx(cycle.hour, 8.0), "Configured ten-minute day must complete once")
    cycle.enabled = false
    cycle.advance(100.0)
    check(is_equal_approx(cycle.hour, 8.0), "Disabled cycle must stop its clock")
    check(float(cycle.sample()["hour"]) == 12.0, "Disabled cycle must provide fixed daylight")
    cycle.enabled = true
    cycle.advance(NAN)
    check(is_finite(cycle.hour), "Invalid delta must not corrupt the clock")
    DirAccess.remove_absolute(TEST_PATH)
    if not failed:
        print("BLINK LIFE control math and settings checks passed.")
    quit(1 if failed else 0)
