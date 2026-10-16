extends Node3D
## BLINK LIFE social neighbourhood: opt-in LAN preview, NOT production matchmaking.
## Two devices on the same trusted local network can roam, chat, visit a home
## only after approval, and ride as passengers only after a driver approves.
## In-game coordinates only. No phone GPS, BLINK wallet or Supabase access.

const PORT: int = 24567
const MAX_PLAYERS: int = 8
const SYNC_SECONDS: float = 0.12
const REQUEST_COOLDOWN_MS: int = 10000
const REQUEST_EXPIRY_MS: int = 60000
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
var last_request_ms: Dictionary = {}
var blocked_peers: Dictionary = {} # Server-only per-session blocking; never published to roster.
var local_blocks: Dictionary = {} # This player's session UI state, not an account-wide block.
var cars: Dictionary = {}
var last_motion_ms: Dictionary = {}
var last_chat_ms: Dictionary = {}
var last_wave_ms: Dictionary = {}
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
var invite_button: Button
var social_layer: CanvasLayer
var chat_lines: Array[String] = []
var secure_dedicated: bool = false
var joining_secure: bool = false
var auth_secret: String = ""
var online_ticket: String = ""
var ticket_input: LineEdit
var online_host_input: LineEdit
var online_port_input: LineEdit
var used_ticket_nonces: Dictionary = {}
var peer_auth_ids: Dictionary = {}
var is_connected_to_authoritative_server: bool = false
var persistent_store: RefCounted
var last_work_ms: Dictionary = {}
var account_status: Label


func configure(game_world: Node3D, avatar: CharacterBody3D) -> void:
    world = game_world
    local_player = avatar
    multiplayer.connected_to_server.connect(_on_connected_to_server)
    multiplayer.connection_failed.connect(_on_connection_failed)
    multiplayer.server_disconnected.connect(_on_server_lost)
    multiplayer.peer_disconnected.connect(_on_peer_left)
    _build_ui()
    var args := OS.get_cmdline_user_args()
    if args.has("--blink-dedicated"):
        _start_dedicated_server()
    else:
        for arg in args:
            if arg.begins_with("--blink-host="):
                online_host_input.text = arg.trim_prefix("--blink-host=")
            elif arg.begins_with("--blink-port="):
                online_port_input.text = arg.trim_prefix("--blink-port=")
            elif arg.begins_with("--blink-ticket="):
                ticket_input.text = arg.trim_prefix("--blink-ticket=")
        # A ticket is only useful after deliberate Play; it must never trigger
        # a network connection merely because the main BLINK app opens.
        if not ticket_input.text.is_empty():
            _join_online()


