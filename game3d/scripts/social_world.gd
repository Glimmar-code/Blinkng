extends Node3D
## BLINK LIFE social neighbourhood: opt-in LAN preview, NOT production matchmaking.
## Two devices on the same trusted local network can roam, chat, visit a home
## only after approval, and ride as passengers only after a driver approves.
## In-game coordinates only. No phone GPS, BLINK wallet or Supabase access.

const PORT: int = 24567
const MAX_PLAYERS: int = 8
const SYNC_SECONDS: float = 0.12
const WORLD_LIMIT: float = 26.0
const HOME_START_X: float = 53.0
const HOME_Z: float = 32.0
const HOME_GAP: float = 16.0

var world: Node3D
var local_player: CharacterBody3D
var people: Dictionary = {}
var remote_avatars: Dictionary = {}
var home_models: Dictionary = {}
var vehicle_models: Dictionary = {}
var visit_requests: Dictionary = {}
var ride_requests: Dictionary = {}
var cars: Dictionary = {}
var last_motion_ms: Dictionary = {}
var last_chat_ms: Dictionary = {}
var muted_peers: Dictionary = {}
var pending_visit_from: int = -1
var pending_ride_from: int = -1
var send_elapsed: float = 0.0
var is_online: bool = false
var slot_counter: int = 0
var player_name_input: LineEdit
var ip_input: LineEdit
var chat_input: LineEdit
var panel: PanelContainer
var player_rows: VBoxContainer
var status_label: Label
var chat_log: Label
var show_location_button: Button
var car_button: Button
var social_layer: CanvasLayer
var chat_lines: Array[String] = []


func configure(game_world: Node3D, avatar: CharacterBody3D) -> void:
    world = game_world
    local_player = avatar
    multiplayer.connected_to_server.connect(_on_connected_to_server)
    multiplayer.connection_failed.connect(_on_connection_failed)
    multiplayer.server_disconnected.connect(_on_server_lost)
    multiplayer.peer_disconnected.connect(_on_peer_left)
    _build_ui()


func _make_button(title: String, callback: Callable, container: Node) -> Button:
    var button := Button.new()
    button.text = title
    button.custom_minimum_size = Vector2(90, 48)
    button.pressed.connect(callback)
    container.add_child(button)
    return button


func _make_label(message: String, container: Node) -> Label:
    var label := Label.new()
    label.text = message
    label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    container.add_child(label)
    return label


