const tabs = [
  ["events", "Events/Servers"],
  ["discovery", "Discovery/LFG"],
  ["feed", "Feed"],
  ["messages", "Messages"],
  ["profile", "Profile"]
];

const popularGames = [
  "Apex Legends",
  "Valorant",
  "Call of Duty: Warzone",
  "Fortnite",
  "Minecraft",
  "Roblox",
  "League of Legends",
  "Counter-Strike 2",
  "Overwatch 2",
  "Rocket League",
  "The Division 2",
  "Rainbow Six Siege",
  "Destiny 2",
  "Grand Theft Auto V",
  "EA Sports FC",
  "NBA 2K",
  "Helldivers 2"
];

const platformOptions = ["PC", "PlayStation", "Xbox", "Nintendo Switch", "Mobile"];
const playStyleOptions = ["Competitive", "Casual", "Good Comms", "Team Player", "Aggressive", "Strategic", "Coach / Mentor"];
const trackerPlatformOptions = [
  ["origin", "PC / EA Origin"],
  ["xbl", "Xbox"],
  ["psn", "PlayStation"]
];
const accountPlatformOptions = [
  ["ea", "EA / Origin"],
  ["ubisoft", "Ubisoft Connect"],
  ["steam", "Steam"],
  ["xbox", "Xbox"],
  ["psn", "PlayStation"],
  ["riot", "Riot"],
  ["bungie", "Bungie"],
  ["nintendo", "Nintendo"],
  ["epic", "Epic Games"]
];
const statSourceOptions = [
  ["ubisoft", "Ubisoft Connect"],
  ["ea", "EA / Origin"],
  ["steam", "Steam"],
  ["epic", "Epic Games"],
  ["xbox", "Xbox"],
  ["psn", "PlayStation"],
  ["nintendo", "Nintendo"],
  ["riot", "Riot"],
  ["bungie", "Bungie"],
  ["mobile", "Mobile"],
  ["manual", "Manual / user provided"]
];
const gameProviderSupport = {
  "Apex Legends": { provider: "tracker-network", providerLabel: "Tracker Network", gameId: "apex-legends", platforms: { ea: "origin", xbox: "xbl", psn: "psn" } },
  "The Division 2": { provider: "tracker-network", providerLabel: "Tracker Network", gameId: "the-division-2", platforms: { ubisoft: "uplay", xbox: "xbl", psn: "psn" } },
  "Rainbow Six Siege": { provider: "r6data", providerLabel: "R6Data", gameId: "rainbow-six-siege", platforms: { ubisoft: "uplay", xbox: "xbl", psn: "psn" } }
};

const state = {
  tab: "profile",
  authMode: "signup",
  authMessage: "",
  profileEdit: false,
  profileMessage: "",
  trackerMessage: "",
  profileDraftGames: null,
  ready: false,
  config: null,
  supabase: null,
  session: null,
  profile: null,
  feed: [],
  profiles: [],
  games: [],
  lfg: [],
  squads: [],
  conversations: [],
  privateProfile: null,
  linkedAccounts: [],
  messages: {}
};

const app = document.querySelector("#app");

boot();

async function boot() {
  renderShell();
  await initSupabase();
  if (state.supabase) {
    const { data } = await state.supabase.auth.getSession();
    state.session = data.session;
    if (state.session) state.tab = "feed";
    state.supabase.auth.onAuthStateChange((_event, session) => {
      state.session = session;
      if (session && state.tab === "profile") state.tab = "feed";
      loadData();
    });
  }
  await loadData();
}

async function initSupabase() {
  try {
    const response = await fetch("/api/config", { cache: "no-store" });
    state.config = await response.json();
    if (!state.config.ready || !window.supabase) return;
    state.supabase = window.supabase.createClient(state.config.supabaseUrl, state.config.supabaseAnonKey);
    state.ready = true;
  } catch (error) {
    console.warn(error);
    state.config = { ready: false, error: "Could not load Supabase config from Vercel." };
  }
}

async function loadData() {
  if (!state.supabase) {
    renderShell();
    return;
  }
  await ensureProfile();
  const [profiles, games, feed, lfg, squads] = await Promise.all([
    read("profiles", "id, handle, display_name, age, region, timezone, platforms, top_games, rank, play_style, availability, bio, avatar_url, online, stats, created_at"),
    read("games", "*"),
    read("feed_posts", "*", { order: "created_at" }),
    read("lfg_posts", "*", { order: "created_at" }),
    read("squads", "*", { order: "created_at" })
  ]);
  state.profiles = profiles || [];
  state.games = games || [];
  state.feed = feed || [];
  state.lfg = lfg || [];
  state.squads = squads || [];
  if (state.session) {
    state.conversations = await loadConversations();
    await loadPrivateProfile();
  }
  renderShell();
}

async function read(table, columns = "*", options = {}) {
  let query = state.supabase.from(table).select(columns);
  if (options.order) query = query.order(options.order, { ascending: false });
  const { data, error } = await query;
  if (error) {
    console.warn(table, error.message);
    return [];
  }
  return data;
}

async function ensureProfile() {
  if (!state.session) {
    state.profile = null;
    return;
  }
  const user = state.session.user;
  const { data } = await state.supabase.from("profiles").select("*").eq("id", user.id).maybeSingle();
  if (data) {
    state.profile = data;
    return;
  }
  const meta = user.user_metadata || {};
  const fallback = (meta.handle || user.email || "player").split("@")[0].replace(/[^a-z0-9_]/gi, "").slice(0, 18) || "Player";
  const profile = {
    id: user.id,
    handle: meta.handle || fallback,
    display_name: meta.display_name || meta.handle || fallback,
    age: numberOrNull(meta.age),
    region: meta.region || "Australia",
    timezone: meta.timezone || "AEST",
    platforms: meta.platforms || ["PC"],
    top_games: meta.top_games || ["apex-legends"],
    rank: meta.rank || "Unranked",
    play_style: meta.play_style || ["Good Comms"],
    availability: meta.availability || {},
    bio: meta.bio || "New Gamer Connect player."
  };
  const { data: created, error } = await state.supabase.from("profiles").insert(profile).select("*").single();
  if (!error) {
    state.profile = created;
    await savePrivateSetup(user.id, meta);
  }
}

