extends RefCounted
## Versioned save for the SOLO, FICTIONAL Game Cash tutorial only.
## Do not sync it to BLINK Coins, online inventory or competitive leaderboards.
## Files are untrusted and can be edited; the dedicated server NEVER loads them.
const SoloHome = preload("res://scripts/solo_home.gd")

const SAVE_PATH: String = "user://blink_life_solo_v1.json"
const BACKUP_PATH: String = "user://blink_life_solo_v1.json.bak"
const TEMP_PATH: String = "user://blink_life_solo_v1.json.tmp"
const MAX_BYTES: int = 4096
const MAX_CASH: int = 1000000
const MAX_JOBS: int = 100000

var save_path: String = SAVE_PATH
var backup_path: String = BACKUP_PATH
var temp_path: String = TEMP_PATH
var write_blocked: bool = false
var warning: String = ""


static func fresh_state() -> Dictionary:
    return {"version": 1, "cash": 500, "jobs": 0, "plant": false,
        "wall_style": 0, "floor_style": 0, "plant_spot": 0,
        "plant_custom": false, "plant_x": SoloHome.PLANT_DEFAULT_X,
        "plant_z": SoloHome.PLANT_DEFAULT_Z, "plant_rotation": 0,
        "chair_x": 2, "chair_z": -1, "chair_rotation": 0,
        "table_x": 2, "table_z": 2, "table_rotation": 0,
        "bed_layout": 0, "wardrobe_layout": 0}


static func _is_integer_number(value: Variant, from_json: bool) -> bool:
    if typeof(value) == TYPE_INT:
        return true
    # Godot JSON parses all numbers as floats. Only accept exact finite whole
    # numbers on disk; never accept a float when building a new transaction.
    if from_json and typeof(value) == TYPE_FLOAT:
        return is_finite(value) and absf(value) <= 1000000.0 and value == floorf(value)
    return false


static func valid_state(data: Variant, from_json: bool = false) -> Dictionary:
    if not (data is Dictionary):
        return {}
    if not _is_integer_number(data.get("version"), from_json) or int(data["version"]) != 1:
        return {}
    if not _is_integer_number(data.get("cash"), from_json) or not _is_integer_number(data.get("jobs"), from_json):
        return {}
    if typeof(data.get("plant")) != TYPE_BOOL:
        return {}
    var cash: int = int(data["cash"])
    var jobs: int = int(data["jobs"])
    if cash < 0 or cash > MAX_CASH or jobs < 0 or jobs > MAX_JOBS:
        return {}
    # Optional fields are backward compatible with existing v1 game saves.
    # Every supplied value is strictly checked, not clamped, to avoid
    # silently corrupting a player's saved decoration.
    var styles: Dictionary = {}
    for key in ["wall_style", "floor_style", "plant_spot"]:
        var value: Variant = data.get(key, 0)
        if not _is_integer_number(value, from_json):
            return {}
        var style: int = int(value)
        if style < 0 or style > 2:
            return {}
        styles[key] = style
    var plant_custom: Variant = data.get("plant_custom", false)
    if typeof(plant_custom) != TYPE_BOOL:
        return {}
    var plant_coordinates: Dictionary = {}
    for key in ["plant_x", "plant_z", "plant_rotation"]:
        var standard: int = 0
        if key == "plant_z":
            standard = SoloHome.PLANT_DEFAULT_Z
        var value: Variant = data.get(key, standard)
        if not _is_integer_number(value, from_json):
            return {}
        plant_coordinates[key] = int(value)
    var x: int = plant_coordinates["plant_x"]
    var z: int = plant_coordinates["plant_z"]
    var rotation: int = plant_coordinates["plant_rotation"]
    if abs(x) > SoloHome.GRID_LIMIT or abs(z) > SoloHome.GRID_LIMIT:
        return {}
    if rotation < 0 or rotation >= 4:
        return {}
    var furniture: Dictionary = {}
    for key in SoloHome.FURNITURE_DEFAULTS:
        var raw: Variant = data.get(key, SoloHome.FURNITURE_DEFAULTS[key])
        if not _is_integer_number(raw, from_json):
            return {}
        furniture[key] = int(raw)
    var layout: Dictionary = furniture.duplicate()
    layout["plant_custom"] = plant_custom
    layout["plant_x"] = x
    layout["plant_z"] = z
    if not SoloHome.layout_allowed(layout):
        return {}
    return {"version": 1, "cash": cash, "jobs": jobs, "plant": data["plant"],
        "wall_style": styles["wall_style"], "floor_style": styles["floor_style"],
        "plant_spot": styles["plant_spot"], "plant_custom": plant_custom,
        "plant_x": x, "plant_z": z, "plant_rotation": rotation,
        "chair_x": furniture["chair_x"], "chair_z": furniture["chair_z"],
        "chair_rotation": furniture["chair_rotation"], "table_x": furniture["table_x"],
        "table_z": furniture["table_z"], "table_rotation": furniture["table_rotation"],
        "bed_layout": furniture["bed_layout"], "wardrobe_layout": furniture["wardrobe_layout"]}


