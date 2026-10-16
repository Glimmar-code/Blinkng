extends SceneTree
## Strict checks for device-local, fictional solo tutorial progress.
## Never run against the dedicated server's property ledger.

const Progress = preload("res://scripts/solo_progress.gd")
const MAIN: String = "user://blink_life_solo_ci.json"
const BAK: String = "user://blink_life_solo_ci.json.bak"
const TMP: String = "user://blink_life_solo_ci.json.tmp"
var failed: bool = false


func check(condition: bool, explanation: String) -> void:
    if not condition:
        failed = true
        printerr("SMOKE FAILURE: " + explanation)


func clean_files() -> void:
    for path in [MAIN, BAK, TMP]:
        if FileAccess.file_exists(path):
            DirAccess.remove_absolute(ProjectSettings.globalize_path(path))


func _store() -> RefCounted:
    var store := Progress.new()
    store.save_path = MAIN
    store.backup_path = BAK
    store.temp_path = TMP
    return store


func write_direct(path: String, contents: String) -> void:
    var stream := FileAccess.open(path, FileAccess.WRITE)
    if stream == null:
        check(false, "Test harness must be able to write isolated user:// files")
        return
    stream.store_string(contents)
    stream.close()


func _initialize() -> void:
    clean_files()
    var store: RefCounted = _store()
    check(store.call("load_state") == Progress.fresh_state(), "Fresh installs start with free demo funds")
    write_direct(TMP, JSON.stringify({"version": 1, "cash": 999999, "jobs": 5, "plant": true}))
    check(store.call("load_state") == Progress.fresh_state(), "Uncommitted temp files are never treated as saves")
    clean_files()

    var parsed_numbers: Dictionary = JSON.parse_string('{"version":1,"cash":620,"jobs":1,"plant":false}')
    check(typeof(parsed_numbers["cash"]) == TYPE_FLOAT, "Godot JSON reader yields floats for numeric fields")
    check(Progress.valid_state(parsed_numbers, true)["cash"] == 620, "Whole JSON float amounts restore as integers")
    check(Progress.valid_state({"version": 1.0, "cash": 500.5, "jobs": 1.0, "plant": false}, true).is_empty(), "Fractional on-disk currency cannot be accepted")
    check(Progress.valid_state(parsed_numbers).is_empty(), "Parsed floats cannot be sent as new reward transactions")

    var first: Dictionary = {"version": 1, "cash": 620, "jobs": 1, "plant": false}
    var second: Dictionary = {"version": 1, "cash": 370, "jobs": 1, "plant": true}
    check(store.call("save_state", first), "Valid first solo result must save")
    check(FileAccess.file_exists(MAIN), "A committed primary save must exist")
    check(store.call("save_state", second), "A second saved transaction must commit")
    check(FileAccess.file_exists(BAK), "Previous valid checkpoint must be backed up")

    var fresh: RefCounted = _store()
    check(fresh.call("load_state") == Progress.valid_state(second), "Old version-one saves load with valid default decoration")
    check(Progress.valid_state(second)["wall_style"] == 0, "Old saves default to original wall palette")
    var painted: Dictionary = {"version": 1, "cash": 370, "jobs": 1, "plant": true,
        "wall_style": 2, "floor_style": 1, "plant_spot": 2}
    check(store.call("save_state", painted), "Players can save three explicit decoration choices")
    check(_store().call("load_state") == painted, "Saved decoration survives a fresh loader")
    var floor_grid: Dictionary = painted.merged({"plant_custom": true,
        "plant_x": -1, "plant_z": 3, "plant_rotation": 3}, true)
    check(store.call("save_state", floor_grid), "Safe floor-grid plant and orientation can be stored")
    check(_store().call("load_state") == Progress.valid_state(floor_grid), "Free grid and rotation survive restart")
    check(not fresh.call("save_state", floor_grid.merged({"plant_x": -3, "plant_z": -3}, true)), "Furniture-occupied grid cells cannot be saved")
    check(not fresh.call("save_state", floor_grid.merged({"plant_x": 4}, true)), "Outside-room grid cells cannot be saved")
    check(not fresh.call("save_state", floor_grid.merged({"plant_rotation": 4}, true)), "Rotation outside four quarter-turns is rejected")
    check(not fresh.call("save_state", floor_grid.merged({"plant_rotation": 0.5}, true)), "Fractional rotation cannot be saved")
    check(not fresh.call("save_state", floor_grid.merged({"plant_custom": "yes"}, true)), "Custom-grid flag must be Boolean")
    check(_store().call("load_state") == Progress.valid_state(floor_grid), "Rejected grid saves preserve the last valid layout")
    check(store.call("save_state", painted), "Restore preset placement for later rollback checks")
    check(not fresh.call("save_state", painted.merged({"wall_style": -1}, true)), "Negative palette is refused")
    check(not fresh.call("save_state", painted.merged({"floor_style": 3}, true)), "Out-of-range floor is refused")
    check(not fresh.call("save_state", painted.merged({"plant_spot": 1.5}, true)), "Fractional plant spot is refused")
    check(_store().call("load_state") == painted, "Invalid decoration cannot overwrite good progress")
    # Put an older-format save back so the existing rollback checks remain meaningful.
    check(store.call("save_state", second), "Legacy shaped solo saves can still be committed")
    check(not fresh.call("save_state", {"version": 1, "cash": -1, "jobs": 0, "plant": false}), "Negative cash must be rejected")
    check(not fresh.call("save_state", {"version": 1, "cash": 1000001, "jobs": 0, "plant": false}), "Excessive balance must be rejected")
    check(not fresh.call("save_state", {"version": 1, "cash": 500.0, "jobs": 0, "plant": false}), "Float input cannot masquerade as integer money")
    check(not fresh.call("save_state", {"version": 1, "cash": 500, "jobs": 0, "plant": "yes"}), "Nonboolean ownership is invalid")
    check(not fresh.call("save_state", {"version": 1.0, "cash": 500, "jobs": 0, "plant": false}), "Schema version must be an integer")
    check(not fresh.call("save_state", {"version": 2, "cash": 500, "jobs": 0, "plant": false}), "Unknown formats are rejected")
    check(fresh.call("load_state") == Progress.valid_state(second), "Rejected writes cannot alter saved progress")

    # Recreate the initial two transaction checkpoints before recovery tests.
    clean_files()
    check(store.call("save_state", first), "Recovery fixture begins with first checkpoint")
    check(store.call("save_state", second), "Recovery fixture preserves prior balance")

    write_direct(MAIN, "{broken-json")
    var fallback: RefCounted = _store()
    check(fallback.call("load_state") == Progress.valid_state(first), "Corrupt primary must recover prior known-good checkpoint")
    check(str(fallback.get("warning")).contains("backup"), "Players get a backup restoration explanation")
    check(fallback.call("save_state", second), "Recovered progress can be committed safely")
    check(_store().call("load_state") == Progress.valid_state(second), "Recovered save reloads after safe commit")

    var newer: Dictionary = {"version": 2, "cash": 500, "jobs": 7, "plant": true}
    write_direct(MAIN, JSON.stringify(newer))
    var old_client: RefCounted = _store()
    check(old_client.call("load_state") == Progress.fresh_state(), "Unknown future versions never execute")
    check(bool(old_client.get("write_blocked")), "Future versions make older builds read-only")
    check(not old_client.call("save_state", first), "Older clients cannot overwrite newer save formats")
    var stream := FileAccess.open(MAIN, FileAccess.READ)
    check(stream != null, "Future save remains present")
    if stream != null:
        var future_data: Dictionary = JSON.parse_string(stream.get_as_text())
        check(int(future_data.get("version", -1)) == 2 and int(future_data.get("jobs", -1)) == 7 and bool(future_data.get("plant", false)), "Future progress must survive an older client")
        stream.close()

    write_direct(MAIN, "invalid")
    write_direct(BAK, "invalid")
    var bad: RefCounted = _store()
    check(bad.call("load_state") == Progress.fresh_state(), "Two corrupt files fall back to safe defaults")
    check(bool(bad.get("write_blocked")), "Unrecoverable corruption never silently destroys the original")
    check(not bad.call("save_state", first), "Corrupted originals are write-protected")
    clean_files()
    if not failed:
        print("BLINK LIFE solo progress: versioned saves, backups, strict validation and rollback smoke passed.")
    quit(1 if failed else 0)