async function loadConversations() {
  const { data, error } = await state.supabase
    .from("conversations")
    .select("*, conversation_participants(profile_id, role)")
    .order("updated_at", { ascending: false });
  if (error) {
    console.warn(error.message);
    return [];
  }
  return data || [];
}

async function loadPrivateProfile() {
  if (!state.profile) {
    state.privateProfile = null;
    state.linkedAccounts = [];
    return;
  }
  const [{ data: privateProfile }, { data: linkedAccounts }] = await Promise.all([
    state.supabase.from("profile_private").select("*").eq("profile_id", state.profile.id).maybeSingle(),
    state.supabase.from("linked_accounts").select("*").eq("profile_id", state.profile.id)
  ]);
  state.privateProfile = privateProfile || null;
  state.linkedAccounts = linkedAccounts || [];
}

function renderShell() {
  app.innerHTML = `
    <div class="app-shell">
      <aside class="sidebar">
        <div class="brand">
          <div class="mark">GC</div>
          <div>
            <h1>Gamer Connect</h1>
            <p>Feed. Squads. Matches.</p>
          </div>
        </div>
        <nav class="nav">
          ${tabs.map(([id, label]) => `<button class="${state.tab === id ? "active" : ""}" data-tab="${id}">${label}</button>`).join("")}
        </nav>
        <div class="session-card">
          <strong>${state.profile?.handle || "Guest"}</strong>
          <span>${state.ready ? "Supabase connected" : "Supabase config missing"}</span>
        </div>
      </aside>
      <section class="main">${renderMain()}</section>
    </div>
  `;
  document.querySelectorAll("[data-tab]").forEach(button => {
    button.addEventListener("click", () => {
      state.tab = button.dataset.tab;
      renderShell();
    });
  });
  bindPageEvents();
}

function renderMain() {
  if (!state.ready) return renderConfigMissing();
  if (!state.session && state.tab !== "profile") return renderAuthGate();
  if (state.tab === "feed") return renderFeed();
  if (state.tab === "discovery") return renderDiscovery();
  if (state.tab === "messages") return renderMessages();
  if (state.tab === "events") return renderEventsServers();
  return renderProfile();
}

function renderAuthGate() {
  return page("Create Your Gamer Connect Account", "Start with a secure account. Your gamer profile can be set up next.", `
    <div class="auth-hero">
      <div>
        <span class="pill hot">Account setup</span>
        <h3>Sign in or create your account.</h3>
        <p>This first step only creates the secure login. Main game, rank, availability, play style, bio, platform handles, and display name will move into a separate profile setup menu.</p>
      </div>
      <button class="button green" data-profile-tab>Create Account</button>
    </div>
    ${renderAuthForms()}
  `);
}

function page(title, subtitle, body) {
  return `
    <header class="topbar">
      <div>
        <h2>${title}</h2>
        <p>${subtitle}</p>
      </div>
      <button class="button dark" data-refresh>Refresh</button>
    </header>
    ${body}
  `;
}

function renderConfigMissing() {
  return page("Setup Needed", "Vercel is live, but Supabase public config is not available yet.", `
    <div class="card notice">
      <h3>Check Vercel Environment Variables</h3>
      <p>${escapeHtml(state.config?.error || "Add NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY, then redeploy.")}</p>
    </div>
  `);
}

function renderFeed() {
  return page("Feed", "Clips, posts, highlights, and squad updates.", `
    ${renderComposer()}
    <div class="grid">
      ${state.feed.length ? state.feed.map(renderFeedPost).join("") : `<div class="empty">No posts yet. Sign in and create the first clip or squad update.</div>`}
    </div>
  `);
}

function renderComposer() {
  if (!state.session) {
    return `<div class="card notice"><h3>Sign in to post</h3><p>Create clips, posts, and event updates from your Profile tab.</p></div>`;
  }
  return `
    <form class="card composer" data-create-post>
      <h3>Share With The Feed</h3>
      <input class="field" name="title" placeholder="Post title" required>
      <textarea name="body" placeholder="Share a clip, highlight, squad update, or callout..." required></textarea>
      <div class="btn-row">
        <select class="field" name="post_type">
          <option value="post">Post</option>
          <option value="clip">Clip</option>
          <option value="event">Event</option>
        </select>
        <button class="button green" type="submit">Publish</button>
      </div>
    </form>
  `;
}

function renderFeedPost(post) {
  const author = profileFor(post.profile_id);
  const game = gameFor(post.game_id);
  const isClip = post.post_type === "clip";
  return `
    <article class="card feed-card">
      <span class="pill hot">${post.post_type || "post"}</span>
      <h3>${escapeHtml(post.title)}</h3>
      <p>${escapeHtml(author?.handle || "Player")} ${game ? `- ${escapeHtml(game.name)}` : ""}</p>
      ${isClip ? `<div class="media">PLAY CLIP</div>` : ""}
      <p>${escapeHtml(post.body)}</p>
      <div class="btn-row">
        <button class="button dark" data-like="${post.id}">Like</button>
        <button class="button dark" data-comment-jump="${post.id}">Comment</button>
        <button class="button" data-share="${post.id}">Share</button>
      </div>
    </article>
  `;
}

function renderDiscovery() {
  const players = state.profiles.filter(profile => profile.id !== state.profile?.id);
  return page("Discovery/LFG", "Find players and parties already forming.", `
    <div class="grid two">
      <div>
        ${players.length ? players.map(renderPlayerCard).join("") : `<div class="empty">No other players yet.</div>`}
      </div>
      <div>
        <div class="card">
          <h3>Looking For Group</h3>
          ${state.lfg.length ? state.lfg.map(post => `<p><strong>${escapeHtml(post.title)}</strong><br>${escapeHtml(post.mode || "")} ${escapeHtml(post.starts_at || "")}</p>`).join("") : `<p>No LFG posts yet.</p>`}
        </div>
      </div>
    </div>
  `);
}

