extends SceneTree
## Local TLS handshake smoke. No Internet server, public network or shared secrets.
## Uses a test-only self-signed CA trusted explicitly by this client.

var failed: bool = false


func check(condition: bool, reason: String) -> void:
    if not condition:
        failed = true
        printerr("BLINK LIFE WSS SMOKE FAILURE: " + reason)


func _run() -> void:
    var crypto := Crypto.new()
    var key: CryptoKey = crypto.generate_rsa(2048)
    var cert: X509Certificate = crypto.generate_self_signed_certificate(
        key, "CN=localhost,O=BLINK Testlab,C=NG"
    )
    check(key != null and cert != null, "Ephemeral TLS credentials generate")
    if key == null or cert == null:
        quit(1)
        return
    var server := WebSocketMultiplayerPeer.new()
    var client := WebSocketMultiplayerPeer.new()
    var server_result := server.create_server(24681, "*", TLSOptions.server(key, cert))
    check(server_result == OK, "TLS WebSocket server binds without plaintext")
    if server_result != OK:
        quit(1)
        return

    # Verify the generated cert instead of bypassing certificate checks.
    var client_result := client.create_client("wss://localhost:24681", TLSOptions.client(cert))
    check(client_result == OK, "CA-verified WSS client begins connection")
    if client_result == OK:
        for unused_index in range(240):
            server.poll()
            client.poll()
            if client.get_connection_status() == MultiplayerPeer.CONNECTION_CONNECTED:
                break
            await process_frame
        check(client.get_connection_status() == MultiplayerPeer.CONNECTION_CONNECTED,
            "Client validates TLS certificate and completes encrypted handshake")
    client.close()
    server.close()
    if failed:
        quit(1)
    else:
        print("BLINK LIFE local TLS encrypted transport smoke passed.")
        quit(0)


func _initialize() -> void:
    call_deferred("_run")
