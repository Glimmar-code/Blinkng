extends SceneTree
## Integration harness ONLY: real local ENet sockets, authenticated tickets,
## two independent client processes and process-restart property ownership.
## This test never calls public services or accesses BLINK coin balances.

const SocialWorld = preload("res://scripts/social_world.gd")
const LIMIT_SECONDS: float = 20.0

var game_world: Node3D
var avatar: CharacterBody3D
var social: Node3D
var mode: String = ""
var expected_plot: int = -1
var elapsed: float = 0.0
var triggered_purchase: bool = false
var purchase_wait: float = 0.0


func _initialize() -> void:
    call_deferred("_bootstrap")


func _bootstrap() -> void:
    var args: PackedStringArray = OS.get_cmdline_user_args()
    for option in args:
        if option.begins_with("--blink-test-role="):
            mode = option.trim_prefix("--blink-test-role=")
        elif option.begins_with("--blink-test-expected-plot="):
            expected_plot = int(option.trim_prefix("--blink-test-expected-plot="))
    if not ["server", "owner", "peer", "reconnect", "invalid"].has(mode):
        _fail("Unknown test role")
        return
    game_world = Node3D.new()
    game_world.name = "IntegrationTestWorld"
    root.add_child(game_world)
    avatar = CharacterBody3D.new()
    avatar.name = "Player"
    avatar.position = Vector3(0, 0.5, 0)
    game_world.add_child(avatar)
    social = SocialWorld.new()
    social.name = "DedicatedTestSocial"
    game_world.add_child(social)
    social.configure(game_world, avatar)
    if mode == "server":
        if not bool(social.get("secure_dedicated")):
            _fail("Dedicated session failed to start")
            return
        print("ONLINE_SMOKE_SERVER_READY")
    else:
        if not bool(social.get("is_online")):
            _fail("Client ENet session failed to start")


func _process(delta: float) -> bool:
    elapsed += delta
    if social == null:
        return false
    if elapsed > LIMIT_SECONDS:
        if mode == "server":
            quit(0) # Test orchestrator kills it after observing clients.
        else:
            _fail("Timed out waiting for authenticated game state: " + mode)
        return false
    if mode == "server":
        return false
    if mode == "invalid":
        if elapsed > 3.0:
            if bool(social.get("is_connected_to_authoritative_server")):
                _fail("Invalid HMAC ticket was admitted")
            else:
                print("ONLINE_SMOKE_PASS:invalid")
                quit(0)
        return false
    if not bool(social.get("is_connected_to_authoritative_server")):
        return false

    var my_id: int = social.multiplayer.get_unique_id()
    var roster: Dictionary = social.get("people")
    if not roster.has(my_id):
        return false
    if mode == "reconnect":
        var current: Dictionary = roster[my_id]
        if bool(current.get("home_owned", false)) and int(current.get("slot", -1)) == expected_plot:
            print("ONLINE_SMOKE_PASS:reconnect:plot=" + str(expected_plot))
            quit(0)
        return false
    if not triggered_purchase:
        triggered_purchase = true
        social.call("_action", "buy_home", 0)
        return false
    var me: Dictionary = roster.get(my_id, {})
    if not bool(me.get("home_owned", false)) or int(me.get("slot", -1)) < 0:
        return false
    var owners: Dictionary = {}
    for user_id in roster.keys():
        var profile: Dictionary = roster[user_id]
        if bool(profile.get("home_owned", false)):
            var plot: int = int(profile.get("slot", -1))
            if plot < 0 or owners.has(plot):
                _fail("Duplicate or invalid authenticated virtual home plots")
                return false
            owners[plot] = true
    if owners.size() < 2:
        purchase_wait = 0.0
        return false
    # Give both independent clients time to observe the same authoritative
    # roster before either disconnects and changes the shared player count.
    purchase_wait += delta
    if purchase_wait < 2.0:
        return false
    var own_plot: int = int(me["slot"])
    print("ONLINE_SMOKE_PASS:" + mode + ":plot=" + str(own_plot))
    quit(0)
    return false


func _fail(message: String) -> void:
    printerr("ONLINE_SMOKE_FAILURE: " + message)
    quit(1)