function renderPlayerCard(profile) {
  return `
    <div class="card">
      <span class="pill hot">${escapeHtml(profile.rank || "Unranked")}</span>
      <h3>${escapeHtml(profile.handle)}</h3>
      <p>${escapeHtml(profile.region || "Unknown")} - ${(profile.platforms || []).join(", ")}</p>
      <p>${escapeHtml(profile.bio || "No bio yet.")}</p>
      <div class="btn-row">
        <button class="button green" data-connect="${profile.id}">Connect</button>
        <button class="button dark" data-new-chat="${profile.id}">Message</button>
      </div>
    </div>
  `;
}

function renderMessages() {
  return page("Messages", "Matched players, direct chats, and group conversations.", `
    ${state.session ? `<div class="card"><h3>Start New Chat</h3><p>Select Message on a player in Discovery/LFG, or start a group from a server page soon.</p></div>` : `<div class="card notice"><h3>Sign in required</h3><p>Messages need a Supabase account.</p></div>`}
    <div class="grid">
      ${state.conversations.length ? state.conversations.map(renderConversation).join("") : `<div class="empty">No conversations yet.</div>`}
    </div>
  `);
}

function renderConversation(conversation) {
  return `
    <div class="card">
      <span class="pill">${conversation.conversation_type}</span>
      <h3>${escapeHtml(conversation.title)}</h3>
      <p>${(conversation.conversation_participants || []).length} participant(s)</p>
      <form class="btn-row" data-send-message="${conversation.id}">
        <input class="field" name="body" placeholder="Write a message..." required>
        <button class="button green" type="submit">Send</button>
      </form>
    </div>
  `;
}

function renderEventsServers() {
  return page("Events/Servers", "Sessions, communities, and game hubs.", `
    <div class="grid two">
      <div class="card"><h3>Tonight</h3><p>Ranked Apex testing window - 7PM to 11PM AEST.</p><button class="button green">Join Event</button></div>
      <div class="card"><h3>Gamer Connect HQ</h3><p>Community hub for updates, feedback, clips, and early testers.</p><button class="button">Open Server</button></div>
      ${state.squads.map(squad => `<div class="card"><h3>${escapeHtml(squad.name)}</h3><p>${escapeHtml(squad.description || "")}</p><span class="pill">${squad.open_slots} slot(s)</span></div>`).join("")}
    </div>
  `);
}

function renderProfile() {
  if (!state.session) {
    return page("Profile", "Sign in or create your secure Gamer Connect account.", renderAuthForms());
  }
  return page("Profile", "This is how other players will see you.", renderSignedInProfile());
}

function renderAuthForms() {
  const isSignup = state.authMode === "signup";
  return `
    <div class="auth-layout">
      <section class="auth-panel">
        <div class="auth-switch" role="tablist" aria-label="Account action">
          <button class="${isSignup ? "active" : ""}" data-auth-mode="signup" type="button">Create Account</button>
          <button class="${!isSignup ? "active" : ""}" data-auth-mode="signin" type="button">Sign In</button>
        </div>
        ${state.authMessage ? `<div class="notice small-notice">${escapeHtml(state.authMessage)}</div>` : ""}
        ${isSignup ? renderSignUpForm() : renderSignInForm()}
      </section>
      <aside class="card auth-preview">
        <span class="pill hot">Step 1</span>
        <h3>Account First</h3>
        <p><strong>Now:</strong> create a secure login with Supabase Auth.</p>
        <p><strong>Next:</strong> build a separate gamer profile setup menu for display name, games, rank, availability, play style, bio, and connected accounts.</p>
      </aside>
    </div>
  `;
}

function renderSignInForm() {
  return `
    <form class="profile-card auth-form" data-sign-in>
      <h3>Welcome Back</h3>
      <label>Email<input class="field" name="email" type="email" placeholder="you@example.com" autocomplete="email" required></label>
      <label>Password<input class="field" name="password" type="password" placeholder="Your password" autocomplete="current-password" required></label>
      <button class="button green wide" type="submit">Sign In</button>
    </form>
  `;
}

function renderSignUpForm() {
  return `
    <form class="profile-card auth-form" data-sign-up>
      <h3>Create Your Account</h3>
      <label>Email<input class="field" name="email" type="email" placeholder="you@example.com" autocomplete="email" required></label>
      <label>Password<input class="field" name="password" type="password" placeholder="At least 6 characters" autocomplete="new-password" minlength="6" required></label>
      <button class="button green wide" type="submit">Create Account</button>
    </form>
  `;
}

