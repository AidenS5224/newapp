package com.gamerconnect.testclient;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(5, 9, 18);
    private static final int SURFACE = Color.rgb(10, 17, 29);
    private static final int PANEL = Color.rgb(14, 24, 40);
    private static final int PANEL_ALT = Color.rgb(18, 30, 49);
    private static final int LINE = Color.rgb(38, 50, 68);
    private static final int TEXT = Color.rgb(239, 243, 255);
    private static final int MUTED = Color.rgb(159, 170, 190);
    private static final int PURPLE = Color.rgb(161, 89, 255);
    private static final int GREEN = Color.rgb(52, 211, 115);
    private static final int RED = Color.rgb(251, 113, 133);
    private static final int GOLD = Color.rgb(245, 158, 11);

    private EditText apiInput;
    private EditText loginInput;
    private EditText passwordInput;
    private TextView statusText;
    private TextView profileText;
    private LinearLayout navBar;
    private LinearLayout content;
    private String apiBase = "http://10.0.2.2:8080";
    private String sessionToken = "";
    private String loggedInHandle = "Guest";
    private String currentTab = "Feed";
    private boolean discoveryStarted = false;
    private int discoveryIndex = 0;
    private String selectedConversationId = "";
    private JSONObject latestHealth;
    private JSONArray latestPlayers;
    private JSONArray latestPosts;
    private JSONArray latestSquads;
    private JSONArray latestFeedPosts;
    private JSONArray latestConversations;
    private JSONArray latestConversationMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(3, 7, 15));
        getWindow().setNavigationBarColor(Color.rgb(3, 7, 15));
        setContentView(buildLayout());
        loadAll();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        applySystemInsets(root);

        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(88));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(buildBottomNav());
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(12));
        header.setBackgroundColor(Color.rgb(3, 7, 15));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = badge("GC", PURPLE, dp(38));
        titleRow.addView(logo);

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setPadding(dp(10), 0, 0, 0);
        titleStack.addView(text("Gamer Connect", 26, TEXT, true));
        titleStack.addView(text("Find your squad. Play together.", 13, MUTED, false));
        titleRow.addView(titleStack, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        statusText = text("Checking backend...", 12, MUTED, false);
        titleRow.addView(statusText);
        header.addView(titleRow);

        apiInput = input("Backend URL", apiBase, false);
        loginInput = input("Email or handle", "NovaPulse", false);
        passwordInput = input("Password", "testpass123", true);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.addView(button("Refresh", PURPLE, v -> loadAll()), weighted());
        header.addView(actionRow, matchWrap());

        profileText = text("Guest mode: protected handles are hidden", 12, MUTED, false);
        profileText.setPadding(0, dp(8), 0, 0);
        header.addView(profileText);
        return header;
    }

    private View buildBottomNav() {
        navBar = new LinearLayout(this);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        navBar.setBackgroundColor(Color.rgb(3, 7, 15));
        renderBottomNav();
        return navBar;
    }

    private void renderBottomNav() {
        navBar.removeAllViews();
        addNavItem("Events/Servers");
        addNavItem("Discovery/LFG");
        addNavItem("Feed");
        addNavItem("Messages");
        addNavItem("Profile");
    }

    private void addNavItem(String label) {
        boolean active = label.equals(currentTab);
        TextView item = navItem(label, active);
        item.setOnClickListener(v -> {
            currentTab = label;
            renderBottomNav();
            renderCurrentView();
        });
        navBar.addView(item, weighted());
    }

    private TextView navItem(String label, boolean active) {
        TextView item = text(label, 12, active ? PURPLE : MUTED, true);
        item.setGravity(Gravity.CENTER);
        item.setText(active ? "* " + label : label);
        return item;
    }

    private void applySystemInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.post(root::requestApplyInsets);
    }

    private void login() {
        apiBase = apiInput.getText().toString().trim();
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("emailOrHandle", loginInput.getText().toString().trim());
                payload.put("password", passwordInput.getText().toString());
                JSONObject response = postJson("/api/auth/login", payload, "");
                sessionToken = response.optString("token");
                JSONObject user = response.optJSONObject("user");
                loggedInHandle = user == null ? "Player" : user.optString("handle", "Player");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Logged in as " + loggedInHandle, Toast.LENGTH_SHORT).show();
                    currentTab = "Profile";
                    renderBottomNav();
                    loadAll();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Login failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void signup() {
        apiBase = apiInput.getText().toString().trim();
        new Thread(() -> {
            try {
                String login = loginInput.getText().toString().trim();
                String email = login.contains("@") ? login : login.toLowerCase() + "@example.local";
                String handle = login.contains("@") ? login.substring(0, login.indexOf("@")) : login;
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("handle", handle);
                payload.put("password", passwordInput.getText().toString());
                payload.put("region", "Australia");
                payload.put("timezone", "AEST");
                payload.put("rank", "Unranked");
                payload.put("bio", "Testing Gamer Connect from Android.");
                payload.put("platforms", new JSONArray().put("PC"));
                payload.put("topGames", new JSONArray().put("apex-legends"));
                payload.put("playStyle", new JSONArray().put("Good Comms").put("Team Player"));
                payload.put("linkedAccounts", new JSONObject().put("discord", handle + "#0001"));
                payload.put("infoStacks", new JSONArray().put(new JSONObject()
                        .put("label", "Discord")
                        .put("value", handle + "#0001")
                        .put("private", true)));
                JSONObject response = postJson("/api/auth/signup", payload, "");
                sessionToken = response.optString("token");
                loggedInHandle = handle;
                runOnUiThread(() -> {
                    Toast.makeText(this, "Signed up as " + loggedInHandle, Toast.LENGTH_SHORT).show();
                    currentTab = "Profile";
                    renderBottomNav();
                    loadAll();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Sign up failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void loadAll() {
        apiBase = apiInput.getText().toString().trim();
        content.removeAllViews();
        content.addView(loadingCard("Discovering players near your game..."));

        new Thread(() -> {
            try {
                JSONObject health = getJson("/api/health");
                JSONObject players = getJson("/api/players");
                JSONObject lfg = getJson("/api/lfg");
                JSONObject squads = getJson("/api/squads");
                JSONObject feed = getJson("/api/feed");
                latestHealth = health;
                latestPlayers = players.optJSONArray("players");
                latestPosts = lfg.optJSONArray("posts");
                latestSquads = squads.optJSONArray("squads");
                latestFeedPosts = feed.optJSONArray("posts");
                if (!sessionToken.isEmpty()) {
                    try {
                        JSONObject conversations = getJson("/api/conversations");
                        latestConversations = conversations.optJSONArray("conversations");
                        if ((selectedConversationId == null || selectedConversationId.isEmpty()) && latestConversations != null && latestConversations.length() > 0) {
                            selectedConversationId = latestConversations.optJSONObject(0).optString("id");
                        }
                        if (selectedConversationId != null && !selectedConversationId.isEmpty()) {
                            JSONObject messages = getJson("/api/conversations/" + selectedConversationId + "/messages");
                            latestConversationMessages = messages.optJSONArray("messages");
                        }
                    } catch (Exception ignored) {
                        latestConversations = new JSONArray();
                        latestConversationMessages = new JSONArray();
                    }
                } else {
                    latestConversations = new JSONArray();
                    latestConversationMessages = new JSONArray();
                }
                runOnUiThread(this::renderCurrentView);
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    statusText.setText("Offline");
                    statusText.setTextColor(RED);
                    content.removeAllViews();
                    content.addView(messageCard("Connection failed", ex.getMessage(), RED));
                });
            }
        }).start();
    }

    private void renderCurrentView() {
        JSONObject health = latestHealth == null ? new JSONObject() : latestHealth;
        statusText.setText(health.optBoolean("ok") ? "Online" : "Offline");
        statusText.setTextColor(health.optBoolean("ok") ? GREEN : RED);
        profileText.setText(sessionToken.isEmpty()
                ? "Guest mode: protected handles are hidden"
                : "Logged in as " + loggedInHandle + " - protected info unlocks on approved connections");

        content.removeAllViews();
        if ("Messages".equals(currentTab)) {
            renderMessages();
            return;
        }
        if ("Profile".equals(currentTab)) {
            renderProfile();
            return;
        }
        if ("Events/Servers".equals(currentTab)) {
            renderEventsServers();
            return;
        }
        if ("Discovery/LFG".equals(currentTab)) {
            renderDiscoveryLfg();
            return;
        }
        renderFeed();
    }

    private void renderDiscoveryLfg() {
        JSONArray players = latestPlayers;
        content.addView(discoveryTabs());
        if (players == null || players.length() == 0) {
            content.addView(messageCard("No Matches Found", "Adjust your filters or refresh discovery.", MUTED));
            return;
        }
        if (!discoveryStarted) {
            content.addView(discoveryStartCard(players.length()));
            content.addView(connectionRequestCard());
            return;
        }
        int safeIndex = Math.max(0, Math.min(discoveryIndex, players.length() - 1));
        JSONObject featured = players.optJSONObject(safeIndex);
        if (featured != null) {
            content.addView(buildHero(safeIndex + 1, players.length()));
            content.addView(filterChips());
            content.addView(playerCard(featured, safeIndex));
            content.addView(connectionRequestCard());
        }
    }

    private View discoveryTabs() {
        LinearLayout card = panel(PANEL, dp(6), dp(12));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(chip("Looking For Group", true), weighted());
        card.addView(chip("Matches", false), weighted());
        card.addView(chip("My Posts", false), weighted());
        return card;
    }

    private View discoveryStartCard(int count) {
        LinearLayout card = panel(PANEL, dp(22), dp(14));
        card.setGravity(Gravity.CENTER);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setMinimumHeight(dp(460));
        card.addView(badge("GC", PURPLE, dp(76)));
        card.addView(centerText("Find players to game with", 22, TEXT, true));
        card.addView(centerText("Swipe right to play, left to pass.", 14, MUTED, false));
        card.addView(button(count > 0 ? "Start Discovery" : "No Players Yet", PURPLE, v -> {
            if (count <= 0) return;
            discoveryStarted = true;
            discoveryIndex = 0;
            renderCurrentView();
        }), matchWrap());
        return card;
    }

    private View connectionRequestCard() {
        return messageCard("Connection Requests", "Incoming requests will live here while Messages stays focused on chats.", PURPLE);
    }

    private void renderFeed() {
        content.addView(section("Feed", "Clips, posts, highlights, and squad updates"));
        content.addView(feedComposer());
        if (latestFeedPosts != null && latestFeedPosts.length() > 0) {
            for (int i = 0; i < latestFeedPosts.length(); i++) {
                JSONObject post = latestFeedPosts.optJSONObject(i);
                if (post != null) content.addView(feedPost(post));
            }
            return;
        }
        content.addView(messageCard("No feed posts yet", "Posts from /api/feed will show here. Use the web app or backend API to create the first one.", MUTED));
    }

    private void renderMessages() {
        content.addView(section("Messages", "Chats from matched players and groups"));
        if (sessionToken.isEmpty()) {
            content.addView(messageCard("Sign in to test chat", "Open Profile, log in, then conversations from /api/conversations will appear here.", PURPLE));
            return;
        }

        LinearLayout toolbar = panel(PANEL, dp(12), dp(12));
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.addView(button("New Test Chat", PURPLE, v -> createTestConversation()), weighted());
        toolbar.addView(button("Refresh", PANEL_ALT, v -> loadAll()), weighted());
        content.addView(toolbar);

        if (latestConversations == null || latestConversations.length() == 0) {
            content.addView(messageCard("No conversations yet", "Tap New Test Chat to create one with the first available player.", MUTED));
            return;
        }

        content.addView(section("Chats", "Tap a conversation to open it"));
        for (int i = 0; i < latestConversations.length(); i++) {
            JSONObject conversation = latestConversations.optJSONObject(i);
            if (conversation != null) content.addView(conversationCard(conversation));
        }

        JSONObject selected = selectedConversation();
        if (selected == null) return;
        content.addView(section(selected.optString("title", "Chat"), "Live backend message thread"));
        for (int i = 0; latestConversationMessages != null && i < latestConversationMessages.length(); i++) {
            JSONObject message = latestConversationMessages.optJSONObject(i);
            if (message != null) content.addView(messageBubble(message));
        }
        content.addView(messageComposer(selected.optString("id")));
    }

    private void renderProfile() {
        content.addView(section("Profile", "Account, sign in, and local backend setup"));

        LinearLayout card = panel(PANEL, dp(16), dp(14));
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(text(sessionToken.isEmpty() ? "Sign In" : "Signed In", 20, TEXT, true));
        card.addView(text(sessionToken.isEmpty()
                ? "Use a seeded account or create a local test account."
                : "You are signed in as " + loggedInHandle + ".", 13, MUTED, false));
        detach(apiInput);
        detach(loginInput);
        detach(passwordInput);
        card.addView(apiInput);
        LinearLayout authRow = new LinearLayout(this);
        authRow.setOrientation(LinearLayout.HORIZONTAL);
        authRow.addView(loginInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        authRow.addView(passwordInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(authRow, matchWrap());
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Login", GREEN, v -> login()), weighted());
        actions.addView(button("Sign Up", PURPLE, v -> signup()), weighted());
        actions.addView(button("Refresh", PANEL_ALT, v -> loadAll()), weighted());
        card.addView(actions);
        content.addView(card);

        content.addView(messageCard(
                "Protected Info",
                "Discord, platform handles, Tracker Network links, and contact details stay hidden until a connection is approved.",
                GREEN
        ));
        content.addView(messageCard("Seed Login", "Handle: NovaPulse\nPassword: testpass123", PURPLE));
    }

    private void renderEventsServers() {
        content.addView(section("Events/Servers", "Sessions, communities, and game hubs"));
        LinearLayout card = panel(PANEL, dp(22), dp(14));
        card.setGravity(Gravity.CENTER);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setMinimumHeight(dp(460));
        card.addView(badge("GC", PURPLE, dp(76)));
        card.addView(chip("Coming Soon", true));
        card.addView(centerText("Events and servers are on the way", 24, TEXT, true));
        card.addView(centerText("Community servers, game nights, squad events, and server discovery will live here.", 14, MUTED, false));
        content.addView(card);
    }

    private View chatCard(String title, String body, int accent) {
        LinearLayout card = panel(PANEL, dp(14), dp(12));
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(avatar(title, title.length()), new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(title, 17, TEXT, true));
        copy.addView(text(body, 13, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(button("Chat", accent, v -> toast("Chat composer coming next")), new LinearLayout.LayoutParams(dp(88), dp(44)));
        card.addView(row);
        return card;
    }

    private View conversationCard(JSONObject conversation) {
        boolean active = conversation.optString("id").equals(selectedConversationId);
        LinearLayout card = panel(active ? Color.rgb(42, 32, 84) : PANEL, dp(12), dp(10));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setOnClickListener(v -> openConversation(conversation.optString("id")));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(avatar(conversation.optString("title", "Chat"), conversation.optString("id").hashCode()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text(conversation.optString("title", "Conversation"), 17, TEXT, true));
        copy.addView(text(conversation.optString("lastMessage", "No messages yet"), 13, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(chip(conversation.optString("type", "direct"), active));
        card.addView(row);
        return card;
    }

    private View messageBubble(JSONObject message) {
        boolean mine = loggedInHandle.equalsIgnoreCase(message.optString("handle"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout bubble = panel(mine ? Color.rgb(34, 89, 76) : PANEL_ALT, dp(12), dp(8));
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setMinimumWidth(dp(190));
        bubble.addView(text(mine ? "You" : message.optString("handle", "Player"), 13, TEXT, true));
        bubble.addView(text(message.optString("body", ""), 15, TEXT, false));
        bubble.addView(text(message.optString("created", ""), 11, MUTED, false));
        row.addView(bubble, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View messageComposer(String conversationId) {
        LinearLayout card = panel(PANEL, dp(10), dp(12));
        card.setOrientation(LinearLayout.HORIZONTAL);
        EditText input = input("Write a message...", "", false);
        card.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(button("Send", GREEN, v -> sendChatMessage(conversationId, input.getText().toString())), new LinearLayout.LayoutParams(dp(90), dp(52)));
        return card;
    }

    private JSONObject selectedConversation() {
        for (int i = 0; latestConversations != null && i < latestConversations.length(); i++) {
            JSONObject conversation = latestConversations.optJSONObject(i);
            if (conversation != null && conversation.optString("id").equals(selectedConversationId)) return conversation;
        }
        return latestConversations != null && latestConversations.length() > 0 ? latestConversations.optJSONObject(0) : null;
    }

    private View feedComposer() {
        LinearLayout card = panel(PANEL, dp(14), dp(12));
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(avatar(loggedInHandle, 0), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView prompt = text(sessionToken.isEmpty() ? "Log in from Profile to post clips." : "Share a clip or squad update...", 14, MUTED, false);
        prompt.setPadding(dp(12), 0, 0, 0);
        row.addView(prompt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Clip", PANEL_ALT, v -> toast("Clip upload coming next")), weighted());
        actions.addView(button("Post", GREEN, v -> toast("Feed posting coming next")), weighted());
        card.addView(actions);
        return card;
    }

    private View feedPost(String author, String body, String game, String type, int accent) {
        LinearLayout card = panel(PANEL, dp(14), dp(14));
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(avatar(author, author.length()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(author, 17, TEXT, true));
        copy.addView(text(game + " - just now", 12, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(chip(type, true));
        card.addView(top);

        if ("CLIP".equals(type)) {
            card.addView(clipPreview(accent));
        }
        card.addView(text(body, 14, MUTED, false));

        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Like", PANEL_ALT, v -> toast("Liked")), weighted());
        actions.addView(button("Comment", PANEL_ALT, v -> toast("Comments coming next")), weighted());
        actions.addView(button("Share", accent, v -> toast("Share coming next")), weighted());
        card.addView(actions);
        return card;
    }

    private View feedPost(JSONObject post) {
        String type = post.optString("type", "post").toUpperCase();
        int accent = "clip".equalsIgnoreCase(post.optString("type")) ? PURPLE : GREEN;
        LinearLayout card = panel(PANEL, dp(14), dp(14));
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String author = post.optString("handle", "Player");
        top.addView(avatar(author, post.optString("id").hashCode()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text(author, 16, TEXT, true));
        copy.addView(text(post.optString("created", "Now") + " - " + post.optString("gameName", "No game"), 12, MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(chip(type, true));
        card.addView(top);

        String title = post.optString("title", "");
        if (!title.isEmpty()) card.addView(text(title, 20, TEXT, true));
        card.addView(text(post.optString("body", ""), 14, MUTED, false));
        if ("clip".equalsIgnoreCase(post.optString("type")) || post.optString("mediaType", "").contains("video")) {
            card.addView(clipPreview(accent));
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(text(post.optInt("reactionCount", 0) + " like(s)", 13, MUTED, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        stats.addView(text(post.optInt("commentCount", 0) + " comment(s)", 13, MUTED, false));
        card.addView(stats);

        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Like", PANEL_ALT, v -> reactToPost(post.optString("id"))), weighted());
        actions.addView(button("Comment", PANEL_ALT, v -> toast("Comments are next for Android")), weighted());
        actions.addView(button("Share", PANEL_ALT, v -> toast("Share sheet coming later")), weighted());
        card.addView(actions);
        return card;
    }

    private View clipPreview(int accent) {
        LinearLayout preview = panel(Color.rgb(9, 18, 31), dp(12), dp(10));
        preview.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(31, 48, 78), Color.rgb(7, 13, 23), accent}
        );
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, LINE);
        preview.setBackground(bg);
        preview.setMinimumHeight(dp(190));
        preview.addView(centerText("PLAY CLIP", 24, TEXT, true), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        preview.addView(text("0:27 highlight", 12, TEXT, true));
        return preview;
    }

    private View buildHero(int current, int total) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(0, dp(4), 0, dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(iconButton("sliders"), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER);
        copy.addView(centerText("Discover Players", 18, TEXT, true));
        copy.addView(centerText("We found someone great for your game", 12, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(iconButton(current + "/" + total), new LinearLayout.LayoutParams(dp(54), dp(42)));
        hero.addView(row);
        return hero;
    }

    private View filterChips() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(8));
        row.addView(chip("All Games", true));
        row.addView(chip("Skill Match", false));
        row.addView(chip("Any Platform", false));
        row.addView(chip("Crossplay", false));
        scroller.addView(row);
        return scroller;
    }

    private View playerCard(JSONObject player, int index) {
        LinearLayout card = panel(PANEL, dp(14), dp(14));
        card.setOrientation(LinearLayout.VERTICAL);

        card.addView(mediaPanel(player));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(12), 0, 0);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(text(player.optString("handle"), 26, TEXT, true));
        TextView age = text("  " + player.optInt("age", 21), 16, MUTED, false);
        nameRow.addView(age);
        nameRow.addView(statusDot(player.optBoolean("online")));
        identity.addView(nameRow);
        identity.addView(text(player.optString("rank") + "   " + joinValues(player.optJSONArray("platforms"), "Any platform") + "   " + player.optString("region", "Unknown"), 14, MUTED, false));
        top.addView(identity, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(top);

        card.addView(tagRow(player.optJSONArray("playStyle")));
        card.addView(text("\"" + player.optString("bio") + "\"", 14, MUTED, false));
        card.addView(traitRow());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(18), 0, 0);
        String targetId = player.optString("id");
        actions.addView(actionStack("X", "PASS", PANEL_ALT, v -> passDiscoveryPlayer()), weighted());
        actions.addView(actionStack("...", "MORE INFO", PANEL_ALT, v -> toast("Profile preview coming next")), weighted());
        actions.addView(actionStack("OK", "PLAY", GREEN, v -> sendConnect(targetId)), weighted());
        card.addView(actions);

        LinearLayout adjust = new LinearLayout(this);
        adjust.setGravity(Gravity.CENTER);
        adjust.setPadding(0, dp(12), 0, 0);
        adjust.addView(text("Not a fit?  ", 12, MUTED, false));
        adjust.addView(chip("Adjust Preferences", false));
        card.addView(adjust);
        return card;
    }

    private void passDiscoveryPlayer() {
        int total = latestPlayers == null ? 0 : latestPlayers.length();
        if (total <= 1) {
            toast("No more players in this test deck");
            return;
        }
        discoveryIndex = (discoveryIndex + 1) % total;
        renderCurrentView();
    }

    private View mediaPanel(JSONObject player) {
        LinearLayout media = panel(Color.rgb(9, 18, 31), dp(12), dp(10));
        media.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(28, 47, 75), Color.rgb(5, 9, 18), Color.rgb(92, 44, 138)}
        );
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, LINE);
        media.setBackground(bg);
        media.setMinimumHeight(dp(230));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.RIGHT);
        top.addView(chip("1 / 12", false));
        media.addView(top);

        TextView play = centerText("PLAY", 28, TEXT, true);
        play.setGravity(Gravity.CENTER);
        media.addView(play, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout thumbs = new LinearLayout(this);
        thumbs.setGravity(Gravity.LEFT);
        thumbs.addView(thumb("A"));
        thumbs.addView(thumb("B"));
        thumbs.addView(thumb("C"));
        thumbs.addView(thumb("+3"));
        media.addView(thumbs);
        return media;
    }

    private View protectedPanel(JSONObject player) {
        LinearLayout panel = panel(PANEL_ALT, dp(10), dp(10));
        panel.setOrientation(LinearLayout.VERTICAL);
        if (player.optBoolean("protectedVisible")) {
            panel.addView(text("Protected Info Unlocked", 13, GREEN, true));
            panel.addView(text(formatAccounts(player.optJSONObject("linkedAccounts")), 13, MUTED, false));
        } else if (player.optBoolean("hasProtectedInfo")) {
            panel.addView(text("Protected Info", 13, PURPLE, true));
            panel.addView(text("Discord, platform handles, and contact details unlock after an approved connection.", 13, MUTED, false));
        } else {
            panel.addView(text("No protected info added yet.", 13, MUTED, false));
        }
        return panel;
    }

    private TextView iconButton(String label) {
        TextView item = text(label, 12, TEXT, true);
        item.setGravity(Gravity.CENTER);
        item.setBackground(rounded(Color.rgb(7, 13, 23), LINE, dp(8)));
        return item;
    }

    private TextView centerText(String value, int size, int color, boolean bold) {
        TextView item = text(value, size, color, bold);
        item.setGravity(Gravity.CENTER);
        return item;
    }

    private TextView thumb(String label) {
        TextView item = text(label, 12, TEXT, true);
        item.setGravity(Gravity.CENTER);
        item.setBackground(rounded(Color.rgb(18, 30, 49), LINE, dp(8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(58), dp(48));
        params.setMargins(0, 0, dp(8), 0);
        item.setLayoutParams(params);
        return item;
    }

    private View traitRow() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(trait("Aggressive"));
        row.addView(trait("Strategic"));
        row.addView(trait("Good Callouts"));
        row.addView(trait("Works Under Pressure"));
        scroller.addView(row);
        return scroller;
    }

    private TextView trait(String label) {
        TextView item = text(label, 11, MUTED, false);
        item.setPadding(0, 0, dp(14), 0);
        return item;
    }

    private View actionStack(String symbol, String label, int color, View.OnClickListener listener) {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER);
        TextView circle = text(symbol, 18, color == GREEN ? TEXT : MUTED, true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(rounded(color, color == GREEN ? GREEN : LINE, dp(28)));
        circle.setOnClickListener(listener);
        stack.addView(circle, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView caption = centerText(label, 11, color == GREEN ? GREEN : MUTED, false);
        stack.addView(caption);
        return stack;
    }

    private String formatAccounts(JSONObject accounts) {
        if (accounts == null || accounts.length() == 0) return "No linked accounts yet.";
        StringBuilder builder = new StringBuilder();
        JSONArray names = accounts.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i);
            builder.append(name).append(": ").append(accounts.optString(name));
            if (i < names.length() - 1) builder.append("\n");
        }
        return builder.toString();
    }

    private String joinValues(JSONArray values, String fallback) {
        if (values == null || values.length() == 0) return fallback;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.optString(i));
        }
        return builder.toString();
    }

    private View tagRow(JSONArray tags) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(6));
        for (int i = 0; tags != null && i < tags.length(); i++) {
            row.addView(chip(tags.optString(i), i == 0));
        }
        scroller.addView(row);
        return scroller;
    }

    private View statsRow(JSONObject stats) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(metric(stats == null ? "-" : stats.optString("winRate", "-"), "Win"), weighted());
        row.addView(metric(stats == null ? "-" : stats.optString("kd", "-"), "K/D"), weighted());
        row.addView(metric(stats == null ? "-" : stats.optString("games", "-"), "Games"), weighted());
        row.addView(metric(stats == null ? "-" : stats.optString("positive", "-"), "Positive"), weighted());
        return row;
    }

    private View lfgCard(JSONObject post) {
        LinearLayout card = panel(PANEL, dp(14), dp(12));
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(text(post.optString("title"), 17, TEXT, true));
        card.addView(text(post.optString("gameName") + " - " + post.optString("mode") + " - " + post.optString("rankRange"), 13, MUTED, false));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(post.optString("startsAt"), 13, MUTED, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(chip(post.optString("partySize"), true));
        card.addView(row);
        return card;
    }

    private View squadCard(JSONObject squad) {
        LinearLayout card = panel(PANEL, dp(14), dp(12));
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(text(squad.optString("name"), 17, TEXT, true));
        card.addView(text(squad.optString("gameName") + " - " + squad.optInt("openSlots") + " open slot(s) - " + squad.optString("voicePreference"), 13, GREEN, false));
        card.addView(text(squad.optString("description"), 13, MUTED, false));
        return card;
    }

    private void sendConnect(String targetId) {
        if (sessionToken.isEmpty()) {
            toast("Log in first so the backend knows who is connecting");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("toPlayerId", targetId);
                payload.put("message", "Want to squad up from Android?");
                JSONObject response = postJson("/api/connections", payload, sessionToken);
                String status = response.getJSONObject("connectionRequest").optString("status");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Connection request: " + status, Toast.LENGTH_SHORT).show();
                    passDiscoveryPlayer();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Connect failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void reactToPost(String postId) {
        if (sessionToken.isEmpty()) {
            toast("Log in first to like posts");
            return;
        }
        new Thread(() -> {
            try {
                postJson("/api/feed/" + postId + "/react", new JSONObject().put("reaction", "like"), sessionToken);
                runOnUiThread(() -> {
                    toast("Liked post");
                    loadAll();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Like failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void createTestConversation() {
        if (sessionToken.isEmpty()) {
            toast("Log in first to create chats");
            return;
        }
        JSONObject target = firstOtherPlayer();
        if (target == null) {
            toast("No other player available for a test chat");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("participantPlayerIds", new JSONArray().put(target.optString("id")));
                payload.put("title", target.optString("handle", "New Chat"));
                payload.put("message", "Hey, want to squad up?");
                JSONObject response = postJson("/api/conversations", payload, sessionToken);
                JSONObject conversation = response.optJSONObject("conversation");
                selectedConversationId = conversation == null ? "" : conversation.optString("id");
                runOnUiThread(this::loadAll);
            } catch (Exception ex) {
                runOnUiThread(() -> toast("New chat failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void openConversation(String conversationId) {
        selectedConversationId = conversationId;
        new Thread(() -> {
            try {
                JSONObject messages = getJson("/api/conversations/" + conversationId + "/messages");
                latestConversationMessages = messages.optJSONArray("messages");
                runOnUiThread(this::renderCurrentView);
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Open chat failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void sendChatMessage(String conversationId, String body) {
        String cleanBody = body == null ? "" : body.trim();
        if (cleanBody.isEmpty()) {
            toast("Write a message first");
            return;
        }
        new Thread(() -> {
            try {
                postJson("/api/conversations/" + conversationId + "/messages", new JSONObject().put("body", cleanBody), sessionToken);
                runOnUiThread(() -> openConversation(conversationId));
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Message failed: " + ex.getMessage()));
            }
        }).start();
    }

    private JSONObject firstOtherPlayer() {
        for (int i = 0; latestPlayers != null && i < latestPlayers.length(); i++) {
            JSONObject player = latestPlayers.optJSONObject(i);
            if (player != null && !loggedInHandle.equalsIgnoreCase(player.optString("handle"))) return player;
        }
        return null;
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiBase + path).openConnection();
        conn.setRequestMethod("GET");
        if (!sessionToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + sessionToken);
        }
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return readJson(conn);
    }

    private JSONObject postJson(String path, JSONObject payload) throws Exception {
        return postJson(path, payload, sessionToken);
    }

    private JSONObject postJson(String path, JSONObject payload, String token) throws Exception {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(apiBase + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        return readJson(conn);
    }

    private JSONObject readJson(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        if (status >= 400) {
            throw new IllegalStateException(builder.toString());
        }
        return new JSONObject(builder.toString());
    }

    private LinearLayout panel(int color, int padding, int marginBottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(padding, padding, padding, padding);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, LINE);
        layout.setBackground(bg);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, marginBottom);
        layout.setLayoutParams(params);
        return layout;
    }

    private TextView loadingCard(String body) {
        TextView view = text(body, 14, MUTED, false);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackgroundColor(PANEL);
        return view;
    }

    private View messageCard(String title, String body, int accent) {
        LinearLayout card = panel(PANEL, dp(16), dp(14));
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(text(title, 18, accent, true));
        card.addView(text(body, 13, MUTED, false));
        return card;
    }

    private View section(String title, String subtitle) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(10), 0, dp(8));
        section.addView(text(title, 21, TEXT, true));
        section.addView(text(subtitle, 13, MUTED, false));
        return section;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        text.setPadding(0, dp(2), 0, dp(2));
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setIncludeFontPadding(true);
        return text;
    }

    private EditText input(String hint, String value, boolean password) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(14);
        input.setHint(hint);
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(rounded(PANEL, LINE, dp(8)));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(3), dp(8), dp(3), 0);
        input.setLayoutParams(params);
        return input;
    }

    private Button button(String label, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(rounded(color, color, dp(8)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(3), dp(8), dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView chip(String label, boolean active) {
        TextView chip = text(label, 12, active ? TEXT : MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(rounded(active ? Color.rgb(74, 33, 143) : PANEL_ALT, active ? PURPLE : LINE, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
        params.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private TextView badge(String label, int accent, int size) {
        TextView badge = text(label, 14, accent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Color.rgb(12, 19, 34), accent, dp(8)));
        badge.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return badge;
    }

    private TextView avatar(String handle, int index) {
        String initials = handle.length() >= 2 ? handle.substring(0, 2).toUpperCase() : handle.toUpperCase();
        int accent = index % 3 == 0 ? PURPLE : index % 3 == 1 ? GREEN : GOLD;
        return badge(initials, accent, dp(54));
    }

    private TextView statusDot(boolean online) {
        TextView dot = text(online ? "Online" : "Away", 12, online ? GREEN : MUTED, true);
        dot.setGravity(Gravity.CENTER);
        dot.setPadding(dp(8), dp(5), dp(8), dp(5));
        dot.setBackground(rounded(PANEL_ALT, online ? GREEN : LINE, dp(18)));
        return dot;
    }

    private View metric(String value, String label) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(4), dp(8), dp(4), dp(8));
        metric.addView(text(value, 15, TEXT, true));
        TextView caption = text(label, 11, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        metric.addView(caption);
        return metric;
    }

    private GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void detach(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
