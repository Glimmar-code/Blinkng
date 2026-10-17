#!/usr/bin/env python3
"""Genuine loopback ENet integration smoke: 1 dedicated host + 2 clients.

Development CI ONLY, not proof of public Internet deployment or encryption.
Each Godot process has a real independent SceneTree and UDP socket.
No Supabase tokens, production accounts, secrets or public endpoints are used.
"""
import hashlib
import hmac
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time


ROOT = Path(__file__).resolve().parents[2]
GODOT = os.environ.get("GODOT_BIN", "godot")
CLIENT_TIMEOUT = 26
ACCOUNT_A = "12345678-1234-4123-8123-123456789abc"
ACCOUNT_B = "87654321-4321-4321-8321-abcdefabcdef"


def fresh_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def ticket(uid: str, secret: str, *, invalid: bool = False) -> str:
    expiry = int(time.time()) + 90
    nonce = secrets.token_hex(16)
    message = f"{uid}.{expiry}.{nonce}"
    key = ("wrong-" + secret) if invalid else secret
    signature = hmac.new(key.encode(), message.encode(), hashlib.sha256).hexdigest()
    return message + "." + signature


class Peer:
    def __init__(self, role: str, base: Path, *, env: dict, opts: list[str]):
        self.role = role
        self.path = base / (role + ".log")
        self.log = self.path.open("w+", encoding="utf-8")
        args = [
            GODOT, "--headless", "--audio-driver", "Dummy", "--path",
            str(ROOT / "game3d"), "--script",
            "res://tests/online_peer_smoke.gd", "--",
            f"--blink-test-role={role}", *opts,
        ]
        self.proc = subprocess.Popen(
            args, stdout=self.log, stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL, env=env, cwd=ROOT,
        )

    def output(self) -> str:
        self.log.flush()
        return self.path.read_text(encoding="utf-8", errors="replace")

    def end(self):
        if self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=3)
        self.log.close()


def ready(peer: Peer, token: str, seconds: float = 14.0):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        contents = peer.output()
        if token in contents:
            return
        if peer.proc.poll() is not None:
            raise AssertionError(
                f"{peer.role} exited before {token}:\n{contents[-3500:]}"
            )
        time.sleep(0.1)
    raise AssertionError(
        f"{peer.role} did not reach {token}:\n{peer.output()[-3500:]}"
    )


def finish(peer: Peer, seconds: float = CLIENT_TIMEOUT) -> str:
    try:
        code = peer.proc.wait(timeout=seconds)
    except subprocess.TimeoutExpired as error:
        raise AssertionError(
            f"{peer.role} timed out:\n{peer.output()[-3500:]}"
        ) from error
    output = peer.output()
    if code != 0 or f"ONLINE_SMOKE_PASS:{peer.role}" not in output:
        raise AssertionError(
            f"{peer.role} failed (exit={code}):\n{output[-4000:]}"
        )
    if re.search(r"(ONLINE_SMOKE_FAILURE:|SCRIPT ERROR:|Parse Error)", output):
        raise AssertionError(
            f"{peer.role} produced a script failure:\n{output[-4000:]}"
        )
    return output


def main() -> None:
    port = fresh_port()
    secret = secrets.token_hex(32)
    with tempfile.TemporaryDirectory(prefix="blink-life-net-") as workspace:
        temp = Path(workspace)
        data_dir = temp / "godot-data"
        data_dir.mkdir()
        env = os.environ.copy()
        env.update({
            "BLINK_LIFE_SERVER_PORT": str(port),
            "BLINK_LIFE_AUTH_SECRET": secret,
            "XDG_DATA_HOME": str(data_dir),
        })
        peers: list[Peer] = []

        def make(role: str, uid: str = "", *, restart_plot: int = -1,
                 invalid: bool = False) -> Peer:
            opts: list[str]
            if role == "server":
                opts = ["--blink-dedicated"]
            else:
                opts = [
                    "--blink-host=127.0.0.1",
                    f"--blink-port={port}",
                    "--blink-ticket=" + ticket(uid, secret, invalid=invalid),
                ]
                if restart_plot >= 0:
                    opts.append(f"--blink-test-expected-plot={restart_plot}")
            peer = Peer(role, temp, env=env, opts=opts)
            peers.append(peer)
            return peer

        try:
            server = make("server")
            ready(server, "ONLINE_SMOKE_SERVER_READY")
            a = make("owner", ACCOUNT_A)
            b = make("peer", ACCOUNT_B)
            out_a = finish(a)
            out_b = finish(b)
            plot_a = re.search(r"ONLINE_SMOKE_PASS:owner:plot=(\d+)", out_a)
            plot_b = re.search(r"ONLINE_SMOKE_PASS:peer:plot=(\d+)", out_b)
            if plot_a is None or plot_b is None:
                raise AssertionError("Authenticated home plot results absent")
            home_a, home_b = int(plot_a.group(1)), int(plot_b.group(1))
            if home_a == home_b:
                raise AssertionError("Two accounts were assigned the same plot")

            invalid_client = make("invalid", ACCOUNT_A, invalid=True)
            finish(invalid_client, seconds=12)

            server.end()
            peers.remove(server)
            # The server's user:// backing directory survives this restart.
            # Godot may need a short interval to relinquish the UDP port.
            time.sleep(0.4)
            server2 = make("server")
            ready(server2, "ONLINE_SMOKE_SERVER_READY")
            reconnect = make("reconnect", ACCOUNT_A, restart_plot=home_a)
            finish(reconnect)
            print(
                "PASS: real local UDP host, two independent authenticated "
                "players, rejected invalid ticket, unique server-owned houses "
                "and account home persistence after host restart."
            )
        except Exception:
            for peer in peers:
                contents = peer.output()
                if contents:
                    print(
                        f"--- {peer.role} Godot log ---\n{contents[-3000:]}",
                        file=sys.stderr,
                    )
            raise
        finally:
            for peer in peers:
                peer.end()


if __name__ == "__main__":
    main()
