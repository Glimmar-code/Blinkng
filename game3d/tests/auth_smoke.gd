extends SceneTree
## Dedicated account-ticket and server-only persistent-home tests.
## Paths are explicitly test-only; never touch a real game-save file.

var failed: bool = false


func check(condition: bool, label: String) -> void:
    if not condition:
        failed = true
        printerr("BLINK LIFE AUTH SMOKE FAILURE: " + label)


func run_test() -> void:
    var ticket_auth: Script = load("res://scripts/ticket_auth.gd")
    check(ticket_auth != null, "Ticket verifier must import")
    var secret := "a-demo-ONLY-sufficiently-long-secret-not-for-production-123456"
    var uid := "12345678-1234-4123-8123-123456789abc"
    var expires := int(Time.get_unix_time_from_system()) + 85
    var nonce := "abcdef0123456789abcdef0123456789"
    var unsigned := "%s.%d.%s" % [uid, expires, nonce]
    var digest := Crypto.new().hmac_digest(
        HashingContext.HASH_SHA256,
        secret.to_utf8_buffer(), unsigned.to_utf8_buffer()
    ).hex_encode()
    var ticket := unsigned + "." + digest
    var used: Dictionary = {}
    var issued: String = ticket_auth.call(
        "verify", ticket, secret, expires - 85, used
    )
    check(issued == uid, "Signed short-lived account ticket should verify")
    var replay: String = ticket_auth.call(
        "verify", ticket, secret, expires - 85, used
    )
    check(replay.is_empty(), "Replaying the same nonce must be denied")
    var incorrect: String = ticket_auth.call(
        "verify", ticket, "different-sufficiently-long-secret-1234567890", expires - 85, {}
    )
    check(incorrect.is_empty(), "Unknown signing key must be denied")
    var old: String = ticket_auth.call(
        "verify", ticket, secret, expires + 10, {}
    )
    check(old.is_empty(), "Expired ticket must be denied")

    var state_script: Script = load("res://scripts/persistent_state.gd")
    check(state_script != null, "Persistent state script must import")
    var store: RefCounted = state_script.new()
    var temp_path := "user://blink_life_auth_smoke_test.json"
    var temp_swap := temp_path + ".tmp"
    store.set("save_file", temp_path)
    store.set("temp_file", temp_swap)
    var default: Dictionary = store.call("get_profile", uid)
    check(int(default.get("cash", -1)) == 500, "New account starts with fictional GC")
    default["cash"] = 100
    default["house_owned"] = true
    default["furniture"] = 2
    check(bool(store.call("update_profile", uid, default)), "Trusted save must succeed")
    var reopened: RefCounted = state_script.new()
    reopened.set("save_file", temp_path)
    reopened.set("temp_file", temp_swap)
    reopened.call("load_from_disk")
    var restored: Dictionary = reopened.call("get_profile", uid)
    check(bool(restored.get("house_owned", false)), "Virtual house ownership survives restart")
    check(int(restored.get("furniture", -1)) == 2, "Furniture survives restart")
    check(int(restored.get("cash", -1)) == 100, "Fictional balance survives restart")
    DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_path))
    DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_swap))
    if failed:
        quit(1)
    else:
        print("BLINK LIFE ticket replay and dedicated property persistence smoke passed.")
        quit(0)


func _initialize() -> void:
    call_deferred("run_test")
