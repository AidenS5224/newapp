const tabs = [
  ["events", "Events/Servers"],
  ["discovery", "Discovery/LFG"],
  ["feed", "Feed"],
  ["messages", "Messages"],
  ["profile", "Profile"]
];

const tabIcons = {
  events: "users",
  discovery: "compass",
  feed: "newspaper",
  messages: "message",
  profile: "user"
};

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
const feedMediaBucket = "feed-media";
const maxFeedImageBytes = 5 * 1024 * 1024;
const maxFeedVideoBytes = 50 * 1024 * 1024;
const maxFeedVideoSourceBytes = 250 * 1024 * 1024;
const maxFeedVideoDurationSeconds = 60;
const compressedFeedVideoBitrate = 5_000_000;
const compressedFeedVideoFps = 30;
const compressedFeedVideoMaxEdge = 1280;
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
  selectedConversationId: "",
  profilePanelOpen: false,
  newChatOpen: false,
  messagesMobileView: "list",
  discoveryTab: "lfg",
  discoveryStarted: false,
  discoveryIndex: 0,
  discoveryPassed: [],
  discoveryFilters: { search: "", game: "", platform: "", style: "" },
  lfgFilters: { search: "", status: "all" },
  lfgBusy: "",
  ready: false,
  config: null,
  supabase: null,
  session: null,
  profile: null,
  feed: [],
  profiles: [],
  games: [],
  lfg: [],
  lfgJoinRequests: [],
  lfgMembers: [],
  squads: [],
  feedReactions: [],
  feedComments: [],
  connections: [],
  blocks: [],
  conversations: [],
  privateProfile: null,
  linkedAccounts: [],
  messages: {},
  realtimeChannel: null,
  refreshTimer: null,
  loadingData: false
};

const app = document.querySelector("#app");

function icon(name) {
  const paths = {
    users: `<path d="M8 18v-1.5A3.5 3.5 0 0 1 11.5 13h1A3.5 3.5 0 0 1 16 16.5V18"/><circle cx="12" cy="7" r="3"/><path d="M3.5 18v-1a3 3 0 0 1 3-3"/><path d="M17.5 14a3 3 0 0 1 3 3v1"/><path d="M6.5 8.5a2 2 0 1 1 1.2-3.6"/><path d="M16.3 4.9a2 2 0 1 1 1.2 3.6"/>`,
    compass: `<circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2 5-5 2 2-5 5-2Z"/>`,
    newspaper: `<path d="M4 5h13a3 3 0 0 1 3 3v10H7a3 3 0 0 1-3-3V5Z"/><path d="M7 8h6"/><path d="M7 12h10"/><path d="M7 16h7"/>`,
    message: `<path d="M4 5h16v11H8l-4 4V5Z"/><path d="M8 9h8"/><path d="M8 13h5"/>`,
    user: `<circle cx="12" cy="8" r="4"/><path d="M5 20a7 7 0 0 1 14 0"/>`,
    sliders: `<path d="M4 7h10"/><path d="M18 7h2"/><circle cx="16" cy="7" r="2"/><path d="M4 17h2"/><path d="M10 17h10"/><circle cx="8" cy="17" r="2"/>`,
    edit: `<path d="M5 19h4l10-10-4-4L5 15v4Z"/><path d="M13 7l4 4"/>`,
    x: `<path d="M6 6l12 12"/><path d="M18 6 6 18"/>`,
    arrowLeft: `<path d="M19 12H5"/><path d="m12 19-7-7 7-7"/>`,
    plus: `<path d="M12 5v14"/><path d="M5 12h14"/>`,
    phone: `<path d="M22 16.9v3a2 2 0 0 1-2.2 2A19.8 19.8 0 0 1 3 5.2 2 2 0 0 1 5 3h3a2 2 0 0 1 2 1.7c.1.9.3 1.7.6 2.5a2 2 0 0 1-.4 2.1L9 10.5a16 16 0 0 0 4.5 4.5l1.2-1.2a2 2 0 0 1 2.1-.4c.8.3 1.6.5 2.5.6a2 2 0 0 1 1.7 1.9Z"/>`,
    video: `<path d="M4 7h11a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4V7Z"/><path d="m17 10 4-2v8l-4-2"/>`,
    info: `<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8h.01"/>`,
    trash: `<path d="M4 7h16"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M6 7l1 14h10l1-14"/><path d="M9 7V4h6v3"/>`,
    send: `<path d="m22 2-7 20-4-9-9-4 20-7Z"/><path d="M22 2 11 13"/>`,
    smile: `<circle cx="12" cy="12" r="9"/><path d="M8 10h.01"/><path d="M16 10h.01"/><path d="M8 15c1.2 1 2.5 1.5 4 1.5s2.8-.5 4-1.5"/>`
  };
  return `<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.message}</svg>`;
}

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
      setupRealtime();
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
  if (state.loadingData) return;
  state.loadingData = true;
  try {
    await ensureProfile();
    const [profiles, games, feed, lfg, lfgJoinRequests, lfgMembers, squads, feedReactions, feedComments] = await Promise.all([
      read("profiles", "id, handle, display_name, age, region, timezone, platforms, top_games, rank, play_style, availability, bio, avatar_url, online, stats, created_at"),
      read("games", "*"),
      read("feed_posts", "*", { order: "created_at" }),
      read("lfg_posts", "*", { order: "created_at" }),
      read("lfg_join_requests", "*", { order: "created_at", ascending: true }),
      read("lfg_members", "*"),
      read("squads", "*", { order: "created_at" }),
      read("feed_reactions", "*"),
      read("feed_comments", "*", { order: "created_at", ascending: true })
    ]);
    state.profiles = profiles || [];
    state.games = games || [];
    state.feed = feed || [];
    state.lfg = lfg || [];
    state.lfgJoinRequests = lfgJoinRequests || [];
    state.lfgMembers = lfgMembers || [];
    state.squads = squads || [];
    state.feedReactions = feedReactions || [];
    state.feedComments = feedComments || [];
    if (state.session) {
      [state.connections, state.blocks] = await Promise.all([loadConnections(), loadBlocks()]);
      state.conversations = await loadConversations();
      state.messages = await loadMessages();
      await loadPrivateProfile();
    } else {
      state.connections = [];
      state.blocks = [];
      state.conversations = [];
      state.messages = {};
      state.privateProfile = null;
      state.linkedAccounts = [];
    }
    setupRealtime();
    renderShell();
  } finally {
    state.loadingData = false;
  }
}