func _build_ui() -> void:
    social_layer = CanvasLayer.new()
    world.add_child(social_layer)

    var toggle := Button.new()
    toggle.text = "FRIENDS / WORLD"
    toggle.position = Vector2(18, 112)
    toggle.custom_minimum_size = Vector2(176, 52)
    social_layer.add_child(toggle)
    toggle.pressed.connect(func(): panel.visible = not panel.visible)

    panel = PanelContainer.new()
    panel.anchor_left = 0.5
    panel.anchor_right = 0.5
    panel.anchor_top = 0.5
    panel.anchor_bottom = 0.5
    panel.offset_left = -320
    panel.offset_right = 320
    panel.offset_top = -255
    panel.offset_bottom = 255
    panel.visible = false
    social_layer.add_child(panel)

    var scroller := ScrollContainer.new()
    scroller.custom_minimum_size = Vector2(620, 495)
    scroller.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    panel.add_child(scroller)

    var rows := VBoxContainer.new()
    rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    rows.add_theme_constant_override("separation", 7)
    scroller.add_child(rows)

    var header := HBoxContainer.new()
    rows.add_child(header)
    _make_label("BLINK LIFE · SOCIAL TESTLAB", header)
    _make_button("CLOSE", func(): panel.visible = false, header)
    _make_label("Only trusted LAN sessions. Never share your real GPS or personal address.", rows)

    var network_row := HBoxContainer.new()
    rows.add_child(network_row)
    player_name_input = LineEdit.new()
    player_name_input.placeholder_text = "Nickname"
    player_name_input.text = "Blink Player"
    player_name_input.max_length = 22
    player_name_input.custom_minimum_size = Vector2(156, 48)
    network_row.add_child(player_name_input)
    ip_input = LineEdit.new()
    ip_input.placeholder_text = "Host LAN IP e.g. 192.168.1.5"
    ip_input.custom_minimum_size = Vector2(230, 48)
    network_row.add_child(ip_input)

    var connection_row := HBoxContainer.new()
    rows.add_child(connection_row)
    _make_button("HOST LAN", _host_lan, connection_row)
    _make_button("JOIN LAN", _join_lan, connection_row)
    _make_button("DISCONNECT", _disconnect, connection_row)

    status_label = _make_label("Offline single-player · no game server connected.", rows)
    var help := _make_label("Host shares their LOCAL Wi-Fi IP privately with a friend. No public matchmaking or account verification yet.", rows)
    help.add_theme_color_override("font_color", Color(0.80, 0.80, 0.84))

    var actions := HBoxContainer.new()
    rows.add_child(actions)
    _make_button("MY HOME", func(): _action("home", 0), actions)
    _make_button("TO CITY", func(): _action("city", 0), actions)
    car_button = _make_button("GET CAR", func(): _action("car", 0), actions)
    _make_button("LEAVE RIDE", func(): _action("exit_ride", 0), actions)
    _make_button("WAVE", func(): _action("wave", 0), actions)

    var privacy := HBoxContainer.new()
    rows.add_child(privacy)
    show_location_button = _make_button("VIRTUAL MAP: OFF", func(): _action("location", 0), privacy)
    _make_label("Opt-in virtual coordinates only. This does not access phone location.", privacy)

    var approvals := HBoxContainer.new()
    rows.add_child(approvals)
    _make_button("APPROVE HOME VISIT", _approve_visit, approvals)
    _make_button("APPROVE PASSENGER", _approve_ride, approvals)

    _make_label("PLAYERS (tap VISIT or RIDE to request permission)", rows)
    player_rows = VBoxContainer.new()
    rows.add_child(player_rows)

    var chat_row := HBoxContainer.new()
    rows.add_child(chat_row)
    chat_input = LineEdit.new()
    chat_input.placeholder_text = "Session chat (120 characters maximum)"
    chat_input.max_length = 120
    chat_input.custom_minimum_size = Vector2(392, 48)
    chat_row.add_child(chat_input)
    _make_button("SEND", _send_chat_from_ui, chat_row)
    chat_input.text_submitted.connect(func(_text): _send_chat_from_ui())
    chat_log = _make_label("No chat history. Messages disappear when this LAN session ends.", rows)
    _refresh_player_rows()


func open_social() -> void:
    panel.visible = true


func _clean_name(raw: String) -> String:
    var cleaned := raw.strip_edges().replace("\n", " ").replace("\r", " ")
    if cleaned.is_empty():
        return "Blink Player"
    return cleaned.substr(0, 22)


func _append_chat(message: String) -> void:
    chat_lines.append(message.substr(0, 150))
    if chat_lines.size() > 5:
        chat_lines.pop_front()
    chat_log.text = "\n".join(chat_lines)


func _host_lan() -> void:
    if is_online:
        status_label.text = "Leave your current session before hosting another."
        return
    var peer := ENetMultiplayerPeer.new()
    var error := peer.create_server(PORT, MAX_PLAYERS)
    if error != OK:
        status_label.text = "Couldn't host on port %d (error %d)." % [PORT, error]
        return
    multiplayer.multiplayer_peer = peer
    is_online = true
    slot_counter = 1
    people = {1: _make_profile(_clean_name(player_name_input.text), 0)}
    cars.clear()
    visit_requests.clear()
    ride_requests.clear()
    status_label.text = "Hosting on port %d. Friends must be on the same trusted Wi-Fi." % PORT
    _reconcile_people()
    _refresh_player_rows()


