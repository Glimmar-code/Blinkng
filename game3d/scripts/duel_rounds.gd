extends RefCounted
## Testlab only: server-scored, consent-only 1v1 quiz in a trusted LAN session.
## No paid entry, no real-world rewards, no BLINK Coins, and no public matchmaking.
## The shared Godot project exposes questions to clients; this is not anti-cheat
## grade security and must not be advertised as a ranked public competition.

const QUESTION_TIMEOUT_MS: int = 30000
const QUESTIONS = [
    {"prompt": "What is 12 × 8?", "options": ["86", "96", "108"], "correct": 1},
    {"prompt": "Which is a renewable energy source?", "options": ["Coal", "Diesel", "Solar"], "correct": 2},
    {"prompt": "What is the binary form of decimal 5?", "options": ["101", "110", "111"], "correct": 0}
]

var matches: Dictionary = {}
var participant_matches: Dictionary = {}
var next_id: int = 1


func is_busy(peer_id: int) -> bool:
    return participant_matches.has(peer_id)


func are_opponents(first: int, second: int) -> bool:
    if not is_busy(first) or not is_busy(second):
        return false
    return int(participant_matches[first]) == int(participant_matches[second])


func start(first: int, second: int, now_ms: int) -> Dictionary:
    if first <= 0 or second <= 0 or first == second or is_busy(first) or is_busy(second):
        return {}
    var scores: Dictionary = {}
    scores[first] = 0
    scores[second] = 0
    var id := next_id
    next_id += 1
    matches[id] = {
        "first": first, "second": second, "round": 0, "answers": {},
        "scores": scores, "deadline": now_ms + QUESTION_TIMEOUT_MS
    }
    participant_matches[first] = id
    participant_matches[second] = id
    return _question_event(id)


func _question_event(id: int) -> Dictionary:
    var match_state: Dictionary = matches[id]
    var question: Dictionary = QUESTIONS[int(match_state["round"])]
    return {
        "type": "question",
        "id": id,
        "first": match_state["first"],
        "second": match_state["second"],
        "round": int(match_state["round"]) + 1,
        "total": QUESTIONS.size(),
        "prompt": question["prompt"],
        "options": question["options"].duplicate()
    }


func answer(peer_id: int, round_number: int, choice: int, now_ms: int) -> Dictionary:
    if not participant_matches.has(peer_id):
        return {}
    var id: int = int(participant_matches[peer_id])
    if not matches.has(id):
        return {}
    var match_state: Dictionary = matches[id]
    # A late/replayed answer from an earlier question must never score in a new round.
    # The deadline is checked using server time; a late answer does not advance play.
    if round_number != int(match_state["round"]) + 1 or now_ms > int(match_state["deadline"]):
        return {}
    if choice < 0 or choice >= 3:
        return {}
    var answers: Dictionary = match_state["answers"]
    if answers.has(peer_id):
        return {} # First answer counts; later packets can never change the score.
    answers[peer_id] = choice
    var question: Dictionary = QUESTIONS[int(match_state["round"])]
    if choice == int(question["correct"]):
        var scores: Dictionary = match_state["scores"]
        scores[peer_id] = int(scores[peer_id]) + 1
    if answers.has(int(match_state["first"])) and answers.has(int(match_state["second"])):
        return _advance(id, now_ms)
    return {"type": "waiting", "player": peer_id}


func _advance(id: int, now_ms: int) -> Dictionary:
    if not matches.has(id):
        return {}
    var match_state: Dictionary = matches[id]
    match_state["round"] = int(match_state["round"]) + 1
    if int(match_state["round"]) >= QUESTIONS.size():
        var scores: Dictionary = match_state["scores"]
        var first: int = int(match_state["first"])
        var second: int = int(match_state["second"])
        participant_matches.erase(first)
        participant_matches.erase(second)
        matches.erase(id)
        return {
            "type": "finished", "first": first, "second": second,
            "first_score": int(scores[first]), "second_score": int(scores[second]),
            "total": QUESTIONS.size()
        }
    match_state["answers"] = {}
    match_state["deadline"] = now_ms + QUESTION_TIMEOUT_MS
    return _question_event(id)


func tick(now_ms: int) -> Array[Dictionary]:
    var ready: Array[Dictionary] = []
    for id in matches.keys():
        if now_ms > int(matches[id]["deadline"]):
            ready.append(_advance(id, now_ms))
    return ready


func disconnect(peer_id: int) -> Dictionary:
    if not participant_matches.has(peer_id):
        return {}
    var id: int = int(participant_matches[peer_id])
    var match_state: Dictionary = matches.get(id, {})
    participant_matches.erase(peer_id)
    if match_state.is_empty():
        return {}
    var first: int = int(match_state["first"])
    var second: int = int(match_state["second"])
    participant_matches.erase(first)
    participant_matches.erase(second)
    matches.erase(id)
    return {"type": "cancelled", "first": first, "second": second, "missing": peer_id}
