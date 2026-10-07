package com.shadow.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class MainActivity extends Activity {

    private ShadowView shadowView;
    private EditText input;
    private TextView responseView;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean listening = false;
    private final android.content.SharedPreferences prefs;

    private static final String PREFS = "shadow_prefs";
    private static final String KEY_API = "gemini_api_key";
    private static final String MODEL = "gemini-3.5-flash-lite";

    public MainActivity() {
        prefs = null;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(2,7,12));
        getWindow().setNavigationBarColor(Color.rgb(2,7,12));

        final android.content.SharedPreferences p =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        shadowView = new ShadowView(this);
        root.addView(shadowView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(18), dp(14), dp(18), dp(8));

        // Top title row
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("SHADOW", 22, Color.WHITE);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        TextView sub = label("  // PERSONAL AI", 12, Color.rgb(80,170,190));
        top.addView(title);
        top.addView(sub, new LinearLayout.LayoutParams(0, -2, 1));

        Button gear = smallButton("⚙");
        gear.setOnClickListener(v -> showApiDialog(p));
        top.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(42)));
        overlay.addView(top);

        Space s1 = new Space(this);
        overlay.addView(s1, new LinearLayout.LayoutParams(1, dp(10)));

        // Main status HUD occupies most of the screen
        FrameLayout hudHolder = new FrameLayout(this);
        overlay.addView(hudHolder, new LinearLayout.LayoutParams(-1, 0, 1));

        responseView = label("Shadow online. Ready, boss.", 14, Color.rgb(160,210,225));
        responseView.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable responseBg = new GradientDrawable();
        responseBg.setColor(Color.argb(210, 4, 18, 27));
        responseBg.setStroke(dp(1), Color.rgb(18,110,145));
        responseView.setBackground(responseBg);
        FrameLayout.LayoutParams responseLp = new FrameLayout.LayoutParams(-1, dp(82));
        responseLp.gravity = Gravity.BOTTOM;
        hudHolder.addView(responseView, responseLp);

        // Input row
        LinearLayout commandRow = new LinearLayout(this);
        commandRow.setGravity(Gravity.CENTER_VERTICAL);
        commandRow.setPadding(0, dp(8), 0, 0);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(70,120,135));
        input.setHint("Talk to SHADOW...");
        input.setTextSize(15);
        input.setPadding(dp(14), 0, dp(10), 0);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.rgb(4,16,24));
        inputBg.setStroke(dp(1), Color.rgb(15,125,165));
        inputBg.setCornerRadius(dp(8));
        input.setBackground(inputBg);
        commandRow.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button mic = smallButton("🎙");
        mic.setOnClickListener(v -> startVoice());
        commandRow.addView(mic, new LinearLayout.LayoutParams(dp(58), dp(52)));

        Button send = smallButton("➤");
        send.setOnClickListener(v -> submitText());
        commandRow.addView(send, new LinearLayout.LayoutParams(dp(58), dp(52)));

        overlay.addView(commandRow);

        setContentView(root);
        FrameLayout.LayoutParams ovLp = new FrameLayout.LayoutParams(-1, -1);
        ovLp.leftMargin = 0;
        ovLp.topMargin = 0;
        root.addView(overlay, ovLp);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                tts.setSpeechRate(0.94f);
                tts.setPitch(0.82f);
            }
        });

        setupSpeechRecognizer();

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 55);
        }

        shadowView.postDelayed(new Runnable() {
            @Override public void run() {
                shadowView.invalidate();
                shadowView.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                shadowView.mode = "LISTENING";
                shadowView.invalidate();
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                listening = false;
                shadowView.mode = "STANDBY";
                shadowView.invalidate();
                say("I didn't catch that.");
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> list = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    input.setText(list.get(0));
                    submitText();
                }
                shadowView.mode = "STANDBY";
                shadowView.invalidate();
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startVoice() {
        if (recognizer == null) {
            say("Voice recognition is not available on this phone.");
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        recognizer.startListening(i);
    }

    private void submitText() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        input.setText("");
        handleCommand(q);
    }

    private void handleCommand(String q) {
        String s = q.toLowerCase(Locale.ROOT).trim();

        // SAFE LOCAL COMMANDS ONLY. Unknown commands are treated as AI questions.
        if (s.equals("hello") || s.equals("hi") || s.contains("hello shadow") ||
                s.contains("hi shadow")) {
            answer("Hello boss. SHADOW is online.");
            return;
        }

        if (s.contains("what time") || s.equals("time") || s.contains("current time")) {
            String time = android.text.format.DateFormat.format("hh:mm a", new Date()).toString();
            answer("It is " + time + ".");
            return;
        }

        if (s.contains("battery")) {
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = registerReceiver(null, f);
            int level = b != null ? b.getIntExtra("level", -1) : -1;
            answer("Battery is at " + level + " percent.");
            return;
        }

        if (s.contains("flashlight") || s.contains("torch")) {
            answer("For safety and compatibility, use the phone's Quick Settings flashlight control.");
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
            return;
        }

        if (s.startsWith("open ")) {
            String app = s.substring(5).trim();
            if (openKnownApp(app)) return;
        }

        if (s.equals("settings") || s.contains("open settings")) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                answer("Opening Settings.");
            } catch (Exception e) {
                answer("I couldn't open Settings.");
            }
            return;
        }

        // Unknown request -> AI. It can use Google Search grounding.
        askGemini(q);
    }

    private boolean openKnownApp(String name) {
        String pkg = null;
        if (name.contains("youtube")) pkg = "com.google.android.youtube";
        else if (name.contains("chrome")) pkg = "com.android.chrome";
        else if (name.contains("whatsapp")) pkg = "com.whatsapp";
        else if (name.contains("instagram")) pkg = "com.instagram.android";
        else if (name.contains("telegram")) pkg = "org.telegram.messenger";
        else if (name.contains("calculator")) pkg = "com.google.android.calculator";

        if (pkg == null) return false;

        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
                answer("Opening " + name + ".");
                return true;
            }
        } catch (Exception ignored) {}
        answer(name + " is not installed.");
        return true;
    }

    private void askGemini(String userText) {
        final String key = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_API, "").trim();

        if (key.isEmpty()) {
            answer("I can answer that, but my Gemini API key is not configured. Tap the gear icon and add your key.");
            return;
        }

        shadowView.mode = "THINKING";
        shadowView.invalidate();
        responseView.setText("SHADOW // thinking + web search...");

        new Thread(() -> {
            String result;
            try {
                result = callGemini(key, userText);
            } catch (Exception e) {
                result = "I couldn't reach the AI service right now. Check your internet connection and API key.";
            }
            final String out = result;
            runOnUiThread(() -> {
                shadowView.mode = "ONLINE";
                shadowView.invalidate();
                answer(out);
            });
        }).start();
    }

    private String callGemini(String apiKey, String userText) throws Exception {
        URL url = new URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                        MODEL + ":generateContent");

        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", apiKey);

        JSONObject body = new JSONObject();

        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        content.put("role", "user");
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text",
                "You are SHADOW, a concise, helpful personal AI assistant. " +
                "Answer naturally in the user's language. " +
                "You may use Google Search grounding when current information is needed. " +
                "Never claim you performed a phone action unless the app actually did it. " +
                "Do not invent facts. Keep normal replies concise. User says: " + userText);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);

        // Current Gemini search-grounding tool.
        JSONArray tools = new JSONArray();
        JSONObject tool = new JSONObject();
        tool.put("google_search", new JSONObject());
        tools.put(tool);
        body.put("tools", tools);

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300
                ? c.getInputStream() : c.getErrorStream();

        String response = readAll(is);
        c.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + response);
        }

        JSONObject root = new JSONObject(response);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IOException("No candidate returned.");
        }

        JSONObject first = candidates.getJSONObject(0);
        JSONObject outContent = first.optJSONObject("content");
        if (outContent == null) throw new IOException("No content.");

        JSONArray outParts = outContent.optJSONArray("parts");
        if (outParts == null) throw new IOException("No parts.");

        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < outParts.length(); i++) {
            JSONObject p = outParts.optJSONObject(i);
            if (p != null && p.has("text")) {
                answer.append(p.optString("text"));
            }
        }

        String text = answer.toString().trim();
        return text.isEmpty() ? "I received an empty response." : text;
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close();
        return b.toString();
    }

    private void answer(String text) {
        responseView.setText(text);
        say(text);
    }

    private void say(String text) {
        if (tts != null) {
            String clean = text.replaceAll("\\[[^\\]]*\\]", "");
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "shadow");
        }
    }

    private void showApiDialog(android.content.SharedPreferences p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));

        TextView info = label(
                "Gemini API key is stored only on this phone.\n" +
                "Do not share the key or upload it to GitHub.",
                13, Color.LTGRAY);
        box.addView(info);

        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setText(p.getString(KEY_API, ""));
        key.setHint("Paste Gemini API key");
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key);

        new AlertDialog.Builder(this)
                .setTitle("SHADOW AI KEY")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (d, w) -> {
                    p.edit().remove(KEY_API).apply();
                    answer("API key cleared.");
                })
                .setPositiveButton("Save", (d, w) -> {
                    String v = key.getText().toString().trim();
                    p.edit().putString(KEY_API, v).apply();
                    answer(v.isEmpty()
                            ? "No API key saved."
                            : "API key saved. SHADOW AI is ready.");
                })
                .show();
    }

    private TextView label(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.rgb(24,207,255));
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    class ShadowView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String mode = "STANDBY";
        float pulse = 0f;

        ShadowView(Context c) {
            super(c);
            p.setTypeface(Typeface.MONOSPACE);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            c.drawColor(Color.rgb(2,7,12));

            // subtle scanlines
            p.setStrokeWidth(1);
            p.setColor(Color.argb(18, 20, 190, 235));
            for (int y = 0; y < h; y += dp(7)) c.drawLine(0, y, w, y, p);

            float cx = w / 2f;
            float cy = Math.min(h * 0.44f, dp(360));
            float base = Math.min(w * 0.34f, dp(145));
            pulse += 0.035f;
            float wave = (float)Math.sin(pulse) * dp(5);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.rgb(18, 207, 255));
            p.setShadowLayer(dp(14), 0, 0, Color.rgb(0, 150, 255));

            for (int i = 0; i < 3; i++) {
                c.drawCircle(cx, cy, base + i * dp(30) + wave, p);
            }

            p.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
            p.setStrokeWidth(dp(5));
            c.drawArc(cx-base-dp(30), cy-base-dp(30),
                    cx+base+dp(30), cy+base+dp(30),
                    -65, 95, false, p);
            c.drawArc(cx-base-dp(55), cy-base-dp(55),
                    cx+base+dp(55), cy+base+dp(55),
                    120, 135, false, p);

            // center disc
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(3, 18, 30));
            c.drawCircle(cx, cy, base * 0.62f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.rgb(24, 207, 255));
            c.drawCircle(cx, cy, base * 0.62f, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(13));
            p.setColor(Color.rgb(24, 207, 255));
            c.drawText("SHADOW", cx, cy - dp(10), p);
            p.setTextSize(dp(22));
            p.setColor(Color.WHITE);
            c.drawText(mode, cx, cy + dp(20), p);

            // metric cards
            drawMetric(c, dp(18), dp(70), "BATTERY", battery());
            drawMetric(c, w-dp(178), dp(70), "RAM", ram());
            drawMetric(c, dp(18), h-dp(185), "CPU CORES", "" + Runtime.getRuntime().availableProcessors());
            drawMetric(c, w-dp(178), h-dp(185), "SCREEN", w + "x" + h);

            // bottom line
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.rgb(18, 120, 155));
            p.setStrokeWidth(dp(1));
            c.drawLine(dp(18), h-dp(110), w-dp(18), h-dp(110), p);

            invalidate();
        }

        private void drawMetric(Canvas c, int x, int y, String title, String value) {
            int bw = dp(160), bh = dp(68);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(210, 4, 18, 27));
            c.drawRect(x, y, x+bw, y+bh, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.rgb(10, 125, 160));
            c.drawRect(x, y, x+bw, y+bh, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(dp(10));
            p.setColor(Color.rgb(110,160,175));
            c.drawText(title, x+dp(10), y+dp(19), p);
            p.setTextSize(dp(17));
            p.setColor(Color.WHITE);
            c.drawText(value, x+dp(10), y+dp(45), p);
        }

        private String battery() {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = b != null ? b.getIntExtra("level", -1) : -1;
            return level + "%";
        }

        private String ram() {
            ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long used = mi.totalMem - mi.availMem;
            return (used / (1024*1024)) + "MB";
        }
    }
}
