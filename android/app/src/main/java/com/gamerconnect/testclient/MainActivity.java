package com.gamerconnect.testclient;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(5, 9, 18);
    private static final int PANEL = Color.rgb(14, 23, 38);
    private static final int TEXT = Color.rgb(239, 243, 255);
    private static final int MUTED = Color.rgb(166, 176, 196);
    private static final int PURPLE = Color.rgb(161, 89, 255);
    private static final int GREEN = Color.rgb(52, 211, 115);

    private EditText apiInput;
    private LinearLayout content;
    private String apiBase = "http://10.0.2.2:8080";

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
        root.setPadding(24, 24, 24, 24);

        TextView title = text("Gamer Connect", 28, TEXT, true);
        TextView subtitle = text("Android test client for the Raspberry Pi backend", 14, MUTED, false);
        root.addView(title);
        root.addView(subtitle);

        apiInput = new EditText(this);
        apiInput.setSingleLine(true);
        apiInput.setText(apiBase);
        apiInput.setTextColor(TEXT);
        apiInput.setHintTextColor(MUTED);
        apiInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        apiInput.setHint("Backend URL, e.g. http://192.168.1.50:8080");
        apiInput.setBackgroundColor(PANEL);
        apiInput.setPadding(18, 12, 18, 12);
        root.addView(apiInput, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button refresh = button("Refresh", PURPLE);
        refresh.setOnClickListener(v -> loadAll());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void loadAll() {
        apiBase = apiInput.getText().toString().trim();
        content.removeAllViews();
        content.addView(text("Loading from " + apiBase + "...", 14, MUTED, false));

        new Thread(() -> {
            try {
                JSONObject health = getJson("/api/health");
                JSONObject players = getJson("/api/players?game=apex-legends");
                JSONObject lfg = getJson("/api/lfg");
                JSONObject squads = getJson("/api/squads");
                runOnUiThread(() -> render(health, players.optJSONArray("players"), lfg.optJSONArray("posts"), squads.optJSONArray("squads")));
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    content.removeAllViews();
                    content.addView(card("Connection failed", ex.getMessage(), MUTED));
                });
            }
        }).start();
    }

    private void render(JSONObject health, JSONArray players, JSONArray posts, JSONArray squads) {
        content.removeAllViews();
        content.addView(card("Backend online", health.optString("app") + " is responding.", GREEN));

        content.addView(section("Discover Players"));
        for (int i = 0; players != null && i < players.length(); i++) {
            JSONObject player = players.optJSONObject(i);
            if (player == null) continue;
            String details = player.optString("rank")
                    + " | " + player.optString("region")
                    + " | " + player.optInt("compatibility") + "% compatible\n"
                    + player.optString("bio");
            LinearLayout playerCard = card(player.optString("handle"), details, PURPLE);
            Button connect = button("Connect", GREEN);
            String targetId = player.optString("id");
            connect.setOnClickListener(v -> sendConnect(targetId));
            playerCard.addView(connect);
            content.addView(playerCard);
        }

        content.addView(section("LFG Posts"));
        for (int i = 0; posts != null && i < posts.length(); i++) {
            JSONObject post = posts.optJSONObject(i);
            if (post == null) continue;
            content.addView(card(
                    post.optString("title"),
                    post.optString("gameName") + " | " + post.optString("mode") + " | " + post.optString("startsAt"),
                    GREEN
            ));
        }

        content.addView(section("Squads"));
        for (int i = 0; squads != null && i < squads.length(); i++) {
            JSONObject squad = squads.optJSONObject(i);
            if (squad == null) continue;
            content.addView(card(
                    squad.optString("name"),
                    squad.optString("gameName") + " | " + squad.optInt("openSlots") + " open slot(s)\n" + squad.optString("description"),
                    PURPLE
            ));
        }
    }

    private void sendConnect(String targetId) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("fromPlayerId", "p_novapulse");
                payload.put("toPlayerId", targetId);
                payload.put("message", "Want to squad up from Android?");
                JSONObject response = postJson("/api/connections", payload);
                String status = response.getJSONObject("connectionRequest").optString("status");
                runOnUiThread(() -> Toast.makeText(this, "Connection request: " + status, Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Connect failed: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiBase + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return readJson(conn);
    }

    private JSONObject postJson(String path, JSONObject payload) throws Exception {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(apiBase + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
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

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        text.setPadding(0, 8, 0, 8);
        text.setTypeface(null, bold ? 1 : 0);
        return text;
    }

    private TextView section(String value) {
        TextView text = text(value, 20, TEXT, true);
        text.setPadding(0, 28, 0, 8);
        return text;
    }

    private LinearLayout card(String title, String body, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 18, 20, 18);
        card.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 10, 0, 10);
        card.setLayoutParams(params);

        TextView titleView = text(title, 17, accent, true);
        TextView bodyView = text(body, 14, MUTED, false);
        card.addView(titleView);
        card.addView(bodyView);
        return card;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(color);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
}