func _is_private_lan_ip(raw: String) -> bool:
    var parts := raw.split(".")
    if parts.size() != 4:
        return false
    var octets: Array[int] = []
    for part in parts:
        if not part.is_valid_int():
            return false
        var value := int(part)
        if value < 0 or value > 255:
            return false
        octets.append(value)
    return (
        octets[0] == 10 or
        (octets[0] == 172 and octets[1] >= 16 and octets[1] <= 31) or
        (octets[0] == 192 and octets[1] == 168) or
        (octets[0] == 127 and octets[1] == 0 and octets[2] == 0 and octets[3] == 1)
    )


func _join_lan() -> void:
    if is_online:
        status_label.text = "Leave your current session before joining."
        return
    var address := ip_input.text.strip_edges()
    if not _is_private_lan_ip(address):
        status_label.text = "Use a private LAN IPv4 only (10.x, 172.16-31.x, 192.168.x)."

        return
    var peer := ENetMultiplayerPeer.new()
    var error := peer.create_client(address, PORT)
    if error != OK:
        status_label.text = "Couldn't connect (error %d)." % error
        return
    multiplayer.multiplayer_peer = peer
    is_online = true
    status_label.text = "Connecting to trusted LAN host %s..." % address


func _on_connected_to_server() -> void:
    _register.rpc_id(1, _clean_name(player_name_input.text))
    status_label.text = "Connected to LAN host. Synchronizing characters..."


func _on_connection_failed() -> void:
    _disconnect()
    status_label.text = "Could not connect. Check Wi-Fi, address and host session."


func _on_server_lost() -> void:
    _disconnect()
    status_label.text = "Host disconnected. BLINK LIFE remains playable offline."


func _on_peer_left(peer_id: int) -> void:
    if not multiplayer.is_server():
        return
    if not people.has(peer_id):
        return
    var departed_slot := int(people[peer_id]["slot"])
    people.erase(peer_id)
    last_motion_ms.erase(peer_id)
    last_chat_ms.erase(peer_id)
    visit_requests.erase(peer_id)
    ride_requests.erase(peer_id)
    cars.erase(peer_id)
    for driver in cars.keys():
        if int(cars[driver]) == peer_id:
            cars[driver] = -1
    for profile_id in people.keys():
        var profile: Dictionary = people[profile_id]
        if int(profile.get("visiting", -1)) == departed_slot:
            profile["visiting"] = -1
            people[profile_id] = profile
            _teleport(profile_id, -1, false)
    _broadcast_roster()


func _disconnect() -> void:
    if multiplayer.multiplayer_peer != null:
        multiplayer.multiplayer_peer.close()
    multiplayer.multiplayer_peer = OfflineMultiplayerPeer.new()
    is_online = false
    people.clear()
    cars.clear()
    visit_requests.clear()
    ride_requests.clear()
    last_motion_ms.clear()
    last_chat_ms.clear()
    pending_visit_from = -1
    pending_ride_from = -1
    for entry in remote_avatars.values():
        entry.queue_free()
    remote_avatars.clear()
    for entry in vehicle_models.values():
        entry.queue_free()
    vehicle_models.clear()
    for entry in home_models.values():
        entry.queue_free()
    home_models.clear()
    if local_player != null:
        local_player.global_position = Vector3(0, 0.55, 0)
        local_player.velocity = Vector3.ZERO
    _refresh_player_rows()
    if status_label != null:
        status_label.text = "Offline single-player · no game server connected."


func _make_profile(display_name: String, room_slot: int) -> Dictionary:
    return {
        "name": display_name,
        "pos": Vector3(0, 0.5, 0),
        "rot": 0.0,
        "slot": room_slot,
        "visiting": -1,
        "map_opt_in": false,
        "car_enabled": false,
        "outfit": 0
    }


