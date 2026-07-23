from __future__ import annotations


OWNER_PANEL_HTML = r"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Gamer Connect Owner Panel</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #050912;
      --panel: #0e1726;
      --panel-2: #111d31;
      --line: #263244;
      --text: #eef3ff;
      --muted: #9aa8bd;
      --purple: #a159ff;
      --green: #34d373;
      --red: #fb7185;
      --amber: #fbbf24;
    }

    * { box-sizing: border-box; }

    body {
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: Inter, Segoe UI, system-ui, sans-serif;
    }

    button, input {
      font: inherit;
    }

    .shell {
      display: grid;
      grid-template-columns: 260px 1fr;
      min-height: 100vh;
    }

    aside {
      border-right: 1px solid var(--line);
      padding: 22px;
      background: #070c16;
    }

    main {
      padding: 24px;
      max-width: 1280px;
    }

    h1, h2, h3, p {
      margin-top: 0;
    }

    h1 {
      font-size: 26px;
      margin-bottom: 6px;
    }

    h2 {
      font-size: 18px;
      margin-bottom: 12px;
    }

    h3 {
      font-size: 15px;
      margin-bottom: 6px;
    }

    .muted {
      color: var(--muted);
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 24px;
      font-weight: 800;
      letter-spacing: 0;
    }

    .mark {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border: 1px solid rgba(161, 89, 255, .7);
      border-radius: 8px;
      color: var(--purple);
      font-weight: 900;
      background: rgba(161, 89, 255, .12);
    }

    nav {
      display: grid;
      gap: 8px;
      margin: 20px 0;
    }

    nav button, .plain-button {
      text-align: left;
      border: 1px solid var(--line);
      color: var(--text);
      background: var(--panel);
      padding: 10px 12px;
      border-radius: 8px;
      cursor: pointer;
    }

    nav button.active {
      border-color: var(--purple);
      background: rgba(161, 89, 255, .16);
    }

    .token-box {
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 12px;
      background: var(--panel);
    }

    .token-box input {
      width: 100%;
      margin: 8px 0;
      padding: 9px 10px;
      color: var(--text);
      border: 1px solid var(--line);
      border-radius: 8px;
      background: #080f1d;
    }

    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 18px;
    }

    .grid {
      display: grid;
      gap: 14px;
    }

    .stats {
      grid-template-columns: repeat(5, minmax(120px, 1fr));
    }

    .two {
      grid-template-columns: 1.2fr .8fr;
    }

    .card {
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      padding: 16px;
    }

    .stat {
      min-height: 96px;
    }

    .stat strong {
      display: block;
      margin-top: 8px;
      font-size: 28px;
    }

    .success { color: var(--green); }
    .danger { color: var(--red); }
    .accent { color: var(--purple); }
    .warn { color: var(--amber); }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    th, td {
      text-align: left;
      padding: 10px;
      border-bottom: 1px solid var(--line);
      vertical-align: top;
      font-size: 14px;
    }

    th {
      color: var(--muted);
      font-weight: 700;
    }

    .pill {
      display: inline-flex;
      align-items: center;
      border: 1px solid var(--line);
      border-radius: 999px;
      padding: 3px 8px;
      margin: 2px;
      color: var(--muted);
      background: #0a1220;
      font-size: 12px;
    }

    .actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .action {
      border: 0;
      border-radius: 8px;
      padding: 9px 12px;
      color: white;
      cursor: pointer;
      background: var(--purple);
    }

    .action.green { background: #168b48; }
    .action.red { background: #b91c1c; }
    .action.dark {
      border: 1px solid var(--line);
      background: var(--panel-2);
    }

    .hidden {
      display: none;
    }

    .notice {
      border: 1px solid rgba(251, 191, 36, .4);
      color: #fde68a;
      background: rgba(251, 191, 36, .08);
      padding: 12px;
      border-radius: 8px;
      margin-bottom: 16px;
    }

    @media (max-width: 900px) {
      .shell { grid-template-columns: 1fr; }
      aside { border-right: 0; border-bottom: 1px solid var(--line); }
      .stats, .two { grid-template-columns: 1fr; }
      .topbar { align-items: stretch; flex-direction: column; }
    }
  </style>
</head>
<body>
  <div class="shell">
    <aside>
      <div class="brand">
        <div class="mark">GC</div>
        <div>
          <div>Gamer Connect</div>
          <div class="muted">Owner Panel</div>
        </div>
      </div>
      <div class="token-box">
        <strong>Owner Access</strong>
        <p class="muted">Paste your admin token if this panel is locked.</p>
        <input id="tokenInput" type="password" placeholder="Admin token" />
        <button class="plain-button" id="saveToken">Save token</button>
      </div>
      <nav>
        <button class="active" data-view="overview">Overview</button>
        <button data-view="players">Players</button>
        <button data-view="connections">Connections</button>
        <button data-view="lfg">LFG</button>
        <button data-view="squads">Squads</button>
        <button data-view="system">System</button>
      </nav>
    </aside>

    <main>
      <div class="topbar">
        <div>
          <h1>Owner Overview</h1>
          <p class="muted">Private local dashboard for testing, analytics, and backend controls.</p>
        </div>
        <div class="actions">
          <button class="action dark" id="refresh">Refresh</button>
          <button class="action red" id="resetDb">Reset Seed Data</button>
        </div>
      </div>

      <div id="notice" class="notice hidden"></div>

      <section id="overview" class="view">
        <div class="grid stats" id="stats"></div>
        <div class="grid two" style="margin-top:14px;">
          <div class="card">
            <h2>Recent Connection Requests</h2>
            <div id="recentConnections"></div>
          </div>
          <div class="card">
            <h2>Top Games</h2>
            <div id="topGames"></div>
          </div>
        </div>
      </section>

      <section id="players" class="view hidden">
        <div class="card">
          <h2>Players</h2>
          <div id="playersTable"></div>
        </div>
      </section>

      <section id="connections" class="view hidden">
        <div class="card">
          <h2>Connection Requests</h2>
          <div id="connectionsTable"></div>
        </div>
      </section>

      <section id="lfg" class="view hidden">
        <div class="card">
          <h2>Looking For Group Posts</h2>
          <div id="lfgTable"></div>
        </div>
      </section>

      <section id="squads" class="view hidden">
        <div class="card">
          <h2>Squads</h2>
          <div id="squadsTable"></div>
        </div>
      </section>

      <section id="system" class="view hidden">
        <div class="grid two">
          <div class="card">
            <h2>System</h2>
            <div id="systemInfo"></div>
          </div>
          <div class="card">
            <h2>Quick Actions</h2>
            <div class="actions">
              <button class="action dark" id="exportData">Export JSON</button>
              <button class="action green" id="seedOnline">Set GhostRider Online</button>
              <button class="action red" id="seedOffline">Set GhostRider Offline</button>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>

  <script>
    const tokenFromUrl = new URLSearchParams(location.search).get("token");
    if (tokenFromUrl) localStorage.setItem("gcAdminToken", tokenFromUrl);
    const tokenInput = document.querySelector("#tokenInput");
    tokenInput.value = localStorage.getItem("gcAdminToken") || "";

    let state = null;

    function token() {
      return localStorage.getItem("gcAdminToken") || "";
    }

    function showNotice(message, isError = false) {
      const notice = document.querySelector("#notice");
      notice.textContent = message;
      notice.classList.remove("hidden");
      notice.style.borderColor = isError ? "rgba(251, 113, 133, .6)" : "rgba(52, 211, 115, .5)";
      notice.style.color = isError ? "#fecdd3" : "#bbf7d0";
      setTimeout(() => notice.classList.add("hidden"), 4500);
    }

    async function api(path, options = {}) {
      const response = await fetch(path, {
        ...options,
        headers: {
          "Content-Type": "application/json",
          "X-Admin-Token": token(),
          ...(options.headers || {})
        }
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || `Request failed: ${response.status}`);
      }
      return response.json();
    }

    function table(headers, rows) {
      if (!rows.length) return "<p class='muted'>(none)</p>";
      return `<table><thead><tr>${headers.map(h => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table>`;
    }

    function tags(items) {
      return (items || []).map(item => `<span class="pill">${item}</span>`).join("");
    }

    function renderStats(summary) {
      const cards = [
        ["Players", summary.counts.players, "accent"],
        ["Online", summary.counts.onlinePlayers, "success"],
        ["Pending", summary.counts.pendingConnections, "warn"],
        ["LFG Posts", summary.counts.lfgPosts, "accent"],
        ["Squads", summary.counts.squads, "success"]
      ];
      document.querySelector("#stats").innerHTML = cards.map(([label, value, cls]) =>
        `<div class="card stat"><span class="muted">${label}</span><strong class="${cls}">${value}</strong></div>`
      ).join("");
    }

    function renderPlayers(players) {
      document.querySelector("#playersTable").innerHTML = table(
        ["Handle", "State", "Rank", "Region", "Games", "Actions"],
        players.map(player => `
          <tr>
            <td><strong>${player.handle}</strong><br><span class="muted">${player.id}</span></td>
            <td class="${player.online ? "success" : "muted"}">${player.online ? "online" : "offline"}</td>
            <td>${player.rank}</td>
            <td>${player.region}</td>
            <td>${tags(player.topGames)}</td>
            <td>
              <div class="actions">
                <button class="action green" onclick="setOnline('${player.id}', true)">Online</button>
                <button class="action dark" onclick="setOnline('${player.id}', false)">Offline</button>
              </div>
            </td>
          </tr>
        `)
      );
    }

    function renderConnections(connections) {
      const rows = connections.map(request => `
        <tr>
          <td><strong>${request.from}</strong> -> <strong>${request.to}</strong><br><span class="muted">${request.id}</span></td>
          <td>${request.status}</td>
          <td>${request.created}</td>
          <td>${request.message}</td>
          <td>
            <div class="actions">
              <button class="action green" onclick="setConnection('${request.id}', 'accepted')">Approve</button>
              <button class="action red" onclick="setConnection('${request.id}', 'rejected')">Reject</button>
            </div>
          </td>
        </tr>
      `);
      document.querySelector("#connectionsTable").innerHTML = table(["Request", "Status", "Created", "Message", "Actions"], rows);
      document.querySelector("#recentConnections").innerHTML = table(
        ["Request", "Status"],
        connections.slice(0, 5).map(request => `<tr><td>${request.from} -> ${request.to}</td><td>${request.status}</td></tr>`)
      );
    }

    function renderLfg(posts) {
      document.querySelector("#lfgTable").innerHTML = table(
        ["Title", "Game", "Host", "Party", "Starts", "Status"],
        posts.map(post => `<tr><td>${post.title}</td><td>${post.gameName}</td><td>${post.handle}</td><td>${post.partySize}</td><td>${post.startsAt}</td><td>${post.status}</td></tr>`)
      );
    }

    function renderSquads(squads) {
      document.querySelector("#squadsTable").innerHTML = table(
        ["Name", "Game", "Open Slots", "Voice", "Schedule", "Description"],
        squads.map(squad => `<tr><td>${squad.name}</td><td>${squad.gameName}</td><td>${squad.openSlots}</td><td>${squad.voicePreference}</td><td>${squad.schedule}</td><td>${squad.description}</td></tr>`)
      );
    }

    function renderTopGames(games) {
      document.querySelector("#topGames").innerHTML = table(
        ["Game", "Players"],
        games.map(game => `<tr><td>${game.name}</td><td>${game.players}</td></tr>`)
      );
    }

    function renderSystem(system) {
      document.querySelector("#systemInfo").innerHTML = table(
        ["Key", "Value"],
        Object.entries(system).map(([key, value]) => `<tr><td>${key}</td><td>${value}</td></tr>`)
      );
    }

    async function refresh() {
      try {
        state = await api("/api/admin/overview");
        renderStats(state.summary);
        renderPlayers(state.players);
        renderConnections(state.connectionRequests);
        renderLfg(state.lfgPosts);
        renderSquads(state.squads);
        renderTopGames(state.summary.topGames);
        renderSystem(state.system);
      } catch (error) {
        showNotice(error.message, true);
      }
    }

    async function setOnline(playerId, online) {
      await api("/api/admin/player-online", {
        method: "POST",
        body: JSON.stringify({ playerId, online })
      });
      showNotice(`${playerId} is now ${online ? "online" : "offline"}`);
      refresh();
    }

    async function setConnection(requestId, status) {
      await api("/api/admin/connection-status", {
        method: "POST",
        body: JSON.stringify({ requestId, status })
      });
      showNotice(`${requestId} marked ${status}`);
      refresh();
    }

    async function resetDb() {
      if (!confirm("Reset local database and restore seed data?")) return;
      await api("/api/admin/reset", { method: "POST", body: "{}" });
      showNotice("Database reset");
      refresh();
    }

    async function exportData() {
      const data = await api("/api/admin/export");
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = "gamer-connect-export.json";
      link.click();
      URL.revokeObjectURL(link.href);
    }

    document.querySelector("#saveToken").addEventListener("click", () => {
      localStorage.setItem("gcAdminToken", tokenInput.value.trim());
      showNotice("Token saved");
      refresh();
    });
    document.querySelector("#refresh").addEventListener("click", refresh);
    document.querySelector("#resetDb").addEventListener("click", resetDb);
    document.querySelector("#exportData").addEventListener("click", exportData);
    document.querySelector("#seedOnline").addEventListener("click", () => setOnline("p_ghost", true));
    document.querySelector("#seedOffline").addEventListener("click", () => setOnline("p_ghost", false));

    document.querySelectorAll("nav button").forEach(button => {
      button.addEventListener("click", () => {
        document.querySelectorAll("nav button").forEach(item => item.classList.remove("active"));
        document.querySelectorAll(".view").forEach(view => view.classList.add("hidden"));
        button.classList.add("active");
        document.querySelector(`#${button.dataset.view}`).classList.remove("hidden");
      });
    });

    refresh();
  </script>
</body>
</html>
"""
