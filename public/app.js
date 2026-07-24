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
const feedMediaBucket = "feed-media";
const maxFeedImageBytes = 5 * 1024 * 1024;
const maxFeedVideoBytes = 50 * 1024 * 1024;
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
    const [profiles, games, feed, lfg, squads, feedReactions, feedComments] = await Promise.all([
      read("profiles", "id, handle, display_name, age, region, timezone, platforms, top_games, rank, play_style, availability, bio, avatar_url, online, stats, created_at"),
      read("games", "*"),
      read("feed_posts", "*", { order: "created_at" }),
      read("lfg_posts", "*", { order: "created_at" }),
      read("squads", "*", { order: "created_at" }),
      read("feed_reactions", "*"),
      read("feed_comments", "*", { order: "created_at", ascending: true })
    ]);
    state.profiles = profiles || [];
    state.games = games || [];
    state.feed = feed || [];
    state.lfg = lfg || [];
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
    <div class="feed-layout">
      <section class="feed-main">
        ${renderComposer()}
        <div class="feed-list">
          ${state.feed.length ? state.feed.map(renderFeedPost).join("") : `<div class="empty">No posts yet. Sign in and create the first clip or squad update.</div>`}
        </div>
      </section>
      <aside class="feed-side">
        <div class="card">
          <h3>Trending Games</h3>
          ${popularGames.slice(0, 6).map(game => `<span class="pill">${escapeHtml(game)}</span>`).join("")}
        </div>
        <div class="card">
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
  return `
    <form class="card composer feed-composer" data-create-post>
      <div class="composer-head">
        <span class="chat-avatar">${renderAvatar(state.profile, state.profile?.handle)}</span>
        <div>
          <h3>Share With The Feed</h3>
          <p>Post clips, squad updates, and game moments.</p>
        </div>
      </div>
      <input class="field" name="title" placeholder="Give it a title" required>
      <textarea name="body" placeholder="What happened? Share a highlight, squad update, or callout..." required></textarea>
      <label class="upload-field">
        <span>Upload Image Or Clip</span>
        <input name="media_file" type="file" accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime">
        <small>Images up to 5 MB. Clips up to 50 MB.</small>
      </label>
      <div class="composer-controls">
        <select class="field" name="post_type">
          <option value="post">Post</option>
          <option value="clip">Clip</option>
          <option value="event">Event</option>
        </select>
        <select class="field" name="game_id">
          <option value="">No game</option>
          ${state.games.map(game => `<option value="${escapeAttribute(game.id)}">${escapeHtml(game.name)}</option>`).join("")}
        </select>
        <button class="button green" type="submit">Publish</button>
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
  return `
    <article class="card feed-card" id="feed-${escapeAttribute(post.id)}">
      <div class="feed-author">
        <span class="chat-avatar">${renderAvatar(author, author?.handle || "Player")}</span>
        <div>
          <strong>${escapeHtml(author?.handle || "Player")}</strong>
          <p>${game ? `${escapeHtml(game.name)} - ` : ""}${escapeHtml(formatRelativeTime(post.created_at))}</p>
        </div>
        <span class="pill hot">${escapeHtml(post.post_type || "post")}</span>
      </div>
      <h3>${escapeHtml(post.title)}</h3>
      <p>${escapeHtml(post.body)}</p>
      ${post.media_url ? renderFeedMedia(post, isClip) : ""}
      <div class="feed-metrics">
        <span>${reactions.length} like(s)</span>
        <span>${comments.length} comment(s)</span>
      </div>
      <div class="feed-actions">
        <button class="button ${liked ? "purple" : "dark"}" data-like="${post.id}">${liked ? "Liked" : "Like"}</button>
        <button class="button dark" data-comment-jump="${post.id}">Comment</button>
        <button class="button dark" data-share="${post.id}">Share</button>
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
  const players = state.profiles.filter(profile => profile.id !== state.profile?.id && !isBlocked(profile.id));
  const incoming = pendingIncomingConnections();
  const outgoing = pendingOutgoingConnections();
  return page("Discovery/LFG", "Find players and parties already forming.", `
    <div class="grid two">
      <div>
        ${players.length ? players.map(renderPlayerCard).join("") : `<div class="empty">No other players yet.</div>`}
      </div>
      <div class="grid">
        <div class="card">
          <h3>Connection Requests</h3>
          ${incoming.length ? incoming.map(renderIncomingConnection).join("") : `<p>No incoming requests.</p>`}
          ${outgoing.length ? `<p class="muted">${outgoing.length} sent request(s) waiting for approval.</p>` : ""}
        </div>
        <div class="card">
          <h3>Looking For Group</h3>
          ${state.lfg.length ? state.lfg.map(post => `<p><strong>${escapeHtml(post.title)}</strong><br>${escapeHtml(post.mode || "")} ${escapeHtml(post.starts_at || "")}</p>`).join("") : `<p>No LFG posts yet.</p>`}
        </div>
      </div>
    </div>
  `);
}

