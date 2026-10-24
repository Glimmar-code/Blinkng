extends Node3D
## Local decorative NPCs; visibly labeled NPC and never counted as online users.
## Eight mesh-only actors in standard mode, four in battery saver; no physics spam.

const Routine = preload("res://scripts/npc_routines.gd")
var residents: Array[Dictionary] = []
var accumulator: float = 0.0
var hour: float = 8.0
var condition_name: String = "clear"
var battery_saver: bool = false
var observer_position: Vector3 = Vector3.ZERO


func _make_box(parent: Node3D, center: Vector3, size: Vector3, color: Color) -> void:
    var mesh := BoxMesh.new()
    mesh.size = size
    var obj := MeshInstance3D.new()
    obj.mesh = mesh
    obj.position = center
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = 0.85
    obj.material_override = material
    parent.add_child(obj)


func _create_actor(person: Dictionary) -> Node3D:
    var actor := Node3D.new()
    actor.name = "NPC_" + str(person["id"])
    actor.position = Routine.entrance(str(person["home"])) + Vector3.UP * 0.15
    add_child(actor)

    _make_box(actor, Vector3(0, 0.88, 0), Vector3(0.56, 0.8, 0.34), person["shirt"])
    _make_box(actor, Vector3(-0.16, 0.3, 0), Vector3(0.2, 0.52, 0.3), Color(0.18, 0.23, 0.29))
    _make_box(actor, Vector3(0.16, 0.3, 0), Vector3(0.2, 0.52, 0.3), Color(0.18, 0.23, 0.29))
    var head := MeshInstance3D.new()
    var geometry := SphereMesh.new()
    geometry.radius = 0.26
    geometry.height = 0.52
    head.mesh = geometry
    head.position = Vector3(0, 1.52, 0)
    var skin := StandardMaterial3D.new()
    skin.albedo_color = person["skin"]
    head.material_override = skin
    actor.add_child(head)

    var nameplate := Label3D.new()
    nameplate.name = "NPCName"
    nameplate.text = "NPC · " + str(person["name"])
    nameplate.position = Vector3(0, 2.1, 0)
    nameplate.font_size = 27
    nameplate.pixel_size = 0.008
    nameplate.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    actor.add_child(nameplate)

    var umbrella := Node3D.new()
    umbrella.name = "RainUmbrella"
    _make_box(umbrella, Vector3(0.43, 1.94, 0), Vector3(0.04, 0.84, 0.04), Color(0.28, 0.29, 0.31))
    _make_box(umbrella, Vector3(0.43, 2.36, 0), Vector3(1.15, 0.1, 1.15), Color(0.27, 0.48, 0.61))
    umbrella.visible = false
    actor.add_child(umbrella)
    return actor


func _ready() -> void:
    name = "Residents"
    for person in Routine.residents():
        var actor: Node3D = _create_actor(person)
        residents.append({
            "person": person,
            "actor": actor,
            "place": "",
            "activity": "",
            "route": [],
            "next": 0
        })


func update_residents(player_position: Vector3, scene_hour: float, weather: String, saver: bool, delta: float) -> void:
    hour = scene_hour
    condition_name = weather
    battery_saver = saver
    observer_position = player_position
    if not is_finite(delta) or delta < 0.0:
        return
    accumulator += minf(delta, 0.15)
    if accumulator < 0.1:
        return
    var step: float = accumulator
    accumulator = 0.0
    for index in range(residents.size()):
        var resident: Dictionary = residents[index]
        var actor: Node3D = resident["actor"]
        var scheduled: Dictionary = Routine.destination(resident["person"], hour, condition_name)
        if str(resident["place"]) != str(scheduled["place"]):
            var endpoint: Vector3 = Routine.entrance(str(scheduled["place"])) + Vector3.UP * 0.15
            resident["route"] = Routine.route_between(actor.position, endpoint)
            resident["next"] = 0
            resident["place"] = str(scheduled["place"])
        resident["activity"] = str(scheduled["activity"])
        var points: Array = resident["route"]
        var cursor: int = int(resident["next"])
        if cursor < points.size():
            var destination_point: Vector3 = points[cursor]
            var difference: Vector3 = destination_point - actor.position
            var distance: float = difference.length()
            if distance <= 0.09:
                actor.position = destination_point
                resident["next"] = cursor + 1
            else:
                var move_distance: float = minf(1.65 * step, distance)
                actor.position += difference.normalized() * move_distance
                actor.rotation.y = atan2(-difference.x, -difference.z)
        var moving: bool = int(resident["next"]) < (resident["route"] as Array).size()
        actor.get_node("RainUmbrella").visible = bool(scheduled["raining"]) and moving
        actor.visible = index < Routine.quality_population(battery_saver) and observer_position.distance_squared_to(actor.global_position) < 1024.0


func nearby_greeting(player_position: Vector3) -> String:
    var selected: String = ""
    var distance_squared: float = 3.0 * 3.0
    for i in range(residents.size()):
        var state: Dictionary = residents[i]
        var actor: Node3D = state["actor"]
        if not actor.visible:
            continue
        var distance: float = player_position.distance_squared_to(actor.global_position)
        if distance < distance_squared:
            distance_squared = distance
            var greeting: String = "Nice to see you around the neighborhood."
            if condition_name == "heavy":
                greeting = "Heavy rain today. Stay near shelter."
            elif condition_name == "light":
                greeting = "A little rain makes the streets feel fresh."
            selected = "NPC %s · %s. %s" % [state["person"]["name"], state["activity"], greeting]
    return selected
