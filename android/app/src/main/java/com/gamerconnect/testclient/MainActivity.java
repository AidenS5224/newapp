package com.gamerconnect.testclient;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
    private LinearLayout content;
    private String apiBase = "http://10.0.2.2:8080";
    private String sessionToken = "";
    private String loggedInHandle = "Guest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        loadAll();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

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
        header.addView(apiInput);

        LinearLayout authRow = new LinearLayout(this);
        authRow.setOrientation(LinearLayout.HORIZONTAL);
        authRow.addView(loginInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        authRow.addView(passwordInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(authRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.addView(button("Refresh", PURPLE, v -> loadAll()), weighted());
        actionRow.addView(button("Login", GREEN, v -> login()), weighted());
        actionRow.addView(button("Sign Up", PURPLE, v -> signup()), weighted());
        header.addView(actionRow);

        profileText = text("Guest mode: protected handles are hidden", 12, MUTED, false);
        profileText.setPadding(0, dp(8), 0, 0);
        header.addView(profileText);
        return header;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackgroundColor(Color.rgb(3, 7, 15));
        nav.addView(navItem("Servers", false), weighted());
        nav.addView(navItem("Events", false), weighted());
        nav.addView(navItem("Discover", true), weighted());
        nav.addView(navItem("Messages", false), weighted());
        nav.addView(navItem("Profile", false), weighted());
        return nav;
    }

    private TextView navItem(String label, boolean active) {
        TextView item = text(label, 12, active ? PURPLE : MUTED, true);
        item.setGravity(Gravity.CENTER);
        item.setText(active ? "• " + label : label);
        return item;
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
                JSONObject players = getJson("/api/players?game=apex-legends&platform=PC");
                JSONObject lfg = getJson("/api/lfg");
                JSONObject squads = getJson("/api/squads");
                runOnUiThread(() -> render(health, players.optJSONArray("players"), lfg.optJSONArray("posts"), squads.optJSONArray("squads")));
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

    private void render(JSONObject health, JSONArray players, JSONArray posts, JSONArray squads) {
        statusText.setText(health.optBoolean("ok") ? "Online" : "Offline");
        statusText.setTextColor(health.optBoolean("ok") ? GREEN : RED);
        profileText.setText(sessionToken.isEmpty()
                ? "Guest mode: protected handles are hidden"
                : "Logged in as " + loggedInHandle + " • protected info unlocks on approved connections");

        content.removeAllViews();
        content.addView(buildHero(players == null ? 0 : players.length()));
        content.addView(filterChips());
        content.addView(section("Discover Players", "Sorted by compatibility for Apex Legends"));

        for (int i = 0; players != null && i < players.length(); i++) {
            JSONObject player = players.optJSONObject(i);
            if (player != null) {
                content.addView(playerCard(player, i));
            }
        }

        content.addView(section("Active LFG Posts", "Jump into sessions already forming"));
        LinearLayout lfgRow = new LinearLayout(this);
        lfgRow.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; posts != null && i < posts.length(); i++) {
            JSONObject post = posts.optJSONObject(i);
            if (post != null) lfgRow.addView(lfgCard(post));
        }
        content.addView(lfgRow);

        content.addView(section("Your Squads", "Voice-ready groups for repeat sessions"));
        for (int i = 0; squads != null && i < squads.length(); i++) {
            JSONObject squad = squads.optJSONObject(i);
            if (squad != null) content.addView(squadCard(squad));
        }
    }

    private View buildHero(int count) {
        LinearLayout hero = panel(PANEL, dp(18), dp(16));
        hero.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Discover Players", 24, TEXT, true));
        copy.addView(text("We found " + count + " solid matches for your squad.", 14, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(badge(String.valueOf(count), GREEN, dp(48)));
        hero.addView(row);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setPadding(0, dp(14), 0, 0);
        metrics.addView(metric("Apex", "Ranked"), weighted());
        metrics.addView(metric("PC", "Crossplay"), weighted());
        metrics.addView(metric("AEST", "Evening"), weighted());
        hero.addView(metrics);
        return hero;
    }

    private View filterChips() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(8));
        row.addView(chip("Apex Legends", true));
        row.addView(chip("Diamond - Master", false));
        row.addView(chip("PC", false));
        row.addView(chip("Crossplay", false));
        row.addView(chip("Good Comms", false));
        scroller.addView(row);
        return scroller;
    }

    private View playerCard(JSONObject player, int index) {
        LinearLayout card = panel(PANEL, dp(16), dp(14));
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(avatar(player.optString("handle"), index));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(12), 0, 0, 0);
        identity.addView(text(player.optString("handle"), 22, TEXT, true));
        identity.addView(text(player.optString("rank") + " • " + player.optString("region") + " • " + player.optInt("compatibility") + "% compatible", 13, MUTED, false));
        top.addView(identity, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusDot(player.optBoolean("online")));
        card.addView(top);

        card.addView(tagRow(player.optJSONArray("playStyle")));
        card.addView(text(player.optString("bio"), 14, MUTED, false));
        card.addView(statsRow(player.optJSONObject("stats")));
        card.addView(protectedPanel(player));

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(12), 0, 0);
        String targetId = player.optString("id");
        actions.addView(button("Pass", PANEL_ALT, v -> toast("Passed for now")), weighted());
        actions.addView(button("More Info", PANEL_ALT, v -> toast("Profile preview coming next")), weighted());
        actions.addView(button("Connect", GREEN, v -> sendConnect(targetId)), weighted());
        card.addView(actions);
        return card;
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
        card.addView(text(post.optString("gameName") + " • " + post.optString("mode") + " • " + post.optString("rankRange"), 13, MUTED, false));
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
        card.addView(text(squad.optString("gameName") + " • " + squad.optInt("openSlots") + " open slot(s) • " + squad.optString("voicePreference"), 13, GREEN, false));
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
                runOnUiThread(() -> Toast.makeText(this, "Connection request: " + status, Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Connect failed: " + ex.getMessage()));
            }
        }).start();
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
