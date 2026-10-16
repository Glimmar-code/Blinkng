extends RefCounted
## Local presentation clock. No economy, NPC schedule or server time authority.

var hour: float = 8.0
var day_minutes: float = 20.0
var enabled: bool = true


func advance(delta: float) -> void:
    if enabled and is_finite(delta) and delta > 0:
        hour = fposmod(hour + delta * 24.0 / (clampf(day_minutes, 10, 60) * 60.0), 24.0)


func sample() -> Dictionary:
    var display_hour: float = hour if enabled else 12.0
    var altitude: float = sin((display_hour - 6.0) * TAU / 24.0)
    # Civil twilight blends either side of the horizon without a sudden switch.
    var daylight: float = smoothstep(-0.15, 0.45, altitude)
    var sky: Color = Color(0.035, 0.055, 0.12).lerp(Color(0.72, 0.82, 0.92), daylight)
    var warmth: float = (1.0 - smoothstep(0.0, 0.5, altitude)) * smoothstep(-0.15, 0.05, altitude)
    sky = sky.lerp(Color(0.72, 0.39, 0.30), warmth * 0.35)
    return {
        "sun_rotation": Vector3(-(display_hour - 6.0) * 15.0, 25.0, 0),
        "sun_energy": maxf(altitude, 0.0) * 1.2,
        "sun_color": Color.WHITE.lerp(Color(1.0, 0.61, 0.35), warmth),
        "ambient_energy": lerpf(0.3, 0.6, daylight),
        "sky_color": sky,
        "daylight": daylight,
        "hour": display_hour
    }
