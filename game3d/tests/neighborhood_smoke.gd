extends SceneTree
## Geometry invariants can run without a renderer or network connection.

const Layout = preload("res://scripts/neighborhood_layout.gd")
var failed: bool = false


func check(condition: bool, explanation: String) -> void:
    if not condition:
        failed = true
        printerr("SMOKE FAILURE: " + explanation)


func _initialize() -> void:
    var ids: Dictionary = {}
    var addresses: Dictionary = {}
    var entries: Array[Vector3] = []
    var places: Array[Dictionary] = Layout.places()
    check(places.size() == 7, "Existing seven destinations must remain available")
    check(is_equal_approx(Layout.position_for(4, 2).distance_to(Layout.position_for(3, 2)), 6.0), "Grid spacing is six metres")
    for place in places:
        check(not ids.has(place["id"]), "Place IDs must be unique")
        check(not addresses.has(place["address"]), "Postal-style game addresses must be unique")
        ids[place["id"]] = true
        addresses[place["address"]] = true
        check(not str(place["district"]).is_empty(), "Every place must have a district tag")
        var entry: Vector3 = Layout.entrance_for(place)
        entries.append(entry)
        check(absf(entry.x) < 26 and absf(entry.z) < 20, "Entrances stay on the existing floor and within multiplayer bounds")
        for obstacle in places:
            var center: Vector3 = Layout.position_for(int(obstacle["col"]), int(obstacle["row"]))
            var half_width: float = Layout.BUILDING_WIDTH / 2.0 + 0.34
            check(absf(entry.x - center.x) > half_width or absf(entry.z - center.z) > half_width, "A full player capsule must fit at each entrance")

    # Flood the rectangle graph: every walkway must connect to the initial path.
    var paths: Array[Dictionary] = Layout.footpaths()
    var reached: Dictionary = {0: true}
    for iteration in range(paths.size()):
        for i in range(paths.size()):
            if reached.has(i):
                continue
            for j in reached.keys():
                var a: Vector3 = paths[i]["center"]
                var b: Vector3 = paths[j]["center"]
                var size_a: Vector3 = paths[i]["size"]
                var size_b: Vector3 = paths[j]["size"]
                if absf(a.x - b.x) <= (size_a.x + size_b.x) / 2.0 and absf(a.z - b.z) <= (size_a.z + size_b.z) / 2.0:
                    reached[i] = true
                    break
    check(reached.size() == paths.size(), "All footpaths form one connected network")
    for entry in entries:
        var covered: bool = false
        for path in paths:
            var center: Vector3 = path["center"]
            var size: Vector3 = path["size"]
            if absf(entry.x - center.x) <= size.x / 2.0 and absf(entry.z - center.z) <= size.z / 2.0:
                covered = true
        check(covered, "Each entrance must meet the footpath network")
    if not failed:
        print("BLINK LIFE neighborhood layout checks passed.")
    quit(1 if failed else 0)
