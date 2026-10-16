extends RefCounted
## Deterministic fictional residents: no BLINK accounts, identity, GPS or social data.
## Schedules run only in the local Godot presentation; server multiplayer is separate.

const Layout = preload("res://scripts/neighborhood_layout.gd")

static func residents() -> Array[Dictionary]:
    return [
        {"id": "ada", "name": "Ada", "home": "homes", "work": "tech", "leisure": "cafe", "shirt": Color(0.42, 0.68, 0.72), "skin": Color(0.55, 0.32, 0.20)},
        {"id": "tayo", "name": "Tayo", "home": "hostel", "work": "campus", "leisure": "market", "shirt": Color(0.72, 0.58, 0.33), "skin": Color(0.38, 0.24, 0.16)},
        {"id": "ife", "name": "Ife", "home": "homes", "work": "cafe", "leisure": "campus", "shirt": Color(0.62, 0.40, 0.62), "skin": Color(0.72, 0.47, 0.31)},
        {"id": "emeka", "name": "Emeka", "home": "hostel", "work": "depot", "leisure": "cafe", "shirt": Color(0.38, 0.53, 0.42), "skin": Color(0.46, 0.29, 0.20)},
        {"id": "zara", "name": "Zara", "home": "homes", "work": "market", "leisure": "campus", "shirt": Color(0.67, 0.43, 0.37), "skin": Color(0.67, 0.43, 0.27)},
        {"id": "bode", "name": "Bode", "home": "hostel", "work": "tech", "leisure": "market", "shirt": Color(0.34, 0.46, 0.64), "skin": Color(0.32, 0.21, 0.17)},
        {"id": "nkechi", "name": "Nkechi", "home": "homes", "work": "campus", "leisure": "cafe", "shirt": Color(0.70, 0.60, 0.42), "skin": Color(0.60, 0.37, 0.25)},
        {"id": "kunle", "name": "Kunle", "home": "hostel", "work": "depot", "leisure": "market", "shirt": Color(0.48, 0.63, 0.52), "skin": Color(0.46, 0.31, 0.23)}
    ]


static func destination(resident: Dictionary, hour: float, weather: String) -> Dictionary:
    var time: float = fposmod(hour, 24.0) if is_finite(hour) else 12.0
    var target: String = str(resident["home"])
    var activity: String = "At home"
    if time >= 7.0 and time < 9.0:
        target = str(resident["work"])
        activity = "Morning commute"
    elif time >= 9.0 and time < 16.0:
        target = str(resident["work"])
        activity = "At work"
    elif time >= 16.0 and time < 20.0:
        target = str(resident["leisure"])
        activity = "Enjoying free time"
    elif time >= 20.0:
        activity = "Returning home"
    if weather == "heavy":
        # Shelter at the nearest planned indoor stop instead of wandering outside.
        target = str(resident["work"]) if time >= 9.0 and time < 16.0 else str(resident["home"])
        activity = "Sheltering from rain"
    return {"place": target, "activity": activity, "raining": weather in ["light", "heavy"]}


static func entrance(place_id: String) -> Vector3:
    for place in Layout.places():
        if str(place["id"]) == place_id:
            return Layout.entrance_for(place)
    return Vector3.ZERO


static func route_between(start: Vector3, destination_point: Vector3) -> Array[Vector3]:
    # Route along the pre-existing connected walkways, never straight through houses.
    # Two protected horizontal lanes are joined by the x=-3m clear corridor.
    var lane_a: float = -3.0 if start.z < 0.0 else 3.0
    var lane_b: float = -3.0 if destination_point.z < 0.0 else 3.0
    var route: Array[Vector3] = [Vector3(start.x, 0.15, lane_a)]
    if not is_equal_approx(lane_a, lane_b):
        route.append(Vector3(-3.0, 0.15, lane_a))
        route.append(Vector3(-3.0, 0.15, lane_b))
    route.append(Vector3(destination_point.x, 0.15, lane_b))
    route.append(Vector3(destination_point.x, 0.15, destination_point.z))
    return route


static func quality_population(battery_saver: bool) -> int:
    return 4 if battery_saver else 8
