const tabs = [
  ["events", "Events/Servers"],
  ["discovery", "Discovery/LFG"],
  ["feed", "Feed"],
  ["messages", "Messages"],
  ["profile", "Profile"]
];

const state = {
  tab: "profile",
  authMode: "signup",
  authMessage: "",
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
  }
}

async function loadData() {
  if (!state.supabase) {
    renderShell();
    return;
  }
  await ensureProfile();
  const [profiles, games, feed, lfg, squads] = await Promise.all([
    read("profiles", "id, handle, display_name, rank, region, platforms, top_games, play_style, bio, avatar_url, online, created_at"),
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
      <p>Add <code>NEXT_PUBLIC_SUPABASE_URL</code> and <code>NEXT_PUBLIC_SUPABASE_ANON_KEY</code>, then redeploy.</p>
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
  return page("Profile", "Sign in or create your secure Gamer Connect account.", `
    ${state.session ? renderSignedInProfile() : renderAuthForms()}
  `);
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
  return `
    <div class="card profile-card">
      <h3>${escapeHtml(state.profile?.handle || state.session.user.email)}</h3>
      <p>${escapeHtml(state.profile?.bio || "Update your profile soon.")}</p>
      <span class="pill hot">${escapeHtml(state.profile?.rank || "Unranked")}</span>
      <button class="button red" data-sign-out>Sign Out</button>
    </div>
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
  document.querySelector("[data-create-post]")?.addEventListener("submit", createPost);
  document.querySelectorAll("[data-like]").forEach(button => button.addEventListener("click", () => likePost(button.dataset.like)));
  document.querySelectorAll("[data-connect]").forEach(button => button.addEventListener("click", () => connectToPlayer(button.dataset.connect)));
  document.querySelectorAll("[data-new-chat]").forEach(button => button.addEventListener("click", () => startChat(button.dataset.newChat)));
  document.querySelectorAll("[data-send-message]").forEach(form => form.addEventListener("submit", sendMessage));
}

async function signIn(event) {
  event.preventDefault();
  state.authMessage = "";
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
  await loadData();
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

function cleanHandle(value) {
  return String(value || "Player").replace(/[^a-z0-9_]/gi, "").slice(0, 18) || "Player";
}

function numberOrNull(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}