function renderSignedInProfile() {
  if (state.profileEdit) return renderProfileEditor();
  const profile = state.profile || {};
  const games = list(profile.top_games).map(gameLabel).filter(Boolean);
  const platforms = list(profile.platforms);
  const styles = list(profile.play_style);
  const availability = availabilityLabel(profile.availability);
  const stats = profile.stats || {};
  const gameRanks = stats.gameRanks || {};
  const gameStatSources = stats.gameStatSources || {};
  const gameManualStats = stats.gameManualStats || {};
  return `
    <section class="profile-page">
      <div class="profile-hero-card">
        <button class="button dark profile-edit-button" data-edit-profile>Edit</button>
        <div class="profile-banner"></div>
        <div class="profile-head">
          <div class="profile-avatar">${escapeHtml(initials(profile.display_name || profile.handle || state.session.user.email))}</div>
          <div>
            <h3>${escapeHtml(profile.display_name || profile.handle || "Player")}</h3>
            <p>@${escapeHtml(profile.handle || "player")} ${profile.online ? "- Online" : "- Offline"}</p>
          </div>
        </div>
        <p class="profile-bio">${escapeHtml(profile.bio || "No bio yet. Add a short intro so squads know what kind of teammate you are.")}</p>
        <div class="profile-meta">
          <span>${escapeHtml(profile.region || "Unknown region")}</span>
          <span>${escapeHtml(profile.timezone || "Timezone not set")}</span>
        </div>
        <div class="profile-actions">
          <button class="button green">Connect</button>
          <button class="button dark">Message</button>
          <button class="button dark">Invite</button>
        </div>
      </div>
      <div class="profile-grid">
        <div class="card">
          <h3>Top Games</h3>
          ${games.length ? games.map(value => {
            const source = gameStatSources[value] || {};
            const manual = gameManualStats[value] || {};
            const profileDetail = [gameRanks[value] || "Rank not set", manual.playlist || "", manual.mmr ? `${manual.mmr} MMR` : "", sourceLabel(source)].filter(Boolean).join(" - ");
            return `<div class="game-rank-row"><span class="pill hot">${escapeHtml(value)}</span><span>${escapeHtml(profileDetail)}</span></div>`;
          }).join("") : `<p>No games set yet.</p>`}
        </div>
        <div class="card">
          <h3>Platforms</h3>
          ${platforms.length ? platforms.map(value => `<span class="pill">${escapeHtml(value)}</span>`).join("") : `<p>No platforms set yet.</p>`}
        </div>
        <div class="card">
          <h3>Play Style</h3>
          ${styles.length ? styles.map(value => `<span class="pill">${escapeHtml(value)}</span>`).join("") : `<p>No play style set yet.</p>`}
        </div>
        <div class="card">
          <h3>Availability</h3>
          <p>${escapeHtml(availability || "No availability set yet.")}</p>
        </div>
        <div class="card">
          <h3>Stats Snapshot</h3>
          <div class="stat-row">
            <div><strong>${escapeHtml(stats.winRate || "0%")}</strong><span>Win Rate</span></div>
            <div><strong>${escapeHtml(stats.kd || "0.00")}</strong><span>K/D</span></div>
            <div><strong>${escapeHtml(stats.games || "0")}</strong><span>Games</span></div>
          </div>
        </div>
        <div class="card">
          <h3>Connected Accounts</h3>
          ${state.linkedAccounts.length ? state.linkedAccounts.map(account => `<span class="pill">${escapeHtml(providerLabel(account.provider))}</span>`).join("") : `<p>No connected accounts yet.</p>`}
          <p class="muted">Handles are protected and only unlock after approved connections.</p>
        </div>
      </div>
      <button class="button red" data-sign-out>Sign Out</button>
    </section>
  `;
}

