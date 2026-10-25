extends RefCounted
## Dedicated online BLINK LIFE server ticket validation.
## Never ship BLINK_LIFE_AUTH_SECRET in Android or Windows clients.

static func _is_lower_hex(value: String, expected_length: int) -> bool:
    if value.length() != expected_length:
        return false
    for index in value.length():
        var character: String = value.substr(index, 1)
        if "0123456789abcdef".find(character) == -1:
            return false
    return true


static func _is_uuid(value: String) -> bool:
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


static func verify(ticket: String, server_secret: String, now_unix: int, used_nonces: Dictionary) -> String:
    if server_secret.length() < 32 or ticket.length() > 240:
        return ""
    var segments := ticket.split(".")
    if segments.size() != 4:
        return ""
    var user_id: String = segments[0]
    var expiry_string: String = segments[1]
    var nonce: String = segments[2]
    var signature: String = segments[3]
    if not _is_uuid(user_id) or not _is_lower_hex(nonce, 32) or not _is_lower_hex(signature, 64):
        return ""
    if not expiry_string.is_valid_int() or expiry_string.length() > 11:
        return ""
    var expiry := int(expiry_string)
    # Only fresh 90-second tickets; small allowance for clock differences.
    if expiry < now_unix - 5 or expiry > now_unix + 120:
        return ""
    if used_nonces.has(nonce):
        return ""

    var message := "%s.%s.%s" % [user_id, expiry_string, nonce]
    var crypto := Crypto.new()
    var expected := crypto.hmac_digest(
        HashingContext.HASH_SHA256,
        server_secret.to_utf8_buffer(), message.to_utf8_buffer()
    )
    var provided := signature.hex_decode()
    if not crypto.constant_time_compare(expected, provided):
        return ""
    used_nonces[nonce] = expiry
    # Bound memory by pruning expired one-time nonces.
    if used_nonces.size() > 2048:
        for key in used_nonces.keys():
            if int(used_nonces[key]) < now_unix:
                used_nonces.erase(key)
    return user_id
