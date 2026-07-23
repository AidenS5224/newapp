from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


BASE_URL = "http://127.0.0.1:8080"
APP = Path(__file__).resolve().parent / "app.py"


def get_json(path: str) -> dict:
    with urllib.request.urlopen(f"{BASE_URL}{path}", timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(path: str, payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def wait_until_ready() -> None:
    deadline = time.time() + 10
    last_error: Exception | None = None
    while time.time() < deadline:
        try:
            if get_json("/api/health")["ok"]:
                return
        except (urllib.error.URLError, TimeoutError, ConnectionError) as exc:
            last_error = exc
            time.sleep(0.2)
    raise RuntimeError(f"API did not become ready: {last_error}")


def main() -> None:
    proc = subprocess.Popen(
        [sys.executable, str(APP)],
        cwd=str(APP.parent),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    try:
        wait_until_ready()

        games = get_json("/api/games")["games"]
        players = get_json("/api/players?game=apex-legends")["players"]
        lfg = get_json("/api/lfg")["posts"]
        squads = get_json("/api/squads")["squads"]
        request = post_json(
            "/api/connections",
            {
                "fromPlayerId": "p_novapulse",
                "toPlayerId": "p_ghost",
                "message": "Want to test ranked tonight?",
            },
        )["connectionRequest"]

        assert len(games) >= 4, "expected seeded games"
        assert any(player["handle"] == "NovaPulse" for player in players), "expected NovaPulse in player search"
        assert lfg, "expected seeded LFG posts"
        assert squads, "expected seeded squads"
        assert request["status"] == "pending", "expected pending connection request"

        print("API smoke test passed")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()


if __name__ == "__main__":
    main()