@rpc("any_peer", "reliable")
func _register(display_name: String) -> void:
    if not multiplayer.is_server():
        return
    var id := multiplayer.get_remote_sender_id()
    if id < 2 or people.has(id) or people.size() >= MAX_PLAYERS:
        return
    people[id] = _make_profile(_clean_name(display_name), slot_counter)
    slot_counter += 1
    _broadcast_roster()


func _broadcast_roster() -> void:
    if not multiplayer.is_server():
        return
    _apply_roster(people, cars)
    _receive_roster.rpc(people, cars)


@rpc("authority", "reliable")
func _receive_roster(roster: Dictionary, shared_cars: Dictionary) -> void:
    if multiplayer.get_remote_sender_id() != 1:
        return
    _apply_roster(roster, shared_cars)


func _apply_roster(roster: Dictionary, shared_cars: Dictionary) -> void:
    people = roster.duplicate(true)
    cars = shared_cars.duplicate(true)
    _reconcile_people()
    _refresh_player_rows()
    var my_id := multiplayer.get_unique_id()
    if people.has(my_id):
        var confirmed: Vector3 = people[my_id]["pos"]
        if local_player.global_position.distance_to(confirmed) > 16.0:
            local_player.global_position = confirmed
            local_player.velocity = Vector3.ZERO
        status_label.text = "LAN ONLINE · %d player(s) · virtual world only" % people.size()
        var my_profile: Dictionary = people[my_id]
        show_location_button.text = "VIRTUAL MAP: ON" if bool(my_profile.get("map_opt_in", false)) else "VIRTUAL MAP: OFF"
        car_button.text = "CAR READY" if bool(my_profile.get("car_enabled", false)) else "GET CAR"


func _reconcile_people() -> void:
    var my_id := multiplayer.get_unique_id()
    for id in remote_avatars.keys():
        if not people.has(id) or id == my_id:
            remote_avatars[id].queue_free()
            remote_avatars.erase(id)
    for id in home_models.keys():
        if not people.has(id):
            home_models[id].queue_free()
            home_models.erase(id)
    for id in vehicle_models.keys():
        if not cars.has(id):
            vehicle_models[id].queue_free()
            vehicle_models.erase(id)

    for id in people.keys():
        if not home_models.has(id):
            _create_home(id, int(people[id]["slot"]))
        if id != my_id and not remote_avatars.has(id):
            remote_avatars[id] = _create_remote_avatar(id, str(people[id]["name"]))
        if id == my_id:
            _apply_outfit(local_player, int(people[id].get("outfit", 0)))
        elif remote_avatars.has(id):
            _apply_outfit(remote_avatars[id], int(people[id].get("outfit", 0)))
    for driver in cars.keys():
        if not vehicle_models.has(driver):
            vehicle_models[driver] = _create_vehicle()


func _make_color(outfit: int) -> Color:
    var palette := [
        Color(0.11, 0.15, 0.22), Color(0.17, 0.37, 0.55),
        Color(0.46, 0.23, 0.42), Color(0.25, 0.45, 0.29)
    ]
    return palette[clampi(outfit, 0, palette.size() - 1)]


func _apply_outfit(avatar: Node3D, outfit: int) -> void:
    if avatar == null:
        return
    for child in avatar.get_children():
        if child is MeshInstance3D and child.material_override is StandardMaterial3D:
            child.material_override.albedo_color = _make_color(outfit)
            break


func _make_mesh_box(parent: Node3D, center: Vector3, size: Vector3, color: Color) -> void:
    var instance := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = size
    instance.mesh = mesh
    var surface := StandardMaterial3D.new()
    surface.albedo_color = color
    instance.material_override = surface
    instance.position = center
    parent.add_child(instance)


func _make_solid_box(parent: Node3D, center: Vector3, size: Vector3, color: Color) -> void:
    var body := StaticBody3D.new()
    body.position = center
    parent.add_child(body)
    _make_mesh_box(body, Vector3.ZERO, size, color)
    var shape := BoxShape3D.new()
    shape.size = size
    var collision := CollisionShape3D.new()
    collision.shape = shape
    body.add_child(collision)