function renderProfileEditor() {
  const profile = state.profile || {};
  const protectedInfo = protectedInfoMap();
  const selectedGames = draftGames(profile);
  const gameRanks = profile.stats?.gameRanks || {};
  const gameStatSources = profile.stats?.gameStatSources || {};
  const gameManualStats = profile.stats?.gameManualStats || {};
  const platformAccounts = platformAccountsMap(protectedInfo);
  return `
    <form class="profile-editor" data-save-profile>
      <div class="editor-head">
        <div>
          <h3>Edit Profile</h3>
          <p>Update the public profile players see and the protected account handles they can unlock later.</p>
        </div>
        <div class="btn-row">
          <button class="button dark" type="button" data-cancel-profile-edit>Cancel</button>
          <button class="button green" type="submit">Save Profile</button>
        </div>
      </div>
      ${state.profileMessage ? `<div class="success small-notice">${escapeHtml(state.profileMessage)}</div>` : ""}
      <div class="grid two">
        <section class="card profile-card">
          <h3>Public Info</h3>
          <label>Display Name<input class="field" name="display_name" value="${escapeAttribute(profile.display_name || "")}" placeholder="NovaPulse"></label>
          <label>Gamer Handle<input class="field" name="handle" value="${escapeAttribute(profile.handle || "")}" placeholder="novapulse" required></label>
          <label>Age<input class="field" name="age" type="number" min="13" max="120" value="${escapeAttribute(profile.age || "")}" placeholder="21"></label>
          <label>Region<input class="field" name="region" value="${escapeAttribute(profile.region || "")}" placeholder="Australia"></label>
          <label>Timezone<input class="field" name="timezone" value="${escapeAttribute(profile.timezone || "")}" placeholder="AEST"></label>
          <label>Bio<textarea name="bio" placeholder="Tell players what kind of squad you are looking for.">${escapeHtml(profile.bio || "")}</textarea></label>
        </section>
        <section class="card profile-card">
          <h3>Gaming Stack</h3>
          <input type="hidden" name="top_games" value="${escapeAttribute(selectedGames.join(", "))}">
          <div class="game-picker">
            <label>Search Popular Games
              <div class="game-search-row">
                <input class="field" name="game_search" list="popular-games" placeholder="Search or type a game">
                <button class="button dark" type="button" data-add-game>Add</button>
              </div>
            </label>
            <datalist id="popular-games">
              ${popularGames.map(game => `<option value="${escapeAttribute(game)}"></option>`).join("")}
            </datalist>
            <div class="popular-games">
              ${popularGames.slice(0, 10).map(game => `<button class="pill ${selectedGames.includes(game) ? "hot" : ""}" type="button" data-pick-game="${escapeAttribute(game)}">${escapeHtml(game)}</button>`).join("")}
            </div>
            <div class="selected-games">
              ${selectedGames.length ? selectedGames.map(game => `
                <div class="selected-game wide-selected-game">
                  <button class="button dark remove-game" type="button" data-remove-game="${escapeAttribute(game)}">x</button>
                  <div class="selected-game-title">
                    <strong>${escapeHtml(game)}</strong>
                    <span>${escapeHtml(syncSupportLabel(game))}</span>
                  </div>
                  <div class="game-fields">
                    <label>Rank<input class="field" name="rank_${escapeAttribute(gameKey(game))}" value="${escapeAttribute(gameRanks[game] || "")}" placeholder="Diamond II, Champion, Gold"></label>
                    <label>Playlist<input class="field" name="playlist_${escapeAttribute(gameKey(game))}" value="${escapeAttribute(gameManualStats[game]?.playlist || "")}" placeholder="2v2, 3v3, Ranked"></label>
                    <label>MMR / Rating<input class="field" name="mmr_${escapeAttribute(gameKey(game))}" value="${escapeAttribute(gameManualStats[game]?.mmr || "")}" placeholder="1042"></label>
                    <label>Stats Source
                      <select class="field" name="source_platform_${escapeAttribute(gameKey(game))}">
                        ${statSourceOptions.map(([value, label]) => `<option value="${escapeAttribute(value)}" ${gameSourcePlatform(gameStatSources, game) === value ? "selected" : ""}>${escapeHtml(label)}</option>`).join("")}
                      </select>
                    </label>
                    <label>Source Username / ID<input class="field" name="source_handle_${escapeAttribute(gameKey(game))}" value="${escapeAttribute(gameSourceHandle(gameStatSources, platformAccounts, game))}" placeholder="Use connected handle or enter one for this game"></label>
                  </div>
                  ${providerForAnyGameSource(game)
                    ? `<button class="button dark sync-game-button" type="button" data-sync-game="${escapeAttribute(game)}">Pull Stats Now</button>`
                    : `<span class="manual-badge">Manual tracking for now</span>`}
                </div>
              `).join("") : `<p>No games selected yet.</p>`}
            </div>
          </div>
          <label>Platform
            <select class="field" name="platform">
              ${platformOptions.map(option => `<option value="${escapeAttribute(option)}" ${list(profile.platforms)[0] === option ? "selected" : ""}>${escapeHtml(option)}</option>`).join("")}
            </select>
          </label>
          <label>Play Style
            <select class="field" name="play_style">
              ${playStyleOptions.map(option => `<option value="${escapeAttribute(option)}" ${list(profile.play_style)[0] === option ? "selected" : ""}>${escapeHtml(option)}</option>`).join("")}
            </select>
          </label>
          <div class="tracker-sync-box">
            <h4>Stats Sources</h4>
            <p>Connect platform accounts once, then choose which account each game should use for stats. Rocket League is enabled here as manual tracking because Tracker Network does not provide a public Rocket League API.</p>
            ${state.trackerMessage ? `<div class="notice small-notice">${escapeHtml(state.trackerMessage)}</div>` : ""}
          </div>
          <label>Availability<input class="field" name="availability" value="${escapeAttribute(availabilityLabel(profile.availability))}" placeholder="Evenings, 7PM - 11PM AEST"></label>
          <label>Avatar URL<input class="field" name="avatar_url" value="${escapeAttribute(profile.avatar_url || "")}" placeholder="https://..."></label>
        </section>
        <section class="card profile-card">
          <h3>Connected Platforms</h3>
          <p class="muted">These are your platform accounts. Later, supported platforms can use OAuth; for now you can enter handles/IDs and choose them per game.</p>
          <label>Discord<input class="field" name="discord" value="${escapeAttribute(protectedInfo.discord || "")}" placeholder="NovaPulse#2042"></label>
          ${accountPlatformOptions.map(([value, label]) => `<label>${escapeHtml(label)}<input class="field" name="platform_${escapeAttribute(value)}" value="${escapeAttribute(platformAccounts[value] || "")}" placeholder="${escapeAttribute(label)} username or account ID"></label>`).join("")}
        </section>
        <section class="card profile-card">
          <h3>Profile Preview</h3>
          <p>This page is laid out like another player viewing your profile. The edit button only appears for you.</p>
          <p class="muted">Protected handles are stored separately from public profile details.</p>
        </section>
      </div>
    </form>
  `;
}

function bindPageEvents() {
  document.querySelector("[data-refresh]")?.addEventListener("click", loadData);
  document.querySelector("[data-profile-tab]")?.addEventListener("click", () => {
    state.tab = "profile";
    state.authMode = "signup";
    renderShell();
  });
  document.querySelectorAll("[data-auth-mode]").forEach(button => button.addEventListener("click", () => {
    state.authMode = button.dataset.authMode;
    state.authMessage = "";
    renderShell();
  }));
  document.querySelector("[data-sign-in]")?.addEventListener("submit", signIn);
  document.querySelector("[data-sign-up]")?.addEventListener("submit", signUp);
  document.querySelector("[data-sign-out]")?.addEventListener("click", signOut);
  document.querySelector("[data-edit-profile]")?.addEventListener("click", () => {
    state.profileEdit = true;
    state.profileMessage = "";
    state.profileDraftGames = list(state.profile?.top_games);
    renderShell();
  });
  document.querySelector("[data-cancel-profile-edit]")?.addEventListener("click", () => {
    state.profileEdit = false;
    state.profileMessage = "";
    state.profileDraftGames = null;
    renderShell();
  });
  document.querySelector("[data-save-profile]")?.addEventListener("submit", saveProfile);
  document.querySelector("[data-add-game]")?.addEventListener("click", addTypedGame);
  document.querySelectorAll("[data-pick-game]").forEach(button => button.addEventListener("click", () => addGame(button.dataset.pickGame)));
  document.querySelectorAll("[data-remove-game]").forEach(button => button.addEventListener("click", () => removeGame(button.dataset.removeGame)));
  document.querySelectorAll("[data-sync-game]").forEach(button => button.addEventListener("click", () => syncGameStats(button.dataset.syncGame)));
  document.querySelector("[data-create-post]")?.addEventListener("submit", createPost);
  document.querySelectorAll("[data-like]").forEach(button => button.addEventListener("click", () => likePost(button.dataset.like)));
  document.querySelectorAll("[data-connect]").forEach(button => button.addEventListener("click", () => connectToPlayer(button.dataset.connect)));
  document.querySelectorAll("[data-new-chat]").forEach(button => button.addEventListener("click", () => startChat(button.dataset.newChat)));
  document.querySelectorAll("[data-send-message]").forEach(form => form.addEventListener("submit", sendMessage));
}

