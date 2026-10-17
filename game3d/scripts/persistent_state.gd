extends RefCounted
## Authoritative, SINGLE-SERVER fictional property ledger.
## This file must only be loaded by the dedicated host, never the APK.
## This hardens restart recovery, not a distributed online property database.
const SAVE_FILE := "user://blink_life_houses_v1.json"
const BACKUP_FILE := "user://blink_life_houses_v1.json.bak"
const TEMP_FILE := "user://blink_life_houses_v1.json.tmp"
const SCHEMA_VERSION: int = 1
const MAX_BYTES: int = 2097152
const MAX_PROFILES: int = 10000
const MAX_CASH: int = 1000000

var save_file: String = SAVE_FILE
var backup_file: String = BACKUP_FILE
var temp_file: String = TEMP_FILE
var records: Dictionary = {}
var write_blocked: bool = false
var recovered_backup: bool = false
var warning: String = ""


static func valid_account_id(value: String) -> bool:
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


func _valid_account_id(value: String) -> bool:
    return valid_account_id(value)


static func _int_value(value: Variant, from_json: bool) -> bool:
    if typeof(value) == TYPE_INT:
        return true
    # JSON.parse() converts integer tokens to floats in Godot 4.3.
    if from_json and typeof(value) == TYPE_FLOAT:
        return is_finite(value) and value == floorf(value) and absf(value) <= 1000000.0
    return false


static func normalize_profile(value: Variant, from_json: bool = false) -> Dictionary:
    if not (value is Dictionary):
        return {}
    for key in value.keys():
        if not ["cash", "house_owned", "furniture"].has(str(key)):
            return {}
    var cash: Variant = value.get("cash", 500)
    var furniture: Variant = value.get("furniture", 0)
    var owned: Variant = value.get("house_owned", false)
    if not _int_value(cash, from_json) or not _int_value(furniture, from_json):
        return {}
    if typeof(owned) != TYPE_BOOL:
        return {}
    var balance: int = int(cash)
    var count: int = int(furniture)
    if balance < 0 or balance > MAX_CASH or count < 0 or count > 12:
        return {}
    if count > 0 and not bool(owned):
        return {}
    return {"cash": balance, "house_owned": owned, "furniture": count}


func _inspect_file(path: String) -> Dictionary:
    if not FileAccess.file_exists(path):
        return {"status": "missing"}
    var file := FileAccess.open(path, FileAccess.READ)
    if file == null:
        return {"status": "corrupt"}
    var bytes: int = file.get_length()
    if bytes <= 0 or bytes > MAX_BYTES:
        file.close()
        return {"status": "corrupt"}
    var raw: String = file.get_as_text()
    file.close()
    var parser := JSON.new()
    if parser.parse(raw) != OK or not (parser.data is Dictionary):
        return {"status": "corrupt"}
    var root: Dictionary = parser.data
    var profiles: Variant = root
    # Legacy v1 files were a bare account->profile dictionary. A new
    # envelope provides explicit schema checks without abandoning them.
    if root.has("schema") or root.has("profiles"):
        if not _int_value(root.get("schema"), true):
            return {"status": "corrupt"}
        var schema: int = int(root["schema"])
        if schema > SCHEMA_VERSION:
            return {"status": "future"}
        if schema != SCHEMA_VERSION or root.size() != 2:
            return {"status": "corrupt"}
        profiles = root.get("profiles")
    if not (profiles is Dictionary) or profiles.size() > MAX_PROFILES:
        return {"status": "corrupt"}
    var clean: Dictionary = {}
    for key in profiles.keys():
        if typeof(key) != TYPE_STRING or not valid_account_id(str(key)):
            return {"status": "corrupt"}
        var profile: Dictionary = normalize_profile(profiles[key], true)
        if profile.is_empty():
            return {"status": "corrupt"}
        clean[key] = profile
    return {"status": "ok", "records": clean}


func load_from_disk() -> void:
    records.clear()
    write_blocked = false
    recovered_backup = false
    warning = ""
    var main: Dictionary = _inspect_file(save_file)
    if main["status"] == "ok":
        records = main["records"]
        return
    if main["status"] == "future":
        write_blocked = true
        warning = "Newer server property schema found. Writes disabled to protect accounts."
        return
    var backup: Dictionary = _inspect_file(backup_file)
    if backup["status"] == "ok":
        records = backup["records"]
        recovered_backup = true
        warning = "Restored previous valid server property checkpoint."
        return
    if main["status"] == "missing" and backup["status"] == "missing":
        return
    write_blocked = true
    warning = "Server property files damaged/unreadable; writes disabled to prevent loss."
    push_warning(warning)


func get_profile(account_id: String) -> Dictionary:
    if not valid_account_id(account_id):
        return {}
    if not records.has(account_id):
        # Read-only recovery may still show a harmless default, but cannot
        # buy, furnish or earn persistent rewards until storage is repaired.
        records[account_id] = {"cash": 500, "house_owned": false, "furniture": 0}
    return records[account_id].duplicate(true)


func update_profile(account_id: String, profile: Dictionary) -> bool:
    if write_blocked or not valid_account_id(account_id):
        return false
    var clean: Dictionary = normalize_profile(profile)
    if clean.is_empty():
        return false
    if not records.has(account_id) and records.size() >= MAX_PROFILES:
        return false
    var previous: Dictionary = records.get(account_id, {}).duplicate(true)
    records[account_id] = clean
    if save_to_disk():
        return true
    if previous.is_empty():
        records.erase(account_id)
    else:
        records[account_id] = previous
    return false


func save_to_disk() -> bool:
    if write_blocked or records.size() > MAX_PROFILES:
        return false
    var payload: String = JSON.stringify({"schema": SCHEMA_VERSION, "profiles": records})
    if payload.to_utf8_buffer().size() > MAX_BYTES:
        warning = "Server property file full; writes disabled until scaled storage is ready."
        return false
    var file := FileAccess.open(temp_file, FileAccess.WRITE)
    if file == null:
        warning = "Dedicated server temporary property file cannot be opened."
        return false
    file.store_string(payload)
    file.flush()
    var disk_error: Error = file.get_error()
    file.close()
    if disk_error != OK:
        DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_file))
        warning = "Dedicated server property storage write failed."
        return false
    # The backup can only be replaced by a verified, readable checkpoint.
    # If primary is corrupt after a crash, retain the known-good backup.
    if _inspect_file(save_file)["status"] == "ok":
        var backup_result := DirAccess.copy_absolute(
            ProjectSettings.globalize_path(save_file),
            ProjectSettings.globalize_path(backup_file)
        )
        if backup_result != OK:
            DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_file))
            warning = "Property backup failed; previous progress preserved."
            return false
    var result := DirAccess.rename_absolute(
        ProjectSettings.globalize_path(temp_file),
        ProjectSettings.globalize_path(save_file)
    )
    if result != OK:
        DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_file))
        warning = "Dedicated server property checkpoint commit failed."
        return false
    recovered_backup = false
    warning = ""
    return true