func _create_remote_avatar(peer_id: int, display_name: String) -> Node3D:
    var actor := Node3D.new()
    actor.name = "LANPlayer_%d" % peer_id
    add_child(actor)
    _make_mesh_box(actor, Vector3(0, 0.85, 0), Vector3(0.62, 0.85, 0.42), _make_color(int(people[peer_id].get("outfit", 0))))
    _make_mesh_box(actor, Vector3(-0.17, 0.22, 0), Vector3(0.20, 0.44, 0.30), Color(0.20, 0.21, 0.25))
    _make_mesh_box(actor, Vector3(0.17, 0.22, 0), Vector3(0.20, 0.44, 0.30), Color(0.20, 0.21, 0.25))
    var head := MeshInstance3D.new()
    head.mesh = SphereMesh.new()
    head.position = Vector3(0, 1.53, 0)
    actor.add_child(head)
    var nameplate := Label3D.new()
    nameplate.text = display_name
    nameplate.font_size = 32
    nameplate.pixel_size = 0.005
    nameplate.position = Vector3(0, 2.1, 0)
    nameplate.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    actor.add_child(nameplate)
    actor.position = people[peer_id]["pos"]
    return actor


func _room_center(slot: int) -> Vector3:
    return Vector3(HOME_START_X + float(slot) * HOME_GAP, 0, HOME_Z)


func _create_home(owner: int, slot: int) -> void:
    var home := Node3D.new()
    home.name = "Home_%d" % owner
    add_child(home)
    home.global_position = _room_center(slot)
    var wall := Color(0.85, 0.81, 0.73)
    _make_solid_box(home, Vector3(0, -0.25, 0), Vector3(10.0, 0.5, 10.0), Color(0.58, 0.56, 0.51))
    _make_solid_box(home, Vector3(0, 1.75, -5), Vector3(10, 3.5, 0.3), wall)
    _make_solid_box(home, Vector3(-5, 1.75, 0), Vector3(0.3, 3.5, 10), wall)
    _make_solid_box(home, Vector3(5, 1.75, 0), Vector3(0.3, 3.5, 10), wall)
    _make_mesh_box(home, Vector3(0, 0.6, -3.5), Vector3(3, 1, 1.1), Color(0.35, 0.49, 0.61))
    _make_mesh_box(home, Vector3(2, 0.55, 1), Vector3(1.8, 0.8, 1.4), Color(0.48, 0.31, 0.25))
    var label := Label3D.new()
    label.text = str(people[owner]["name"]) + "'s demo home"
    label.font_size = 40
    label.pixel_size = 0.007
    label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    label.position = Vector3(0, 3.1, 0)
    home.add_child(label)
    home_models[owner] = home


func _create_vehicle() -> Node3D:
    var vehicle := Node3D.new()
    add_child(vehicle)
    _make_mesh_box(vehicle, Vector3(0, 0.53, 0), Vector3(1.9, 0.62, 3.15), Color(0.94, 0.57, 0.17))
    _make_mesh_box(vehicle, Vector3(0, 1.05, -0.2), Vector3(1.5, 0.55, 1.65), Color(0.27, 0.32, 0.39))
    for x in [-0.98, 0.98]:
        for z in [-0.95, 0.95]:
            _make_mesh_box(vehicle, Vector3(x, 0.30, z), Vector3(0.22, 0.60, 0.57), Color(0.09, 0.10, 0.12))
    return vehicle