func _start_dedicated_server() -> void:
    if OS.has_feature("web"):
        return # Browser-hosted on-demand game must never run a dedicated UDP server.
    auth_secret = OS.get_environment("BLINK_LIFE_AUTH_SECRET")
    var port_str := OS.get_environment("BLINK_LIFE_SERVER_PORT")
    var port := int(port_str) if port_str.is_valid_int() else PORT
    if auth_secret.length() < 32 or port < 1024 or port > 65535:
        push_error("Dedicated server disabled: missing secret or invalid configured port.")
        return
    var peer := ENetMultiplayerPeer.new()
    var result := peer.create_server(port, MAX_PLAYERS)
    if result != OK:
        push_error("Dedicated server unable to bind UDP port (code: %d)." % result)
        return
    multiplayer.multiplayer_peer = peer
    secure_dedicated = true
    is_online = true
    var store_script: Script = load("res://scripts/persistent_state.gd")
    persistent_store = store_script.new()
    persistent_store.call("load_from_disk")
    people.clear()
    slot_counter = 0
    status_label.text = "Dedicated session online: authenticated tickets required."
    # No fake host appears in the roster; only authenticated human peers.
    print("BLINK LIFE dedicated session listening on UDP port %d" % port)


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
    _make_label("Trusted LAN demo or ticket-authenticated dedicated server. Never share real GPS.", rows)

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

    var online_row := HBoxContainer.new()
    rows.add_child(online_row)
    online_host_input = LineEdit.new()
    online_host_input.placeholder_text = "Dedicated game host"
    online_host_input.custom_minimum_size = Vector2(190, 48)
    online_row.add_child(online_host_input)
    online_port_input = LineEdit.new()
    online_port_input.placeholder_text = "UDP port"
    online_port_input.text = "24567"
    online_port_input.custom_minimum_size = Vector2(80, 48)
    online_row.add_child(online_port_input)
    ticket_input = LineEdit.new()
    ticket_input.placeholder_text = "Signed BLINK game ticket"
    ticket_input.secret = true
    ticket_input.custom_minimum_size = Vector2(200, 48)
    online_row.add_child(ticket_input)
    _make_button("JOIN ONLINE", _join_online, rows)

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
    var property_actions := HBoxContainer.new()
    rows.add_child(property_actions)
    _make_button("BUY HOME (400 GC)", func(): _action("buy_home", 0), property_actions)
    _make_button("BUY CAR (300 GC)", func(): _action("buy_car", 0), property_actions)
    _make_button("FURNITURE (150 GC)", func(): _action("furnish", 0), property_actions)
    _make_button("DEPOT SHIFT (+80 GC)", func(): _action("work", 0), property_actions)
    account_status = _make_label("Persistent property needs a signed-in dedicated world.", rows)
    _make_button("WAVE", func(): _action("wave", 0), actions)

    var privacy := HBoxContainer.new()
    rows.add_child(privacy)
    invite_button = _make_button("INVITES: OFF", func(): _action("invites", 0), privacy)
    show_location_button = _make_button("VIRTUAL MAP: OFF", func(): _action("location", 0), privacy)
    _make_label("Opt-in virtual coordinates only. This does not access phone location.", privacy)

    var approvals := HBoxContainer.new()
    rows.add_child(approvals)
    _make_button("APPROVE HOME VISIT", _approve_visit, approvals)
    _make_button("DECLINE VISIT", _decline_visit, approvals)
    _make_button("APPROVE PASSENGER", _approve_ride, approvals)
    _make_button("DECLINE RIDE", _decline_ride, approvals)

    _make_label("PLAYERS (invites are OFF by default; session blocks are not permanent)", rows)
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
    if OS.has_feature("web"):
        status_label.text = "This on-demand Web version supports offline 3D play. Multiplayer requires the native Play version."
        return
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
    last_request_ms.clear()
    blocked_peers.clear()
    local_blocks.clear()
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
    if OS.has_feature("web"):
        status_label.text = "This on-demand Web version supports offline 3D play. Multiplayer requires the native Play version."
        return
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


func _join_online() -> void:
    if OS.has_feature("web"):
        status_label.text = "This on-demand Web version supports offline 3D play. Multiplayer requires the native Play version."
        return
    if is_online:
        status_label.text = "Disconnect first before opening another world."
        return
    var host := online_host_input.text.strip_edges()
    var port := int(online_port_input.text) if online_port_input.text.is_valid_int() else 0
    var ticket := ticket_input.text.strip_edges()
    if host.is_empty() or host.length() > 253 or host.contains("/") or host.contains(" ") or port < 1024 or port > 65535:
        status_label.text = "The dedicated game host or UDP port is invalid."
        return
    if ticket.length() < 110 or ticket.length() > 240 or ticket.split(".").size() != 4:
        status_label.text = "Request a new short-lived ticket while signed into BLINK."
        return
    var peer := ENetMultiplayerPeer.new()
    var code := peer.create_client(host, port)
    if code != OK:
        status_label.text = "Could not connect to the dedicated game server."
        return
    joining_secure = true
    online_ticket = ticket
    ticket_input.clear()
    multiplayer.multiplayer_peer = peer
    is_online = true
    status_label.text = "Connecting to authenticated dedicated session..."


func _on_connected_to_server() -> void:
    _register.rpc_id(1, _clean_name(player_name_input.text), online_ticket)
    online_ticket = ""
    status_label.text = "Connected. Waiting for game server to authenticate the ticket..."


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
    peer_auth_ids.erase(peer_id)
    last_work_ms.erase(peer_id)
    last_motion_ms.erase(peer_id)
    last_chat_ms.erase(peer_id)
    last_wave_ms.erase(peer_id)
    last_request_ms.erase(peer_id)
    blocked_peers.erase(peer_id)
    for blocker in blocked_peers.keys():
        blocked_peers[blocker].erase(peer_id)
    visit_requests.erase(peer_id)
    ride_requests.erase(peer_id)
    for target in visit_requests.keys():
        if int(visit_requests[target].get("source", -1)) == peer_id:
            visit_requests.erase(target)
    for target in ride_requests.keys():
        if int(ride_requests[target].get("source", -1)) == peer_id:
            ride_requests.erase(target)
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
    joining_secure = false
    online_ticket = ""
    is_connected_to_authoritative_server = false
    secure_dedicated = false
    auth_secret = ""
    peer_auth_ids.clear()
    used_ticket_nonces.clear()
    persistent_store = null
    last_work_ms.clear()
    people.clear()
    cars.clear()
    visit_requests.clear()
    ride_requests.clear()
    last_motion_ms.clear()
    last_chat_ms.clear()
    last_wave_ms.clear()
    last_request_ms.clear()
    blocked_peers.clear()
    local_blocks.clear()
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
        "allow_requests": false,
        "car_enabled": false,
        "outfit": 0,
        "home_owned": false,
        "vehicle_owned": false,
        "furniture": 0
    }


