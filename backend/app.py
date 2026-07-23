from __future__ import annotations

import json
import os
import hashlib
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
SESSION_TTL_SECONDS = 60 * 60 * 24 * 30
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


def json_text(value: Any) -> str:
    return json.dumps(value if value is not None else {})


def hash_password(password: str, salt: str | None = None) -> str:
    salt = salt or secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120_000)
    return f"pbkdf2_sha256${salt}${digest.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        algorithm, salt, expected = stored.split("$", 2)
    except ValueError:
        return False
    if algorithm != "pbkdf2_sha256":
        return False
    actual = hash_password(password, salt).split("$", 2)[-1]
    return secrets.compare_digest(actual, expected)


def public_linked_accounts(accounts: dict[str, Any]) -> dict[str, Any]:
    return {name: {"connected": bool(value)} for name, value in accounts.items()}


def row_to_player(row: dict[str, Any], include_protected: bool = False) -> dict[str, Any]:
    linked_accounts = parse_json(row["linked_accounts"], {})
    protected_info = parse_json(row.get("protected_info"), {})
    player = {
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
        "linkedAccounts": linked_accounts if include_protected else public_linked_accounts(linked_accounts),
        "protectedInfo": protected_info if include_protected else {},
        "infoStacks": parse_json(row.get("info_stacks"), []),
        "hasProtectedInfo": bool(linked_accounts or protected_info),
        "compatibility": row.get("compatibility"),
    }
    return player