async function signIn(event) {
  event.preventDefault();
  state.authMessage = "";
  if (!state.ready) {
    state.authMessage = state.config?.error || "Supabase is not configured yet.";
    renderShell();
    return;
  }
  const form = new FormData(event.currentTarget);
  const { error } = await state.supabase.auth.signInWithPassword({
    email: form.get("email"),
    password: form.get("password")
  });
  if (error) {
    state.authMessage = error.message;
    renderShell();
    return;
  }
  state.tab = "feed";
  await loadData();
}

async function signUp(event) {
  event.preventDefault();
  state.authMessage = "";
  if (!state.ready) {
    state.authMessage = state.config?.error || "Supabase is not configured yet.";
    renderShell();
    return;
  }
  const form = new FormData(event.currentTarget);
  const setup = profileSetupFromForm(form);
  const { data, error } = await state.supabase.auth.signUp({
    email: form.get("email"),
    password: form.get("password"),
    options: { data: setup }
  });
  if (error) {
    state.authMessage = error.message;
    renderShell();
    return;
  }
  if (data.session) {
    state.session = data.session;
    await state.supabase.from("profiles").upsert({
      id: data.user.id,
      handle: setup.handle,
      display_name: setup.display_name,
      region: setup.region,
      timezone: setup.timezone,
      platforms: setup.platforms,
      top_games: setup.top_games,
      rank: setup.rank,
      play_style: setup.play_style,
      availability: setup.availability,
      bio: setup.bio
    });
    state.tab = "feed";
    state.authMessage = "Account created. Welcome to Gamer Connect.";
  } else {
    state.authMode = "signin";
    state.authMessage = "Check your email to confirm your account, then sign in.";
  }
  await loadData();
}

async function signOut() {
  await state.supabase.auth.signOut();
  state.session = null;
  state.profile = null;
  state.profileEdit = false;
  state.profileDraftGames = null;
  await loadData();
}

async function saveProfile(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  const form = new FormData(event.currentTarget);
  const handle = cleanHandle(form.get("handle"));
  const topGames = splitList(form.get("top_games"));
  const platformAccounts = platformAccountsFromForm(form);
  const gameRanks = {};
  const gameStatSources = {};
  const gameManualStats = {};
  topGames.forEach(game => {
    const key = gameKey(game);
    const rank = String(form.get(`rank_${key}`) || "").trim();
    if (rank) gameRanks[game] = rank;
    const playlist = String(form.get(`playlist_${key}`) || "").trim();
    const mmr = String(form.get(`mmr_${key}`) || "").trim();
    const sourcePlatform = normalizeStatsSource(form.get(`source_platform_${key}`) || "manual");
    const sourceHandle = String(form.get(`source_handle_${key}`) || platformAccounts[sourcePlatform] || "").trim();
    const provider = providerForGameSource(game, sourcePlatform);
    gameStatSources[game] = {
      platform: sourcePlatform,
      handle: sourceHandle,
      provider,
      sourceType: provider ? "approved_third_party" : "user_provided",
      syncStatus: provider ? "ready_to_sync" : "manual"
    };
    gameManualStats[game] = {
      playlist,
      mmr,
      sourceType: provider ? "approved_third_party" : "user_provided",
      updatedAt: new Date().toISOString()
    };
  });
  const profile = {
    id: state.profile.id,
    display_name: String(form.get("display_name") || handle).trim() || handle,
    handle,
    age: numberOrNull(form.get("age")),
    region: String(form.get("region") || "Australia").trim() || "Australia",
    timezone: String(form.get("timezone") || "AEST").trim() || "AEST",
    rank: topGames.length ? gameRanks[topGames[0]] || "Unranked" : "Unranked",
    bio: String(form.get("bio") || "").trim(),
    top_games: topGames,
    play_style: [String(form.get("play_style") || "Good Comms")],
    availability: { summary: String(form.get("availability") || "").trim() },
    avatar_url: String(form.get("avatar_url") || "").trim() || null,
    platforms: selectedPublicPlatforms(platformAccounts),
    stats: { ...(state.profile.stats || {}), gameRanks, gameStatSources, gameManualStats }
  };
  const { data, error } = await state.supabase.from("profiles").upsert(profile).select("*").single();
  if (error) return alert(error.message);

  const protectedInfo = {
    discord: String(form.get("discord") || "").trim(),
    platformAccounts
  };
  await state.supabase.from("profile_private").upsert({
    profile_id: state.profile.id,
    protected_info: protectedInfo,
    info_stacks: [
      { label: "Gaming", values: profile.top_games },
      { label: "Platforms", values: profile.platforms },
      { label: "Protected", values: Object.keys(platformAccounts).filter(key => platformAccounts[key]) }
    ]
  });
  const linked = [
    ["discord", protectedInfo.discord],
    ...Object.entries(platformAccounts)
  ]
    .filter(([, accountHandle]) => accountHandle)
    .map(([provider, accountHandle]) => ({
      profile_id: state.profile.id,
      provider,
      account_handle: accountHandle,
      is_private: true
    }));
  if (linked.length) {
    await state.supabase.from("linked_accounts").upsert(linked, { onConflict: "profile_id,provider" });
  }
  const emptyProviders = [
    ["discord", protectedInfo.discord],
    ...Object.entries(platformAccounts)
  ]
    .filter(([, accountHandle]) => !accountHandle)
    .map(([provider]) => provider);
  if (emptyProviders.length) {
    await state.supabase
      .from("linked_accounts")
      .delete()
      .eq("profile_id", state.profile.id)
      .in("provider", emptyProviders);
  }
  state.profile = data;
  state.profileEdit = false;
  state.profileDraftGames = null;
  state.profileMessage = "Profile saved.";
  await loadData();
}

function addTypedGame() {
  const input = document.querySelector("[name='game_search']");
  addGame(input?.value);
}

