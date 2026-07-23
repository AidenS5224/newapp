from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


TEST_PORT = "18080"
BASE_URL = f"http://127.0.0.1:{TEST_PORT}"
APP = Path(__file__).resolve().parent / "app.py"
sys.path.insert(0, str(APP.parent))

from app import admin_token  # noqa: E402


def get_json(path: str) -> dict:
    with urllib.request.urlopen(f"{BASE_URL}{path}", timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def get_admin_json(path: str) -> dict:
    request = urllib.request.Request(
        f"{BASE_URL}{path}",
        headers={"X-Admin-Token": admin_token()},
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(path: str, payload: dict, token: str | None = None) -> dict:
    body = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Admin-Token"] = token
    request = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=body,
        headers=headers,
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
        env={**os.environ, "GAMER_CONNECT_PORT": TEST_PORT},
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

        try:
            get_json("/api/admin/overview")
            raise AssertionError("expected owner overview to require a token")
        except urllib.error.HTTPError as exc:
            assert exc.code == 401, "expected unauthorized owner overview to return 401"

        overview = get_admin_json("/api/admin/overview")
        assert overview["summary"]["counts"]["players"] >= 3, "expected admin player count"
        assert overview["summary"]["counts"]["connectionRequests"] >= 1, "expected admin connection count"

        updated = post_json(
            "/api/admin/player-online",
            {"playerId": "p_ghost", "online": True},
            token=admin_token(),
        )
        assert updated["online"] is True, "expected admin online update"

        connection = post_json(
            "/api/admin/connection-status",
            {"requestId": request["id"], "status": "accepted"},
            token=admin_token(),
        )
        assert connection["status"] == "accepted", "expected admin connection approval"

        print("API smoke test passed")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()


if __name__ == "__main__":
    main()
