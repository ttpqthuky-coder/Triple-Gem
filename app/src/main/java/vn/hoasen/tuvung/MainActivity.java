package vn.hoasen.tuvung;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Hoa Sen Từ Vựng: toàn bộ giao diện và thuật toán ôn tập nằm trong assets/index.html.
 * Activity này hiển thị trang đó và cung cấp cho JavaScript (qua window.Android):
 *  - phát âm bằng TextToSpeech, kể cả hàng đợi Anh/Việt cho chế độ nghe rảnh tay,
 *  - nhận dạng giọng nói (SpeechRecognizer) để chấm phát âm,
 *  - sao chép, giữ màn hình sáng, mở cài đặt giọng đọc và nút Quay lại.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQ_MIC = 42;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private final Object ttsLock = new Object();

    private SpeechRecognizer recognizer;
    private String pendingLang;
    private String pendingId;
    private String activeId;
    private boolean activeDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage lưu tiến độ học
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");

        tts = new TextToSpeech(this, this);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        tts.setLanguage(Locale.UK);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { sendTts("start", id); }
            @Override public void onDone(String id) { sendTts("done", id); }
            @Override public void onError(String id) { sendTts("done", id); }
        });
        ttsReady = true;
    }

    private static Locale localeFor(String tag) {
        if (tag == null) return Locale.UK;
        if (tag.equalsIgnoreCase("en-US")) return Locale.US;
        if (tag.equalsIgnoreCase("en-GB")) return Locale.UK;
        Locale l = Locale.forLanguageTag(tag);
        return l.getLanguage().isEmpty() ? Locale.UK : l;
    }

    /** Đặt ngôn ngữ; với tiếng Anh thì lùi về Locale.ENGLISH nếu thiếu giọng vùng miền. */
    private boolean useLanguage(String tag) {
        Locale loc = localeFor(tag);
        int res = tts.setLanguage(loc);
        if (res < TextToSpeech.LANG_AVAILABLE && "en".equals(loc.getLanguage())) {
            res = tts.setLanguage(Locale.ENGLISH);
        }
        return res >= TextToSpeech.LANG_AVAILABLE;
    }

    private void js(final String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }

    private void sendTts(String type, String id) {
        if (id == null || id.startsWith("hs")) return;
        js("window.ttsEvent&&window.ttsEvent(" + JSONObject.quote(type) + "," + JSONObject.quote(id) + ")");
    }

    private void sendSpeech(String id, boolean ok, String error, ArrayList<String> results, String state) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            if (state != null) {
                o.put("state", state);
            } else {
                o.put("ok", ok);
                if (error != null) o.put("error", error);
                JSONArray arr = new JSONArray();
                if (results != null) for (String r : results) arr.put(r);
                o.put("results", arr);
            }
            js("window.onSpeech&&window.onSpeech(" + JSONObject.quote(o.toString()) + ")");
        } catch (Exception ignored) { }
    }

    private static String errName(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "network_timeout";
            case SpeechRecognizer.ERROR_NETWORK: return "network";
            case SpeechRecognizer.ERROR_AUDIO: return "audio";
            case SpeechRecognizer.ERROR_SERVER: return "server";
            case SpeechRecognizer.ERROR_CLIENT: return "client";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "speech_timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "no_match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "busy";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "insufficient_permissions";
            default: return "error_" + code;
        }
    }

    /** Chạy trên luồng giao diện. */
    private void startRecognition(final String lang, final String id) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendSpeech(id, false, "not_available", null, null);
            return;
        }
        if (tts != null) tts.stop();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        activeId = id;
        activeDone = false;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { sendSpeech(id, false, null, null, "listening"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { sendSpeech(id, false, null, null, "processing"); }
            @Override public void onError(int error) {
                if (activeDone || !id.equals(activeId)) return;
                activeDone = true;
                sendSpeech(id, false, errName(error), null, null);
            }
            @Override public void onResults(Bundle results) {
                if (activeDone || !id.equals(activeId)) return;
                activeDone = true;
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                sendSpeech(id, true, null, list, null);
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null ? "en-GB" : lang);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        recognizer.startListening(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC || pendingId == null) return;
        String id = pendingId, lang = pendingLang;
        pendingId = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecognition(lang, id);
        } else {
            sendSpeech(id, false, "denied", null, null);
        }
    }

    /** Các hàm JavaScript gọi được qua window.Android.* */
    private class Bridge {
        @JavascriptInterface
        public boolean speak(String text, String lang, float rate) {
            if (!ttsReady || tts == null) return false;
            synchronized (ttsLock) {
                if (!useLanguage(lang)) return false;
                tts.setSpeechRate(rate > 0 ? rate : 0.9f);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hs" + System.nanoTime());
            }
            return true;
        }

        /** Hàng đợi cho chế độ nghe rảnh tay: [{t:text, l:lang, r:rate, p:pauseMs}, ...]. */
        @JavascriptInterface
        public boolean speakQueue(String json, int gen) {
            if (!ttsReady || tts == null) return false;
            try {
                JSONArray arr = new JSONArray(json);
                synchronized (ttsLock) {
                    tts.stop();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String id = gen + ":" + i;
                        String text = o.optString("t", "");
                        long pause = o.optLong("p", 0);
                        boolean spoken = false;
                        if (text.length() > 0 && useLanguage(o.optString("l", "en-GB"))) {
                            tts.setSpeechRate((float) o.optDouble("r", 0.9));
                            spoken = tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS;
                        }
                        if (!spoken) tts.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, id);
                        if (pause > 0) tts.playSilentUtterance(pause, TextToSpeech.QUEUE_ADD, "p" + id);
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public boolean ready() {
            return ttsReady;
        }

        @JavascriptInterface
        public boolean hasLang(String lang) {
            if (!ttsReady || tts == null) return false;
            try {
                return tts.isLanguageAvailable(localeFor(lang)) >= TextToSpeech.LANG_AVAILABLE;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean canListen() {
            return SpeechRecognizer.isRecognitionAvailable(MainActivity.this);
        }

        @JavascriptInterface
        public void listen(final String lang, final String id) {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingLang = lang;
                    pendingId = id;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    return;
                }
                startRecognition(lang, id);
            });
        }

        @JavascriptInterface
        public void stopListening() {
            runOnUiThread(() -> {
                if (recognizer != null) recognizer.cancel();
            });
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Hoa Sen", text));
            });
        }

        @JavascriptInterface
        public void openTtsSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception ignored) { }
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.appBack && window.appBack()) ? '1' : '0'", value -> {
            if (!"\"1\"".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