@rpc("any_peer", "reliable")
func _register(display_name: String, signed_ticket: String = "") -> void:
    if not multiplayer.is_server():
        return
    var id := multiplayer.get_remote_sender_id()
    if id < 2 or people.has(id) or people.size() >= MAX_PLAYERS:
        return
    var actual_name := _clean_name(display_name)
    if secure_dedicated:
        var auth_script: Script = load("res://scripts/ticket_auth.gd")
        var account_id: String = auth_script.verify(
            signed_ticket, auth_secret, int(Time.get_unix_time_from_system()), used_ticket_nonces
        )
        if account_id.is_empty() or peer_auth_ids.values().has(account_id):
            multiplayer.multiplayer_peer.disconnect_peer(id)
            return
        peer_auth_ids[id] = account_id
        # The editable nickname cannot impersonate a verified BLINK profile.
        actual_name = "BLINK-" + account_id.substr(0, 8)
    var member := _make_profile(actual_name, slot_counter)
    if secure_dedicated and persistent_store != null:
        var saved: Dictionary = persistent_store.call("get_profile", peer_auth_ids[id])
        member["home_owned"] = bool(saved.get("house_owned", false))
        member["vehicle_owned"] = bool(saved.get("vehicle_owned", false))
        member["furniture"] = int(saved.get("furniture", 0))
    else:
        member["home_owned"] = true # LAN demonstration has free temporary homes.
    people[id] = member
    slot_counter += 1
    if secure_dedicated:
        _send_private_wallet(id)
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
        status_label.text = "ONLINE · %d player(s) · in-game world only" % people.size()
        is_connected_to_authoritative_server = true
        var my_profile: Dictionary = people[my_id]
        show_location_button.text = "VIRTUAL MAP: ON" if bool(my_profile.get("map_opt_in", false)) else "VIRTUAL MAP: OFF"
        invite_button.text = "INVITES: ON" if bool(my_profile.get("allow_requests", false)) else "INVITES: OFF"
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
    var count: int = clampi(int(people[owner].get("furniture", 0)), 0, 12)
    for index in count:
        var column: int = index % 4
        var row: int = index / 4
        _make_mesh_box(
            home, Vector3(-3.5 + float(column) * 2.0, 0.35, 2.0 - float(row) * 2.0),
            Vector3(0.9, 0.7, 0.7), Color(0.32, 0.53, 0.54)
        )
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
        var previous_position: Vector3 = previous["pos"]
        var distance: float = position.distance_to(previous_position)
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


func _is_blocked(first: int, second: int) -> bool:
    return bool(blocked_peers.get(first, {}).get(second, false)) or \
        bool(blocked_peers.get(second, {}).get(first, false))


func _can_request(sender: int, target: int) -> bool:
    if sender == target or not people.has(sender) or not people.has(target):
        return false
    if _is_blocked(sender, target):
        return false
    return bool(people[target].get("allow_requests", false))


func _fresh_request(requests: Dictionary, owner: int, sender: int) -> bool:
    var invite: Dictionary = requests.get(owner, {})
    var issued: int = int(invite.get("issued", -1))
    return int(invite.get("source", -1)) == sender and issued >= 0 and \
        Time.get_ticks_msec() - issued <= REQUEST_EXPIRY_MS and \
        _can_request(sender, owner)


func _request_rate_ok(sender: int) -> bool:
    var now := Time.get_ticks_msec()
    if last_request_ms.has(sender) and now - int(last_request_ms[sender]) < REQUEST_COOLDOWN_MS:
        return false
    last_request_ms[sender] = now
    return true