function addGame(value) {
  const game = normalizeGameName(value);
  if (!game) return;
  const current = state.profileDraftGames ?? list(state.profile?.top_games);
  state.profileDraftGames = [...new Set([...current, game])];
  renderShell();
}

function removeGame(value) {
  const game = normalizeGameName(value);
  const current = state.profileDraftGames ?? list(state.profile?.top_games);
  state.profileDraftGames = current.filter(item => item !== game);
  renderShell();
}

function draftGames(profile) {
  return state.profileDraftGames ?? list(profile.top_games);
}

async function syncGameStats(game) {
  const key = gameKey(game);
  const sourcePlatform = normalizeStatsSource(document.querySelector(`[name='source_platform_${key}']`)?.value || "manual");
  const handle = document.querySelector(`[name='source_handle_${key}']`)?.value?.trim();
  const support = gameProviderSupport[game];
  const providerPlatform = support?.platforms[sourcePlatform];
  if (!support || !providerPlatform) {
    state.trackerMessage = `${game} does not have an approved automatic stats source for ${sourceLabel({ platform: sourcePlatform })} yet. You can still save manual rank/source data.`;
    renderShell();
    return;
  }
  if (!handle) {
    state.trackerMessage = `Add the ${sourceLabel({ platform: sourcePlatform })} username or account ID for ${game} first.`;
    renderShell();
    return;
  }

  state.trackerMessage = "";
  try {
    const query = new URLSearchParams({ provider: support.provider, game: support.gameId, platform: providerPlatform, handle });
    const response = await fetch(`/api/tracker/profile?${query.toString()}`, { cache: "no-store" });
    const result = await response.json();
    if (!response.ok || !result.ok) {
      state.trackerMessage = result.error || "Stats sync failed.";
      renderShell();
      return;
    }

    state.profileDraftGames = [...new Set([...(state.profileDraftGames ?? list(state.profile?.top_games)), game])];
    const rankInputName = `rank_${key}`;
    const existingStats = state.profile?.stats || {};
    state.profile = {
      ...state.profile,
      stats: {
        ...existingStats,
        trackerNetwork: {
          ...(existingStats.trackerNetwork || {}),
          [support.gameId]: result
        },
        gameRanks: {
          ...(existingStats.gameRanks || {}),
          [game]: result.rank || existingStats.gameRanks?.[game] || ""
        },
        gameStatSources: {
          ...(existingStats.gameStatSources || {}),
          [game]: {
            platform: sourcePlatform,
            handle,
            provider: support.provider,
            sourceType: "approved_third_party",
            syncStatus: result.rank ? "synced" : "synced_no_rank",
            lastSyncedAt: new Date().toISOString()
          }
        },
        gameManualStats: {
          ...(existingStats.gameManualStats || {}),
          [game]: {
            playlist: result.normalizedRank?.playlist || existingStats.gameManualStats?.[game]?.playlist || "",
            mmr: result.normalizedRank?.numericValue || existingStats.gameManualStats?.[game]?.mmr || "",
            sourceType: "approved_third_party",
            updatedAt: new Date().toISOString()
          }
        }
      }
    };
    state.trackerMessage = result.rank
      ? `Found ${game} rank: ${result.rank}. Hit Save Profile to keep it.`
      : `${game} profile found, but no rank field was returned. Hit Save Profile to keep the linked data.`;
    renderShell();
    const rankInput = document.querySelector(`[name='${rankInputName}']`);
    if (rankInput && result.rank) rankInput.value = result.rank;
  } catch (_error) {
    state.trackerMessage = "Could not reach the Gamer Connect stats endpoint.";
    renderShell();
  }
}

async function createPost(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  const form = new FormData(event.currentTarget);
  const { error } = await state.supabase.from("feed_posts").insert({
    profile_id: state.profile.id,
    post_type: form.get("post_type"),
    title: form.get("title"),
    body: form.get("body"),
    game_id: "apex-legends",
    media_type: form.get("post_type") === "clip" ? "video" : null
  });
  if (error) return alert(error.message);
  await loadData();
}

async function likePost(postId) {
  if (!state.profile) return alert("Sign in first.");
  const { error } = await state.supabase.from("feed_reactions").upsert({
    post_id: postId,
    profile_id: state.profile.id,
    reaction: "like"
  });
  if (error) return alert(error.message);
  await loadData();
}

async function connectToPlayer(profileId) {
  if (!state.profile) return alert("Sign in first.");
  const { error } = await state.supabase.from("connections").insert({
    from_profile_id: state.profile.id,
    to_profile_id: profileId,
    message: "Want to squad up?"
  });
  if (error) return alert(error.message);
  alert("Connection request sent.");
}

async function startChat(profileId) {
  if (!state.profile) return alert("Sign in first.");
  const target = profileFor(profileId);
  const { data: conversation, error } = await state.supabase.from("conversations").insert({
    title: target?.handle || "New Chat",
    conversation_type: "direct",
    created_by_profile_id: state.profile.id
  }).select("*").single();
  if (error) return alert(error.message);
  const { error: participantError } = await state.supabase.from("conversation_participants").insert([
    { conversation_id: conversation.id, profile_id: state.profile.id, role: "owner" },
    { conversation_id: conversation.id, profile_id: profileId, role: "member" }
  ]);
  if (participantError) return alert(participantError.message);
  state.tab = "messages";
  await loadData();
}

async function sendMessage(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  const form = new FormData(event.currentTarget);
  const body = form.get("body");
  const conversationId = event.currentTarget.dataset.sendMessage;
  const { error } = await state.supabase.from("messages").insert({
    conversation_id: conversationId,
    sender_profile_id: state.profile.id,
    body
  });
  if (error) return alert(error.message);
  event.currentTarget.reset();
  await loadData();
}

function profileFor(id) {
  return state.profiles.find(profile => profile.id === id);
}

function gameFor(id) {
  return state.games.find(game => game.id === id);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  }[char]));
}

