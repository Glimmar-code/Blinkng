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

    var online_script: Script = load("res://scripts/social_world.gd")
    check(online_script != null, "Authoritative dedicated server script imports")
    check(not bool(online_script.call("auth_timed_out", 500, 10499)),
        "Public game authentication retains its fixed ten-second window")
    check(bool(online_script.call("auth_timed_out", 500, 10500)),
        "Unauthenticated network peers must expire after ten seconds")
    check(not bool(online_script.call("auth_timed_out", 500, 499)),
        "Clock anomalies cannot prematurely expire network admission")

    var social: Node3D = online_script.new()
    var profiles: Dictionary = {
        2: {"slot": 0, "visiting": -1, "home_owned": true},
        3: {"slot": 1, "visiting": 0, "home_owned": false}
    }
    social.set("people", profiles)
    var home_invites: Dictionary = {2: 3}
    var ride_invites: Dictionary = {2: 3}
    social.set("visit_requests", home_invites)
    social.set("ride_requests", ride_invites)
    check(bool(social.call("_invitations_allowed", 3, 2)),
        "Initial account-approved session players can request visits")
    check(bool(social.call("_block_invitations", 2, 3, true)),
        "Homeowner can block unwanted future visits and rides")
    check(not bool(social.call("_invitations_allowed", 3, 2)),
        "Server refuses home invites when either player blocks")
    check(not bool(social.call("_invitations_allowed", 2, 3)),
        "Server refuses the blocked relationship in both directions")
    check((social.get("visit_requests") as Dictionary).is_empty() and
        (social.get("ride_requests") as Dictionary).is_empty(),
        "Blocking revokes all previous pending invites")
    check(int((social.get("people") as Dictionary)[3]["visiting"]) == -1,
        "Blocking a guest revokes the previous home authorization")
    check(not bool(social.call("_block_invitations", 2, 2, true)),
        "A user cannot block themselves")
    check(not bool(social.call("_block_invitations", 2, 99, true)),
        "Nonexistent multiplayer identities cannot be blocked")
    check(bool(social.call("_block_invitations", 2, 3, false)),
        "Blocked invites can be re-enabled explicitly")
    check(bool(social.call("_invitations_allowed", 3, 2)),
        "Unblock makes new requests possible without restoring old visits")
    social.queue_free()

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
    check(not bool(reopened.call("update_profile", uid,
        {"cash": -2, "house_owned": true, "furniture": 2})),
        "Negative fictional server cash cannot enter the trusted ledger")
    check(not bool(reopened.call("update_profile", uid,
        {"cash": 100, "house_owned": "yes", "furniture": 2})),
        "String property ownership is not valid")
    check(not bool(reopened.call("update_profile", uid,
        {"cash": 100, "house_owned": false, "furniture": 1})),
        "Furniture cannot exist without an owned home")
    check(not bool(reopened.call("update_profile", uid,
        {"cash": 100, "house_owned": true, "furniture": 2, "admin": true})),
        "Unknown or privileged player-record fields cannot be saved")
    var backup_path: String = temp_path + ".bak"
    reopened.set("backup_file", backup_path)
    var changed: Dictionary = restored.duplicate()
    changed["cash"] = 80
    check(bool(reopened.call("update_profile", uid, changed)),
        "Second valid transaction commits with backup")
    check(FileAccess.file_exists(backup_path),
        "Previous committed ledger is saved as backup checkpoint")
    # A corrupt current snapshot must never erase the last good state.
    var broken := FileAccess.open(temp_path, FileAccess.WRITE)
    check(broken != null, "Server test harness can simulate a corrupt primary")
    if broken != null:
        broken.store_string("{bad-property-json")
        broken.close()
    var fallback: RefCounted = state_script.new()
    fallback.set("save_file", temp_path)
    fallback.set("temp_file", temp_swap)
    fallback.set("backup_file", backup_path)
    fallback.call("load_from_disk")
    check(bool(fallback.get("recovered_backup")), "Previous valid property checkpoint is recovered")
    check(int(fallback.call("get_profile", uid)["cash"]) == 100,
        "Recovery restores account's prior server-owned balance")
    check(bool(fallback.call("update_profile", uid, changed)),
        "Recovered property state can safely commit again")
    var reread: RefCounted = state_script.new()
    reread.set("save_file", temp_path)
    reread.set("temp_file", temp_swap)
    reread.set("backup_file", backup_path)
    reread.call("load_from_disk")
    check(int(reread.call("get_profile", uid)["cash"]) == 80,
        "Property changes survive another dedicated-server restart")
    # Newer clients may persist a future incompatible record schema.
    # Old dedicated servers must fail closed instead of overwriting it.
    var future_profiles: Dictionary = {}
    future_profiles[uid] = changed
    var future := FileAccess.open(temp_path, FileAccess.WRITE)
    if future != null:
        future.store_string(JSON.stringify({"schema": 2, "profiles": future_profiles}))
        future.close()
    var stale_server: RefCounted = state_script.new()
    stale_server.set("save_file", temp_path)
    stale_server.set("temp_file", temp_swap)
    stale_server.set("backup_file", backup_path)
    stale_server.call("load_from_disk")
    check(bool(stale_server.get("write_blocked")),
        "Future property schema blocks all writes, even if backup exists")
    check(not bool(stale_server.call("update_profile", uid, changed)),
        "Server cannot overwrite a newer ledger")
    var check_future := FileAccess.open(temp_path, FileAccess.READ)
    if check_future != null:
        check(int(JSON.parse_string(check_future.get_as_text())["schema"]) == 2,
            "Newer property's actual file remains untouched")
        check_future.close()
    # Backwards compatibility: original saved records had a bare UUID map.
    DirAccess.remove_absolute(ProjectSettings.globalize_path(backup_path))
    var legacy_profiles: Dictionary = {}
    legacy_profiles[uid] = {"cash": 50, "house_owned": true, "furniture": 1}
    var legacy := FileAccess.open(temp_path, FileAccess.WRITE)
    if legacy != null:
        legacy.store_string(JSON.stringify(legacy_profiles))
        legacy.close()
    var imported: RefCounted = state_script.new()
    imported.set("save_file", temp_path)
    imported.set("temp_file", temp_swap)
    imported.set("backup_file", backup_path)
    imported.call("load_from_disk")
    check(int(imported.call("get_profile", uid)["cash"]) == 50,
        "Original bare-dictionary server home saves still load")
    check(bool(imported.call("update_profile", uid,
        {"cash": 40, "house_owned": true, "furniture": 1})),
        "Legacy ledger transparently upgrades when it next saves")
    # No backup must mean corrupted primary cannot be silently reset.
    DirAccess.remove_absolute(ProjectSettings.globalize_path(backup_path))
    var poisoned_profiles: Dictionary = {}
    poisoned_profiles[uid] = {"cash": "infinite", "house_owned": true, "furniture": 2}
    var poisoned := FileAccess.open(temp_path, FileAccess.WRITE)
    if poisoned != null:
        poisoned.store_string(JSON.stringify({"schema": 1, "profiles": poisoned_profiles}))
        poisoned.close()
    var protected: RefCounted = state_script.new()
    protected.set("save_file", temp_path)
    protected.set("temp_file", temp_swap)
    protected.set("backup_file", backup_path)
    protected.call("load_from_disk")
    check(bool(protected.get("write_blocked")),
        "Unrecoverably invalid property records fail closed")
    check(not bool(protected.call("update_profile", uid, changed)),
        "Corrupt server storage cannot create a phantom balance")
    DirAccess.remove_absolute(ProjectSettings.globalize_path(backup_path))
    DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_path))
    DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_swap))
    if failed:
        quit(1)
    else:
        print("BLINK LIFE ticket replay and dedicated property persistence smoke passed.")
        quit(0)


func _initialize() -> void:
    call_deferred("run_test")