async function read(table, columns = "*", options = {}) {
  let query = state.supabase.from(table).select(columns);
  if (options.order) query = query.order(options.order, { ascending: options.ascending ?? false });
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

async function loadConnections() {
  const { data, error } = await state.supabase
    .from("connections")
    .select("*")
    .order("created_at", { ascending: false });
  if (error) {
    console.warn(error.message);
    return [];
  }
  return data || [];
}

async function loadBlocks() {
  const { data, error } = await state.supabase
    .from("blocked_profiles")
    .select("*")
    .order("created_at", { ascending: false });
  if (error) {
    console.warn(error.message);
    return [];
  }
  return data || [];
}

async function loadMessages() {
  const { data, error } = await state.supabase
    .from("messages")
    .select("*")
    .order("created_at", { ascending: true });
  if (error) {
    console.warn(error.message);
    return {};
  }
  return (data || []).reduce((grouped, message) => {
    grouped[message.conversation_id] ||= [];
    grouped[message.conversation_id].push(message);
    return grouped;
  }, {});
}

function setupRealtime() {
  if (!state.supabase) return;
  if (!state.session) {
    teardownRealtime();
    return;
  }
  if (state.realtimeChannel) return;
  state.realtimeChannel = state.supabase
    .channel(`gamer-connect-live-${state.session.user.id}`)
    .on("postgres_changes", { event: "*", schema: "public", table: "messages" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "conversations" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "conversation_participants" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "feed_posts" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "feed_reactions" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "feed_comments" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "lfg_posts" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "lfg_join_requests" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "lfg_members" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "connections" }, handleRealtimeChange)
    .on("postgres_changes", { event: "*", schema: "public", table: "blocked_profiles" }, handleRealtimeChange)
    .subscribe(status => {
      if (["CHANNEL_ERROR", "TIMED_OUT"].includes(status)) console.warn("Realtime status:", status);
    });
}

function teardownRealtime() {
  if (state.refreshTimer) {
    clearTimeout(state.refreshTimer);
    state.refreshTimer = null;
  }
  if (!state.realtimeChannel || !state.supabase) {
    state.realtimeChannel = null;
    return;
  }
  state.supabase.removeChannel(state.realtimeChannel);
  state.realtimeChannel = null;
}

function handleRealtimeChange(payload) {
  if (payload.table === "messages" && payload.eventType === "INSERT") {
    mergeRealtimeMessage(payload.new);
    renderShell();
  }
  scheduleRealtimeRefresh();
}

function mergeRealtimeMessage(message) {
  if (!message?.conversation_id) return;
  state.messages[message.conversation_id] ||= [];
  if (state.messages[message.conversation_id].some(existing => existing.id === message.id)) return;
  state.messages[message.conversation_id].push(message);
}

function scheduleRealtimeRefresh() {
  if (state.refreshTimer) clearTimeout(state.refreshTimer);
  state.refreshTimer = setTimeout(() => {
    state.refreshTimer = null;
    loadData();
  }, 350);
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
    <div class="app-shell ${state.tab === "messages" ? "messages-app" : ""}">
      <aside class="sidebar">
        <div class="brand">
          <div class="mark">GC</div>
          <div>
            <h1>Gamer Connect</h1>
            <p>Feed. Squads. Matches.</p>
          </div>
        </div>
        <nav class="nav">
          ${tabs.map(([id, label]) => `<button class="${state.tab === id ? "active" : ""}" data-tab="${id}">${icon(tabIcons[id])}<span>${label}</span></button>`).join("")}
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
    <div class="feed-layout">
      <section class="feed-main">
        <div class="feed-tabs">
          <button class="active" type="button">For You</button>
          <button type="button">Following</button>
          <button type="button">Groups</button>
        </div>
        ${renderComposer()}
        <div class="feed-list">
          ${state.feed.length ? state.feed.map(renderFeedPost).join("") : `<div class="empty">No posts yet. Sign in and create the first clip or squad update.</div>`}
        </div>
      </section>
      <aside class="feed-side">
        <div class="card feed-side-card">
          <h3>Trending Games</h3>
          ${popularGames.slice(0, 6).map(game => `<span class="pill">${escapeHtml(game)}</span>`).join("")}
        </div>
        <div class="card feed-side-card">
          <h3>Feed Signals</h3>
          <p>${state.feed.length} post(s)</p>
          <p>${state.feedComments.length} comment(s)</p>
          <p>${state.feedReactions.length} like(s)</p>
        </div>
      </aside>
    </div>
  `);
}

function renderComposer() {
  if (!state.session) {
    return `<div class="card notice"><h3>Sign in to post</h3><p>Create clips, posts, and event updates from your Profile tab.</p></div>`;
  }
  const gameOptions = profileFeedGameOptions();
  return `
    <form class="card composer feed-composer" data-create-post>
      <div class="composer-head">
        <span class="chat-avatar">${renderAvatar(state.profile, state.profile?.handle)}</span>
        <div>
          <h3>New Post</h3>
          <p>Clip, text, or squad update.</p>
        </div>
        <span class="pill hot">Feed</span>
      </div>
      <input class="field" name="title" placeholder="Give it a title" required>
      <textarea name="body" placeholder="What happened? Share a highlight, squad update, or callout..." required></textarea>
      <label class="upload-field">
        <span>Upload Image Or Clip</span>
        <input name="media_file" type="file" accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime">
        <small>Images up to 5 MB. Clips up to 60 seconds. Oversized clips will try to compress to 720p/30fps before upload.</small>
      </label>
      <div class="composer-controls">
        <select class="field" name="post_type">
          <option value="post">Post</option>
          <option value="clip">Clip</option>
          <option value="event">Event</option>
        </select>
        <select class="field" name="game_id">
          <option value="">No game</option>
          ${gameOptions.map(game => `<option value="${escapeAttribute(game.id)}">${escapeHtml(game.name)}</option>`).join("")}
        </select>
        ${gameOptions.length ? `<p class="composer-hint">Showing games selected on your Profile.</p>` : `<p class="composer-hint">Add games on your Profile to tag feed posts. If you already have games selected, run migration 0013 to seed the games table.</p>`}
        <button class="button purple" type="submit">Post</button>
      </div>
    </form>
  `;
}

function renderFeedPost(post) {
  const author = profileFor(post.profile_id);
  const game = gameFor(post.game_id);
  const isClip = post.media_type === "video" || post.post_type === "clip";
  const reactions = reactionsForPost(post.id);
  const comments = commentsForPost(post.id);
  const liked = reactions.some(reaction => reaction.profile_id === state.profile?.id);
  const canDelete = post.profile_id === state.profile?.id;
  const tags = [game?.name, post.post_type && post.post_type !== "post" ? post.post_type : ""].filter(Boolean);
  return `
    <article class="card feed-card" id="feed-${escapeAttribute(post.id)}">
      <div class="feed-author">
        <span class="chat-avatar">${renderAvatar(author, author?.handle || "Player")}</span>
        <div>
          <strong>${escapeHtml(author?.handle || "Player")}</strong>
          <p>${escapeHtml(formatRelativeTime(post.created_at))}${game ? ` - ${escapeHtml(game.name)}` : ""}</p>
        </div>
        <button class="post-menu" type="button" title="More">...</button>
      </div>
      <h3>${escapeHtml(post.title)}</h3>
      <p>${escapeHtml(post.body)}</p>
      ${tags.length ? `<div class="feed-tags">${tags.map(tag => `<span>#${escapeHtml(tag.replace(/\s+/g, ""))}</span>`).join("")}</div>` : ""}
      ${post.media_url ? renderFeedMedia(post, isClip) : ""}
      <div class="feed-actions">
        <button class="feed-action ${liked ? "active" : ""}" data-like="${post.id}"><span>Like</span>${reactions.length}</button>
        <button class="feed-action" data-comment-jump="${post.id}"><span>Comment</span>${comments.length}</button>
        <button class="feed-action" data-share="${post.id}"><span>Share</span></button>
        <button class="feed-action save" type="button"><span>Save</span></button>
        ${canDelete ? `<button class="button red" data-delete-post="${post.id}">Delete</button>` : ""}
      </div>
      <div class="comment-list" id="comments-${post.id}">
        ${comments.slice(-3).map(renderFeedComment).join("")}
      </div>
      ${state.session ? `
        <form class="comment-form" data-create-comment="${post.id}">
          <input class="field" name="body" placeholder="Write a comment..." required autocomplete="off">
          <button class="button green" type="submit">Send</button>
        </form>
      ` : ""}
    </article>
  `;
}

function renderFeedMedia(post, isClip) {
  const url = escapeAttribute(post.media_url);
  if (isClip) {
    return `
      <div class="media clip-media">
        <video controls preload="metadata" src="${url}"></video>
        <span class="clip-badge">Clip</span>
      </div>
    `;
  }
  return `<img class="feed-image" src="${url}" alt="">`;
}

function renderFeedComment(comment) {
  const author = profileFor(comment.profile_id);
  return `
    <div class="feed-comment">
      <span class="chat-avatar">${renderAvatar(author, author?.handle || "Player")}</span>
      <div>
        <strong>${escapeHtml(author?.handle || "Player")}</strong>
        <p>${escapeHtml(comment.body)}</p>
      </div>
    </div>
  `;
}

function renderDiscovery() {
  const players = discoveryPlayers();
  const incoming = pendingIncomingConnections();
  const outgoing = pendingOutgoingConnections();
  const filterGames = discoveryGameOptions();
  const activeIndex = Math.min(state.discoveryIndex || 0, Math.max(players.length - 1, 0));
  const activeProfile = players[activeIndex];
  const currentTab = state.discoveryTab || "lfg";
  return page("Discovery/LFG", "Find players and parties already forming.", `
    <div class="discovery-page deck-page">
      <div class="discovery-mobile-head">
        <strong>DISCOVERY / LFG</strong>
        <span>${players.length ? `${activeIndex + 1}/${players.length}` : "0/0"}</span>
      </div>
      <nav class="discovery-tabs" aria-label="Discovery sections">
        <button type="button" class="${currentTab === "lfg" ? "active" : ""}" data-discovery-tab="lfg">Looking For Group</button>
        <button type="button" class="${currentTab === "matches" ? "active" : ""}" data-discovery-tab="matches">Matches</button>
        <button type="button" class="${currentTab === "posts" ? "active" : ""}" data-discovery-tab="posts">My Posts</button>
      </nav>
      ${currentTab === "lfg" ? `
        <div class="discovery-deck-layout">
          <section class="deck-stage">
            ${state.discoveryStarted && activeProfile ? renderDiscoveryDeckCard(activeProfile, activeIndex, players.length) : renderDiscoveryStart(players.length)}
          </section>
          <aside class="discovery-side deck-side">
            <form class="discovery-filter-card" data-discovery-filters>
              <div>
                <h3>Match Filters</h3>
                <p>Tune the deck by game, platform, style, or player name.</p>
              </div>
              <input class="field" name="search" placeholder="Search players" value="${escapeAttribute(state.discoveryFilters.search)}">
              <select class="field" name="game">
                <option value="">Any game</option>
                ${filterGames.map(game => `<option value="${escapeAttribute(game.value)}" ${game.value === state.discoveryFilters.game ? "selected" : ""}>${escapeHtml(game.label)}</option>`).join("")}
              </select>
              <div class="filter-row">
                <select class="field" name="platform">
                  <option value="">Any platform</option>
                  ${platformOptions.map(platform => `<option value="${escapeAttribute(platform)}" ${platform === state.discoveryFilters.platform ? "selected" : ""}>${escapeHtml(platform)}</option>`).join("")}
                </select>
                <select class="field" name="style">
                  <option value="">Any play style</option>
                  ${playStyleOptions.map(style => `<option value="${escapeAttribute(style)}" ${style === state.discoveryFilters.style ? "selected" : ""}>${escapeHtml(style)}</option>`).join("")}
                </select>
              </div>
            </form>
            <div class="card discovery-side-card">
              <h3>Connection Requests</h3>
              ${incoming.length ? incoming.map(renderIncomingConnection).join("") : `<p>No incoming requests.</p>`}
              ${outgoing.length ? `<p class="muted">${outgoing.length} sent request(s) waiting for approval.</p>` : ""}
            </div>
            ${renderLfgCreateCard()}
            ${renderLfgBrowseCard()}
          </aside>
        </div>
      ` : ""}
      ${currentTab === "matches" ? renderDiscoveryMatches() : ""}
      ${currentTab === "posts" ? renderDiscoveryPosts() : ""}
    </div>
  `);
}

function renderDiscoveryStart(count) {
  return `
    <section class="discovery-start-card">
      <div class="start-icon">GC</div>
      <h3>Find players to game with</h3>
      <p>Swipe through players, review fit details, then choose Play or Pass.</p>
      <button class="button purple wide" type="button" data-start-discovery ${count ? "" : "disabled"}>${count ? "Start Discovery" : "No Players Yet"}</button>
    </section>
  `;
}

function renderDiscoveryDeckCard(profile, index, total) {
  const connection = connectionWith(profile.id);
  const score = discoveryScore(profile);
  const games = list(profile.top_games).map(gameLabel).filter(Boolean);
  const styles = list(profile.play_style).slice(0, 4);
  const platforms = list(profile.platforms).slice(0, 3);
  const primaryGame = games[0] || "Any Game";
  const matchTone = score.score >= 82 ? "hot" : score.score >= 70 ? "good" : "soft";
  return `
    <article class="discovery-deck-card">
      <div class="deck-media game-${gameSlug(primaryGame)}">
        <div class="deck-counter">${index + 1}/${total}</div>
        <div class="deck-score ${matchTone}">
          <strong>${score.score}%</strong>
          <span>Match</span>
        </div>
        <div class="deck-media-title">
          <span>Looking for</span>
          <strong>${escapeHtml(primaryGame)}</strong>
        </div>
      </div>
      <div class="deck-body">
        <div class="deck-profile-row">
          <span class="chat-avatar xl">${renderAvatar(profile, profile.handle)}</span>
          <div>
            <h3>${escapeHtml(profile.handle)}</h3>
            <p>${escapeHtml(profile.age || "")}${profile.age ? " - " : ""}${escapeHtml(profile.region || "Unknown region")}</p>
            <span class="pill hot">${escapeHtml(profile.rank || "Unranked")}</span>
          </div>
        </div>
        <div class="deck-mini-tags">
          ${platforms.map(platform => `<span>${escapeHtml(platform)}</span>`).join("")}
          ${styles.map(style => `<span>${escapeHtml(style)}</span>`).join("")}
        </div>
        <p class="deck-bio">${escapeHtml(profile.bio || "No bio yet.")}</p>
        <div class="deck-reasons">
          ${score.reasons.map(reason => `<span>${escapeHtml(reason)}</span>`).join("")}
        </div>
        ${connection ? `<p class="muted">${escapeHtml(connectionSummary(connection, profile.id))}</p>` : ""}
        <div class="deck-actions">${renderDeckActions(profile, connection)}</div>
      </div>
    </article>
  `;
}

function renderDeckActions(profile, connection) {
  if (!state.profile) return `<button class="deck-action pass" type="button" data-profile-tab>Sign In</button>`;
  const pass = `<button class="deck-action pass" type="button" data-discovery-pass>Pass</button>`;
  if (isBlocked(profile.id)) {
    return `${pass}<button class="deck-action play" type="button" data-unblock="${profile.id}">Unblock</button>`;
  }
  if (!connection) {
    return `${pass}<button class="deck-action play" type="button" data-connect="${profile.id}">Play</button>`;
  }
  if (connection.status === "accepted") {
    return `${pass}<button class="deck-action play" type="button" data-new-chat="${profile.id}">Open Chat</button>`;
  }
  if (connection.status === "pending" && connection.to_profile_id === state.profile.id) {
    return `${pass}<button class="deck-action play" type="button" data-connection-response="${connection.id}" data-status="accepted">Accept</button>`;
  }
  if (connection.status === "pending") {
    return `${pass}<button class="deck-action play" type="button" disabled>Request Sent</button>`;
  }
  return `${pass}<button class="deck-action play" type="button" data-connect="${profile.id}">Try Again</button>`;
}

function renderLfgCreateCard() {
  const games = lfgGameOptions();
  if (!state.profile) {
    return `
      <div class="card discovery-side-card">
        <h3>Create LFG</h3>
        <p>Sign in to post a party and review join requests.</p>
        <button class="button purple" type="button" data-profile-tab>Sign In</button>
      </div>
    `;
  }
  return `
    <form class="card lfg-create-card" data-create-lfg>
      <div>
        <h3>Create LFG</h3>
        <p>Post an open squad slot for players to request.</p>
      </div>
      <input class="field" name="title" maxlength="80" placeholder="Title, e.g. Ranked grind tonight" required>
      <select class="field" name="game_id">
        <option value="">Any game</option>
        ${games.map(game => `<option value="${escapeAttribute(game.id)}">${escapeHtml(game.name)}</option>`).join("")}
      </select>
      <div class="filter-row">
        <input class="field" name="mode" maxlength="40" placeholder="Mode" value="Ranked">
        <input class="field" name="party_size" maxlength="20" placeholder="Party size, e.g. 2 / 4">
      </div>
      <div class="filter-row">
        <input class="field" name="rank_range" maxlength="40" placeholder="Rank range">
        <input class="field" name="starts_at" maxlength="60" placeholder="When">
      </div>
      <button class="button green wide" type="submit" ${state.lfgBusy === "create" ? "disabled" : ""}>${state.lfgBusy === "create" ? "Posting..." : "Post LFG"}</button>
    </form>
  `;
}

function renderLfgBrowseCard() {
  const posts = filteredLfgPosts().filter(post => post.profile_id !== state.profile?.id).slice(0, 8);
  return `
    <div class="card lfg-browse-card">
      <div>
        <h3>Live LFG Posts</h3>
        <p>Request to join open parties. Owners approve requests from My Posts.</p>
      </div>
      <form class="lfg-filter-row" data-lfg-filters>
        <input class="field" name="search" placeholder="Search LFG posts" value="${escapeAttribute(state.lfgFilters.search)}">
        <select class="field" name="status">
          ${["all", "open", "filled", "closed"].map(status => `<option value="${status}" ${state.lfgFilters.status === status ? "selected" : ""}>${escapeHtml(status === "all" ? "All" : status)}</option>`).join("")}
        </select>
      </form>
      <div class="lfg-list">
        ${posts.length ? posts.map(renderDiscoveryLfgPost).join("") : `<div class="empty">No LFG posts match those filters.</div>`}
      </div>
    </div>
  `;
}

function renderDiscoveryLfgPost(post) {
  const owner = profileFor(post.profile_id);
  const request = myLfgRequest(post.id);
  const ownerView = post.profile_id === state.profile?.id;
  const status = lfgStatus(post);
  const disabled = !state.profile || ownerView || status !== "open" || Boolean(request) || state.lfgBusy === post.id;
  const actionLabel = !state.profile
    ? "Sign In"
    : ownerView
      ? "Your Post"
      : request
        ? request.status
        : status === "open"
          ? "Request To Join"
          : status;
  const joinAction = !state.profile
    ? `<button class="button green" type="button" data-profile-tab>Sign In</button>`
    : `<button class="button green" type="button" data-join-lfg="${post.id}" ${disabled ? "disabled" : ""}>${escapeHtml(actionLabel)}</button>`;
  return `
    <article class="lfg-card">
      <div class="lfg-card-head">
        <div>
          <span class="pill ${status === "open" ? "hot" : ""}">${escapeHtml(status)}</span>
          <h3>${escapeHtml(post.title || "Untitled LFG")}</h3>
          <p>${escapeHtml(owner?.handle || "Unknown player")} - ${escapeHtml(lfgGameLabel(post))}</p>
        </div>
        <span class="chat-avatar">${renderAvatar(owner, owner?.handle || "GC")}</span>
      </div>
      <div class="lfg-meta-grid">
        <span><strong>Mode</strong>${escapeHtml(post.mode || "Any mode")}</span>
        <span><strong>Rank</strong>${escapeHtml(post.rank_range || "Any rank")}</span>
        <span><strong>Party</strong>${escapeHtml(post.party_size || "Any size")}</span>
        <span><strong>When</strong>${escapeHtml(post.starts_at || "Flexible")}</span>
      </div>
      <div class="lfg-actions">
        ${ownerView && status !== "closed" ? `<button class="button red" type="button" data-close-lfg="${post.id}" ${state.lfgBusy === post.id ? "disabled" : ""}>${state.lfgBusy === post.id ? "Closing..." : "Close"}</button>` : ""}
        ${ownerView ? `<span class="muted">${lfgPendingRequests(post.id).length} pending request(s)</span>` : joinAction}
      </div>
    </article>
  `;
}

function renderLfgOwnerRequests(post) {
  const requests = lfgPendingRequests(post.id);
  if (!requests.length) return "";
  return `
    <div class="lfg-request-list">
      <h4>Pending Requests</h4>
      ${requests.map(request => {
        const profile = profileFor(request.requester_profile_id);
        return `
          <div class="lfg-request-row">
            <span class="chat-avatar">${renderAvatar(profile, profile?.handle || "GC")}</span>
            <div>
              <strong>${escapeHtml(profile?.handle || "Unknown player")}</strong>
              <p>${escapeHtml(profile?.region || "Unknown region")} - ${escapeHtml(profile?.rank || "Unranked")}</p>
            </div>
            <button class="button green" type="button" data-accept-lfg="${request.id}" ${state.lfgBusy === request.id ? "disabled" : ""}>Accept</button>
            <button class="button dark" type="button" data-reject-lfg="${request.id}" ${state.lfgBusy === request.id ? "disabled" : ""}>Reject</button>
          </div>
        `;
      }).join("")}
    </div>
  `;
}

function renderDiscoveryMatches() {
  const matches = acceptedConnections()
    .map(connection => otherProfileForConnection(connection))
    .filter(Boolean);
  return `
    <section class="discovery-panel-grid">
      ${matches.length ? matches.map(profile => `
        <article class="discovery-match-card">
          <span class="chat-avatar lg">${renderAvatar(profile, profile.handle)}</span>
          <div>
            <h3>${escapeHtml(profile.handle)}</h3>
            <p>${escapeHtml(profile.region || "Unknown region")} - ${escapeHtml(profile.rank || "Unranked")}</p>
          </div>
          <button class="button green" type="button" data-new-chat="${profile.id}">Open Chat</button>
        </article>
      `).join("") : `<div class="empty">No matches yet. Start Discovery and press Play on someone who looks like a good fit.</div>`}
    </section>
  `;
}

function renderDiscoveryPosts() {
  const mine = state.profile ? state.lfg.filter(post => post.profile_id === state.profile.id || post.created_by_profile_id === state.profile.id) : [];
  return `
    <section class="lfg-manage-layout">
      ${renderLfgCreateCard()}
      <div class="lfg-manage-list">
        <h3>My LFG Posts</h3>
        ${mine.length ? mine.map(post => `${renderDiscoveryLfgPost(post)}${renderLfgOwnerRequests(post)}`).join("") : `<div class="empty">Your LFG posts will show here after you create one.</div>`}
      </div>
    </section>
  `;
}

function renderPlayerCard(profile) {
  const connection = connectionWith(profile.id);
  const actions = renderConnectionActions(profile, connection);
  const score = discoveryScore(profile);
  const games = list(profile.top_games).map(gameLabel).filter(Boolean).slice(0, 4);
  const styles = list(profile.play_style).slice(0, 4);
  const platforms = list(profile.platforms).slice(0, 3);
  return `
    <article class="discovery-card">
      <div class="discovery-match">
        <strong>${score.score}%</strong>
        <span>Match</span>
      </div>
      <div class="discovery-profile">
        <span class="chat-avatar lg">${renderAvatar(profile, profile.handle)}</span>
        <div>
          <h3>${escapeHtml(profile.handle)}</h3>
          <p>${escapeHtml(profile.rank || "Unranked")} - ${escapeHtml(profile.region || "Unknown region")}</p>
        </div>
      </div>
      <p>${escapeHtml(profile.bio || "No bio yet.")}</p>
      <div class="discovery-tags">
        ${games.map(game => `<span class="pill hot">${escapeHtml(game)}</span>`).join("")}
        ${platforms.map(platform => `<span class="pill">${escapeHtml(platform)}</span>`).join("")}
        ${styles.map(style => `<span class="pill">${escapeHtml(style)}</span>`).join("")}
      </div>
      <div class="compat-grid">
        ${score.reasons.map(reason => `<span>${escapeHtml(reason)}</span>`).join("")}
      </div>
      ${connection ? `<p class="muted">${escapeHtml(connectionSummary(connection, profile.id))}</p>` : ""}
      <div class="discovery-actions">${actions}</div>
    </article>
  `;
}

function renderConnectionActions(profile, connection) {
  if (!state.profile) return `<button class="button dark" data-profile-tab>Sign In</button>`;
  if (isBlocked(profile.id)) return `<button class="button dark" data-unblock="${profile.id}">Unblock</button>`;
  if (!connection) return `<button class="button green" data-connect="${profile.id}">Add Player</button>`;
  if (connection.status === "accepted") {
    return `
      <button class="button green" data-new-chat="${profile.id}">Message</button>
      <button class="button dark" data-unfriend="${profile.id}">Unfriend</button>
      <button class="button red" data-block="${profile.id}">Block</button>
    `;
  }
  if (connection.status === "pending" && connection.to_profile_id === state.profile.id) {
    return `
      <button class="button green" data-connection-response="${connection.id}" data-status="accepted">Accept</button>
      <button class="button dark" data-connection-response="${connection.id}" data-status="rejected">Decline</button>
      <button class="button red" data-block="${profile.id}">Block</button>
    `;
  }
  if (connection.status === "pending") return `<button class="button dark" disabled>Request Sent</button>`;
  if (connection.status === "rejected" && connection.to_profile_id === state.profile.id) {
    return `<button class="button green" data-connect="${profile.id}">Send Request</button>`;
  }
  return `<button class="button dark" disabled>Declined</button>`;
}

function renderMessages() {
  const accepted = acceptedConnections();
  const conversations = visibleConversations();
  const selectedConversation = selectedConversationFrom(conversations);
  const selectedProfile = selectedConversation ? selectedConversationProfile(selectedConversation) : null;
  const profilePanelVisible = Boolean(!state.newChatOpen && state.profilePanelOpen && selectedProfile);
  const mobileView = state.newChatOpen ? "new" : selectedConversation ? (state.messagesMobileView || "list") : "list";
  return page("Messages", "Matched players, direct chats, and group conversations.", `
    ${state.session ? `
      <div class="message-page-wrap ${state.newChatOpen ? "new-chat-open" : ""} mobile-${escapeAttribute(mobileView)}">
        <div class="messages-shell ${profilePanelVisible ? "profile-open" : ""} ${state.newChatOpen ? "compose-open" : ""}">
          <aside class="messages-side">
            <div class="messages-list-panel">
              <div class="messages-list-head">
                <div>
                  <span>MESSAGES</span>
                  <h3>Messages</h3>
                </div>
                <div class="messages-head-actions">
                  <button class="icon-button small" title="Filters" type="button">${icon("sliders")}</button>
                  <button class="icon-button purple" title="New chat" data-focus-new-chat>${icon("edit")}</button>
                </div>
              </div>
              <label class="message-search">
                <span>Search</span>
                <input placeholder="Search" disabled>
              </label>
              <div class="message-tabs">
                <button class="active" type="button">All</button>
                <button type="button">Unread</button>
                <button type="button">Groups</button>
                <button type="button">Requests</button>
              </div>
              <div class="conversation-list">
                ${conversations.length ? conversations.map(conversation => renderConversationListItem(conversation, selectedConversation?.id)).join("") : `<p>No conversations yet.</p>`}
              </div>
              <div class="find-more-card" data-tab="discovery">
                <strong>Find More Players</strong>
                <span>Expand your circle</span>
              </div>
            </div>
          </aside>
          <section class="chat-panel">
            ${selectedConversation ? renderChatThread(selectedConversation) : renderNoChatSelected()}
          </section>
          ${state.newChatOpen ? `
            <aside class="new-chat-panel">
              ${renderNewChatPopover(accepted)}
            </aside>
          ` : ""}
          ${profilePanelVisible ? `
            <aside class="chat-profile-panel">
              ${renderChatProfilePanel(selectedProfile, selectedConversation)}
            </aside>
          ` : ""}
        </div>
      </div>
    ` : `<div class="card notice"><h3>Sign in required</h3><p>Messages need a Supabase account.</p></div>`}
  `);
}

function renderNoChatSelected() {
  return `
    <div class="chat-thread empty-chat-thread">
      <div class="empty">
        <h3>No chat selected</h3>
        <p>Create a chat or choose one from Messages.</p>
        <button class="button purple" type="button" data-messages-list>Back To Messages</button>
      </div>
    </div>
  `;
}

function renderNewChatPopover(acceptedConnectionsList) {
  return `
    <div class="new-chat-popover">
      <div class="new-chat-head">
        <button class="icon-button" type="button" title="Close new chat" data-close-new-chat>${icon("x")}</button>
        <h3>New Chat</h3>
        <span>${acceptedConnectionsList.length}</span>
      </div>
      ${acceptedConnectionsList.length ? renderNewChatForm(acceptedConnectionsList) : `<p>Add people first, then start direct or group chats here.</p>`}
    </div>
  `;
}

function renderIncomingConnection(connection) {
  const from = profileFor(connection.from_profile_id);
  return `
    <div class="connection-row">
      <div>
        <strong>${escapeHtml(from?.handle || "Player")}</strong>
        <p>${escapeHtml(connection.message || "Wants to connect.")}</p>
      </div>
      <div class="btn-row">
        <button class="button green" data-connection-response="${connection.id}" data-status="accepted">Accept</button>
        <button class="button dark" data-connection-response="${connection.id}" data-status="rejected">Decline</button>
      </div>
    </div>
  `;
}

function renderNewChatForm(acceptedConnectionsList) {
  const people = acceptedConnectionsList.map(otherProfileForConnection).filter(Boolean);
  return `
    <form class="new-chat-form" data-new-chat-form>
      <label class="message-search new-chat-search">
        <span>Search</span>
        <input placeholder="Search by username" data-new-chat-search autocomplete="off">
      </label>
      <div class="new-chat-quick-row">
        <div class="create-group-bubble">
          <span>${icon("plus")}</span>
          <strong>Create Group</strong>
        </div>
        ${people.slice(0, 6).map(profile => `
          <label class="quick-person">
            <input type="checkbox" name="members" value="${escapeAttribute(profile.id)}">
            <span class="chat-avatar">${renderAvatar(profile, profile.handle)}</span>
            <strong>${escapeHtml(profile.handle)}</strong>
          </label>
        `).join("")}
      </div>
      <label>Chat Title<input class="field" name="title" placeholder="Optional group name"></label>
      <div class="new-chat-suggested-head">
        <span>SUGGESTED</span>
        <small>${people.length} available</small>
      </div>
      <div class="chat-picker suggested-picker">
        ${people.map(profile => `
          <label class="check-row person-row" data-person-row="${escapeAttribute(profile.handle)}">
            <input type="checkbox" name="members" value="${escapeAttribute(profile.id)}">
            <span class="chat-avatar">${renderAvatar(profile, profile.handle)}</span>
            <span>
              <strong>${escapeHtml(profile.handle)}</strong>
              <small>${escapeHtml(profile.online ? "Online" : profile.region || "Offline")}</small>
            </span>
          </label>
        `).join("")}
      </div>
      <label>First Message<input class="field" name="first_message" placeholder="Optional first message"></label>
      <div class="selected-chat-preview">
        ${people.slice(0, 2).map(profile => `<span>${escapeHtml(profile.handle)} <small>x</small></span>`).join("")}
      </div>
      <button class="button purple" type="submit">${icon("message")}Create Chat</button>
      <button class="button dark" type="button">${icon("users")}Create Group Chat</button>
    </form>
  `;
}

function renderAcceptedConnection(connection) {
  const other = otherProfileForConnection(connection);
  if (!other) return "";
  return `
    <div class="connection-row">
      <div>
        <strong>${escapeHtml(other.handle)}</strong>
        <p>${escapeHtml(other.region || "Unknown region")} - ${escapeHtml(connection.status)}</p>
      </div>
      <button class="button green" data-new-chat="${other.id}">Message</button>
      <button class="button dark" data-unfriend="${other.id}">Unfriend</button>
      <button class="button red" data-block="${other.id}">Block</button>
    </div>
  `;
}

function renderConversationListItem(conversation, selectedId) {
  const messages = state.messages[conversation.id] || [];
  const last = messages[messages.length - 1];
  const participants = conversationParticipants(conversation);
  const title = conversationTitle(conversation);
  const avatarProfile = selectedConversationProfile(conversation) || participants[0];
  return `
    <button class="conversation-item ${conversation.id === selectedId ? "active" : ""}" data-select-conversation="${conversation.id}">
      <span class="chat-avatar">${renderAvatar(avatarProfile, title)}</span>
      <span class="conversation-copy">
        <strong>${escapeHtml(title)}</strong>
        <span>${escapeHtml(last ? lastMessagePreview(last) : `${participants.length} participant(s)`)}</span>
      </span>
      <span class="conversation-meta">
        <small>${escapeHtml(formatRelativeTime(last?.created_at || conversation.updated_at || conversation.created_at))}</small>
        ${messages.length ? `<em>${Math.min(messages.length, 9)}</em>` : ""}
      </span>
    </button>
  `;
}

function renderChatThread(conversation) {
  const messages = state.messages[conversation.id] || [];
  const participants = conversationParticipants(conversation);
  const title = conversationTitle(conversation);
  const other = selectedConversationProfile(conversation);
  return `
    <div class="chat-thread">
      <div class="chat-head">
        <button class="chat-title-wrap" type="button" data-toggle-chat-profile>
          <span class="mobile-chat-back" data-messages-list>${icon("arrowLeft")}</span>
          <span class="chat-avatar lg">${renderAvatar(other || participants[0], title)}</span>
          <div>
            <h3>${escapeHtml(title)}</h3>
            <p>${other ? `${escapeHtml(other.online ? "Online" : "Online soon")} - ${escapeHtml(other.rank || "Unranked")}` : `${participants.length} participant(s)`}</p>
          </div>
        </button>
        <div class="chat-actions">
          <button class="icon-button" title="Call" type="button">${icon("phone")}</button>
          <button class="icon-button" title="Video" type="button">${icon("video")}</button>
          <button class="icon-button" title="Info" type="button" data-toggle-chat-profile>${icon("info")}</button>
          <button class="icon-button danger" title="Delete conversation" type="button" data-delete-conversation="${conversation.id}">${icon("trash")}</button>
        </div>
      </div>
      <div class="message-list">
        <div class="day-divider">Today</div>
        ${messages.length ? messages.map(renderMessageBubble).join("") : `<div class="empty">No messages yet. Send the first one.</div>`}
      </div>
      <form class="message-compose" data-send-message="${conversation.id}">
        <button class="icon-button compose-plus" type="button">${icon("plus")}</button>
        <input class="field" name="body" placeholder="Write a message..." autocomplete="off" required>
        <button class="icon-button" type="button">${icon("smile")}</button>
        <button class="button green send-button" type="submit">${icon("send")}<span>Send</span></button>
      </form>
    </div>
  `;
}

function renderMessageBubble(message) {
  const mine = message.sender_profile_id === state.profile?.id;
  const sender = profileFor(message.sender_profile_id);
  return `
    <div class="message-bubble ${mine ? "mine" : ""}">
      <strong>${escapeHtml(mine ? "You" : sender?.handle || "Player")}</strong>
      <p>${escapeHtml(message.body)}</p>
      <span>${escapeHtml(message.pending ? "Sending..." : formatShortDate(message.created_at))}</span>
    </div>
  `;
}

function renderNewChatAside(accepted) {
  return `
    <div class="side-profile-empty">
      <div class="mark">GC</div>
      <h3>Ready To Squad Up?</h3>
      <p>Select a chat or create a new direct/group conversation with accepted players.</p>
      <span class="pill">${accepted.length} people available</span>
    </div>
  `;
}

function renderChatProfilePanel(profile, conversation) {
  const games = list(profile.top_games).map(gameLabel).filter(Boolean);
  const styles = list(profile.play_style);
  const stats = profile.stats || {};
  const gameRanks = stats.gameRanks || {};
  return `
    <div class="chat-profile-card">
      <button class="icon-button profile-panel-close" title="Close profile" type="button" data-toggle-chat-profile>${icon("x")}</button>
      <div class="profile-cover"></div>
      <div class="profile-summary">
        <span class="chat-avatar xl">${renderAvatar(profile, profile.handle)}</span>
        <h3>${escapeHtml(profile.handle || "Player")}</h3>
        <p>${escapeHtml(profile.rank || "Unranked")} - ${escapeHtml(profile.region || "Unknown region")}</p>
      </div>
      <div class="profile-actions mini">
        <button class="button" type="button" disabled>View Profile</button>
        <button class="button dark" data-unfriend="${profile.id}">Unfriend</button>
      </div>
      <section>
        <h4>About</h4>
        <p>${escapeHtml(profile.bio || "No bio yet.")}</p>
      </section>
      <section>
        <h4>Play Style</h4>
        ${styles.length ? styles.slice(0, 4).map(style => `<span class="pill hot">${escapeHtml(style)}</span>`).join("") : `<p>No play style set.</p>`}
      </section>
      <section>
        <h4>Games</h4>
        ${games.length ? games.slice(0, 3).map(game => `<div class="mini-game-row"><span>${escapeHtml(game)}</span><small>${escapeHtml(gameRanks[game] || profile.rank || "Unranked")}</small></div>`).join("") : `<p>No games set.</p>`}
      </section>
      <section>
        <h4>Availability</h4>
        <div class="mini-game-row"><span>${escapeHtml(availabilityLabel(profile.availability) || "Not set")}</span><small>${escapeHtml(profile.timezone || "")}</small></div>
      </section>
      <button class="button red wide" data-block="${profile.id}">Block User</button>
    </div>
  `;
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
    <section class="coming-soon-panel">
      <div class="coming-soon-mark">GC</div>
      <span class="pill hot">Coming Soon</span>
      <h3>Events and servers are on the way</h3>
      <p>This section will become the home for community servers, scheduled sessions, tournaments, and game-specific hubs.</p>
      <div class="coming-soon-grid">
        <span>Community servers</span>
        <span>Scheduled game nights</span>
        <span>Squad events</span>
        <span>Server discovery</span>
      </div>
    </section>
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
  document.querySelector("[data-discovery-filters]")?.addEventListener("input", updateDiscoveryFilters);
  document.querySelector("[data-lfg-filters]")?.addEventListener("input", updateLfgFilters);
  document.querySelectorAll("[data-discovery-tab]").forEach(button => button.addEventListener("click", () => switchDiscoveryTab(button.dataset.discoveryTab)));
  document.querySelector("[data-start-discovery]")?.addEventListener("click", startDiscovery);
  document.querySelector("[data-discovery-pass]")?.addEventListener("click", passDiscoveryProfile);
  document.querySelector("[data-create-lfg]")?.addEventListener("submit", createLfgPost);
  document.querySelectorAll("[data-join-lfg]").forEach(button => button.addEventListener("click", () => requestLfgJoin(button.dataset.joinLfg)));
  document.querySelectorAll("[data-accept-lfg]").forEach(button => button.addEventListener("click", () => acceptLfgRequest(button.dataset.acceptLfg)));
  document.querySelectorAll("[data-reject-lfg]").forEach(button => button.addEventListener("click", () => rejectLfgRequest(button.dataset.rejectLfg)));
  document.querySelectorAll("[data-close-lfg]").forEach(button => button.addEventListener("click", () => closeLfgPost(button.dataset.closeLfg)));
  document.querySelector("[data-create-post]")?.addEventListener("submit", createPost);
  document.querySelectorAll("[data-like]").forEach(button => button.addEventListener("click", () => likePost(button.dataset.like)));
  document.querySelectorAll("[data-delete-post]").forEach(button => button.addEventListener("click", () => deletePost(button.dataset.deletePost)));
  document.querySelectorAll("[data-create-comment]").forEach(form => form.addEventListener("submit", createComment));
  document.querySelectorAll("[data-comment-jump]").forEach(button => button.addEventListener("click", () => focusComment(button.dataset.commentJump)));
  document.querySelectorAll("[data-share]").forEach(button => button.addEventListener("click", () => sharePost(button.dataset.share)));
  document.querySelectorAll("[data-connect]").forEach(button => button.addEventListener("click", () => connectToPlayer(button.dataset.connect)));
  document.querySelectorAll("[data-connection-response]").forEach(button => button.addEventListener("click", () => respondToConnection(button.dataset.connectionResponse, button.dataset.status)));
  document.querySelectorAll("[data-new-chat]").forEach(button => button.addEventListener("click", () => startChat(button.dataset.newChat)));
  document.querySelectorAll("[data-unfriend]").forEach(button => button.addEventListener("click", () => unfriendPlayer(button.dataset.unfriend)));
  document.querySelectorAll("[data-block]").forEach(button => button.addEventListener("click", () => blockPlayer(button.dataset.block)));
  document.querySelectorAll("[data-unblock]").forEach(button => button.addEventListener("click", () => unblockPlayer(button.dataset.unblock)));
  document.querySelectorAll("[data-select-conversation]").forEach(button => button.addEventListener("click", () => selectConversation(button.dataset.selectConversation)));
  document.querySelectorAll("[data-messages-list]").forEach(button => button.addEventListener("click", showMessagesList));
  document.querySelectorAll("[data-toggle-chat-profile]").forEach(button => button.addEventListener("click", toggleChatProfile));
  document.querySelectorAll("[data-delete-conversation]").forEach(button => button.addEventListener("click", () => deleteConversation(button.dataset.deleteConversation)));
  document.querySelectorAll("[data-focus-new-chat]").forEach(button => button.addEventListener("click", toggleNewChat));
  document.querySelector("[data-close-new-chat]")?.addEventListener("click", closeNewChat);
  document.querySelector("[data-new-chat-search]")?.addEventListener("input", filterNewChatPeople);
  document.querySelector("[data-new-chat-form]")?.addEventListener("submit", createChatFromForm);
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
  const submitButton = event.currentTarget.querySelector("button[type='submit']");
  if (submitButton) {
    submitButton.disabled = true;
    submitButton.textContent = "Publishing...";
  }
  const file = form.get("media_file");
  const upload = await uploadFeedMedia(file && file.size > 0 ? file : null);
  if (upload.error) {
    if (submitButton) {
      submitButton.disabled = false;
      submitButton.textContent = "Publish";
    }
    alert(upload.error);
    return;
  }
  const postType = String(form.get("post_type") || "post");
  const { error } = await state.supabase.from("feed_posts").insert({
    profile_id: state.profile.id,
    post_type: postType,
    title: form.get("title"),
    body: form.get("body"),
    game_id: form.get("game_id") || null,
    media_url: upload.url,
    media_type: upload.mediaType
  });
  if (error) {
    if (submitButton) {
      submitButton.disabled = false;
      submitButton.textContent = "Publish";
    }
    if (String(error.message || "").toLowerCase().includes("game_id")) {
      return alert(`${error.message}\n\nRun supabase/migrations/0013_seed_popular_games.sql so selected profile games can be tagged in feed posts.`);
    }
    return alert(error.message);
  }
  event.currentTarget.reset();
  await loadData();
}

async function uploadFeedMedia(file) {
  if (!file) return { url: null, mediaType: null, error: "" };
  const isImage = file.type.startsWith("image/");
  const isVideo = file.type.startsWith("video/");
  if (!isImage && !isVideo) return { url: null, mediaType: null, error: "Upload an image or video file." };
  if (isImage && file.size > maxFeedImageBytes) return { url: null, mediaType: null, error: "Images need to be 5 MB or smaller for now." };
  let uploadFile = file;
  if (isVideo) {
    if (file.size > maxFeedVideoSourceBytes) {
      return { url: null, mediaType: null, error: "That clip is too large to compress in the browser. Try a file under 250 MB." };
    }
    const metadata = await readVideoMetadata(file);
    if (metadata.error) return { url: null, mediaType: null, error: metadata.error };
    if (metadata.duration > maxFeedVideoDurationSeconds + 0.5) {
      return { url: null, mediaType: null, error: "Clips need to be 60 seconds or shorter for now." };
    }
    if (file.size > maxFeedVideoBytes) {
      const compressed = await compressFeedVideo(file, metadata);
      if (compressed.error) return { url: null, mediaType: null, error: compressed.error };
      uploadFile = compressed.file;
    }
    if (uploadFile.size > maxFeedVideoBytes) {
      return { url: null, mediaType: null, error: "The compressed clip is still over 50 MB. Trim it slightly or lower the source quality." };
    }
  }

  const extension = uploadFile.name.includes(".") ? uploadFile.name.split(".").pop().toLowerCase().replace(/[^a-z0-9]/g, "") : (isVideo ? "webm" : "jpg");
  const uniquePart = window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const path = `${state.profile.id}/${Date.now()}-${uniquePart}.${extension}`;
  const { error } = await state.supabase.storage
    .from(feedMediaBucket)
    .upload(path, uploadFile, {
      cacheControl: "3600",
      contentType: uploadFile.type,
      upsert: false
    });
  if (error) {
    return {
      url: null,
      mediaType: null,
      error: `${error.message}\n\nIf this mentions bucket or row-level security, run supabase/migrations/0012_feed_media_storage.sql in Supabase SQL Editor.`
    };
  }
  const { data } = state.supabase.storage.from(feedMediaBucket).getPublicUrl(path);
  return { url: data.publicUrl, mediaType: isVideo ? "video" : "image", error: "" };
}

function readVideoMetadata(file) {
  return new Promise(resolve => {
    const video = document.createElement("video");
    const url = URL.createObjectURL(file);
    const cleanup = () => URL.revokeObjectURL(url);
    video.preload = "metadata";
    video.muted = true;
    video.onloadedmetadata = () => {
      const metadata = {
        duration: Number.isFinite(video.duration) ? video.duration : 0,
        width: video.videoWidth || 1280,
        height: video.videoHeight || 720,
        error: ""
      };
      cleanup();
      resolve(metadata);
    };
    video.onerror = () => {
      cleanup();
      resolve({ duration: 0, width: 0, height: 0, error: "Could not read that video file." });
    };
    video.src = url;
  });
}

function compressedVideoDimensions(width, height) {
  const maxSide = Math.max(width, height);
  if (maxSide <= compressedFeedVideoMaxEdge) return { width, height };
  const scale = compressedFeedVideoMaxEdge / maxSide;
  return {
    width: Math.max(2, Math.round(width * scale / 2) * 2),
    height: Math.max(2, Math.round(height * scale / 2) * 2)
  };
}

async function compressFeedVideo(file, metadata) {
  if (!window.MediaRecorder) {
    return { file: null, error: "This browser cannot compress video yet. Try uploading a clip under 50 MB." };
  }
  const mimeType = MediaRecorder.isTypeSupported("video/webm;codecs=vp9")
    ? "video/webm;codecs=vp9"
    : MediaRecorder.isTypeSupported("video/webm;codecs=vp8")
      ? "video/webm;codecs=vp8"
      : "video/webm";
  if (!MediaRecorder.isTypeSupported(mimeType)) {
    return { file: null, error: "This browser cannot create compressed WebM clips. Try a clip under 50 MB." };
  }

  const video = document.createElement("video");
  const canvas = document.createElement("canvas");
  const sourceUrl = URL.createObjectURL(file);
  const dimensions = compressedVideoDimensions(metadata.width, metadata.height);
  canvas.width = dimensions.width;
  canvas.height = dimensions.height;
  video.src = sourceUrl;
  video.muted = true;
  video.playsInline = true;
  video.preload = "auto";

  try {
    await once(video, "loadeddata");
    const context = canvas.getContext("2d");
    const stream = canvas.captureStream(compressedFeedVideoFps);
    const sourceStream = video.captureStream ? video.captureStream() : null;
    sourceStream?.getAudioTracks().forEach(track => stream.addTrack(track));
    const recorder = new MediaRecorder(stream, {
      mimeType,
      videoBitsPerSecond: compressedFeedVideoBitrate
    });
    const chunks = [];
    recorder.ondataavailable = event => {
      if (event.data?.size) chunks.push(event.data);
    };

    const done = once(recorder, "stop");
    const drawFrame = () => {
      if (video.paused || video.ended) return;
      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      window.requestAnimationFrame(drawFrame);
    };
    recorder.start(1000);
    await video.play();
    drawFrame();
    await once(video, "ended");
    recorder.stop();
    await done;

    const blob = new Blob(chunks, { type: "video/webm" });
    return {
      file: new File([blob], replaceFileExtension(file.name, "webm"), { type: "video/webm" }),
      error: ""
    };
  } catch (_error) {
    return { file: null, error: "Could not compress that clip in the browser. Try a clip under 50 MB." };
  } finally {
    URL.revokeObjectURL(sourceUrl);
  }
}

function once(target, eventName) {
  return new Promise((resolve, reject) => {
    target.addEventListener(eventName, resolve, { once: true });
    target.addEventListener("error", reject, { once: true });
  });
}

function replaceFileExtension(name, extension) {
  const baseName = name.includes(".") ? name.slice(0, name.lastIndexOf(".")) : name;
  return `${baseName || "clip"}.${extension}`;
}

async function likePost(postId) {
  if (!state.profile) return alert("Sign in first.");
  const existing = state.feedReactions.find(reaction => reaction.post_id === postId && reaction.profile_id === state.profile.id);
  if (existing) {
    const { error } = await state.supabase
      .from("feed_reactions")
      .delete()
      .eq("post_id", postId)
      .eq("profile_id", state.profile.id);
    if (error) return alert(error.message);
    await loadData();
    return;
  }
  const { error } = await state.supabase.from("feed_reactions").insert({
    post_id: postId,
    profile_id: state.profile.id,
    reaction: "like"
  });
  if (error) return alert(error.message);
  await loadData();
}

async function createComment(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  const postId = event.currentTarget.dataset.createComment;
  const form = new FormData(event.currentTarget);
  const body = String(form.get("body") || "").trim();
  if (!body) return;
  const { error } = await state.supabase.from("feed_comments").insert({
    post_id: postId,
    profile_id: state.profile.id,
    body
  });
  if (error) return alert(error.message);
  event.currentTarget.reset();
  await loadData();
}

async function deletePost(postId) {
  if (!state.profile) return alert("Sign in first.");
  if (!confirm("Delete this feed post?")) return;
  const { error } = await state.supabase
    .from("feed_posts")
    .delete()
    .eq("id", postId)
    .eq("profile_id", state.profile.id);
  if (error) return alert(error.message);
  await loadData();
}

function focusComment(postId) {
  const form = [...document.querySelectorAll("[data-create-comment]")].find(item => item.dataset.createComment === postId);
  const input = form?.querySelector("input[name='body']");
  input?.focus();
  input?.scrollIntoView({ behavior: "smooth", block: "center" });
}

async function sharePost(postId) {
  const url = `${window.location.origin}${window.location.pathname}#feed-${postId}`;
  try {
    await navigator.clipboard.writeText(url);
    alert("Post link copied.");
  } catch (_error) {
    alert(url);
  }
}

async function connectToPlayer(profileId) {
  if (!state.profile) return alert("Sign in first.");
  if (isBlocked(profileId)) return alert("Unblock this player before adding them again.");
  const existing = connectionWith(profileId);
  if (existing?.status === "accepted") {
    await startChat(profileId);
    return;
  }
  if (existing?.status === "pending") {
    alert(existing.to_profile_id === state.profile.id ? "They already sent you a request. Accept it to connect." : "Connection request already sent.");
    return;
  }
  const { error } = await state.supabase.from("connections").insert({
    from_profile_id: state.profile.id,
    to_profile_id: profileId,
    message: "Want to squad up?"
  });
  if (error) return alert(error.message);
  state.profileMessage = "Connection request sent.";
  await loadData();
}

async function startChat(profileId) {
  if (!state.profile) return alert("Sign in first.");
  if (isBlocked(profileId)) return alert("Unblock this player before messaging them.");
  const connection = connectionWith(profileId);
  if (!connection || connection.status !== "accepted") {
    alert("You can message players after a connection is accepted.");
    return;
  }
  const conversationId = await ensureChat([profileId]);
  if (!conversationId) return;
  state.selectedConversationId = conversationId;
  state.newChatOpen = false;
  state.messagesMobileView = "chat";
  state.tab = "messages";
  await loadData();
}

async function unfriendPlayer(profileId) {
  if (!state.profile) return alert("Sign in first.");
  const connection = connectionWith(profileId);
  if (!connection) return;
  const { error } = await removeConnectionsWith(profileId);
  if (error) return alert(error.message);
  await loadData();
}

async function blockPlayer(profileId) {
  if (!state.profile) return alert("Sign in first.");
  if (!confirm("Block this player? They will be removed from your people and hidden from your discovery list.")) return;
  const { error } = await state.supabase.rpc("block_profile", { target_profile_id: profileId });
  if (error) return alert(error.message);
  await loadData();
}

async function removeConnectionsWith(profileId) {
  return state.supabase.rpc("remove_connection_with", { target_profile_id: profileId });
}

async function unblockPlayer(profileId) {
  if (!state.profile) return alert("Sign in first.");
  const { error } = await state.supabase
    .from("blocked_profiles")
    .delete()
    .eq("blocker_profile_id", state.profile.id)
    .eq("blocked_profile_id", profileId);
  if (error) return alert(error.message);
  await loadData();
}

async function respondToConnection(connectionId, status) {
  if (!state.profile) return alert("Sign in first.");
  if (!["accepted", "rejected"].includes(status)) return;
  const connection = state.connections.find(item => item.id === connectionId);
  if (!connection || connection.to_profile_id !== state.profile.id) {
    alert("Only incoming requests can be updated.");
    return;
  }
  const { error } = await state.supabase
    .from("connections")
    .update({ status })
    .eq("id", connectionId);
  if (error) return alert(error.message);
  if (status === "accepted") {
    state.selectedConversationId = await ensureChat([connection.from_profile_id]);
    state.messagesMobileView = "chat";
    state.tab = "messages";
  }
  await loadData();
}

async function createChatFromForm(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  const form = new FormData(event.currentTarget);
  const members = form.getAll("members").map(String).filter(Boolean);
  if (!members.length) return alert("Choose at least one player.");
  const conversationId = await ensureChat(members, String(form.get("title") || ""), String(form.get("first_message") || ""));
  if (!conversationId) return;
  state.selectedConversationId = conversationId;
  state.newChatOpen = false;
  state.tab = "messages";
  state.messagesMobileView = "chat";
  event.currentTarget.reset();
  await loadData();
}

async function deleteConversation(conversationId) {
  if (!state.profile) return alert("Sign in first.");
  if (!conversationId) return;
  const conversation = state.conversations.find(item => item.id === conversationId);
  const title = conversation ? conversationTitle(conversation) : "this conversation";
  if (!confirm(`Delete ${title}? It will be removed from your Messages list.`)) return;
  const { error } = await state.supabase.rpc("delete_conversation", { target_conversation_id: conversationId });
  if (error) {
    alert(`${error.message}\n\nIf this mentions delete_conversation, run supabase/migrations/0010_conversation_delete_and_direct_dedupe.sql in Supabase SQL Editor.`);
    return;
  }
  if (state.selectedConversationId === conversationId) state.selectedConversationId = "";
  delete state.messages[conversationId];
  state.conversations = state.conversations.filter(item => item.id !== conversationId);
  await loadData();
}

async function ensureChat(profileIds, title = "", firstMessage = "") {
  const cleanIds = [...new Set(profileIds)].filter(profileId => profileId && !isBlocked(profileId));
  if (!cleanIds.length) {
    alert("Choose at least one unblocked player.");
    return "";
  }
  if (cleanIds.length === 1) {
    const existing = directConversationWith(cleanIds[0]);
    if (existing) return existing.id;
  }
  const { data, error } = await state.supabase.rpc("create_chat", {
    target_profile_ids: cleanIds,
    chat_title: title,
    first_message: firstMessage
  });
  if (error) {
    alert(`${error.message}\n\nIf this mentions create_chat, run supabase/migrations/0007_relationship_chat_rpc.sql in Supabase SQL Editor.`);
    return "";
  }
  return data || "";
}

async function sendMessage(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  const form = new FormData(event.currentTarget);
  const body = String(form.get("body") || "").trim();
  const conversationId = event.currentTarget.dataset.sendMessage;
  if (!body) return;
  if (isConversationBlocked(conversationId)) {
    alert("Unblock this player before sending messages.");
    return;
  }
  const tempId = `pending-${Date.now()}`;
  mergeRealtimeMessage({
    id: tempId,
    conversation_id: conversationId,
    sender_profile_id: state.profile.id,
    body,
    created_at: new Date().toISOString(),
    pending: true
  });
  state.selectedConversationId = conversationId;
  event.currentTarget.reset();
  renderShell();
  const { error } = await state.supabase.rpc("send_chat_message", {
    target_conversation_id: conversationId,
    message_body: body
  });
  if (error) {
    removeOptimisticMessage(conversationId, tempId);
    renderShell();
    alert(`${error.message}\n\nIf this mentions send_chat_message, run supabase/migrations/0008_chat_message_rpc.sql in Supabase SQL Editor.`);
    return;
  }
  await loadData();
}

function removeOptimisticMessage(conversationId, messageId) {
  state.messages[conversationId] = (state.messages[conversationId] || []).filter(message => message.id !== messageId);
}

function selectConversation(conversationId) {
  state.selectedConversationId = conversationId;
  state.profilePanelOpen = false;
  state.newChatOpen = false;
  state.messagesMobileView = "chat";
  renderShell();
}

function toggleChatProfile() {
  state.profilePanelOpen = !state.profilePanelOpen;
  renderShell();
}

function toggleNewChat() {
  state.newChatOpen = !state.newChatOpen;
  if (state.newChatOpen) {
    state.profilePanelOpen = false;
    state.messagesMobileView = "new";
  } else {
    state.messagesMobileView = "list";
  }
  renderShell();
  focusNewChatInput();
}

function closeNewChat() {
  state.newChatOpen = false;
  state.messagesMobileView = "list";
  renderShell();
}

function showMessagesList(event) {
  event?.stopPropagation();
  state.profilePanelOpen = false;
  state.newChatOpen = false;
  state.messagesMobileView = "list";
  renderShell();
}

function focusNewChatInput() {
  if (!state.newChatOpen) return;
  window.setTimeout(() => {
    document.querySelector(".new-chat-popover [data-new-chat-search]")?.focus();
  }, 0);
}

function filterNewChatPeople(event) {
  const query = event.currentTarget.value.trim().toLowerCase();
  document.querySelectorAll("[data-person-row]").forEach(row => {
    const name = (row.dataset.personRow || "").toLowerCase();
    row.hidden = query.length > 0 && !name.includes(query);
  });
}

function profileFor(id) {
  return state.profiles.find(profile => profile.id === id);
}

function connectionWith(profileId) {
  if (!state.profile) return null;
  return state.connections.find(connection =>
    (connection.from_profile_id === state.profile.id && connection.to_profile_id === profileId) ||
    (connection.from_profile_id === profileId && connection.to_profile_id === state.profile.id)
  ) || null;
}

function connectionSummary(connection, profileId) {
  if (connection.status === "accepted") return "Connected";
  if (connection.status === "rejected") return "Request declined";
  if (connection.to_profile_id === state.profile?.id) return "Incoming request";
  if (connection.from_profile_id === state.profile?.id && connection.to_profile_id === profileId) return "Request sent";
  return "Pending";
}

function pendingIncomingConnections() {
  if (!state.profile) return [];
  return state.connections.filter(connection => connection.status === "pending" && connection.to_profile_id === state.profile.id);
}

function pendingOutgoingConnections() {
  if (!state.profile) return [];
  return state.connections.filter(connection => connection.status === "pending" && connection.from_profile_id === state.profile.id);
}

function acceptedConnections() {
  if (!state.profile) return [];
  const seen = new Set();
  return state.connections.filter(connection => {
    if (connection.status !== "accepted") return false;
    const otherId = connection.from_profile_id === state.profile.id ? connection.to_profile_id : connection.from_profile_id;
    if (seen.has(otherId)) return false;
    seen.add(otherId);
    return true;
  });
}

function reactionsForPost(postId) {
  return state.feedReactions.filter(reaction => reaction.post_id === postId);
}

function commentsForPost(postId) {
  return state.feedComments.filter(comment => comment.post_id === postId);
}

function updateDiscoveryFilters(event) {
  const activeField = event.target?.name;
  const form = new FormData(event.currentTarget);
  state.discoveryFilters = {
    search: String(form.get("search") || ""),
    game: String(form.get("game") || ""),
    platform: String(form.get("platform") || ""),
    style: String(form.get("style") || "")
  };
  state.discoveryIndex = 0;
  renderShell();
  if (activeField === "search") {
    window.setTimeout(() => document.querySelector("[data-discovery-filters] [name='search']")?.focus(), 0);
  }
}

function updateLfgFilters(event) {
  const activeField = event.target?.name;
  const form = new FormData(event.currentTarget);
  state.lfgFilters = {
    search: String(form.get("search") || ""),
    status: String(form.get("status") || "all")
  };
  renderShell();
  if (activeField === "search") {
    window.setTimeout(() => document.querySelector("[data-lfg-filters] [name='search']")?.focus(), 0);
  }
}

function switchDiscoveryTab(tab) {
  state.discoveryTab = tab || "lfg";
  renderShell();
}

function startDiscovery() {
  state.discoveryStarted = true;
  state.discoveryIndex = 0;
  renderShell();
}

function passDiscoveryProfile() {
  const players = discoveryPlayers();
  const current = players[state.discoveryIndex || 0];
  if (current && !state.discoveryPassed.includes(current.id)) {
    state.discoveryPassed = [...state.discoveryPassed, current.id].slice(-50);
  }
  state.discoveryIndex = Math.min(state.discoveryIndex || 0, Math.max(players.length - 2, 0));
  renderShell();
}

async function createLfgPost(event) {
  event.preventDefault();
  if (!state.profile) return alert("Sign in first.");
  if (state.lfgBusy) return;
  const form = new FormData(event.currentTarget);
  state.lfgBusy = "create";
  renderShell();
  const payload = {
    profile_id: state.profile.id,
    game_id: form.get("game_id") || null,
    title: String(form.get("title") || "").trim(),
    mode: String(form.get("mode") || "Any mode").trim() || "Any mode",
    rank_range: String(form.get("rank_range") || "").trim(),
    party_size: String(form.get("party_size") || "").trim(),
    starts_at: String(form.get("starts_at") || "").trim(),
    status: "open"
  };
  const { error } = await state.supabase.from("lfg_posts").insert(payload);
  state.lfgBusy = "";
  if (error) {
    renderShell();
    return alert(`${error.message}\n\nIf this mentions lfg_posts, make sure the Supabase LFG migrations through 0018 have been run.`);
  }
  state.discoveryTab = "posts";
  await loadData();
}

async function requestLfgJoin(postId) {
  if (!state.profile) return alert("Sign in first.");
  const post = state.lfg.find(item => item.id === postId);
  if (!post) return alert("That LFG post could not be found.");
  if (post.profile_id === state.profile.id) return alert("This is your LFG post.");
  if (lfgStatus(post) !== "open") return alert("This LFG post is not accepting requests.");
  if (myLfgRequest(postId)) return alert("You already requested to join this LFG.");
  if (state.lfgBusy) return;
  state.lfgBusy = postId;
  renderShell();
  const { error } = await state.supabase.from("lfg_join_requests").insert({
    lfg_post_id: postId,
    requester_profile_id: state.profile.id,
    status: "pending"
  });
  state.lfgBusy = "";
  if (error) {
    renderShell();
    return alert(error.message);
  }
  await loadData();
}

async function acceptLfgRequest(requestId) {
  if (!state.profile || state.lfgBusy) return;
  state.lfgBusy = requestId;
  renderShell();
  const { data, error } = await state.supabase.rpc("accept_lfg_join_request", { request_id: requestId });
  state.lfgBusy = "";
  if (error) {
    renderShell();
    return alert(`${error.message}\n\nIf this mentions accept_lfg_join_request, run the LFG migrations in Supabase.`);
  }
  if (data) {
    state.selectedConversationId = data;
    state.tab = "messages";
    state.messagesMobileView = "chat";
  }
  await loadData();
}

async function rejectLfgRequest(requestId) {
  if (!state.profile || state.lfgBusy) return;
  state.lfgBusy = requestId;
  renderShell();
  const { error } = await state.supabase
    .from("lfg_join_requests")
    .update({ status: "rejected" })
    .eq("id", requestId);
  state.lfgBusy = "";
  if (error) {
    renderShell();
    return alert(error.message);
  }
  await loadData();
}

async function closeLfgPost(postId) {
  if (!state.profile) return alert("Sign in first.");
  const post = state.lfg.find(item => item.id === postId);
  if (!post || post.profile_id !== state.profile.id) return alert("Only the LFG owner can close this post.");
  if (state.lfgBusy) return;
  if (!confirm("Close this LFG post? Players will not be able to send new join requests.")) return;
  state.lfgBusy = postId;
  renderShell();
  const { error } = await state.supabase.rpc("close_lfg_post", { post_id: postId });
  state.lfgBusy = "";
  if (error) {
    renderShell();
    return alert(`${error.message}\n\nIf this mentions close_lfg_post, run supabase/migrations/0017_lfg_lifecycle.sql in Supabase.`);
  }
  await loadData();
}

function discoveryPlayers() {
  const filters = state.discoveryFilters;
  return state.profiles
    .filter(profile => profile.id !== state.profile?.id && !isBlocked(profile.id))
    .filter(profile => !state.discoveryPassed.includes(profile.id))
    .filter(profile => discoveryProfileMatches(profile, filters))
    .sort((left, right) => discoveryScore(right).score - discoveryScore(left).score);
}

function discoveryProfileMatches(profile, filters) {
  const query = filters.search.trim().toLowerCase();
  const games = list(profile.top_games);
  const platforms = list(profile.platforms);
  const styles = list(profile.play_style);
  if (query) {
    const haystack = [profile.handle, profile.display_name, profile.bio, profile.region, ...games.map(gameLabel), ...platforms, ...styles]
      .join(" ")
      .toLowerCase();
    if (!haystack.includes(query)) return false;
  }
  if (filters.game && !games.some(game => normalizeGameLookup(game) === normalizeGameLookup(filters.game))) return false;
  if (filters.platform && !platforms.includes(filters.platform)) return false;
  if (filters.style && !styles.includes(filters.style)) return false;
  return true;
}

function discoveryScore(profile) {
  const mine = state.profile || {};
  const profileGames = list(profile.top_games).map(normalizeGameLookup);
  const myGames = list(mine.top_games).map(normalizeGameLookup);
  const profilePlatforms = list(profile.platforms);
  const myPlatforms = list(mine.platforms);
  const profileStyles = list(profile.play_style);
  const myStyles = list(mine.play_style);
  const reasons = [];
  let score = 48;
  const sharedGames = profileGames.filter(game => myGames.includes(game));
  const sharedPlatforms = profilePlatforms.filter(platform => myPlatforms.includes(platform));
  const sharedStyles = profileStyles.filter(style => myStyles.includes(style));
  if (sharedGames.length) {
    score += 22;
    reasons.push(`${sharedGames.length} shared game(s)`);
  }
  if (sharedPlatforms.length) {
    score += 12;
    reasons.push(`${sharedPlatforms.join(", ")}`);
  }
  if (sharedStyles.length) {
    score += 10;
    reasons.push(`${sharedStyles.slice(0, 2).join(", ")}`);
  }
  if (profile.region && mine.region && profile.region === mine.region) {
    score += 8;
    reasons.push("Same region");
  }
  if (profile.timezone && mine.timezone && profile.timezone === mine.timezone) {
    score += 5;
    reasons.push("Good schedule fit");
  }
  if (!reasons.length) reasons.push("New player to check out");
  return { score: Math.min(score, 98), reasons: reasons.slice(0, 4) };
}

function discoveryGameOptions() {
  const seen = new Set();
  return state.profiles.flatMap(profile => list(profile.top_games)).map(value => ({
    value,
    label: gameLabel(value)
  })).filter(option => {
    const key = normalizeGameLookup(option.value);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).sort((left, right) => left.label.localeCompare(right.label));
}

function lfgGameOptions() {
  const fromGames = state.games.map(game => ({ id: game.id, name: game.name }));
  const fromProfile = list(state.profile?.top_games).map(value => gameForProfileValue(value)).filter(Boolean);
  const fromPopular = popularGames.map(value => gameForProfileValue(value)).filter(Boolean);
  const seen = new Set();
  return [...fromProfile, ...fromGames, ...fromPopular].filter(game => {
    if (!game?.id || seen.has(game.id)) return false;
    seen.add(game.id);
    return true;
  }).sort((left, right) => left.name.localeCompare(right.name));
}

function filteredLfgPosts() {
  const query = state.lfgFilters.search.trim().toLowerCase();
  const status = state.lfgFilters.status || "all";
  return state.lfg.filter(post => {
    if (status !== "all" && lfgStatus(post) !== status) return false;
    if (!query) return true;
    const owner = profileFor(post.profile_id);
    const haystack = [
      post.title,
      post.mode,
      post.rank_range,
      post.party_size,
      post.starts_at,
      post.status,
      lfgGameLabel(post),
      owner?.handle,
      owner?.display_name
    ].join(" ").toLowerCase();
    return haystack.includes(query);
  });
}

function lfgStatus(post) {
  return ["open", "filled", "closed"].includes(post?.status) ? post.status : "open";
}

function lfgGameLabel(post) {
  return post?.game_id ? gameLabel(post.game_id) : "Any game";
}

function myLfgRequest(postId) {
  if (!state.profile) return null;
  return state.lfgJoinRequests.find(request =>
    request.lfg_post_id === postId &&
    request.requester_profile_id === state.profile.id
  ) || null;
}

function lfgPendingRequests(postId) {
  return state.lfgJoinRequests.filter(request => request.lfg_post_id === postId && request.status === "pending");
}

function profileFeedGameOptions() {
  const selectedGames = list(state.profile?.top_games);
  const seen = new Set();
  return selectedGames.map(value => gameForProfileValue(value)).filter(Boolean).filter(game => {
    if (seen.has(game.id)) return false;
    seen.add(game.id);
    return true;
  });
}

function gameForProfileValue(value) {
  const raw = String(value || "").trim();
  if (!raw) return null;
  const rawKey = normalizeGameLookup(raw);
  const existing = state.games.find(game => game.id === raw || normalizeGameLookup(game.id) === rawKey || normalizeGameLookup(game.name) === rawKey);
  return existing || { id: rawKey, name: gameLabel(raw) };
}

function normalizeGameLookup(value) {
  return String(value || "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function gameSlug(value) {
  return normalizeGameLookup(value) || "default";
}

function visibleConversations() {
  const seenDirect = new Set();
  return state.conversations.filter(conversation => {
    if (isConversationBlocked(conversation.id)) return false;
    if (conversation.conversation_type !== "direct") return true;
    const otherId = conversationOtherProfileId(conversation);
    if (!otherId) return true;
    if (seenDirect.has(otherId)) return false;
    seenDirect.add(otherId);
    return true;
  });
}

function selectedConversationFrom(conversations) {
  if (!conversations.length) return null;
  return conversations.find(conversation => conversation.id === state.selectedConversationId) || conversations[0];
}

function selectedConversationProfile(conversation) {
  const otherId = conversationOtherProfileId(conversation);
  return otherId ? profileFor(otherId) : null;
}

function conversationParticipants(conversation) {
  return (conversation.conversation_participants || [])
    .map(participant => profileFor(participant.profile_id))
    .filter(Boolean);
}

function renderAvatar(profile, fallback = "GC") {
  if (profile?.avatar_url) return `<img src="${escapeAttribute(profile.avatar_url)}" alt="">`;
  return `<span>${escapeHtml(initials(profile?.display_name || profile?.handle || fallback))}</span>`;
}

function lastMessagePreview(message) {
  if (!message) return "";
  const mine = message.sender_profile_id === state.profile?.id;
  return `${mine ? "You: " : ""}${message.body || ""}`;
}

function conversationTitle(conversation) {
  if (conversation.conversation_type === "direct") {
    const otherId = conversationOtherProfileId(conversation);
    const other = profileFor(otherId);
    if (other) return other.handle;
  }
  return conversation.title || "Chat";
}

function otherProfileForConnection(connection) {
  if (!state.profile) return null;
  const otherId = connection.from_profile_id === state.profile.id ? connection.to_profile_id : connection.from_profile_id;
  return profileFor(otherId);
}

function blockedProfiles() {
  return state.blocks.map(block => profileFor(block.blocked_profile_id)).filter(Boolean);
}

function isBlocked(profileId) {
  return state.blocks.some(block => block.blocked_profile_id === profileId);
}

function isConversationBlocked(conversationId) {
  const conversation = state.conversations.find(item => item.id === conversationId);
  if (!conversation || conversation.conversation_type !== "direct") return false;
  const otherId = conversationOtherProfileId(conversation);
  return otherId ? isBlocked(otherId) : false;
}

function conversationOtherProfileId(conversation) {
  if (!state.profile) return "";
  const participant = (conversation.conversation_participants || []).find(item => item.profile_id !== state.profile.id);
  return participant?.profile_id || "";
}

function formatShortDate(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

function formatRelativeTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const diffMs = Date.now() - date.getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "now";
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  return `${days}d`;
}

function directConversationWith(profileId) {
  if (!state.profile) return null;
  return state.conversations.find(conversation => {
    if (conversation.conversation_type !== "direct") return false;
    const participants = conversation.conversation_participants || [];
    return participants.some(participant => participant.profile_id === state.profile.id) &&
      participants.some(participant => participant.profile_id === profileId);
  }) || null;
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
