#!/usr/bin/env python3
"""Operator-run read-only preflight for a BLINK LIFE staging WSS game host.

A successful result confirms DNS, hostname-verified TLS, and a WebSocket
upgrade, not authenticated multiplayer, database durability, or device QA.
No JWTs, tickets, Supabase API credentials, or user identifiers are sent.
"""
import argparse
import base64
import ipaddress
from pathlib import Path
import re
import secrets
import socket
import ssl
import sys


def check_hostname(name: str, allow_local: bool = False) -> str:
    if not name or len(name) > 253 or not re.fullmatch(
        r"[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?", name
    ) or ".." in name:
        raise ValueError("Provide a DNS hostname without a URL path or scheme.")
    if name == "localhost":
        if not allow_local:
            raise ValueError("localhost is valid only for an explicit local test.")
        return name
    try:
        ipaddress.ip_address(name)
    except ValueError:
        pass
    else:
        raise ValueError("Use a DNS name matching the TLS certificate, not a raw IP.")
    if "." not in name or name.endswith(".local"):
        raise ValueError("Use a fully-qualified staging hostname.")
    if any(not part or part.startswith("-") or part.endswith("-") for part in name.split(".")):
        raise ValueError("Invalid DNS hostname.")
    return name


def probe(host: str, port: int, *, cafile: Path | None = None,
          loopback: bool = False, timeout: float = 5.0) -> None:
    host = check_hostname(host, allow_local=loopback)
    if port < 1024 or port > 65535:
        raise ValueError("Staging game TCP port must be in 1024..65535.")
    if cafile and not (loopback and host == "localhost"):
        raise ValueError("Custom trust roots are allowed only for the local CI smoke test.")

    # Default context verifies BOTH hostname and public CA trust. There
    # is deliberately no unverified/unsafe SSL mode.
    tls = ssl.create_default_context(
        cafile=str(cafile) if cafile is not None else None
    )
    # WebSocket upgrade is unauthenticated; Godot must require the later
    # signed ticket before registering a peer into the actual game roster.
    key = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
    handshake = (
        f"GET / HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Connection: Upgrade\r\n"
        "Upgrade: websocket\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n"
    ).encode("ascii")
    with socket.create_connection((host, port), timeout=timeout) as raw:
        with tls.wrap_socket(raw, server_hostname=host) as connection:
            connection.settimeout(timeout)
            connection.sendall(handshake)
            response = bytearray()
            while b"\r\n\r\n" not in response:
                packet = connection.recv(4096)
                if not packet:
                    break
                response.extend(packet)
                if len(response) > 16384:
                    raise ConnectionError("Excessive WSS upgrade response size.")
    status = bytes(response).split(b"\r\n", 1)[0]
    if not status.startswith(b"HTTP/1.1 101 ") and not status.startswith(
        b"HTTP/1.0 101 "
    ):
        raise ConnectionError(
            "TLS passed, but the server did not accept a WebSocket upgrade."
        )
    headers = bytes(response).lower()
    if b"upgrade: websocket" not in headers:
        raise ConnectionError("Server omitted the WebSocket upgrade response.")
    print("PASS: hostname-verified TLS and a working staging WebSocket upgrade.")
    print("NOT VERIFIED: authenticated game login, Internet routing or device QA.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="Game TLS DNS hostname")
    parser.add_argument("--port", type=int, default=24567)
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument(
        "--loopback-only", action="store_true",
        help="Allow localhost, strictly for local test automation",
    )
    parser.add_argument(
        "--cafile", type=Path, help="Ephemeral local CA for loopback test ONLY",
    )
    options = parser.parse_args()
    try:
        probe(
            options.host, options.port, cafile=options.cafile,
            loopback=options.loopback_only, timeout=options.timeout,
        )
    except (OSError, ValueError, ssl.SSLError, ConnectionError) as error:
        # No tickets or auth secrets were ever passed to this probe.
        print("FAIL: staging game TLS/WSS preflight: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
