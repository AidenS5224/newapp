$ErrorActionPreference = "Stop"
$env:GAMER_CONNECT_HOST = "0.0.0.0"
$env:GAMER_CONNECT_PORT = "8080"
python "$PSScriptRoot\app.py"