func _process(delta: float) -> void:
    if not is_online or local_player == null:
        return
    var my_id := multiplayer.get_unique_id()
    # Prevent stepping into an unapproved player's home even locally.
    if people.has(my_id) and not _allowed_position(my_id, local_player.global_position):
        local_player.global_position = people[my_id]["pos"]
        local_player.velocity = Vector3.ZERO
        status_label.text = "Private home: request permission before entering."
    send_elapsed += delta
    if send_elapsed >= SYNC_SECONDS and people.has(my_id):
        send_elapsed = 0.0
        if multiplayer.is_server():
            _record_host_position()
        else:
            _send_position.rpc_id(1, local_player.global_position, local_player.rotation.y)

    for id in remote_avatars.keys():
        if not people.has(id):
            continue
        var actor: Node3D = remote_avatars[id]
        var state: Dictionary = people[id]
        actor.global_position = actor.global_position.lerp(state["pos"], minf(delta * 12.0, 1.0))
        actor.rotation.y = lerp_angle(actor.rotation.y, float(state["rot"]), minf(delta * 12.0, 1.0))

    for driver in vehicle_models.keys():
        if not people.has(driver):
            continue
        var car: Node3D = vehicle_models[driver]
        car.global_position = people[driver]["pos"]
        car.rotation.y = float(people[driver]["rot"])


func _record_host_position() -> void:
    if not people.has(1):
        return
    var profile: Dictionary = people[1]
    profile["pos"] = local_player.global_position
    profile["rot"] = local_player.rotation.y
    people[1] = profile
    _receive_position.rpc(1, local_player.global_position, local_player.rotation.y)


@rpc("any_peer", "unreliable")
func _send_position(position: Vector3, yaw: float) -> void:
    if not multiplayer.is_server():
        return
    var sender := multiplayer.get_remote_sender_id()
    if not people.has(sender):
        return
    if not position.is_finite() or absf(yaw) > 100000.0:
        return
    if not _allowed_position(sender, position):
        return
    var now := Time.get_ticks_msec()
    var previous: Dictionary = people[sender]
    var earlier := int(last_motion_ms.get(sender, 0))
    if earlier > 0:
        var elapsed := maxf(float(now - earlier) / 1000.0, 0.01)
        var distance := (position - previous["pos"]).length()
        if distance > elapsed * 24.0 + 2.0:
            return
    last_motion_ms[sender] = now
    previous["pos"] = position
    previous["rot"] = yaw
    people[sender] = previous
    _receive_position.rpc(sender, position, yaw)


func _allowed_position(sender: int, position: Vector3) -> bool:
    if position.y < -2.0 or position.y > 9.0:
        return false
    if absf(position.x) < WORLD_LIMIT and absf(position.z) < WORLD_LIMIT:
        return true
    var profile: Dictionary = people[sender]
    var own_slot := int(profile["slot"])
    var visiting := int(profile.get("visiting", -1))
    return _near_home(position, own_slot) or (visiting >= 0 and _near_home(position, visiting))


func _near_home(position: Vector3, slot: int) -> bool:
    var base := _room_center(slot)
    return absf(position.x - base.x) < 5.5 and absf(position.z - base.z) < 5.5


@rpc("authority", "unreliable")
func _receive_position(peer_id: int, position: Vector3, yaw: float) -> void:
    if multiplayer.get_remote_sender_id() != 1 or not people.has(peer_id):
        return
    var profile: Dictionary = people[peer_id]
    profile["pos"] = position
    profile["rot"] = yaw
    people[peer_id] = profile


func is_local_passenger() -> bool:
    if not is_online:
        return false
    var me := multiplayer.get_unique_id()
    for driver in cars.keys():
        if int(cars[driver]) == me:
            return true
    return false


func is_local_driver() -> bool:
    return is_online and cars.has(multiplayer.get_unique_id())


func passenger_position() -> Vector3:
    var me := multiplayer.get_unique_id()
    for driver in cars.keys():
        if int(cars[driver]) == me and people.has(driver):
            var pos: Vector3 = people[driver]["pos"]
            return pos + Vector3(1.1, 0.1, 0.0)
    return local_player.global_position


func _action(kind: String, target: int) -> void:
    if not is_online:
        status_label.text = "Host or join a trusted LAN session to use multiplayer features."
        return
    if multiplayer.is_server():
        _server_action(1, kind, target)
    else:
        _request_action.rpc_id(1, kind, target)


