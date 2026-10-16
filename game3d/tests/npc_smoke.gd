extends SceneTree
## No renderer/network needed for deterministic local resident schedule checks.

const Routines = preload("res://scripts/npc_routines.gd")
const Layout = preload("res://scripts/neighborhood_layout.gd")
var failed: bool = false


func check(ok: bool, message: String) -> void:
    if not ok:
        failed = true
        printerr("SMOKE FAILURE: " + message)


func _initialize() -> void:
    var profiles: Array[Dictionary] = Routines.residents()
    check(profiles.size() == 8, "Standard city has eight original fictional residents")
    check(Routines.quality_population(false) == 8, "Standard graphic budget permits eight actors")
    check(Routines.quality_population(true) == 4, "Battery saver halves active resident population")
    var ids: Dictionary = {}
    var valid_places: Dictionary = {}
    for place in Layout.places():
        valid_places[place["id"]] = true
    for person in profiles:
        check(not ids.has(person["id"]), "NPC IDs must be unique")
        ids[person["id"]] = true
        for key in ["home", "work", "leisure"]:
            check(valid_places.has(person[key]), "Every " + key + " must be a known destination")

        var sleeping: Dictionary = Routines.destination(person, 2.0, "clear")
        var working: Dictionary = Routines.destination(person, 12.0, "clear")
        var leisure: Dictionary = Routines.destination(person, 17.0, "clear")
        var wet: Dictionary = Routines.destination(person, 17.0, "heavy")
        check(sleeping["place"] == person["home"], "Residents return home at night")
        check(working["place"] == person["work"], "Residents go to work by midday")
        check(leisure["place"] == person["leisure"], "Residents have a local leisure destination")
        check(wet["place"] == person["home"], "Heavy rain redirects leisure to shelter")
        check(bool(Routines.destination(person, 8.0, "light")["raining"]), "Rain state is reflected in NPC decisions")
        check(Routines.entrance(str(person["home"])) != Vector3.ZERO, "Home location must be a real frontage")
        check(Routines.entrance(str(person["work"])) != Vector3.ZERO, "Work location must be a real frontage")

        var route: Array[Vector3] = Routines.route_between(
            Routines.entrance(str(person["home"])) + Vector3.UP * 0.15,
            Routines.entrance(str(person["work"])) + Vector3.UP * 0.15
        )
        check(route.size() >= 3, "Residents follow pedestrian waypoints")
        check(route.back().distance_to(Routines.entrance(str(person["work"]))) < 0.16, "Route ends at the intended workplace")
        # Check every straight segment against all solid building rectangles,
        # including the complete segment, rather than endpoints alone.
        var previous: Vector3 = Routines.entrance(str(person["home"])) + Vector3.UP * 0.15
        for point in route:
            for block in Layout.places():
                var center: Vector3 = Layout.position_for(int(block["col"]), int(block["row"]))
                var half: float = Layout.BUILDING_WIDTH / 2.0 + 0.28
                var near_x: float = clampf(center.x, minf(previous.x, point.x), maxf(previous.x, point.x))
                var near_z: float = clampf(center.z, minf(previous.z, point.z), maxf(previous.z, point.z))
                # Axis-aligned walking segments, with a 0.28m safe radius.
                if is_equal_approx(previous.x, point.x):
                    check(absf(previous.x - center.x) > half or absf(near_z - center.z) > half, "Walking route must not clip through a building")
                elif is_equal_approx(previous.z, point.z):
                    check(absf(previous.z - center.z) > half or absf(near_x - center.x) > half, "Walking route must not clip through a building")
            previous = point
    if not failed:
        print("BLINK LIFE local resident schedule and route tests passed.")
    quit(1 if failed else 0)