function escapeAttribute(value) {
  return escapeHtml(value).replace(/`/g, "&#096;");
}

function profileSetupFromForm(form) {
  const handle = cleanHandle(String(form.get("email") || "Player").split("@")[0]);
  return {
    handle,
    display_name: handle,
    region: "Australia",
    timezone: "AEST",
    platforms: [],
    top_games: [],
    rank: "Unranked",
    play_style: [],
    availability: {},
    bio: ""
  };
}

async function savePrivateSetup(profileId, setup) {
  if (!profileId || !setup) return;
  const protectedInfo = setup.protected_info || {};
  await state.supabase.from("profile_private").upsert({
    profile_id: profileId,
    protected_info: protectedInfo,
    info_stacks: [
      { label: "Account", values: ["email", "supabase_auth"] },
      { label: "Gaming", values: setup.top_games || [] },
      { label: "Contact", values: Object.keys(protectedInfo).filter(key => protectedInfo[key]) }
    ]
  });
  const linked = [
    ["discord", protectedInfo.discord],
    ["tracker_network", protectedInfo.trackerNetwork],
    ["steam", protectedInfo.steam],
    ["other", protectedInfo.other]
  ].filter(([, accountHandle]) => accountHandle);
  if (linked.length) {
    await state.supabase.from("linked_accounts").upsert(linked.map(([provider, accountHandle]) => ({
      profile_id: profileId,
      provider,
      account_handle: accountHandle,
      is_private: true
    })), { onConflict: "profile_id,provider" });
  }
}

function list(value) {
  if (Array.isArray(value)) return value.filter(Boolean);
  if (!value) return [];
  if (typeof value === "string") return splitList(value);
  return [];
}

function splitList(value) {
  return String(value || "")
    .split(",")
    .map(item => item.trim())
    .filter(Boolean);
}

function availabilityLabel(value) {
  if (!value) return "";
  if (typeof value === "string") return value;
  return value.summary || "";
}

function gameLabel(value) {
  const game = gameFor(value);
  if (game) return game.name;
  return String(value || "").replace(/-/g, " ").replace(/\b\w/g, char => char.toUpperCase());
}

function providerLabel(value) {
  return String(value || "")
    .replace(/_/g, " ")
    .replace(/\b\w/g, char => char.toUpperCase());
}

function sourceLabel(source = {}) {
  const platform = source.platform || "manual";
  if (platform === "manual") return "User-provided";
  return statSourceOptions.find(([value]) => value === platform)?.[1] || providerLabel(platform);
}

function syncSupportLabel(game) {
  const support = gameProviderSupport[game];
  if (!support) return "No automatic stats provider yet. Manual data is fine.";
  const labels = Object.keys(support.platforms).map(platform => sourceLabel({ platform }));
  return `Automatic pull available through ${support.providerLabel} for: ${labels.join(", ")}.`;
}

function providerForGameSource(game, platform) {
  const support = gameProviderSupport[game];
  return support?.platforms[platform] ? support.provider : "";
}

function providerForAnyGameSource(game) {
  return Boolean(gameProviderSupport[game]);
}

function gameSourcePlatform(gameStatSources, game) {
  return normalizeStatsSource(gameStatSources[game]?.platform || "manual");
}

function gameSourceHandle(gameStatSources, platformAccounts, game) {
  const source = gameStatSources[game] || {};
  return source.handle || platformAccounts[source.platform] || "";
}

function platformAccountsMap(protectedInfo = {}) {
  return {
    ...(protectedInfo.platformAccounts || {}),
    ea: protectedInfo.ea || protectedInfo.origin || "",
    ubisoft: protectedInfo.ubisoft || protectedInfo.uplay || "",
    steam: protectedInfo.steam || "",
    xbox: protectedInfo.xbox || "",
    psn: protectedInfo.psn || protectedInfo.playstation || "",
    riot: protectedInfo.riot || "",
    bungie: protectedInfo.bungie || "",
    nintendo: protectedInfo.nintendo || "",
    epic: protectedInfo.epic || ""
  };
}

function normalizeStatsSource(value) {
  const source = String(value || "").trim().toLowerCase();
  if (["ubi", "uplay", "ubisoft-connect", "ubisoft connect"].includes(source)) return "ubisoft";
  if (["origin", "ea app", "ea-app"].includes(source)) return "ea";
  if (["playstation", "ps", "psn"].includes(source)) return "psn";
  if (["xbl", "xbox-live", "xbox live"].includes(source)) return "xbox";
  return source || "manual";
}

function platformAccountsFromForm(form) {
  const accounts = {};
  accountPlatformOptions.forEach(([value]) => {
    const accountHandle = String(form.get(`platform_${value}`) || "").trim();
    if (accountHandle) accounts[value] = accountHandle;
  });
  return accounts;
}

function selectedPublicPlatforms(platformAccounts) {
  return Object.keys(platformAccounts)
    .filter(key => platformAccounts[key])
    .map(key => sourceLabel({ platform: key }));
}

function normalizeGameName(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  const match = popularGames.find(game => game.toLowerCase() === raw.toLowerCase());
  return match || raw.replace(/\s+/g, " ").replace(/\b\w/g, char => char.toUpperCase());
}

function gameKey(value) {
  return String(value || "").replace(/[^a-z0-9]/gi, "_").toLowerCase();
}

function protectedInfoMap() {
  const protectedInfo = state.privateProfile?.protected_info || {};
  const linkedInfo = {};
  state.linkedAccounts.forEach(account => {
    linkedInfo[account.provider] = account.account_handle;
  });
  return { ...linkedInfo, ...protectedInfo };
}

function initials(value) {
  const words = String(value || "GC").trim().split(/\s+/).slice(0, 2);
  return words.map(word => word[0]?.toUpperCase() || "").join("") || "GC";
}

function cleanHandle(value) {
  return String(value || "Player").replace(/[^a-z0-9_]/gi, "").slice(0, 18) || "Player";
}

function numberOrNull(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}