@rpc("any_peer", "reliable")
func _request_action(kind: String, target: int) -> void:
    if not multiplayer.is_server():
        return
    _server_action(multiplayer.get_remote_sender_id(), kind, target)


func _server_action(sender: int, kind: String, target: int) -> void:
    if not people.has(sender):
        return
    var profile: Dictionary = people[sender]
    match kind:
        "location":
            profile["map_opt_in"] = not bool(profile["map_opt_in"])
            people[sender] = profile
        "car":
            profile["car_enabled"] = true
            people[sender] = profile
            if not cars.has(sender):
                cars[sender] = -1
        "exit_ride":
            for driver in cars.keys():
                if int(cars[driver]) == sender:
                    cars[driver] = -1
        "home":
            _teleport(sender, int(profile["slot"]), true)
        "city":
            profile["visiting"] = -1
            people[sender] = profile
            _teleport(sender, -1, false)
        "visit":
            if target == sender or not people.has(target):
                return
            visit_requests[target] = sender
            _deliver_request(target, sender, "home")
            return
        "approve_visit":
            if int(visit_requests.get(sender, -1)) != target or not people.has(target):
                return
            visit_requests.erase(sender)
            var guest: Dictionary = people[target]
            guest["visiting"] = int(profile["slot"])
            people[target] = guest
            _teleport(target, int(profile["slot"]), true)
        "ride":
            if target == sender or not people.has(target) or not cars.has(target):
                return
            var source_pos: Vector3 = people[sender]["pos"]
            var driver_pos: Vector3 = people[target]["pos"]
            if source_pos.distance_to(driver_pos) > 7.0:
                _notify(sender, "Move near the vehicle before requesting a ride.")
                return
            ride_requests[target] = sender
            _deliver_request(target, sender, "ride")
            return
        "approve_ride":
            if int(ride_requests.get(sender, -1)) != target or not people.has(target) or not cars.has(sender):
                return
            if cars[sender] != -1:
                return
            var source_pos: Vector3 = people[sender]["pos"]
            var rider_pos: Vector3 = people[target]["pos"]
            if source_pos.distance_to(rider_pos) > 7.0:
                _notify(sender, "The rider is too far away.")
                return
            cars[sender] = target
            ride_requests.erase(sender)
        "wave":
            _wave.rpc(sender)
            _append_chat(str(profile["name"]) + " waved hello!")
            return
        "outfit":
            profile["outfit"] = posmod(int(profile["outfit"]) + 1, 4)
            people[sender] = profile
        _:
            return
    _broadcast_roster()


func _teleport(peer_id: int, slot: int, to_home: bool) -> void:
    var location := Vector3(0, 0.55, 0)
    if to_home:
        location = _room_center(slot) + Vector3(0, 0.55, 1.5)
    var profile: Dictionary = people[peer_id]
    profile["pos"] = location
    people[peer_id] = profile
    last_motion_ms.erase(peer_id)
    if peer_id == 1:
        local_player.global_position = location
        local_player.velocity = Vector3.ZERO
    else:
        _receive_teleport.rpc_id(peer_id, location)


@rpc("authority", "reliable")
func _receive_teleport(location: Vector3) -> void:
    if multiplayer.get_remote_sender_id() != 1:
        return
    local_player.global_position = location
    local_player.velocity = Vector3.ZERO
    status_label.text = "Arrived. Only approved friends can visit your demo home."


func _deliver_request(recipient: int, source: int, kind: String) -> void:
    if recipient == 1:
        _incoming_request(kind, source)
    else:
        _incoming_request.rpc_id(recipient, kind, source)


@rpc("authority", "reliable")
func _incoming_request(kind: String, source: int) -> void:
    if multiplayer.get_remote_sender_id() != 1 and not multiplayer.is_server():
        return
    var name: String = str(people.get(source, {}).get("name", "Friend"))
    if kind == "home":
        pending_visit_from = source
        status_label.text = "%s wants to visit your DEMO HOME. Approve or ignore." % name
    elif kind == "ride":
        pending_ride_from = source
        status_label.text = "%s wants a passenger seat. Approve or ignore." % name
    panel.visible = true


