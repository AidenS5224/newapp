# Gamer Connect Backend

No external Python packages are required.

## Start

```bash
python3 app.py
```

On Windows:

```powershell
python app.py
```

## Configuration

Environment variables:

```text
GAMER_CONNECT_HOST=0.0.0.0
GAMER_CONNECT_PORT=8080
GAMER_CONNECT_DB=./data/gamer_connect.sqlite3
```

## Test

```bash
python3 test_api.py
```

The test starts the API, checks seeded endpoints, creates a connection request, and shuts the server down.

## Control Terminal

Interactive admin/control shell:

```bash
python3 control.py
```

Windows:

```powershell
python control.py
```

One-off commands:

```bash
python3 control.py status
python3 control.py players
python3 control.py lfg
python3 control.py connections
python3 control.py online p_ghost on
python3 control.py export ./data/export.json
```

The control terminal edits the same SQLite database used by the running backend.