function renderPlayerCard(profile) {
  const connection = connectionWith(profile.id);
  const actions = renderConnectionActions(profile, connection);
  return `
    <div class="card">
      <span class="pill hot">${escapeHtml(profile.rank || "Unranked")}</span>
      <h3>${escapeHtml(profile.handle)}</h3>
      <p>${escapeHtml(profile.region || "Unknown")} - ${(profile.platforms || []).join(", ")}</p>
      <p>${escapeHtml(profile.bio || "No bio yet.")}</p>
      ${connection ? `<p class="muted">${escapeHtml(connectionSummary(connection, profile.id))}</p>` : ""}
      <div class="btn-row">${actions}</div>
    </div>
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
  const profilePanelVisible = Boolean(state.profilePanelOpen && selectedProfile);
  return page("Messages", "Matched players, direct chats, and group conversations.", `
    ${state.session ? `
      <div class="message-page-wrap">
        <div class="messages-toolbar">
          <button class="button" type="button" data-focus-new-chat>${state.newChatOpen ? "Close New Chat" : "New Chat"}</button>
        </div>
        ${state.newChatOpen ? renderNewChatPopover(accepted) : ""}
        <div class="messages-shell ${profilePanelVisible ? "profile-open" : ""}">
          <aside class="messages-side">
            <div class="messages-list-panel">
              <div class="messages-list-head">
                <h3>Messages</h3>
                <button class="icon-button" title="New chat" data-focus-new-chat>+</button>
              </div>
              <label class="message-search">
                <span>Search</span>
                <input placeholder="Search messages" disabled>
              </label>
              <div class="message-tabs">
                <button class="active" type="button">All</button>
                <button type="button">Unread</button>
                <button type="button">Groups</button>
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
            ${selectedConversation ? renderChatThread(selectedConversation) : `<div class="empty">Create a chat or select a conversation.</div>`}
          </section>
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

function renderNewChatPopover(acceptedConnectionsList) {
  return `
    <div class="new-chat-popover">
      <div class="section-head">
        <div>
          <h3>New Chat</h3>
          <p>${acceptedConnectionsList.length} connected player(s)</p>
        </div>
        <button class="icon-button" type="button" title="Close new chat" data-close-new-chat>x</button>
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
          <span>+</span>
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
        <span>Suggested</span>
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
      <button class="button purple" type="submit">Chat</button>
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
          <span class="chat-avatar lg">${renderAvatar(other || participants[0], title)}</span>
          <div>
            <h3>${escapeHtml(title)}</h3>
            <p>${other ? `${escapeHtml(other.online ? "Online" : "Online soon")} - ${escapeHtml(other.rank || "Unranked")}` : `${participants.length} participant(s)`}</p>
          </div>
        </button>
        <div class="chat-actions">
          <button class="icon-button" title="Video" type="button">Vid</button>
          <button class="icon-button" title="Call" type="button">Call</button>
          <button class="icon-button" title="Info" type="button" data-toggle-chat-profile>Info</button>
          <button class="icon-button danger" title="Delete conversation" type="button" data-delete-conversation="${conversation.id}">Del</button>
        </div>
      </div>
      <div class="message-list">
        <div class="day-divider">Today</div>
        ${messages.length ? messages.map(renderMessageBubble).join("") : `<div class="empty">No messages yet. Send the first one.</div>`}
      </div>
      <form class="message-compose" data-send-message="${conversation.id}">
        <button class="icon-button compose-plus" type="button">+</button>
        <input class="field" name="body" placeholder="Write a message..." autocomplete="off" required>
        <button class="icon-button" type="button">GIF</button>
        <button class="button green" type="submit">Send</button>
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
      <button class="icon-button profile-panel-close" title="Close profile" type="button" data-toggle-chat-profile>x</button>
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
  if (isVideo && file.size > maxFeedVideoBytes) return { url: null, mediaType: null, error: "Clips need to be 50 MB or smaller for now." };

  const extension = file.name.includes(".") ? file.name.split(".").pop().toLowerCase().replace(/[^a-z0-9]/g, "") : (isVideo ? "mp4" : "jpg");
  const uniquePart = window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const path = `${state.profile.id}/${Date.now()}-${uniquePart}.${extension}`;
  const { error } = await state.supabase.storage
    .from(feedMediaBucket)
    .upload(path, file, {
      cacheControl: "3600",
      contentType: file.type,
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
  renderShell();
}

function toggleChatProfile() {
  state.profilePanelOpen = !state.profilePanelOpen;
  renderShell();
}

function toggleNewChat() {
  state.newChatOpen = !state.newChatOpen;
  if (state.newChatOpen) state.profilePanelOpen = false;
  renderShell();
  focusNewChatInput();
}

function closeNewChat() {
  state.newChatOpen = false;
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