func _read_json(path: String) -> Variant:
    var source := FileAccess.open(path, FileAccess.READ)
    if source == null:
        return null
    var bytes: int = source.get_length()
    if bytes <= 0 or bytes > MAX_BYTES:
        source.close()
        return null
    var raw: String = source.get_as_text()
    source.close()
    # JSON.parse_string prints engine errors on corrupt inputs. The instance
    # parser returns a normal error code so recoverable save damage stays quiet.
    var parser := JSON.new()
    if parser.parse(raw) != OK:
        return null
    return parser.data


func load_state() -> Dictionary:
    write_blocked = false
    warning = ""
    if not FileAccess.file_exists(save_path):
        # Crash before rename: never accept an uncommitted .tmp file.
        return fresh_state()
    var data: Variant = _read_json(save_path)
    var loaded: Dictionary = valid_state(data, true)
    if not loaded.is_empty():
        return loaded
    if data is Dictionary and _is_integer_number(data.get("version"), true) and int(data["version"]) > 1:
        # Prevent older builds from overwriting newer saves.
        write_blocked = true
        warning = "A newer solo-save format was found. Progress will not be overwritten."
        return fresh_state()
    if FileAccess.file_exists(backup_path):
        var backup: Dictionary = valid_state(_read_json(backup_path), true)
        if not backup.is_empty():
            warning = "Recovered the last valid local progress backup."
            return backup
    warning = "Local progress was unreadable; starting an unsaved demo."
    # An unknown/corrupt primary without a valid backup must not be overwritten.
    write_blocked = true
    return fresh_state()


func save_state(candidate: Dictionary) -> bool:
    warning = ""
    if write_blocked:
        warning = "Save unavailable: preserve the existing progress file."
        return false
    var safe: Dictionary = valid_state(candidate)
    if safe.is_empty():
        warning = "Invalid solo progress was rejected."
        return false
    var file := FileAccess.open(temp_path, FileAccess.WRITE)
    if file == null:
        warning = "Not enough storage or permission to save local progress."
        return false
    file.store_string(JSON.stringify(safe))
    file.flush()
    var write_error: Error = file.get_error()
    file.close()
    if write_error != OK:
        DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_path))
        warning = "Local progress could not be written."
        return false
    # Retain the previous known-good save as a recovery checkpoint.
    var source_path: String = ProjectSettings.globalize_path(save_path)
    if FileAccess.file_exists(save_path) and not valid_state(_read_json(save_path), true).is_empty():
        if DirAccess.copy_absolute(source_path, ProjectSettings.globalize_path(backup_path)) != OK:
            DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_path))
            warning = "Backup failed; previous local progress was preserved."
            return false
    if DirAccess.rename_absolute(ProjectSettings.globalize_path(temp_path), source_path) != OK:
        DirAccess.remove_absolute(ProjectSettings.globalize_path(temp_path))
        warning = "Could not commit solo progress; previous progress was preserved."
        return false
    return true
