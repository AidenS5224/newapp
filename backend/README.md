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