func _server_action(sender: int, kind: String, target: int) -> void:
    if not people.has(sender):
        return
    var profile: Dictionary = people[sender]
    match kind:
        "invites":
            profile["allow_requests"] = not bool(profile.get("allow_requests", false))
            people[sender] = profile
            if not bool(profile["allow_requests"]):
                visit_requests.erase(sender)
                ride_requests.erase(sender)
        "block":
            if not people.has(target) or target == sender:
                return
            var blocked: Dictionary = blocked_peers.get(sender, {})
            if blocked.has(target):
                blocked.erase(target)
            else:
                blocked[target] = true
            blocked_peers[sender] = blocked
            if _is_blocked(sender, target):
                for pending in [visit_requests, ride_requests]:
                    for owner in pending.keys():
                        if (owner == sender and int(pending[owner].get("source", -1)) == target) or \
                            (owner == target and int(pending[owner].get("source", -1)) == sender):
                            pending.erase(owner)
                if cars.has(sender) and int(cars[sender]) == target:
                    cars[sender] = -1
                if cars.has(target) and int(cars[target]) == sender:
                    cars[target] = -1
        "location":
            profile["map_opt_in"] = not bool(profile["map_opt_in"])
            people[sender] = profile
        "car":
            if secure_dedicated and not bool(profile.get("vehicle_owned", false)):
                _notify(sender, "Purchase a virtual vehicle for 300 fictional GC first.")
                return
            profile["car_enabled"] = true
            people[sender] = profile
            if not cars.has(sender):
                cars[sender] = -1
        "exit_ride":
            for driver in cars.keys():
                if int(cars[driver]) == sender:
                    cars[driver] = -1
        "home":
            if secure_dedicated and not bool(profile.get("home_owned", false)):
                _notify(sender, "Purchase your permanent virtual home for 400 fictional GC first.")
                return
            _teleport(sender, int(profile["slot"]), true)
        "city":
            profile["visiting"] = -1
            people[sender] = profile
            _teleport(sender, -1, false)
        "visit":
            if not _can_request(sender, target):
                _notify(sender, "Home invitations are disabled or this player is unavailable.")
                return
            if not _request_rate_ok(sender):
                _notify(sender, "Please slow down before sending another invitation.")
                return
            if secure_dedicated and not bool(people[target].get("home_owned", false)):
                _notify(sender, "This player has not bought a virtual home yet.")
                return
            visit_requests[target] = {"source": sender, "issued": Time.get_ticks_msec()}
            _deliver_request(target, sender, "home")
            return
        "decline_visit":
            if _fresh_request(visit_requests, sender, target):
                visit_requests.erase(sender)
                _notify(target, "Your home visit request was declined.")
            return
        "approve_visit":
            if not _fresh_request(visit_requests, sender, target):
                visit_requests.erase(sender)
                return
            visit_requests.erase(sender)
            var guest: Dictionary = people[target]
            guest["visiting"] = int(profile["slot"])
            people[target] = guest
            _teleport(target, int(profile["slot"]), true)
        "ride":
            if not _can_request(sender, target) or not cars.has(target):
                _notify(sender, "Passenger invitations are disabled or this driver is unavailable.")
                return
            if not _request_rate_ok(sender):
                _notify(sender, "Please slow down before requesting another ride.")
                return
            var source_pos: Vector3 = people[sender]["pos"]
            var driver_pos: Vector3 = people[target]["pos"]
            if cars.values().has(sender):
                _notify(sender, "Leave your current ride before requesting another.")
                return
            if source_pos.distance_to(driver_pos) > 7.0:
                _notify(sender, "Move near the vehicle before requesting a ride.")
                return
            ride_requests[target] = {"source": sender, "issued": Time.get_ticks_msec()}
            _deliver_request(target, sender, "ride")
            return
        "decline_ride":
            if _fresh_request(ride_requests, sender, target):
                ride_requests.erase(sender)
                _notify(target, "Your passenger request was declined.")
            return
        "approve_ride":
            if not _fresh_request(ride_requests, sender, target) or not cars.has(sender):
                ride_requests.erase(sender)
                return
            if cars[sender] != -1 or cars.values().has(target):
                _notify(sender, "That passenger is already in another car.")
                ride_requests.erase(sender)
                return
            var source_pos: Vector3 = people[sender]["pos"]
            var rider_pos: Vector3 = people[target]["pos"]
            if source_pos.distance_to(rider_pos) > 7.0:
                _notify(sender, "The rider is too far away.")
                return
            cars[sender] = target
            ride_requests.erase(sender)
        "wave":
            var now := Time.get_ticks_msec()
            if last_wave_ms.has(sender) and now - int(last_wave_ms[sender]) < 2500:
                return
            last_wave_ms[sender] = now
            for recipient in people.keys():
                if _is_blocked(recipient, sender):
                    continue
                if recipient == 1:
                    if not muted_peers.has(sender):
                        _append_chat(str(profile["name"]) + " waved hello!")
                else:
                    _wave.rpc_id(recipient, sender)
            return
        "outfit":
            profile["outfit"] = posmod(int(profile["outfit"]) + 1, 4)
            people[sender] = profile
        "buy_car":
            if not secure_dedicated or persistent_store == null:
                _notify(sender, "Vehicle ownership needs an authenticated dedicated session.")
                return
            var account: String = str(peer_auth_ids.get(sender, ""))
            var saved: Dictionary = persistent_store.call("get_profile", account)
            if saved.is_empty():
                return
            if bool(saved.get("vehicle_owned", false)):
                _notify(sender, "You already own a virtual car. Tap GET CAR to use it.")
                return
            if int(saved.get("cash", 0)) < 300:
                _notify(sender, "You need 300 fictional GC to buy a virtual car.")
                return
            saved["cash"] = int(saved["cash"]) - 300
            saved["vehicle_owned"] = true
            if not bool(persistent_store.call("update_profile", account, saved)):
                _notify(sender, "Vehicle purchase could not be saved.")
                return
            profile["vehicle_owned"] = true
            people[sender] = profile
            _send_private_wallet(sender)
            _notify(sender, "Virtual vehicle purchased! Tap GET CAR to use it.")
        "buy_home":
            if not secure_dedicated or persistent_store == null:
                _notify(sender, "Permanent homes are only available in account-backed dedicated sessions.")
                return
            var account: String = str(peer_auth_ids.get(sender, ""))
            var saved: Dictionary = persistent_store.call("get_profile", account)
            if bool(saved.get("house_owned", false)):
                _notify(sender, "You already own this virtual home.")
                return
            if int(saved["cash"]) < 400:
                _notify(sender, "Earn more fictional GC from gameplay missions first.")
                return
            saved["cash"] = int(saved["cash"]) - 400
            saved["house_owned"] = true
            if not bool(persistent_store.call("update_profile", account, saved)):
                _notify(sender, "Save failed; ask the game server operator to check storage.")
                return
            profile["home_owned"] = true
            people[sender] = profile
            _send_private_wallet(sender)
        "furnish":
            if not secure_dedicated or persistent_store == null:
                _notify(sender, "Persistent furnishings require a dedicated world.")
                return
            var account: String = str(peer_auth_ids.get(sender, ""))
            var saved: Dictionary = persistent_store.call("get_profile", account)
            if not bool(saved.get("house_owned", false)) or int(saved.get("furniture", 0)) >= 12 or int(saved["cash"]) < 150:
                _notify(sender, "Own a home and keep at least 150 fictional GC for each furniture item (maximum 12).")
                return
            saved["cash"] = int(saved["cash"]) - 150
            saved["furniture"] = int(saved["furniture"]) + 1
            if not bool(persistent_store.call("update_profile", account, saved)):
                _notify(sender, "House inventory couldn't be saved.")
                return
            profile["furniture"] = int(saved["furniture"])
            people[sender] = profile
            if home_models.has(sender):
                home_models[sender].queue_free()
                home_models.erase(sender)
            _send_private_wallet(sender)
        "work":
            if not secure_dedicated or persistent_store == null:
                _notify(sender, "Persistent jobs require an authenticated dedicated session.")
                return
            var location: Vector3 = profile["pos"]
            var depot := Vector3(-6, 0.5, 12)
            if location.distance_to(depot) > 7.0:
                _notify(sender, "Walk to the delivery depot to begin a shift.")
                return
            var now := Time.get_ticks_msec()
            if last_work_ms.has(sender) and now - int(last_work_ms[sender]) < 30000:
                _notify(sender, "Your virtual job cooldown has not finished.")
                return
            last_work_ms[sender] = now
            var account: String = str(peer_auth_ids.get(sender, ""))
            var saved: Dictionary = persistent_store.call("get_profile", account)
            saved["cash"] = mini(int(saved["cash"]) + 80, 1000000)
            if not bool(persistent_store.call("update_profile", account, saved)):
                _notify(sender, "This job couldn't save its result.")
                return
            _send_private_wallet(sender)
            _notify(sender, "Shift complete: +80 fictional Game Cash.")
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