func _approve_visit() -> void:
    if pending_visit_from == -1:
        status_label.text = "No home visit requests pending."
        return
    _action("approve_visit", pending_visit_from)
    pending_visit_from = -1


func _approve_ride() -> void:
    if pending_ride_from == -1:
        status_label.text = "No passenger requests pending."
        return
    _action("approve_ride", pending_ride_from)
    pending_ride_from = -1


func _notify(peer_id: int, message: String) -> void:
    if peer_id == 1:
        status_label.text = message
    else:
        _receive_notice.rpc_id(peer_id, message)


@rpc("authority", "reliable")
func _receive_notice(message: String) -> void:
    if multiplayer.get_remote_sender_id() == 1:
        status_label.text = message.substr(0, 160)


@rpc("authority", "reliable")
func _wave(peer_id: int) -> void:
    if multiplayer.get_remote_sender_id() != 1:
        return
    if people.has(peer_id):
        _append_chat(str(people[peer_id]["name"]) + " waved hello!")


func _send_chat_from_ui() -> void:
    if not is_online:
        status_label.text = "Join a trusted LAN session to chat."
        return
    var message := chat_input.text.strip_edges()
    chat_input.clear()
    if message.is_empty():
        return
    if multiplayer.is_server():
        _server_chat(1, message)
    else:
        _send_chat.rpc_id(1, message)


@rpc("any_peer", "reliable")
func _send_chat(message: String) -> void:
    if multiplayer.is_server():
        _server_chat(multiplayer.get_remote_sender_id(), message)


func _server_chat(sender: int, message: String) -> void:
    if not people.has(sender):
        return
    var now := Time.get_ticks_msec()
    if last_chat_ms.has(sender) and now - int(last_chat_ms[sender]) < 1500:
        _notify(sender, "Please slow down before sending another message.")
        return
    var safe := message.strip_edges().replace("\n", " ").replace("\r", " ").substr(0, 120)
    if safe.is_empty():
        return
    last_chat_ms[sender] = now
    _append_chat(str(people[sender]["name"]) + ": " + safe)
    _receive_chat.rpc(sender, safe)


@rpc("authority", "reliable")
func _receive_chat(sender: int, message: String) -> void:
    if multiplayer.get_remote_sender_id() != 1 or not people.has(sender):
        return
    if muted_peers.has(sender):
        return
    _append_chat(str(people[sender]["name"]) + ": " + message.substr(0, 120))


func _refresh_player_rows() -> void:
    if player_rows == null:
        return
    for child in player_rows.get_children():
        child.queue_free()
    if people.is_empty():
        _make_label("No one online. Single-player jobs still work.", player_rows)
        return
    var my_id := multiplayer.get_unique_id()
    for peer_id in people.keys():
        var state: Dictionary = people[peer_id]
        var row := HBoxContainer.new()
        player_rows.add_child(row)
        var display := str(state["name"])
        if peer_id == my_id:
            display += " (you)"
        if bool(state.get("map_opt_in", false)):
            var where: Vector3 = state["pos"]
            display += " · virtual (%.0f, %.0f)" % [where.x, where.z]
        _make_label(display, row)
        if peer_id != my_id:
            _make_button("VISIT", func(): _action("visit", peer_id), row)
            _make_button("RIDE", func(): _action("ride", peer_id), row)
            var muted := muted_peers.has(peer_id)
            _make_button("UNMUTE" if muted else "MUTE", func(): _toggle_mute(peer_id), row)
    if is_online:
        _make_button("CHANGE OUTFIT", func(): _action("outfit", 0), player_rows)


func _toggle_mute(peer_id: int) -> void:
    if muted_peers.has(peer_id):
        muted_peers.erase(peer_id)
    else:
        muted_peers[peer_id] = true
    _refresh_player_rows()
