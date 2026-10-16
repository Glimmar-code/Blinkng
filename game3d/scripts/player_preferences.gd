extends RefCounted
## Cosmetic/local controls only. Never stores identity, currency or progression.

const PATH: String = "user://blink_life_controls.cfg"
var camera_distance: float = 13.0
var movement_scale: float = 1.0
var reduced_motion: bool = false
var day_cycle_enabled: bool = true
var day_minutes: float = 20.0


func read_settings(path: String = PATH) -> void:
    var config := ConfigFile.new()
    if config.load(path) != OK:
        return
    camera_distance = _bounded(config.get_value("controls", "camera_distance", 13.0), 8.0, 20.0, 13.0)
    movement_scale = _bounded(config.get_value("controls", "movement_scale", 1.0), 0.5, 1.0, 1.0)
    var motion: Variant = config.get_value("controls", "reduced_motion", false)
    reduced_motion = motion if motion is bool else false
    var cycle: Variant = config.get_value("controls", "day_cycle_enabled", true)
    day_cycle_enabled = cycle if cycle is bool else true
    day_minutes = _bounded(config.get_value("controls", "day_minutes", 20.0), 10.0, 60.0, 20.0)


func write_settings(path: String = PATH) -> Error:
    var config := ConfigFile.new()
    config.set_value("controls", "camera_distance", _bounded(camera_distance, 8.0, 20.0, 13.0))
    config.set_value("controls", "movement_scale", _bounded(movement_scale, 0.5, 1.0, 1.0))
    config.set_value("controls", "reduced_motion", reduced_motion)
    config.set_value("controls", "day_cycle_enabled", day_cycle_enabled)
    config.set_value("controls", "day_minutes", _bounded(day_minutes, 10.0, 60.0, 20.0))
    return config.save(path)


static func _bounded(value: Variant, minimum: float, maximum: float, fallback: float) -> float:
    if not (value is float or value is int):
        return fallback
    var number: float = float(value)
    return clampf(number, minimum, maximum) if is_finite(number) else fallback


static func horizontal_velocity(current: Vector2, direction: Vector2, speed: float, delta: float) -> Vector2:
    # Acceleration and braking are independent of render frame rate.
    var target: Vector2 = direction.limit_length(1.0) * speed
    var rate: float = 24.0 if direction.is_zero_approx() else 16.0
    return current.move_toward(target, rate * clampf(delta, 0.0, 0.1))
