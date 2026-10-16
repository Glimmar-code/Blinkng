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
    check(_store().call("load_state") == Progress.valid_state(painted), "Saved decoration survives a fresh loader")
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
    var furnishing: Dictionary = floor_grid.merged({"chair_x": 1, "chair_z": -1,
        "chair_rotation": 1, "table_x": 3, "table_z": 2, "table_rotation": 2}, true)
    check(store.call("save_state", furnishing), "Chair and side table can be placed on valid separate cells")
    check(_store().call("load_state") == Progress.valid_state(furnishing), "Chair and table locations and rotations survive relaunch")
    check(not fresh.call("save_state", furnishing.merged({"table_x": 1, "table_z": -1}, true)),
        "Side table may not occupy the chair position")
    check(not fresh.call("save_state", furnishing.merged({"chair_x": -3, "chair_z": -3}, true)),
        "Chair cannot overlap the bed")
    check(not fresh.call("save_state", furnishing.merged({"table_x": 10}, true)),
        "Furniture cannot escape the bounded room grid")
    check(not fresh.call("save_state", furnishing.merged({"chair_rotation": 0.5}, true)),
        "Furniture rotation may not be fractional")
    check(_store().call("load_state") == Progress.valid_state(furnishing),
        "Blocked furniture placements never overwrite saved home state")
    for preset in range(3):
        var candidate: Dictionary = furnishing.duplicate()
        candidate["plant_custom"] = false
        candidate["plant_spot"] = 0
        candidate["bed_layout"] = preset
        candidate["wardrobe_layout"] = preset
        check(store.call("save_state", candidate), "Curated bed/wardrobe position %d must save safely" % preset)
        check(_store().call("load_state") == Progress.valid_state(candidate),
            "Bed/wardrobe layout %d must survive file reload" % preset)
    var bad_bed: Dictionary = furnishing.merged({"bed_layout": 3}, true)
    check(not fresh.call("save_state", bad_bed), "Bed position outside approved range is rejected")
    var bad_closet: Dictionary = furnishing.merged({"wardrobe_layout": -1}, true)
    check(not fresh.call("save_state", bad_closet), "Negative wardrobe position is rejected")
    check(not fresh.call("save_state", furnishing.merged({"bed_layout": 1.5}, true)),
        "Fractional bed layout IDs cannot be persisted")
    check(not fresh.call("save_state", furnishing.merged({"chair_x": -2, "chair_z": -2}, true)),
        "Furniture may not overlap a bed by moving into its footprint")
    var sanitized_furniture: Dictionary = Progress.valid_state(furnishing)
    var saved_bookmark: Dictionary = {}
    for key in Progress.DECOR_KEYS:
        saved_bookmark[key] = sanitized_furniture[key]
    var with_slot: Dictionary = furnishing.duplicate()
    with_slot["layout_slots"] = {"slot_1": saved_bookmark}
    check(store.call("save_state", with_slot), "Complete safe room layout bookmarks can save")
    check(_store().call("load_state") == Progress.valid_state(with_slot),
        "A saved room bookmark must survive a fresh load")
    var invalid_bookmark: Dictionary = saved_bookmark.duplicate()
    invalid_bookmark["cash"] = 1000000
    check(not store.call("save_state", with_slot.merged({"layout_slots": {"slot_1": invalid_bookmark}}, true)),
        "No money or reward fields may be included in a layout bookmark")
    check(not store.call("save_state", with_slot.merged({"layout_slots": {"slot_3": saved_bookmark}}, true)),
        "Only the two named private layout slots are permitted")
    check(not store.call("save_state", with_slot.merged({"layout_slots": {"slot_1": {"chair_x": 2}}}, true)),
        "Partial layout bookmarks cannot overwrite complete saved plans")
    var blocking_bookmark: Dictionary = saved_bookmark.duplicate()
    blocking_bookmark["table_x"] = int(blocking_bookmark["chair_x"])
    blocking_bookmark["table_z"] = int(blocking_bookmark["chair_z"])
    check(not store.call("save_state", with_slot.merged({"layout_slots": {"slot_1": blocking_bookmark}}, true)),
        "Saved bookmarks must not contain overlapping furniture")
    check(_store().call("load_state") == Progress.valid_state(with_slot),
        "Rejected bookmarks preserve the previous valid save and account fields")
    var bright_home: Dictionary = with_slot.duplicate(true)
    bright_home["light_mode"] = 2
    check(store.call("save_state", bright_home), "Three-state room lighting can persist")
    check(int(_store().call("load_state")["light_mode"]) == 2, "Room lighting survives reload")
    check(not store.call("save_state", bright_home.merged({"light_mode": 3}, true)),
        "Light preference outside three available levels is rejected")
    check(not store.call("save_state", bright_home.merged({"light_mode": -1}, true)),
        "Negative room light IDs are rejected")
    check(not store.call("save_state", bright_home.merged({"light_mode": 1.5}, true)),
        "Fractional room light IDs are rejected")
    check(int(_store().call("load_state")["light_mode"]) == 2,
        "Rejected light choices preserve valid saved preferences")
    check(int(Progress.valid_state(second)["light_mode"]) == 0,
        "Older solo saves without lighting preferences use default warm light")
    check(store.call("save_state", painted), "Restore preset placement for later rollback checks")
    check(not fresh.call("save_state", painted.merged({"wall_style": -1}, true)), "Negative palette is refused")
    check(not fresh.call("save_state", painted.merged({"floor_style": 3}, true)), "Out-of-range floor is refused")
    check(not fresh.call("save_state", painted.merged({"plant_spot": 1.5}, true)), "Fractional plant spot is refused")
    check(_store().call("load_state") == Progress.valid_state(painted), "Invalid decoration cannot overwrite good progress")
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
