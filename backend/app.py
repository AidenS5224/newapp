from __future__ import annotations

import json
import os
import secrets
import sqlite3
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

from admin_panel import OWNER_PANEL_HTML


APP_NAME = "Gamer Connect API"
APP_VERSION = "0.1"
BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
DB_PATH = Path(os.environ.get("GAMER_CONNECT_DB", DATA_DIR / "gamer_connect.sqlite3"))
TOKEN_PATH = DATA_DIR / "admin_token.txt"
HOST = os.environ.get("GAMER_CONNECT_HOST", "0.0.0.0")
PORT = int(os.environ.get("GAMER_CONNECT_PORT", "8080"))


def dict_factory(cursor: sqlite3.Cursor, row: tuple[Any, ...]) -> dict[str, Any]:
    return {column[0]: row[index] for index, column in enumerate(cursor.description)}


def db() -> sqlite3.Connection:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = dict_factory
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def admin_token() -> str:
    env_token = os.environ.get("GAMER_CONNECT_ADMIN_TOKEN")
    if env_token:
        return env_token.strip()
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if TOKEN_PATH.exists():
        return TOKEN_PATH.read_text(encoding="utf-8").strip()
    token = secrets.token_urlsafe(32)
    TOKEN_PATH.write_text(token, encoding="utf-8")
    return token


def parse_json(value: str | None, fallback: Any) -> Any:
    if not value:
        return fallback
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return fallback


def row_to_player(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": row["id"],
        "handle": row["handle"],
        "displayName": row["display_name"],
        "age": row["age"],
        "region": row["region"],
        "timezone": row["timezone"],
        "platforms": parse_json(row["platforms"], []),
        "topGames": parse_json(row["top_games"], []),
        "rank": row["rank"],
        "playStyle": parse_json(row["play_style"], []),
        "availability": parse_json(row["availability"], {}),
        "bio": row["bio"],
        "avatarUrl": row["avatar_url"],
        "online": bool(row["online"]),
        "stats": parse_json(row["stats"], {}),
        "linkedAccounts": parse_json(row["linked_accounts"], {}),
        "compatibility": row.get("compatibility"),
    }


