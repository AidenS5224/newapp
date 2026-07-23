from __future__ import annotations

import json
import shlex
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

from app import DB_PATH, db, init_db, list_games, list_lfg, list_players, list_squads, row_to_player


PROMPT = "gamer-connect> "


def print_table(headers: list[str], rows: list[list[Any]]) -> None:
    if not rows:
        print("(none)")
        return
    widths = [len(header) for header in headers]
    for row in rows:
        for index, value in enumerate(row):
            widths[index] = max(widths[index], len(str(value)))
    line = "  ".join(header.ljust(widths[index]) for index, header in enumerate(headers))
    print(line)
    print("  ".join("-" * width for width in widths))
    for row in rows:
        print("  ".join(str(value).ljust(widths[index]) for index, value in enumerate(row)))


def read_json(value: str, fallback: Any) -> Any:
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return fallback


def command_help(_: list[str]) -> None:
    print(
        """
Commands:
  help                         Show this help
  status                       Show database and record counts
  games                        List supported games
  players [game_id]            List players, optionally filtered by game
  player <player_id>           Show full player JSON
  online <player_id> <on|off>  Set test online state
  lfg                          List looking-for-group posts
  squads                       List squads
  connections                  List connection requests
  approve <request_id>         Mark a connection request accepted
  reject <request_id>          Mark a connection request rejected
  export <path>                Export current test data to JSON
  reset                        Delete test data and restore seed data
  quit                         Exit interactive mode
""".strip()
    )


def command_status(_: list[str]) -> None:
    init_db()
    with db() as conn:
        rows = []
        for table in ("games", "players", "lfg_posts", "squads", "connection_requests"):
            total = conn.execute(f"SELECT COUNT(*) AS total FROM {table}").fetchone()["total"]
            rows.append([table, total])
    print(f"Database: {DB_PATH}")
    print_table(["table", "rows"], rows)


def command_games(_: list[str]) -> None:
    init_db()
    with db() as conn:
        rows = [
            [game["id"], game["name"], ",".join(game["platforms"]), "yes" if game["crossplay"] else "no"]
            for game in list_games(conn)
        ]
    print_table(["id", "name", "platforms", "crossplay"], rows)


def command_players(args: list[str]) -> None:
    init_db()
    query = {"game": [args[0]]} if args else {}
    with db() as conn:
        rows = [
            [
                player["id"],
                player["handle"],
                "online" if player["online"] else "offline",
                player["rank"],
                player["region"],
                player["compatibility"],
            ]
            for player in list_players(conn, query)
        ]
    print_table(["id", "handle", "state", "rank", "region", "fit"], rows)


def command_player(args: list[str]) -> None:
    if len(args) != 1:
        raise ValueError("Usage: player <player_id>")
    init_db()
    with db() as conn:
        row = conn.execute("SELECT * FROM players WHERE id = ?", (args[0],)).fetchone()
    if not row:
        raise ValueError("Player not found")
    print(json.dumps(row_to_player(row), indent=2))


def command_online(args: list[str]) -> None:
    if len(args) != 2 or args[1] not in {"on", "off"}:
        raise ValueError("Usage: online <player_id> <on|off>")
    init_db()
    online = 1 if args[1] == "on" else 0
    with db() as conn:
        cursor = conn.execute("UPDATE players SET online = ? WHERE id = ?", (online, args[0]))
        if cursor.rowcount == 0:
            raise ValueError("Player not found")
    print(f"{args[0]} is now {'online' if online else 'offline'}")


def command_lfg(_: list[str]) -> None:
    init_db()
    with db() as conn:
        rows = [
            [post["id"], post["title"], post["gameName"], post["handle"], post["partySize"], post["startsAt"], post["status"]]
            for post in list_lfg(conn)
        ]
    print_table(["id", "title", "game", "host", "party", "starts", "status"], rows)


def command_squads(_: list[str]) -> None:
    init_db()
    with db() as conn:
        rows = [
            [squad["id"], squad["name"], squad["gameName"], squad["openSlots"], squad["voicePreference"], squad["schedule"]]
            for squad in list_squads(conn)
        ]
    print_table(["id", "name", "game", "slots", "voice", "schedule"], rows)


def command_connections(_: list[str]) -> None:
    init_db()
    with db() as conn:
        rows = conn.execute(
            """
            SELECT cr.id, fp.handle AS from_handle, tp.handle AS to_handle, cr.status, cr.message, cr.created_at
            FROM connection_requests cr
            JOIN players fp ON fp.id = cr.from_player_id
            JOIN players tp ON tp.id = cr.to_player_id
            ORDER BY cr.created_at DESC
            """
        ).fetchall()
    print_table(
        ["id", "from", "to", "status", "created", "message"],
        [
            [
                row["id"],
                row["from_handle"],
                row["to_handle"],
                row["status"],
                datetime.fromtimestamp(row["created_at"]).strftime("%Y-%m-%d %H:%M"),
                row["message"],
            ]
            for row in rows
        ],
    )


def update_connection_status(args: list[str], status: str) -> None:
    if len(args) != 1:
        raise ValueError(f"Usage: {status} <request_id>")
    init_db()
    with db() as conn:
        cursor = conn.execute("UPDATE connection_requests SET status = ? WHERE id = ?", (status, args[0]))
        if cursor.rowcount == 0:
            raise ValueError("Connection request not found")
    print(f"{args[0]} marked {status}")


def command_export(args: list[str]) -> None:
    if len(args) != 1:
        raise ValueError("Usage: export <path>")
    init_db()
    output = Path(args[0]).expanduser().resolve()
    with db() as conn:
        data = {
            "games": list_games(conn),
            "players": list_players(conn, {}),
            "lfgPosts": list_lfg(conn),
            "squads": list_squads(conn),
            "connectionRequests": conn.execute("SELECT * FROM connection_requests ORDER BY created_at DESC").fetchall(),
        }
    output.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"Exported {output}")


def command_reset(_: list[str]) -> None:
    DB_PATH.unlink(missing_ok=True)
    init_db()
    print("Database reset and seed data restored")


COMMANDS = {
    "help": command_help,
    "?": command_help,
    "status": command_status,
    "games": command_games,
    "players": command_players,
    "player": command_player,
    "online": command_online,
    "lfg": command_lfg,
    "squads": command_squads,
    "connections": command_connections,
    "approve": lambda args: update_connection_status(args, "accepted"),
    "reject": lambda args: update_connection_status(args, "rejected"),
    "export": command_export,
    "reset": command_reset,
}


def run_command(parts: list[str]) -> bool:
    if not parts:
        return True
    command, args = parts[0].lower(), parts[1:]
    if command in {"quit", "exit"}:
        return False
    handler = COMMANDS.get(command)
    if not handler:
        print(f"Unknown command: {command}. Type help for commands.")
        return True
    try:
        handler(args)
    except Exception as exc:
        print(f"Error: {exc}")
    return True


def interactive() -> None:
    print("Gamer Connect backend control terminal")
    print("Type help for commands. Type quit to exit.")
    init_db()
    while True:
        try:
            line = input(PROMPT)
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not run_command(shlex.split(line)):
            break


def main() -> None:
    if len(sys.argv) > 1:
        run_command(sys.argv[1:])
        return
    interactive()


if __name__ == "__main__":
    main()