def ensure_column(conn: sqlite3.Connection, table: str, column: str, definition: str) -> None:
    columns = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})").fetchall()}
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")


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
                protected_info TEXT NOT NULL DEFAULT '{}',
                info_stacks TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS user_accounts (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL UNIQUE,
                handle TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                player_id TEXT NOT NULL UNIQUE REFERENCES players(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
                expires_at INTEGER NOT NULL,
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

            CREATE TABLE IF NOT EXISTS feed_posts (
                id TEXT PRIMARY KEY,
                player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                post_type TEXT NOT NULL,
                game_id TEXT,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                media_url TEXT,
                media_type TEXT,
                visibility TEXT NOT NULL DEFAULT 'public',
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS feed_reactions (
                post_id TEXT NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
                player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                reaction TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (post_id, player_id)
            );

            CREATE TABLE IF NOT EXISTS feed_comments (
                id TEXT PRIMARY KEY,
                post_id TEXT NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
                player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                conversation_type TEXT NOT NULL,
                created_by_player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS conversation_participants (
                conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                role TEXT NOT NULL DEFAULT 'member',
                joined_at INTEGER NOT NULL,
                PRIMARY KEY (conversation_id, player_id)
            );

            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                sender_player_id TEXT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """
        )
        ensure_column(conn, "players", "protected_info", "TEXT NOT NULL DEFAULT '{}'")
        ensure_column(conn, "players", "info_stacks", "TEXT NOT NULL DEFAULT '[]'")
        seed(conn)


def seed(conn: sqlite3.Connection) -> None:
    existing = conn.execute("SELECT COUNT(*) AS total FROM players").fetchone()["total"]
    if existing:
        seed_test_accounts(conn)
        seed_social_data(conn)
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
            "protected_info": {"phone": "", "notes": "Share Discord after connection is approved."},
            "info_stacks": [
                {"label": "Main Game", "value": "Apex Legends Ranked", "private": False},
                {"label": "Discord", "value": "NovaPulse#2042", "private": True},
                {"label": "Tracker Network", "value": "apex/NovaPulse", "private": True},
            ],
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
            "protected_info": {"phone": "", "notes": "Prefers Discord voice after squad approval."},
            "info_stacks": [
                {"label": "Role", "value": "IGL / shot caller", "private": False},
                {"label": "Discord", "value": "GhostRider#7741", "private": True},
                {"label": "Xbox", "value": "GhostRiderAU", "private": True},
            ],
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
            "protected_info": {"phone": "", "notes": "Riot ID stays protected until matched."},
            "info_stacks": [
                {"label": "Role", "value": "Entry fragger", "private": False},
                {"label": "Discord", "value": "ZaneFPS#1188", "private": True},
                {"label": "Riot", "value": "ZaneFPS#OCE", "private": True},
            ],
        },
    ]
    conn.executemany(
        """
        INSERT INTO players (
            id, handle, display_name, age, region, timezone, platforms, top_games,
            rank, play_style, availability, bio, avatar_url, online, stats,
            linked_accounts, protected_info, info_stacks, created_at
        )
        VALUES (
            :id, :handle, :display_name, :age, :region, :timezone, :platforms, :top_games,
            :rank, :play_style, :availability, :bio, :avatar_url, :online, :stats,
            :linked_accounts, :protected_info, :info_stacks, :created_at
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
                "protected_info": json.dumps(player["protected_info"]),
                "info_stacks": json.dumps(player["info_stacks"]),
                "created_at": now,
            }
            for player in players
        ],
    )

    seed_test_accounts(conn)

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
    seed_social_data(conn)


def seed_test_accounts(conn: sqlite3.Connection) -> None:
    now = int(time.time())
    seed_accounts = [
        ("u_novapulse", "nova@example.local", "NovaPulse", "p_novapulse"),
        ("u_ghost", "ghost@example.local", "GhostRider", "p_ghost"),
        ("u_zane", "zane@example.local", "ZaneFPS", "p_zane"),
    ]
    conn.executemany(
        """
        INSERT OR IGNORE INTO user_accounts (id, email, handle, password_hash, player_id, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [(uid, email, handle, hash_password("testpass123"), player_id, now) for uid, email, handle, player_id in seed_accounts],
    )


def seed_social_data(conn: sqlite3.Connection) -> None:
    if conn.execute("SELECT COUNT(*) AS total FROM feed_posts").fetchone()["total"]:
        return
    now = int(time.time())
    conn.executemany(
        """
        INSERT OR IGNORE INTO feed_posts (
            id, player_id, post_type, game_id, title, body, media_url, media_type, visibility, created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            ("post_clip_nova_1", "p_novapulse", "clip", "apex-legends", "Ranked clutch from last night", "Looking for two calm teammates for the next push.", None, "video", "public", now - 3600),
            ("post_group_weekend_1", "p_ghost", "event", "apex-legends", "Friday squad night is open", "Bring comms, good vibes, and a warm-up game.", None, None, "public", now - 2400),
            ("post_zane_routes_1", "p_zane", "post", "valorant", "Entry routes I am testing", "Drop your best retake setup for split-site pushes.", None, None, "public", now - 1200),
        ],
    )
    conn.executemany(
        """
        INSERT OR IGNORE INTO feed_reactions (post_id, player_id, reaction, created_at)
        VALUES (?, ?, ?, ?)
        """,
        [
            ("post_clip_nova_1", "p_ghost", "like", now - 3300),
            ("post_clip_nova_1", "p_zane", "like", now - 3100),
            ("post_group_weekend_1", "p_novapulse", "like", now - 2100),
        ],
    )
    conn.executemany(
        """
        INSERT OR IGNORE INTO feed_comments (id, post_id, player_id, body, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        [
            ("comment_clip_1", "post_clip_nova_1", "p_ghost", "Clean rotate. I am in for tonight.", now - 3000),
            ("comment_group_1", "post_group_weekend_1", "p_zane", "Can join after warmups.", now - 1900),
        ],
    )
    conn.executemany(
        """
        INSERT OR IGNORE INTO conversations (id, title, conversation_type, created_by_player_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            ("conv_nova_ghost", "NovaPulse / GhostRider", "direct", "p_novapulse", now - 1800, now - 600),
            ("conv_weekend", "Weekend Warriors", "group", "p_ghost", now - 7200, now - 900),
        ],
    )
    conn.executemany(
        """
        INSERT OR IGNORE INTO conversation_participants (conversation_id, player_id, role, joined_at)
        VALUES (?, ?, ?, ?)
        """,
        [
            ("conv_nova_ghost", "p_novapulse", "member", now - 1800),
            ("conv_nova_ghost", "p_ghost", "member", now - 1800),
            ("conv_weekend", "p_novapulse", "member", now - 7200),
            ("conv_weekend", "p_ghost", "owner", now - 7200),
            ("conv_weekend", "p_zane", "member", now - 7000),
        ],
    )
    conn.executemany(
        """
        INSERT OR IGNORE INTO messages (id, conversation_id, sender_player_id, body, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        [
            ("msg_nova_ghost_1", "conv_nova_ghost", "p_ghost", "Saw your clip. Want to run ranked tonight?", now - 1200),
            ("msg_nova_ghost_2", "conv_nova_ghost", "p_novapulse", "Yep. 7PM AEST works.", now - 600),
            ("msg_weekend_1", "conv_weekend", "p_ghost", "Friday squad night is open.", now - 900),
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


def issue_session(conn: sqlite3.Connection, user_id: str) -> str:
    token = secrets.token_urlsafe(32)
    now = int(time.time())
    conn.execute(
        "INSERT INTO sessions (token, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)",
        (token, user_id, now + SESSION_TTL_SECONDS, now),
    )
    return token


def session_user(conn: sqlite3.Connection, token: str | None) -> dict[str, Any] | None:
    if not token:
        return None
    row = conn.execute(
        """
        SELECT ua.*
        FROM sessions s
        JOIN user_accounts ua ON ua.id = s.user_id
        WHERE s.token = ? AND s.expires_at > ?
        """,
        (token, int(time.time())),
    ).fetchone()
    return row


def can_view_protected(conn: sqlite3.Connection, viewer_player_id: str | None, target_player_id: str) -> bool:
    if not viewer_player_id:
        return False
    if viewer_player_id == target_player_id:
        return True
    row = conn.execute(
        """
        SELECT 1
        FROM connection_requests
        WHERE status = 'accepted'
          AND (
            (from_player_id = ? AND to_player_id = ?)
            OR (from_player_id = ? AND to_player_id = ?)
          )
        LIMIT 1
        """,
        (viewer_player_id, target_player_id, target_player_id, viewer_player_id),
    ).fetchone()
    return bool(row)


def visible_info_stacks(stacks: list[dict[str, Any]], include_protected: bool) -> list[dict[str, Any]]:
    visible = []
    for stack in stacks:
        if stack.get("private") and not include_protected:
            visible.append({"label": stack.get("label"), "private": True, "protected": True})
        else:
            visible.append(stack)
    return visible


def player_for_view(conn: sqlite3.Connection, row: dict[str, Any], viewer_player_id: str | None = None, force_private: bool = False) -> dict[str, Any]:
    include_protected = force_private or can_view_protected(conn, viewer_player_id, row["id"])
    player = row_to_player(row, include_protected=include_protected)
    player["infoStacks"] = visible_info_stacks(player["infoStacks"], include_protected)
    player["protectedVisible"] = include_protected
    return player


def list_players(conn: sqlite3.Connection, query: dict[str, list[str]], viewer_player_id: str | None = None, force_private: bool = False) -> list[dict[str, Any]]:
    game = query.get("game", [None])[0]
    platform = query.get("platform", [None])[0]
    rows = conn.execute("SELECT * FROM players ORDER BY online DESC, handle").fetchall()
    players = [player_for_view(conn, row, viewer_player_id, force_private) for row in rows]
    if game:
        players = [player for player in players if game in player["topGames"]]
    if platform:
        players = [player for player in players if platform in player["platforms"] or "Crossplay" in player["platforms"]]
    for player in players:
        player["compatibility"] = compatibility_score(player)
    return players


def user_payload(conn: sqlite3.Connection, user: dict[str, Any]) -> dict[str, Any]:
    row = conn.execute("SELECT * FROM players WHERE id = ?", (user["player_id"],)).fetchone()
    return {
        "id": user["id"],
        "email": user["email"],
        "handle": user["handle"],
        "player": player_for_view(conn, row, user["player_id"], force_private=True) if row else None,
    }


def safe_player_id(handle: str) -> str:
    slug = "".join(char.lower() if char.isalnum() else "_" for char in handle).strip("_")
    return f"p_{slug or secrets.token_hex(4)}"


def create_account(conn: sqlite3.Connection, payload: dict[str, Any]) -> dict[str, Any]:
    email = str(payload.get("email", "")).strip().lower()
    handle = str(payload.get("handle", "")).strip()
    password = str(payload.get("password", ""))
    if not email or "@" not in email:
        raise ValueError("A valid email is required")
    if len(handle) < 3:
        raise ValueError("Handle must be at least 3 characters")
    if len(password) < 8:
        raise ValueError("Password must be at least 8 characters")

    now = int(time.time())
    player_id = safe_player_id(handle)
    while conn.execute("SELECT 1 FROM players WHERE id = ?", (player_id,)).fetchone():
        player_id = f"{safe_player_id(handle)}_{secrets.token_hex(2)}"

    profile = {
        "id": player_id,
        "handle": handle,
        "display_name": str(payload.get("displayName") or handle).strip(),
        "age": int(payload.get("age", 18)),
        "region": str(payload.get("region") or "Australia").strip(),
        "timezone": str(payload.get("timezone") or "AEST").strip(),
        "platforms": payload.get("platforms") or ["PC"],
        "top_games": payload.get("topGames") or [],
        "rank": str(payload.get("rank") or "Unranked").strip(),
        "play_style": payload.get("playStyle") or [],
        "availability": payload.get("availability") or {},
        "bio": str(payload.get("bio") or "New Gamer Connect player.").strip(),
        "avatar_url": payload.get("avatarUrl"),
        "online": 1,
        "stats": payload.get("stats") or {},
        "linked_accounts": payload.get("linkedAccounts") or {},
        "protected_info": payload.get("protectedInfo") or {},
        "info_stacks": payload.get("infoStacks") or [],
        "created_at": now,
    }
    conn.execute(
        """
        INSERT INTO players (
            id, handle, display_name, age, region, timezone, platforms, top_games,
            rank, play_style, availability, bio, avatar_url, online, stats,
            linked_accounts, protected_info, info_stacks, created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            profile["id"],
            profile["handle"],
            profile["display_name"],
            profile["age"],
            profile["region"],
            profile["timezone"],
            json_text(profile["platforms"]),
            json_text(profile["top_games"]),
            profile["rank"],
            json_text(profile["play_style"]),
            json_text(profile["availability"]),
            profile["bio"],
            profile["avatar_url"],
            profile["online"],
            json_text(profile["stats"]),
            json_text(profile["linked_accounts"]),
            json_text(profile["protected_info"]),
            json_text(profile["info_stacks"]),
            profile["created_at"],
        ),
    )
    user_id = f"u_{secrets.token_hex(8)}"
    conn.execute(
        "INSERT INTO user_accounts (id, email, handle, password_hash, player_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        (user_id, email, handle, hash_password(password), player_id, now),
    )
    return conn.execute("SELECT * FROM user_accounts WHERE id = ?", (user_id,)).fetchone()


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


def list_feed_posts(conn: sqlite3.Connection, viewer_player_id: str | None = None) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT fp.*, p.handle, p.display_name, g.name AS game_name,
               COUNT(DISTINCT fr.player_id) AS reaction_count,
               COUNT(DISTINCT fc.id) AS comment_count,
               MAX(CASE WHEN fr.player_id = ? THEN 1 ELSE 0 END) AS reacted_by_viewer
        FROM feed_posts fp
        JOIN players p ON p.id = fp.player_id
        LEFT JOIN games g ON g.id = fp.game_id
        LEFT JOIN feed_reactions fr ON fr.post_id = fp.id
        LEFT JOIN feed_comments fc ON fc.post_id = fp.id
        WHERE fp.visibility = 'public'
        GROUP BY fp.id
        ORDER BY fp.created_at DESC
        """,
        (viewer_player_id or "",),
    ).fetchall()
    return [
        {
            "id": row["id"],
            "playerId": row["player_id"],
            "handle": row["handle"],
            "displayName": row["display_name"],
            "type": row["post_type"],
            "gameId": row["game_id"],
            "gameName": row["game_name"],
            "title": row["title"],
            "body": row["body"],
            "mediaUrl": row["media_url"],
            "mediaType": row["media_type"],
            "visibility": row["visibility"],
            "reactionCount": row["reaction_count"],
            "commentCount": row["comment_count"],
            "reactedByViewer": bool(row["reacted_by_viewer"]),
            "createdAt": row["created_at"],
            "created": time.strftime("%Y-%m-%d %H:%M", time.localtime(row["created_at"])),
        }
        for row in rows
    ]


def list_feed_comments(conn: sqlite3.Connection, post_id: str) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT fc.*, p.handle, p.display_name
        FROM feed_comments fc
        JOIN players p ON p.id = fc.player_id
        WHERE fc.post_id = ?
        ORDER BY fc.created_at ASC
        """,
        (post_id,),
    ).fetchall()
    return [
        {
            "id": row["id"],
            "postId": row["post_id"],
            "playerId": row["player_id"],
            "handle": row["handle"],
            "displayName": row["display_name"],
            "body": row["body"],
            "createdAt": row["created_at"],
            "created": time.strftime("%Y-%m-%d %H:%M", time.localtime(row["created_at"])),
        }
        for row in rows
    ]


def list_conversations(conn: sqlite3.Connection, player_id: str) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT c.*,
               m.body AS last_message,
               m.created_at AS last_message_at
        FROM conversations c
        JOIN conversation_participants cp ON cp.conversation_id = c.id
        LEFT JOIN messages m ON m.id = (
            SELECT id FROM messages
            WHERE conversation_id = c.id
            ORDER BY created_at DESC
            LIMIT 1
        )
        WHERE cp.player_id = ?
        ORDER BY c.updated_at DESC
        """,
        (player_id,),
    ).fetchall()
    return [
        {
            "id": row["id"],
            "title": row["title"],
            "type": row["conversation_type"],
            "createdByPlayerId": row["created_by_player_id"],
            "lastMessage": row["last_message"],
            "lastMessageAt": row["last_message_at"],
            "updatedAt": row["updated_at"],
            "participants": conversation_participants(conn, row["id"]),
        }
        for row in rows
    ]


def conversation_participants(conn: sqlite3.Connection, conversation_id: str) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT cp.*, p.handle, p.display_name
        FROM conversation_participants cp
        JOIN players p ON p.id = cp.player_id
        WHERE cp.conversation_id = ?
        ORDER BY cp.joined_at ASC
        """,
        (conversation_id,),
    ).fetchall()
    return [
        {
            "playerId": row["player_id"],
            "handle": row["handle"],
            "displayName": row["display_name"],
            "role": row["role"],
            "joinedAt": row["joined_at"],
        }
        for row in rows
    ]


