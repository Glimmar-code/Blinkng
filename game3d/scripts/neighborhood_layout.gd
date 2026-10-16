extends RefCounted
## Shared metric layout. One world unit is one metre; no device-specific scaling.
## Starter block only: district tags do not claim that full districts exist yet.

const GRID_METRES: float = 6.0
const BUILDING_WIDTH: float = 4.2
const ROAD_WIDTH: float = 4.6
const FOOTPATH_WIDTH: float = 1.4
const FOUNDATION_SIZE: Vector3 = Vector3(55.0, 0.6, 42.0)
const STREETS: Array[Dictionary] = [
    {"id": "market_walk", "name": "Market Walk", "position": Vector3(-11, 0, 0)},
    {"id": "campus_way", "name": "Campus Way", "position": Vector3(0, 0, -6)}
]


static func position_for(col: int, row: int) -> Vector3:
    return Vector3((col - 3) * GRID_METRES, 0.0, (row - 2) * GRID_METRES)


static func places() -> Array[Dictionary]:
    return [
        {"id": "campus", "name": "Campus", "col": 3, "row": 0, "height": 5.1, "color": Color(0.45, 0.51, 0.59), "district": "Campus Quarter", "address": "1 Campus Way"},
        {"id": "cafe", "name": "Café", "col": 0, "row": 1, "height": 3.1, "color": Color(0.75, 0.48, 0.36), "district": "Market Quarter", "address": "2 Market Walk"},
        {"id": "tech", "name": "Tech hub", "col": 6, "row": 1, "height": 4.4, "color": Color(0.40, 0.53, 0.67), "district": "Campus Quarter", "address": "8 Market Walk"},
        {"id": "market", "name": "Market", "col": 0, "row": 3, "height": 3.2, "color": Color(0.79, 0.65, 0.39), "district": "Market Quarter", "address": "3 Market Walk"},
        {"id": "depot", "name": "Delivery depot", "col": 2, "row": 4, "height": 3.2, "color": Color(0.50, 0.51, 0.59), "district": "Depot Quarter", "address": "5 Campus Way"},
        {"id": "hostel", "name": "Hostel", "col": 6, "row": 4, "height": 5.3, "color": Color(0.63, 0.55, 0.47), "district": "Residential Quarter", "address": "11 Market Walk"},
        {"id": "homes", "name": "Homes", "col": 4, "row": 3, "height": 3.8, "color": Color(0.73, 0.59, 0.42), "district": "Residential Quarter", "address": "7 Market Walk"}
    ]


static func entrance_for(place: Dictionary) -> Vector3:
    var location: Vector3 = position_for(int(place["col"]), int(place["row"]))
    # Frontages face Market Walk so the approach never passes through a building.
    var facing: float = 1.0 if location.z < 0.0 else -1.0
    return location + Vector3(0, 0, facing * (BUILDING_WIDTH / 2.0 + 0.65))


static func footpaths() -> Array[Dictionary]:
    var paths: Array[Dictionary] = []
    for side in [-1.0, 1.0]:
        paths.append({"center": Vector3(0, 0.04, side * 3.0), "size": Vector3(52, 0.05, FOOTPATH_WIDTH)})
        paths.append({"center": Vector3(side * 3.0, 0.04, 4.5), "size": Vector3(FOOTPATH_WIDTH, 0.05, 27)})
    for place in places():
        var entry: Vector3 = entrance_for(place)
        var road_edge: float = -3.0 if entry.z < 0.0 else 3.0
        var length: float = absf(entry.z - road_edge) + FOOTPATH_WIDTH
        paths.append({"center": Vector3(entry.x, 0.045, (entry.z + road_edge) / 2.0), "size": Vector3(FOOTPATH_WIDTH, 0.05, length)})
    return paths
