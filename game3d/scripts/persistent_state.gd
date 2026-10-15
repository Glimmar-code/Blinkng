extends RefCounted
## Small trusted-dedicated-server persistence adapter.
## Houses, cosmetic furniture and fictional GC survive process restarts only if
## the server's user:// storage is mounted to a persistent disk.
## This is not yet a distributed database/Supabase or real currency.
const SAVE_FILE := "user://blink_life_houses_v1.json"
const TEMP_FILE := "user://blink_life_houses_v1.json.tmp"

var records: Dictionary = {}


func load_from_disk() -> void:
    records.clear()
    if not FileAccess.file_exists(SAVE_FILE):
        return
    var file := FileAccess.open(SAVE_FILE, FileAccess.READ)
    if file == null:
        push_error("BLINK LIFE: cannot read dedicated-server house file")
        return
    var data = JSON.parse_string(file.get_as_text())
    file.close()
    if typeof(data) != TYPE_DICTIONARY:
        push_error("BLINK LIFE: invalid persisted house JSON")
        return
    for key in data.keys():
        var item = data[key]
        if typeof(item) != TYPE_DICTIONARY:
            continue
        if not _valid_account_id(str(key)):
            continue
        records[key] = {
            "cash": clampi(int(item.get("cash", 500)), 0, 1000000),
            "house_owned": bool(item.get("house_owned", false)),
            "furniture": clampi(int(item.get("furniture", 0)), 0, 12),
        }


func _valid_account_id(value: String) -> bool:
    if value.length() != 36:
        return false
    for index in value.length():
        var character: String = value.substr(index, 1)
        if index in [8, 13, 18, 23]:
            if character != "-":
                return false
        elif "0123456789abcdef".find(character) == -1:
            return false
    return true


func get_profile(account_id: String) -> Dictionary:
    if not _valid_account_id(account_id):
        return {}
    if not records.has(account_id):
        records[account_id] = {
            "cash": 500,
            "house_owned": false,
            "furniture": 0,
        }
    return records[account_id].duplicate(true)


func update_profile(account_id: String, profile: Dictionary) -> bool:
    if not _valid_account_id(account_id):
        return false
    var clean := {
        "cash": clampi(int(profile.get("cash", 0)), 0, 1000000),
        "house_owned": bool(profile.get("house_owned", false)),
        "furniture": clampi(int(profile.get("furniture", 0)), 0, 12),
    }
    records[account_id] = clean
    return save_to_disk()


func save_to_disk() -> bool:
    var file := FileAccess.open(TEMP_FILE, FileAccess.WRITE)
    if file == null:
        push_error("BLINK LIFE: could not open safe temporary house save")
        return false
    file.store_string(JSON.stringify(records))
    file.flush()
    file.close()
    var result := DirAccess.rename_absolute(
        ProjectSettings.globalize_path(TEMP_FILE),
        ProjectSettings.globalize_path(SAVE_FILE)
    )
    if result != OK:
        push_error("BLINK LIFE: could not commit house save")
        return false
    return true