def list_messages(conn: sqlite3.Connection, conversation_id: str, player_id: str) -> list[dict[str, Any]]:
    if not is_conversation_member(conn, conversation_id, player_id):
        raise PermissionError("Conversation not found")
    rows = conn.execute(
        """
        SELECT m.*, p.handle, p.display_name
        FROM messages m
        JOIN players p ON p.id = m.sender_player_id
        WHERE m.conversation_id = ?
        ORDER BY m.created_at ASC
        """,
        (conversation_id,),
    ).fetchall()
    return [
        {
            "id": row["id"],
            "conversationId": row["conversation_id"],
            "senderPlayerId": row["sender_player_id"],
            "handle": row["handle"],
            "displayName": row["display_name"],
            "body": row["body"],
            "createdAt": row["created_at"],
            "created": time.strftime("%Y-%m-%d %H:%M", time.localtime(row["created_at"])),
        }
        for row in rows
    ]


def is_conversation_member(conn: sqlite3.Connection, conversation_id: str, player_id: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND player_id = ?",
        (conversation_id, player_id),
    ).fetchone()
    return bool(row)


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
    players = list_players(conn, {}, force_private=True)
    games = list_games(conn)
    lfg_posts = list_lfg(conn)
    squads = list_squads(conn)
    feed_posts = list_feed_posts(conn)
    connection_requests = list_connection_requests(conn)
    conversations = conn.execute("SELECT COUNT(*) AS total FROM conversations").fetchone()["total"]
    pending = [request for request in connection_requests if request["status"] == "pending"]
    return {
        "summary": {
            "counts": {
                "players": len(players),
                "onlinePlayers": sum(1 for player in players if player["online"]),
                "games": len(games),
                "lfgPosts": len(lfg_posts),
                "squads": len(squads),
                "feedPosts": len(feed_posts),
                "conversations": conversations,
                "connectionRequests": len(connection_requests),
                "pendingConnections": len(pending),
            },
            "topGames": top_games(players, games),
        },
        "players": players,
        "games": games,
        "lfgPosts": lfg_posts,
        "squads": squads,
        "feedPosts": feed_posts,
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
            DROP TABLE IF EXISTS messages;
            DROP TABLE IF EXISTS conversation_participants;
            DROP TABLE IF EXISTS conversations;
            DROP TABLE IF EXISTS feed_comments;
            DROP TABLE IF EXISTS feed_reactions;
            DROP TABLE IF EXISTS feed_posts;
            DROP TABLE IF EXISTS squads;
            DROP TABLE IF EXISTS lfg_posts;
            DROP TABLE IF EXISTS sessions;
            DROP TABLE IF EXISTS user_accounts;
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
                elif parsed.path == "/api/me":
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    self.send_json({"user": user_payload(conn, user)})
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
                elif parsed.path == "/api/feed":
                    user = self.current_user(conn)
                    viewer_player_id = user["player_id"] if user else None
                    self.send_json({"posts": list_feed_posts(conn, viewer_player_id)})
                elif parsed.path.startswith("/api/feed/") and parsed.path.endswith("/comments"):
                    post_id = parsed.path.split("/")[3]
                    self.send_json({"comments": list_feed_comments(conn, post_id)})
                elif parsed.path == "/api/conversations":
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    self.send_json({"conversations": list_conversations(conn, user["player_id"])})
                elif parsed.path.startswith("/api/conversations/") and parsed.path.endswith("/messages"):
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    conversation_id = parsed.path.split("/")[3]
                    try:
                        self.send_json({"messages": list_messages(conn, conversation_id, user["player_id"])})
                    except PermissionError:
                        self.send_error_json(HTTPStatus.NOT_FOUND, "Conversation not found")
                elif parsed.path == "/api/players":
                    user = self.current_user(conn)
                    viewer_player_id = user["player_id"] if user else None
                    self.send_json({"players": list_players(conn, query, viewer_player_id)})
                elif parsed.path.startswith("/api/players/"):
                    user = self.current_user(conn)
                    viewer_player_id = user["player_id"] if user else None
                    player_id = parsed.path.rsplit("/", 1)[-1]
                    row = conn.execute("SELECT * FROM players WHERE id = ?", (player_id,)).fetchone()
                    if not row:
                        self.send_error_json(HTTPStatus.NOT_FOUND, "Player not found")
                        return
                    player = player_for_view(conn, row, viewer_player_id)
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
                if parsed.path == "/api/auth/signup":
                    user = create_account(conn, payload)
                    token = issue_session(conn, user["id"])
                    self.send_json({"token": token, "user": user_payload(conn, user)}, HTTPStatus.CREATED)
                elif parsed.path == "/api/auth/login":
                    login = str(payload.get("emailOrHandle") or payload.get("email") or payload.get("handle") or "").strip()
                    password = str(payload.get("password") or "")
                    user = conn.execute(
                        "SELECT * FROM user_accounts WHERE lower(email) = lower(?) OR lower(handle) = lower(?)",
                        (login, login),
                    ).fetchone()
                    if not user or not verify_password(password, user["password_hash"]):
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Invalid email/handle or password")
                        return
                    token = issue_session(conn, user["id"])
                    self.send_json({"token": token, "user": user_payload(conn, user)})
                elif parsed.path == "/api/auth/logout":
                    token = self.bearer_token()
                    if token:
                        conn.execute("DELETE FROM sessions WHERE token = ?", (token,))
                    self.send_json({"ok": True})
                elif parsed.path == "/api/me/profile":
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    self.update_profile(conn, user["player_id"], payload)
                    refreshed = conn.execute("SELECT * FROM user_accounts WHERE id = ?", (user["id"],)).fetchone()
                    self.send_json({"user": user_payload(conn, refreshed)})
                elif parsed.path == "/api/feed/posts":
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    post_id = self.create_feed_post(conn, user["player_id"], payload)
                    self.send_json({"post": next(post for post in list_feed_posts(conn, user["player_id"]) if post["id"] == post_id)}, HTTPStatus.CREATED)
                elif parsed.path.startswith("/api/feed/") and parsed.path.endswith("/react"):
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    post_id = parsed.path.split("/")[3]
                    reaction = str(payload.get("reaction") or "like")
                    conn.execute(
                        """
                        INSERT INTO feed_reactions (post_id, player_id, reaction, created_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(post_id, player_id) DO UPDATE SET reaction = excluded.reaction, created_at = excluded.created_at
                        """,
                        (post_id, user["player_id"], reaction, int(time.time())),
                    )
                    self.send_json({"postId": post_id, "reaction": reaction})
                elif parsed.path.startswith("/api/feed/") and parsed.path.endswith("/comments"):
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    post_id = parsed.path.split("/")[3]
                    body = str(payload.get("body") or "").strip()
                    if not body:
                        self.send_error_json(HTTPStatus.BAD_REQUEST, "Comment body is required")
                        return
                    comment_id = f"comment_{int(time.time() * 1000)}_{secrets.token_hex(3)}"
                    conn.execute(
                        "INSERT INTO feed_comments (id, post_id, player_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                        (comment_id, post_id, user["player_id"], body, int(time.time())),
                    )
                    self.send_json({"comment": list_feed_comments(conn, post_id)[-1]}, HTTPStatus.CREATED)
                elif parsed.path == "/api/conversations":
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    conversation_id = self.create_conversation(conn, user["player_id"], payload)
                    conversation = next(item for item in list_conversations(conn, user["player_id"]) if item["id"] == conversation_id)
                    self.send_json({"conversation": conversation}, HTTPStatus.CREATED)
                elif parsed.path.startswith("/api/conversations/") and parsed.path.endswith("/messages"):
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    conversation_id = parsed.path.split("/")[3]
                    if not is_conversation_member(conn, conversation_id, user["player_id"]):
                        self.send_error_json(HTTPStatus.NOT_FOUND, "Conversation not found")
                        return
                    body = str(payload.get("body") or "").strip()
                    if not body:
                        self.send_error_json(HTTPStatus.BAD_REQUEST, "Message body is required")
                        return
                    message_id = f"msg_{int(time.time() * 1000)}_{secrets.token_hex(3)}"
                    now = int(time.time())
                    conn.execute(
                        "INSERT INTO messages (id, conversation_id, sender_player_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                        (message_id, conversation_id, user["player_id"], body, now),
                    )
                    conn.execute("UPDATE conversations SET updated_at = ? WHERE id = ?", (now, conversation_id))
                    message = list_messages(conn, conversation_id, user["player_id"])[-1]
                    self.send_json({"message": message}, HTTPStatus.CREATED)
                elif parsed.path == "/api/admin/player-online":
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
                    user = self.current_user(conn)
                    if not user:
                        self.send_error_json(HTTPStatus.UNAUTHORIZED, "Login required")
                        return
                    from_player_id = user["player_id"]
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

    def bearer_token(self) -> str | None:
        auth = self.headers.get("Authorization", "").strip()
        if auth.lower().startswith("bearer "):
            return auth[7:].strip()
        token = self.headers.get("X-Session-Token", "").strip()
        return token or None

    def current_user(self, conn: sqlite3.Connection) -> dict[str, Any] | None:
        return session_user(conn, self.bearer_token())

    def update_profile(self, conn: sqlite3.Connection, player_id: str, payload: dict[str, Any]) -> None:
        field_map = {
            "displayName": ("display_name", str),
            "age": ("age", int),
            "region": ("region", str),
            "timezone": ("timezone", str),
            "platforms": ("platforms", json_text),
            "topGames": ("top_games", json_text),
            "rank": ("rank", str),
            "playStyle": ("play_style", json_text),
            "availability": ("availability", json_text),
            "bio": ("bio", str),
            "avatarUrl": ("avatar_url", lambda value: value),
            "stats": ("stats", json_text),
            "linkedAccounts": ("linked_accounts", json_text),
            "protectedInfo": ("protected_info", json_text),
            "infoStacks": ("info_stacks", json_text),
        }
        updates = []
        values: list[Any] = []
        for public_name, (column, caster) in field_map.items():
            if public_name in payload:
                updates.append(f"{column} = ?")
                values.append(caster(payload[public_name]))
        if not updates:
            raise ValueError("No profile fields supplied")
        values.append(player_id)
        conn.execute(f"UPDATE players SET {', '.join(updates)} WHERE id = ?", values)

    def create_feed_post(self, conn: sqlite3.Connection, player_id: str, payload: dict[str, Any]) -> str:
        title = str(payload.get("title") or "").strip()
        body = str(payload.get("body") or "").strip()
        post_type = str(payload.get("type") or payload.get("postType") or "post").strip().lower()
        if post_type not in {"post", "clip", "event"}:
            raise ValueError("type must be post, clip, or event")
        if not title:
            raise ValueError("Feed post title is required")
        if not body:
            raise ValueError("Feed post body is required")
        post_id = f"post_{int(time.time() * 1000)}_{secrets.token_hex(3)}"
        conn.execute(
            """
            INSERT INTO feed_posts (
                id, player_id, post_type, game_id, title, body, media_url, media_type, visibility, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                post_id,
                player_id,
                post_type,
                payload.get("gameId"),
                title,
                body,
                payload.get("mediaUrl"),
                payload.get("mediaType"),
                payload.get("visibility") or "public",
                int(time.time()),
            ),
        )
        return post_id

    def create_conversation(self, conn: sqlite3.Connection, player_id: str, payload: dict[str, Any]) -> str:
        participant_ids = payload.get("participantPlayerIds") or payload.get("participants") or []
        if not isinstance(participant_ids, list):
            raise ValueError("participantPlayerIds must be a list")
        clean_ids = []
        for participant_id in participant_ids:
            participant_id = str(participant_id).strip()
            if participant_id and participant_id not in clean_ids and participant_id != player_id:
                clean_ids.append(participant_id)
        if not clean_ids:
            raise ValueError("At least one other participant is required")
        for participant_id in clean_ids:
            if not conn.execute("SELECT 1 FROM players WHERE id = ?", (participant_id,)).fetchone():
                raise ValueError(f"Player not found: {participant_id}")

        conversation_type = "group" if len(clean_ids) > 1 else "direct"
        title = str(payload.get("title") or "").strip()
        if not title:
            handles = conn.execute(
                f"SELECT handle FROM players WHERE id IN ({','.join('?' for _ in clean_ids)})",
                clean_ids,
            ).fetchall()
            title = ", ".join(row["handle"] for row in handles) or "New Chat"
        conversation_id = f"conv_{int(time.time() * 1000)}_{secrets.token_hex(3)}"
        now = int(time.time())
        conn.execute(
            "INSERT INTO conversations (id, title, conversation_type, created_by_player_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            (conversation_id, title, conversation_type, player_id, now, now),
        )
        participants = [player_id, *clean_ids]
        conn.executemany(
            "INSERT INTO conversation_participants (conversation_id, player_id, role, joined_at) VALUES (?, ?, ?, ?)",
            [(conversation_id, participant_id, "owner" if participant_id == player_id else "member", now) for participant_id in participants],
        )
        first_message = str(payload.get("message") or "").strip()
        if first_message:
            conn.execute(
                "INSERT INTO messages (id, conversation_id, sender_player_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                (f"msg_{int(time.time() * 1000)}_{secrets.token_hex(3)}", conversation_id, player_id, first_message, now),
            )
        return conversation_id

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
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token, X-Session-Token, Authorization")

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