def init_db() -> None:
    with db() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS players (
                id TEXT PRIMARY KEY,
                handle TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                age INTEGER,
                region TEXT NOT NULL,
                timezone TEXT NOT NULL,
                platforms TEXT NOT NULL,
                top_games TEXT NOT NULL,
                rank TEXT NOT NULL,
                play_style TEXT NOT NULL,
                availability TEXT NOT NULL,
                bio TEXT NOT NULL,
                avatar_url TEXT,
                online INTEGER NOT NULL DEFAULT 0,
                stats TEXT NOT NULL,
                linked_accounts TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS games (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                modes TEXT NOT NULL,
                platforms TEXT NOT NULL,
                crossplay INTEGER NOT NULL DEFAULT 1
            );

            CREATE TABLE IF NOT EXISTS lfg_posts (
                id TEXT PRIMARY KEY,
                player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                mode TEXT NOT NULL,
                rank_range TEXT NOT NULL,
                party_size TEXT NOT NULL,
                starts_at TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS squads (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                game_id TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
                description TEXT NOT NULL,
                members TEXT NOT NULL,
                open_slots INTEGER NOT NULL,
                voice_preference TEXT NOT NULL,
                schedule TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS connection_requests (
                id TEXT PRIMARY KEY,
                from_player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                to_player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                message TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """
        )
        seed(conn)


def seed(conn: sqlite3.Connection) -> None:
    existing = conn.execute("SELECT COUNT(*) AS total FROM players").fetchone()["total"]
    if existing:
        return

    now = int(time.time())
    games = [
        ("apex-legends", "Apex Legends", ["Ranked", "Trios", "Duos"], ["PC", "Xbox", "PlayStation", "Switch"], 1),
        ("valorant", "VALORANT", ["Competitive", "Unrated", "Swiftplay"], ["PC"], 0),
        ("warzone", "Call of Duty: Warzone", ["Battle Royale", "Resurgence", "Ranked"], ["PC", "Xbox", "PlayStation"], 1),
        ("rocket-league", "Rocket League", ["Ranked 2v2", "Ranked 3v3", "Casual"], ["PC", "Xbox", "PlayStation", "Switch"], 1),
    ]
    conn.executemany(
        "INSERT INTO games (id, name, modes, platforms, crossplay) VALUES (?, ?, ?, ?, ?)",
        [(gid, name, json.dumps(modes), json.dumps(platforms), crossplay) for gid, name, modes, platforms, crossplay in games],
    )

    players = [
        {
            "id": "p_novapulse",
            "handle": "NovaPulse",
            "display_name": "NovaPulse",
            "age": 21,
            "region": "Australia",
            "timezone": "AEST",
            "platforms": ["PC", "Crossplay"],
            "top_games": ["apex-legends", "valorant", "warzone"],
            "rank": "Diamond II",
            "play_style": ["Competitive", "Good Comms", "Team Player"],
            "availability": {"days": ["Fri", "Sat", "Sun"], "window": "7PM - 11PM AEST"},
            "bio": "I main Valk. Looking for a consistent team to grind ranked and get better together.",
            "avatar_url": None,
            "online": 1,
            "stats": {"winRate": "57%", "kd": "1.32", "games": "2.1K", "positive": "94%"},
            "linked_accounts": {"discord": "NovaPulse#2042", "steam": "novapulse", "trackerNetwork": "apex/NovaPulse"},
        },
        {
            "id": "p_ghost",
            "handle": "GhostRider",
            "display_name": "GhostRider",
            "age": 24,
            "region": "Australia",
            "timezone": "AEST",
            "platforms": ["PC", "Xbox"],
            "top_games": ["apex-legends", "rocket-league"],
            "rank": "Diamond III",
            "play_style": ["Strategic", "Calm", "IGL"],
            "availability": {"days": ["Thu", "Fri"], "window": "8PM - 12AM AEST"},
            "bio": "Shot caller, ranked grinder, zero tilt. Prefer squads that review games after a session.",
            "avatar_url": None,
            "online": 0,
            "stats": {"winRate": "54%", "kd": "1.19", "games": "1.4K", "positive": "91%"},
            "linked_accounts": {"discord": "GhostRider#7741", "xbox": "GhostRiderAU"},
        },
        {
            "id": "p_zane",
            "handle": "ZaneFPS",
            "display_name": "ZaneFPS",
            "age": 19,
            "region": "Australia",
            "timezone": "AEST",
            "platforms": ["PC"],
            "top_games": ["valorant", "apex-legends"],
            "rank": "Ascendant I",
            "play_style": ["Aggressive", "Entry", "Good Comms"],
            "availability": {"days": ["Mon", "Wed", "Sat"], "window": "6PM - 10PM AEST"},
            "bio": "Entry fragger. Looking for serious ranked teammates who still keep it friendly.",
            "avatar_url": None,
            "online": 1,
            "stats": {"winRate": "61%", "kd": "1.41", "games": "980", "positive": "88%"},
            "linked_accounts": {"discord": "ZaneFPS#1188", "riot": "ZaneFPS#OCE"},
        },
    ]
    conn.executemany(
        """
        INSERT INTO players (
            id, handle, display_name, age, region, timezone, platforms, top_games,
            rank, play_style, availability, bio, avatar_url, online, stats,
            linked_accounts, created_at
        )
        VALUES (
            :id, :handle, :display_name, :age, :region, :timezone, :platforms, :top_games,
            :rank, :play_style, :availability, :bio, :avatar_url, :online, :stats,
            :linked_accounts, :created_at
        )
        """,
        [
            {
                **player,
                "platforms": json.dumps(player["platforms"]),
                "top_games": json.dumps(player["top_games"]),
                "play_style": json.dumps(player["play_style"]),
                "availability": json.dumps(player["availability"]),
                "stats": json.dumps(player["stats"]),
                "linked_accounts": json.dumps(player["linked_accounts"]),
                "created_at": now,
            }
            for player in players
        ],
    )

    conn.executemany(
        """
        INSERT INTO lfg_posts (id, player_id, game_id, title, mode, rank_range, party_size, starts_at, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            ("lfg_ranked_apex_1", "p_ghost", "apex-legends", "Need 2 for Ranked", "Ranked", "Diamond - Master", "2 / 4", "Tonight, 7PM AEST", "open", now),
            ("lfg_valorant_1", "p_zane", "valorant", "Duo queue comp", "Competitive", "Diamond - Ascendant", "1 / 2", "Tonight, 8PM AEST", "open", now),
        ],
    )

    conn.executemany(
        """
        INSERT INTO squads (id, name, game_id, description, members, open_slots, voice_preference, schedule)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            ("s_weekend_warriors", "Weekend Warriors", "apex-legends", "Ranked squad for calm, consistent weekend sessions.", json.dumps(["p_novapulse", "p_ghost"]), 1, "Discord", "Fri/Sat evenings"),
            ("s_oce_clutch", "OCE Clutch", "valorant", "Small Valorant group looking for friendly competitive players.", json.dumps(["p_zane"]), 2, "Discord", "Weeknights"),
        ],
    )


def list_games(conn: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = conn.execute("SELECT * FROM games ORDER BY name").fetchall()
    return [
        {
            "id": row["id"],
            "name": row["name"],
            "modes": parse_json(row["modes"], []),
            "platforms": parse_json(row["platforms"], []),
            "crossplay": bool(row["crossplay"]),
        }
        for row in rows
    ]


def list_players(conn: sqlite3.Connection, query: dict[str, list[str]]) -> list[dict[str, Any]]:
    game = query.get("game", [None])[0]
    platform = query.get("platform", [None])[0]
    rows = conn.execute("SELECT * FROM players ORDER BY online DESC, handle").fetchall()
    players = [row_to_player(row) for row in rows]
    if game:
        players = [player for player in players if game in player["topGames"]]
    if platform:
        players = [player for player in players if platform in player["platforms"] or "Crossplay" in player["platforms"]]
    for player in players:
        player["compatibility"] = compatibility_score(player)
    return players


def compatibility_score(player: dict[str, Any]) -> int:
    score = 62
    if "apex-legends" in player["topGames"]:
        score += 10
    if "Australia" == player["region"]:
        score += 8
    if "Good Comms" in player["playStyle"]:
        score += 8
    if "Competitive" in player["playStyle"]:
        score += 6
    if player["online"]:
        score += 4
    return min(score, 98)


def list_lfg(conn: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT l.*, p.handle, p.display_name, g.name AS game_name
        FROM lfg_posts l
        JOIN players p ON p.id = l.player_id
        JOIN games g ON g.id = l.game_id
        ORDER BY l.created_at DESC
        """
    ).fetchall()
    return [
        {
            "id": row["id"],
            "playerId": row["player_id"],
            "handle": row["handle"],
            "displayName": row["display_name"],
            "gameId": row["game_id"],
            "gameName": row["game_name"],
            "title": row["title"],
            "mode": row["mode"],
            "rankRange": row["rank_range"],
            "partySize": row["party_size"],
            "startsAt": row["starts_at"],
            "status": row["status"],
        }
        for row in rows
    ]


def list_squads(conn: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT s.*, g.name AS game_name
        FROM squads s
        JOIN games g ON g.id = s.game_id
        ORDER BY s.name
        """
    ).fetchall()
    return [
        {
            "id": row["id"],
            "name": row["name"],
            "gameId": row["game_id"],
            "gameName": row["game_name"],
            "description": row["description"],
            "members": parse_json(row["members"], []),
            "openSlots": row["open_slots"],
            "voicePreference": row["voice_preference"],
            "schedule": row["schedule"],
        }
        for row in rows
    ]


def list_connection_requests(conn: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT
            cr.*,
            fp.handle AS from_handle,
            tp.handle AS to_handle
        FROM connection_requests cr
        JOIN players fp ON fp.id = cr.from_player_id
        JOIN players tp ON tp.id = cr.to_player_id
        ORDER BY cr.created_at DESC
        """
    ).fetchall()
    return [
        {
            "id": row["id"],
            "fromPlayerId": row["from_player_id"],
            "toPlayerId": row["to_player_id"],
            "from": row["from_handle"],
            "to": row["to_handle"],
            "message": row["message"],
            "status": row["status"],
            "createdAt": row["created_at"],
            "created": time.strftime("%Y-%m-%d %H:%M", time.localtime(row["created_at"])),
        }
        for row in rows
    ]


def top_games(players: list[dict[str, Any]], games: list[dict[str, Any]]) -> list[dict[str, Any]]:
    names = {game["id"]: game["name"] for game in games}
    counts: dict[str, int] = {}
    for player in players:
        for game_id in player["topGames"]:
            counts[game_id] = counts.get(game_id, 0) + 1
    return [
        {"id": game_id, "name": names.get(game_id, game_id), "players": count}
        for game_id, count in sorted(counts.items(), key=lambda item: item[1], reverse=True)
    ]


def admin_overview(conn: sqlite3.Connection) -> dict[str, Any]:
    players = list_players(conn, {})
    games = list_games(conn)
    lfg_posts = list_lfg(conn)
    squads = list_squads(conn)
    connection_requests = list_connection_requests(conn)
    pending = [request for request in connection_requests if request["status"] == "pending"]
    return {
        "summary": {
            "counts": {
                "players": len(players),
                "onlinePlayers": sum(1 for player in players if player["online"]),
                "games": len(games),
                "lfgPosts": len(lfg_posts),
                "squads": len(squads),
                "connectionRequests": len(connection_requests),
                "pendingConnections": len(pending),
            },
            "topGames": top_games(players, games),
        },
        "players": players,
        "games": games,
        "lfgPosts": lfg_posts,
        "squads": squads,
        "connectionRequests": connection_requests,
        "system": {
            "app": APP_NAME,
            "version": APP_VERSION,
            "host": HOST,
            "port": PORT,
            "database": str(DB_PATH),
            "ownerPanel": f"http://127.0.0.1:{PORT}/owner",
            "time": int(time.time()),
        },
    }


def reset_database() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with db() as conn:
        conn.executescript(
            """
            DROP TABLE IF EXISTS connection_requests;
            DROP TABLE IF EXISTS squads;
            DROP TABLE IF EXISTS lfg_posts;
            DROP TABLE IF EXISTS games;
            DROP TABLE IF EXISTS players;
            """
        )
    init_db()


class ApiHandler(BaseHTTPRequestHandler):
    server_version = "GamerConnectAPI/0.1"

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        try:
            with db() as conn:
                if parsed.path == "/owner":
                    self.send_html(OWNER_PANEL_HTML)
                elif parsed.path == "/api/health":
                    self.send_json({"ok": True, "app": APP_NAME, "time": int(time.time())})
                elif parsed.path == "/api/admin/overview":
                    if not self.is_admin(query):
                        return
                    self.send_json(admin_overview(conn))
                elif parsed.path == "/api/admin/export":
                    if not self.is_admin(query):
                        return
                    self.send_json({"exportedAt": int(time.time()), "data": admin_overview(conn)})
                elif parsed.path == "/api/games":
                    self.send_json({"games": list_games(conn)})
                elif parsed.path == "/api/players":
                    self.send_json({"players": list_players(conn, query)})
                elif parsed.path.startswith("/api/players/"):
                    player_id = parsed.path.rsplit("/", 1)[-1]
                    row = conn.execute("SELECT * FROM players WHERE id = ?", (player_id,)).fetchone()
                    if not row:
                        self.send_error_json(HTTPStatus.NOT_FOUND, "Player not found")
                        return
                    player = row_to_player(row)
                    player["compatibility"] = compatibility_score(player)
                    self.send_json({"player": player})
                elif parsed.path == "/api/lfg":
                    self.send_json({"posts": list_lfg(conn)})
                elif parsed.path == "/api/squads":
                    self.send_json({"squads": list_squads(conn)})
                else:
                    self.send_error_json(HTTPStatus.NOT_FOUND, "Route not found")
        except Exception as exc:
            self.send_error_json(HTTPStatus.INTERNAL_SERVER_ERROR, str(exc))

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        try:
            payload = self.read_json_body()
            if parsed.path == "/api/admin/reset":
                if not self.is_admin(parse_qs(parsed.query)):
                    return
                reset_database()
                self.send_json({"ok": True, "message": "Database reset"})
                return
            with db() as conn:
                if parsed.path == "/api/admin/player-online":
                    if not self.is_admin(parse_qs(parsed.query)):
                        return
                    player_id = payload.get("playerId")
                    online = payload.get("online")
                    if not player_id or not isinstance(online, bool):
                        self.send_error_json(HTTPStatus.BAD_REQUEST, "playerId and boolean online are required")
                        return
                    result = conn.execute("UPDATE players SET online = ? WHERE id = ?", (1 if online else 0, player_id))
                    if result.rowcount == 0:
                        self.send_error_json(HTTPStatus.NOT_FOUND, "Player not found")
                        return
                    self.send_json({"playerId": player_id, "online": online})
                elif parsed.path == "/api/admin/connection-status":
                    if not self.is_admin(parse_qs(parsed.query)):
                        return
                    request_id = payload.get("requestId")
                    status = payload.get("status")
                    if status not in {"pending", "accepted", "rejected"} or not request_id:
                        self.send_error_json(HTTPStatus.BAD_REQUEST, "requestId and status pending/accepted/rejected are required")
                        return
                    result = conn.execute("UPDATE connection_requests SET status = ? WHERE id = ?", (status, request_id))
                    if result.rowcount == 0:
                        self.send_error_json(HTTPStatus.NOT_FOUND, "Connection request not found")
                        return
                    self.send_json({"requestId": request_id, "status": status})
                elif parsed.path == "/api/connections":
                    from_player_id = payload.get("fromPlayerId", "p_novapulse")
                    to_player_id = payload.get("toPlayerId")
                    message = payload.get("message", "Want to squad up?")
                    if not to_player_id:
                        self.send_error_json(HTTPStatus.BAD_REQUEST, "toPlayerId is required")
                        return
                    request_id = f"cr_{int(time.time() * 1000)}"
                    conn.execute(
                        """
                        INSERT INTO connection_requests (id, from_player_id, to_player_id, message, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        (request_id, from_player_id, to_player_id, message, "pending", int(time.time())),
                    )
                    self.send_json({"connectionRequest": {"id": request_id, "status": "pending"}}, HTTPStatus.CREATED)
                else:
                    self.send_error_json(HTTPStatus.NOT_FOUND, "Route not found")
        except ValueError as exc:
            self.send_error_json(HTTPStatus.BAD_REQUEST, str(exc))
        except Exception as exc:
            self.send_error_json(HTTPStatus.INTERNAL_SERVER_ERROR, str(exc))

    def read_json_body(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("Invalid JSON body") from exc
        if not isinstance(payload, dict):
            raise ValueError("JSON body must be an object")
        return payload

    def is_admin(self, query: dict[str, list[str]]) -> bool:
        expected = admin_token()
        supplied = self.headers.get("X-Admin-Token", "").strip()
        auth = self.headers.get("Authorization", "").strip()
        if auth.lower().startswith("bearer "):
            supplied = auth[7:].strip()
        supplied = supplied or query.get("token", [""])[0].strip()
        if secrets.compare_digest(supplied, expected):
            return True
        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Owner token required")
        return False

    def send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_html(self, html: str, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = html.encode("utf-8")
        self.send_response(status)
        self.send_cors_headers()
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_error_json(self, status: HTTPStatus, message: str) -> None:
        self.send_json({"error": message, "status": status.value}, status)

    def send_cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token, Authorization")

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[{self.log_date_time_string()}] {self.address_string()} {fmt % args}")


def main() -> None:
    init_db()
    token = admin_token()
    server = ThreadingHTTPServer((HOST, PORT), ApiHandler)
    print(f"{APP_NAME} listening on http://{HOST}:{PORT}")
    print(f"Owner panel: http://127.0.0.1:{PORT}/owner?token={token}")
    print(f"Database: {DB_PATH}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