func _decline_visit() -> void:
    if pending_visit_from == -1:
        status_label.text = "No home visit requests pending."
        return
    _action("decline_visit", pending_visit_from)
    pending_visit_from = -1
    status_label.text = "Home visit declined."


func _decline_ride() -> void:
    if pending_ride_from == -1:
        status_label.text = "No passenger requests pending."
        return
    _action("decline_ride", pending_ride_from)
    pending_ride_from = -1
    status_label.text = "Passenger request declined."


func _send_private_wallet(peer_id: int) -> void:
    if not secure_dedicated or persistent_store == null:
        return
    var account: String = str(peer_auth_ids.get(peer_id, ""))
    var saved: Dictionary = persistent_store.call("get_profile", account)
    if saved.is_empty():
        return
    if peer_id == 1:
        _receive_private_wallet(int(saved["cash"]), bool(saved["house_owned"]), int(saved["furniture"]), bool(saved.get("vehicle_owned", false)))
    else:
        _receive_private_wallet.rpc_id(
            peer_id, int(saved["cash"]), bool(saved["house_owned"]), int(saved["furniture"]), bool(saved.get("vehicle_owned", false))
        )


@rpc("authority", "reliable")
func _receive_private_wallet(cash: int, owns_home: bool, furnishing: int, owns_vehicle: bool) -> void:
    if multiplayer.get_remote_sender_id() != 1:
        return
    if account_status != null:
        account_status.text = "My fictional GC: %d · Home: %s · Furniture: %d · Car: %s" % [
            cash, "owned" if owns_home else "not owned", furnishing,
            "owned" if owns_vehicle else "not owned"
        ]


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
    if secure_dedicated:
        _notify(sender, "Public session chat is disabled until moderation is enabled.")
        return
    var now := Time.get_ticks_msec()
    if last_chat_ms.has(sender) and now - int(last_chat_ms[sender]) < 1500:
        _notify(sender, "Please slow down before sending another message.")
        return
    var safe := message.strip_edges().replace("\n", " ").replace("\r", " ").substr(0, 120)
    if safe.is_empty():
        return
    last_chat_ms[sender] = now
    for recipient in people.keys():
        if _is_blocked(recipient, sender):
            continue
        if recipient == 1:
            if not muted_peers.has(sender):
                _append_chat(str(people[sender]["name"]) + ": " + safe)
        else:
            _receive_chat.rpc_id(recipient, sender, safe)


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
        var row := VBoxContainer.new()
        player_rows.add_child(row)
        var display := str(state["name"])
        if peer_id == my_id:
            display += " (you)"
        if bool(state.get("map_opt_in", false)):
            var where: Vector3 = state["pos"]
            display += " · virtual (%.0f, %.0f)" % [where.x, where.z]
        _make_label(display, row)
        if peer_id != my_id:
            var actions := HBoxContainer.new()
            row.add_child(actions)
            _make_button("VISIT", func(): _action("visit", peer_id), actions)
            _make_button("RIDE", func(): _action("ride", peer_id), actions)
            var blocked_here: bool = local_blocks.has(peer_id)
            _make_button("UNBLOCK" if blocked_here else "BLOCK", func(): _toggle_block(peer_id), actions)
            var muted := muted_peers.has(peer_id)
            _make_button("UNMUTE" if muted else "MUTE", func(): _toggle_mute(peer_id), actions)
    if is_online:
        _make_button("CHANGE OUTFIT", func(): _action("outfit", 0), player_rows)


func _toggle_block(peer_id: int) -> void:
    if not people.has(peer_id) or peer_id == multiplayer.get_unique_id():
        return
    if local_blocks.has(peer_id):
        local_blocks.erase(peer_id)
    else:
        local_blocks[peer_id] = true
    _action("block", peer_id)
    _refresh_player_rows()


func _toggle_mute(peer_id: int) -> void:
    if muted_peers.has(peer_id):
        muted_peers.erase(peer_id)
    else:
        muted_peers[peer_id] = true
    _refresh_player_rows()
