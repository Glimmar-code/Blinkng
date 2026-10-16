extends RefCounted
## Fictional, device-local weather preview. Never drives server state, rewards or health.

const MODES: Array[String] = ["auto", "clear", "light", "heavy"]
var mode: String = "auto"
var elapsed: float = 0.0


static func validated_mode(value: Variant) -> String:
    if value is String and MODES.has(value):
        return value
    return "auto"


func advance(delta: float) -> void:
    if is_finite(delta) and delta > 0.0:
        # Long app-background gaps cannot skip many weather states at once.
        elapsed = fposmod(elapsed + minf(delta, 120.0), 270.0)


func condition() -> String:
    var selected: String = validated_mode(mode)
    if selected != "auto":
        return selected
    if elapsed < 90.0:
        return "clear"
    if elapsed < 180.0:
        return "light"
    return "heavy"


static func label_for(condition_name: String) -> String:
    match condition_name:
        "light":
            return "Light rain"
        "heavy":
            return "Tropical rain"
        _:
            return "Clear"
