from __future__ import annotations

import json
import socket
import sys
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parent
BACKEND = ROOT / "backend"
ANDROID = ROOT / "android"
sys.path.insert(0, str(BACKEND))

from app import APP_NAME, DB_PATH, HOST, PORT, admin_token, init_db, main as run_backend  # noqa: E402
import control  # noqa: E402


def local_ip() -> str | None:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except OSError:
        return None


def print_help() -> None:
    print(
        """
Gamer Connect local platform CLI

Usage:
  python gamer.py dev              Start the local backend API
  python gamer.py control          Open the backend control terminal
  python gamer.py admin            Print your private owner panel URL
  python gamer.py token            Print your local admin token
  python gamer.py status           Show database record counts
  python gamer.py health           Check the running API
  python gamer.py doctor           Check local project setup
  python gamer.py urls             Print emulator and phone API URLs
  python gamer.py reset            Reset local seed database
  python gamer.py players          List players
  python gamer.py lfg              List LFG posts
  python gamer.py squads           List squads
  python gamer.py connections      List connection requests
  python gamer.py help             Show this help

Examples:
  python gamer.py dev
  python gamer.py control
  python gamer.py online p_ghost on
""".strip()
    )


def command_dev(_: list[str]) -> None:
    print_banner()
    print("Starting local backend. Press Ctrl+C to stop.")
    run_backend()


def print_banner() -> None:
    print("Gamer Connect")
    print("=" * 13)
    print(f"API:      http://127.0.0.1:{PORT}")
    print(f"Owner:    http://127.0.0.1:{PORT}/owner?token={admin_token()}")
    print(f"Emulator: http://10.0.2.2:{PORT}")
    lan_ip = local_ip()
    if lan_ip:
        print(f"Phone:    http://{lan_ip}:{PORT}")
    print(f"Database: {DB_PATH}")
    print()


def command_health(_: list[str]) -> None:
    url = f"http://127.0.0.1:{PORT}/api/health"
    try:
        with urllib.request.urlopen(url, timeout=3) as response:
            payload = json.loads(response.read().decode("utf-8"))
        print(json.dumps(payload, indent=2))
    except (urllib.error.URLError, TimeoutError, ConnectionError) as exc:
        print(f"API is not responding at {url}")
        print(f"Error: {exc}")
        print("Start it with: python gamer.py dev")


def command_urls(_: list[str]) -> None:
    print(f"Windows/local:    http://127.0.0.1:{PORT}")
    print(f"Owner panel:      http://127.0.0.1:{PORT}/owner?token={admin_token()}")
    print(f"Android emulator: http://10.0.2.2:{PORT}")
    lan_ip = local_ip()
    if lan_ip:
        print(f"Phone on Wi-Fi:   http://{lan_ip}:{PORT}")
    else:
        print("Phone on Wi-Fi:   could not detect LAN IP")


def command_doctor(_: list[str]) -> None:
    init_db()
    print("Gamer Connect doctor")
    print_table(
        [
            ["project root", ROOT, "ok" if ROOT.exists() else "missing"],
            ["backend", BACKEND, "ok" if BACKEND.exists() else "missing"],
            ["android", ANDROID, "ok" if ANDROID.exists() else "missing"],
            ["database", DB_PATH, "ok"],
            ["python", sys.version.split()[0], "ok"],
            ["api command", "python gamer.py dev", "ready"],
            ["control command", "python gamer.py control", "ready"],
        ]
    )
    print()
    command_urls([])
    print()
    command_health([])


def command_admin(_: list[str]) -> None:
    print(f"Owner panel: http://127.0.0.1:{PORT}/owner?token={admin_token()}")
    print("Start the backend first with: python gamer.py dev")


def command_token(_: list[str]) -> None:
    print(admin_token())


def print_table(rows: list[list[object]]) -> None:
    widths = [0, 0, 0]
    for row in rows:
        for index, value in enumerate(row):
            widths[index] = max(widths[index], len(str(value)))
    for row in rows:
        print("  ".join(str(value).ljust(widths[index]) for index, value in enumerate(row)))


def run_control_command(name: str, args: list[str]) -> None:
    control.run_command([name, *args])


def main() -> None:
    if len(sys.argv) == 1:
        print_help()
        return

    command = sys.argv[1].lower()
    args = sys.argv[2:]
    if command in {"help", "-h", "--help"}:
        print_help()
    elif command == "dev":
        command_dev(args)
    elif command == "control":
        control.interactive()
    elif command in {"admin", "owner"}:
        command_admin(args)
    elif command == "token":
        command_token(args)
    elif command == "health":
        command_health(args)
    elif command == "doctor":
        command_doctor(args)
    elif command == "urls":
        command_urls(args)
    elif command in control.COMMANDS:
        run_control_command(command, args)
    else:
        print(f"Unknown command: {command}")
        print_help()
        raise SystemExit(1)


if __name__ == "__main__":
    main()
