extends SceneTree
## Pure server-side 1v1 quiz state checks. No real accounts, stakes or coins.

var failed: bool = false


func check(condition: bool, detail: String) -> void:
    if not condition:
        failed = true
        printerr("BLINK LIFE DUEL SMOKE FAILURE: " + detail)


func run_test() -> void:
    var logic: Script = load("res://scripts/duel_rounds.gd")
    check(logic != null, "Duel service must load")
    if logic == null:
        quit(1)
        return

    var duels: RefCounted = logic.new()
    var first: Dictionary = duels.call("start", 2, 3, 1000)
    check(first.get("type", "") == "question", "A two-person match starts with a quiz question")
    check(int(first.get("round", -1)) == 1, "First round is one")
    check(not first.has("correct"), "Correct answers must not be included in server messages")
    check(bool(duels.call("is_busy", 2)), "First player must be marked busy")
    check(bool(duels.call("is_busy", 3)), "Second player must be marked busy")
    check(duels.call("start", 2, 4, 1001).is_empty(), "A player cannot enter two matches")
    check(duels.call("start", 2, 2, 1001).is_empty(), "Cannot challenge oneself")
    check(duels.call("answer", 4, 1, 1100).is_empty(), "Unregistered player cannot score")
    check(duels.call("answer", 2, -1, 1100).is_empty(), "Negative option index must be rejected")
    check(duels.call("answer", 2, 10, 1100).is_empty(), "Out-of-bounds options must be rejected")

    var answer: Dictionary = duels.call("answer", 2, 0, 1100)
    check(answer.get("type", "") == "waiting", "First answer waits for opponent")
    check(duels.call("answer", 2, 1, 1101).is_empty(), "Duplicate answers cannot replace a prior choice")
    answer = duels.call("answer", 3, 1, 1102)
    check(int(answer.get("round", -1)) == 2, "Two recorded answers start second question")

    answer = duels.call("answer", 2, 2, 1200)
    check(answer.get("type", "") == "waiting", "Second-round answer is recorded")
    answer = duels.call("answer", 3, 0, 1201)
    check(int(answer.get("round", -1)) == 3, "Both answers progress to final question")

    answer = duels.call("answer", 2, 0, 1300)
    answer = duels.call("answer", 3, 2, 1301)
    check(answer.get("type", "") == "finished", "Three rounds finish deterministically")
    check(int(answer.get("first_score", -1)) == 2, "Server counts only first participant's correct answers")
    check(int(answer.get("second_score", -1)) == 1, "Server counts second participant's correct answers")
    check(not bool(duels.call("is_busy", 2)), "Finished match releases first player")
    check(not bool(duels.call("is_busy", 3)), "Finished match releases second player")

    var second: Dictionary = duels.call("start", 4, 5, 2000)
    check(second.get("type", "") == "question", "Match can start after prior completed")
    var timed: Array[Dictionary] = duels.call("tick", 32001)
    check(timed.size() == 1 and int(timed[0].get("round", -1)) == 2,
        "Expired question advances without assigning imaginary points")
    var cancelled: Dictionary = duels.call("disconnect", 5)
    check(cancelled.get("type", "") == "cancelled", "Disconnect cancels open match")
    check(not bool(duels.call("is_busy", 4)), "Disconnect releases opponent")
    check(duels.call("disconnect", 4).is_empty(), "Duplicate disconnect cannot end a match twice")

    if failed:
        quit(1)
    else:
        print("BLINK LIFE server-scored consent-only 1v1 quiz smoke passed.")
        quit(0)


func _initialize() -> void:
    call_deferred("run_test")
