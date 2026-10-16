extends Node3D
## Four clearly labelled offline NPC pedestrians. No real player identities or rewards.
## Keeps the preview lightweight; hides residents while LAN/online sessions run.

const PEOPLE: Array[Dictionary] = [
    {"id":"npc_ada","name":"Ada","role":"Market commuter","xmin":-22.0,"xmax":-15.0,"z":-3.35,"speed":0.75,"shirt":Color(0.80,0.38,0.22),"skin":Color(0.47,0.28,0.18)},
    {"id":"npc_musa","name":"Musa","role":"Campus pedestrian","xmin":-11.0,"xmax":-4.0,"z":-3.35,"speed":0.63,"shirt":Color(0.24,0.53,0.60),"skin":Color(0.31,0.18,0.12)},
    {"id":"npc_efe","name":"Efe","role":"Tech commuter","xmin":4.0,"xmax":11.0,"z":-3.35,"speed":0.68,"shirt":Color(0.44,0.63,0.28),"skin":Color(0.63,0.40,0.26)},
    {"id":"npc_tola","name":"Tola","role":"Hostel pedestrian","xmin":11.0,"xmax":21.0,"z":3.35,"speed":0.55,"shirt":Color(0.58,0.35,0.64),"skin":Color(0.43,0.25,0.17)}
]
var people: Array[Dictionary] = []
var time_elapsed: float = 0.0


static func allowed_count(hour: float, weather_name: String, saving: bool, connected: bool) -> int:
    if connected or not is_finite(hour) or hour < 7.0 or hour >= 21.0:
        return 0
    if weather_name == "heavy":
        return 1 if saving else 2
    return 2 if saving else 4


func _ready() -> void:
    name = "LocalCitizens"
    for definition in PEOPLE:
        var actor := Node3D.new()
        actor.name = str(definition["id"])
        add_child(actor)
        var body := MeshInstance3D.new()
        var shirt := BoxMesh.new()
        shirt.size = Vector3(0.46,0.75,0.3)
        body.mesh = shirt
        body.position = Vector3(0,0.85,0)
        var material := StandardMaterial3D.new()
        material.albedo_color = definition["shirt"]
        body.material_override = material
        actor.add_child(body)
        for x in [-0.13,0.13]:
            var leg := MeshInstance3D.new()
            var limb := BoxMesh.new()
            limb.size = Vector3(0.15,0.55,0.24)
            leg.mesh = limb
            leg.position = Vector3(x,0.28,0)
            leg.material_override = material
            actor.add_child(leg)
        var head := MeshInstance3D.new()
        var sphere := SphereMesh.new()
        sphere.radius = 0.24
        sphere.height = 0.48
        head.mesh = sphere
        head.position = Vector3(0,1.43,0)
        var head_color := StandardMaterial3D.new()
        head_color.albedo_color = definition["skin"]
        head.material_override = head_color
        actor.add_child(head)
        var marker := Label3D.new()
        marker.text = "NPC • %s" % definition["name"]
        marker.position = Vector3(0,2.0,0)
        marker.pixel_size = 0.007
        marker.billboard = BaseMaterial3D.BILLBOARD_ENABLED
        actor.add_child(marker)
        actor.visible = false
        people.append({"node":actor, "data":definition})


func update_routines(hour: float, weather_name: String, delta: float, saving: bool, connected: bool) -> void:
    var budget: int = allowed_count(hour, weather_name, saving, connected)
    if is_finite(delta) and delta > 0:
        time_elapsed = fposmod(time_elapsed + minf(delta, 0.1), 2000.0)
    for i in range(people.size()):
        var entry: Dictionary = people[i]
        var actor: Node3D = entry["node"]
        var definition: Dictionary = entry["data"]
        actor.visible = i < budget
        if not actor.visible:
            continue
        var start_x: float = float(definition["xmin"])
        var end_x: float = float(definition["xmax"])
        var length: float = end_x - start_x
        var progression: float = fposmod(time_elapsed * float(definition["speed"]) + float(i) * 1.7, length * 2.0)
        actor.position = Vector3(start_x + (progression if progression <= length else 2.0 * length - progression), 0.0, float(definition["z"]))
        actor.rotation.y = PI / 2.0 if progression <= length else -PI / 2.0
