package com.transportegalapa.evomensajes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Evo Mensajes — cliente Android para Evolution API v2.
 * Sin bibliotecas externas. Credenciales cifradas en Android Keystore.
 * Se envía únicamente un mensaje por operación, con confirmación explícita.
 */
public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(17, 47, 41);
    private static final int GREEN = Color.rgb(8, 127, 91);
    private static final int MINT = Color.rgb(230, 244, 237);
    private static final int PAPER = Color.rgb(245, 247, 245);
    private static final int MUTED = Color.rgb(104, 119, 113);
    private static final String KEY_ALIAS = "EvoMensajesAccessKey";
    private static final String PREFS = "evo_settings";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlField, instanceField, apiField, phoneField, messageField;
    private TextView statusView, resultView, counterView;
    private Button checkButton, sendButton, saveButton;
    private SharedPreferences preferences;
    private boolean busy = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        drawScreen();
        restoreCredentials();
    }

    private void drawScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);

        LinearLayout header = vertical();
        header.setPadding(dp(24), dp(32), dp(24), dp(30));
        header.setBackgroundColor(NAVY);
        TextView eyebrow = text("E V O   /   M E S S A G E   H U B", 10, 0xFFA0E5C2, Typeface.BOLD);
        header.addView(eyebrow);
        TextView title = text("Mensajes", 34, Color.WHITE, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, 0);
        header.addView(title);
        TextView sub = text("Envíos individuales desde tu WhatsApp", 13, 0xFFB7CBC3, Typeface.NORMAL);
        sub.setPadding(0, dp(5), 0, dp(18));
        header.addView(sub);
        statusView = text("● Sin comprobar la conexión", 13, 0xFFDAE8DF, Typeface.BOLD);
        statusView.setPadding(dp(14), dp(10), dp(14), dp(10));
        statusView.setBackground(round(0xFF24473E, 14, 0));
        header.addView(statusView);
        page.addView(header);

        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(18), dp(16), dp(30));
        page.addView(content);
        LinearLayout connection = card(content);
        connection.addView(sectionLabel("01  /  CONFIGURACIÓN"));
        connection.addView(sectionTitle("Conecta tu instancia"));
        connection.addView(help("Ingresa los datos de tu servidor Evolution API v2."));
        urlField = field(connection, "URL del servidor", "https://evo.tudominio.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false);
        instanceField = field(connection, "Nombre de la instancia", "transportes", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, false);
        apiField = field(connection, "API Key", "Introduce tu clave privada", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
        checkButton = action(connection, "Comprobar conexión", true, () -> checkConnection());
        saveButton = action(connection, "Guardar configuración segura", false, () -> saveCredentials());
        TextView note = help("La clave se cifra en este dispositivo. Solo se permiten servidores HTTPS.");
        note.setPadding(0, dp(12), 0, 0);
        connection.addView(note);
        action(connection, "Borrar datos guardados", false, this::confirmErase);

        LinearLayout send = card(content);
        send.addView(sectionLabel("02  /  REDACTAR"));
        send.addView(sectionTitle("Nuevo mensaje"));
        phoneField = field(send, "Número de WhatsApp", "573001234567", InputType.TYPE_CLASS_PHONE, false);
        TextView phoneHelp = help("Incluye indicativo del país, sin + ni espacios. Ejemplo: 57 para Colombia.");
        phoneHelp.setPadding(0, 0, 0, dp(8));
        send.addView(phoneHelp);
        messageField = field(send, "Mensaje", "Escribe aquí tu mensaje...", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true);
        counterView = text("0 / 4096", 12, MUTED, Typeface.NORMAL);
        counterView.setGravity(Gravity.END);
        send.addView(counterView);
        messageField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                counterView.setText(s.length() + " / 4096");
                counterView.setTextColor(s.length() > 4096 ? Color.RED : MUTED);
            }
            public void afterTextChanged(Editable s) {}
        });
        sendButton = action(send, "ENVIAR MENSAJE  ↗", true, this::confirmSend);
        resultView = text("Aquí aparecerá el resultado del envío.", 13, MUTED, Typeface.NORMAL);
        resultView.setPadding(dp(12), dp(13), dp(12), dp(13));
        resultView.setBackground(round(PAPER, 12, 0));
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.topMargin = dp(14);
        send.addView(resultView, resultParams);

        TextView footer = help("Versión 1.0  •  Cliente Evolution API v2\nSolo envía a destinatarios que aceptaron recibir mensajes.");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(8), dp(10), dp(24));
        content.addView(footer);
        setContentView(scroll);
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = vertical();
        card.setPadding(dp(20), dp(21), dp(20), dp(22));
        card.setBackground(round(Color.WHITE, 22, 0xFFE2E7E3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(15);
        parent.addView(card, lp);
        return card;
    }
    private TextView text(String s, int sp, int color, int weight) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, weight);
        return v;
    }
    private TextView sectionLabel(String str) {
        TextView t = text(str, 10, GREEN, Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }
    private TextView sectionTitle(String str) {
        TextView t = text(str, 22, NAVY, Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }
    private TextView help(String str) {
        TextView t = text(str, 12, MUTED, Typeface.NORMAL);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }
    private GradientDrawable round(int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }
    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }

    private EditText field(LinearLayout parent, String label, String hint, int type, boolean multiline) {
        TextView head = text(label, 12, NAVY, Typeface.BOLD);
        head.setPadding(0, dp(12), 0, dp(7));
        parent.addView(head);
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(NAVY);
        e.setHintTextColor(0xFF9DAAA5);
        e.setBackground(round(0xFFF6F8F6, 12, 0xFFE2EAE5));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setInputType(type);
        if (multiline) {
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinLines(4);
            e.setMaxLines(8);
        } else {
            e.setSingleLine(true);
        }
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }
    private Button action(LinearLayout parent, String title, boolean primary, Runnable callback) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : GREEN);
        b.setBackground(round(primary ? GREEN : MINT, 12, 0));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setMinHeight(dp(52));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        parent.addView(b, p);
        b.setOnClickListener(v -> callback.run());
        return b;
    }

    private String val(EditText e) { return e.getText().toString().trim(); }
    private String cleanBaseUrl() {
        String input = val(urlField);
        while (input.endsWith("/")) input = input.substring(0, input.length() - 1);
        try {
            URL url = new URL(input);
            if (!url.getProtocol().equalsIgnoreCase("https") || url.getHost().isEmpty() ||
                    url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) {
                throw new IllegalArgumentException("La URL debe comenzar con https:// y no contener consultas ni credenciales.");
            }
            return input;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Escribe una URL HTTPS válida de Evolution API.");
        }
    }
    private String getInstanceName() {
        String name = val(instanceField);
        if (!name.matches("[A-Za-z0-9_.-]{1,90}"))
            throw new IllegalArgumentException("Revisa el nombre de la instancia: no debe tener espacios ni barras.");
        return name;
    }
    private String getApiKey() {
        String key = val(apiField);
        if (key.isEmpty()) throw new IllegalArgumentException("Falta la API Key.");
        return key;
    }
    private String normalizedPhone() {
        String raw = val(phoneField);
        if (raw.startsWith("+")) raw = raw.substring(1);
        String digits = raw.replaceAll("[\\s()\\-]", "");
        if (!digits.matches("[1-9][0-9]{9,14}"))
            throw new IllegalArgumentException("Escribe el número completo con indicativo. Ejemplo: 573001234567.");
        return digits;
    }
    private void setStatus(String message, boolean connected) {
        statusView.setText((connected ? "● " : "○ ") + message);
        statusView.setTextColor(connected ? 0xFFA0E5C2 : 0xFFF3E8CF);
    }
    private void showError(String str) {
        new AlertDialog.Builder(this).setTitle("Revisa estos datos").setMessage(str)
                .setPositiveButton("Entendido", null).show();
    }
    private void checkConnection() {
        final String base, instance, api;
        try {
            base = cleanBaseUrl(); instance = getInstanceName(); api = getApiKey();
        } catch (IllegalArgumentException ex) { showError(ex.getMessage()); return; }
        if (busy) return;
        setBusy(true);
        setStatus("Comprobando instancia...", false);
        executor.execute(() -> {
            try {
                ApiResponse result = request(base + "/instance/connectionState/" + instance, api, null);
                if (!result.isSuccess()) throw new Exception(httpError(result));
                JSONObject response = new JSONObject(result.body);
                String state = response.optJSONObject("instance") != null
                        ? response.optJSONObject("instance").optString("state", "")
                        : response.optString("state", response.optString("connectionStatus", ""));
                boolean connected = "open".equalsIgnoreCase(state) || "connected".equalsIgnoreCase(state);
                String outcome = connected ? "Instancia conectada" : (state.isEmpty()
                        ? "Respuesta recibida (estado no identificado)" : "Estado: " + state);
                runOnUiThread(() -> { setStatus(outcome, connected); setBusy(false); });
            } catch (Exception ex) {
                runOnUiThread(() -> { setStatus("No se pudo conectar", false); setBusy(false); showError(ex.getMessage()); });
            }
        });
    }
    private void confirmSend() {
        final String base, instance, key, phone, message;
        try {
            base = cleanBaseUrl(); instance = getInstanceName(); key = getApiKey();
            phone = normalizedPhone(); message = messageField.getText().toString().trim();
            if (message.isEmpty()) throw new IllegalArgumentException("Escribe un mensaje.");
            if (message.length() > 4096) throw new IllegalArgumentException("El máximo es de 4096 caracteres.");
        } catch (IllegalArgumentException ex) { showError(ex.getMessage()); return; }
        if (busy) return;
        new AlertDialog.Builder(this)
                .setTitle("Confirmar envío")
                .setMessage("¿Enviar un mensaje de WhatsApp a +" + phone + "?\n\n" +
                        (message.length() > 140 ? message.substring(0, 140) + "…" : message))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Enviar", (d, w) -> send(base, instance, key, phone, message))
                .show();
    }
    private void send(String base, String instance, String key, String phone, String message) {
        setBusy(true);
        resultView.setText("Enviando solicitud a Evolution API...");
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(messageField.getWindowToken(), 0);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("number", phone);
                body.put("text", message);
                ApiResponse result = request(base + "/message/sendText/" + instance, key, body);
                if (!result.isSuccess()) throw new Exception(httpError(result));
                JSONObject response = new JSONObject(result.body);
                String id = response.optJSONObject("key") == null ? "" :
                        response.optJSONObject("key").optString("id", "");
                String status = response.optString("status", "ACEPTADO");
                String feedback = "✓ Solicitud aceptada por Evolution API (HTTP " + result.code + ")." +
                        "\nEstado inicial: " + status +
                        (id.isEmpty() ? "" : "\nID: " + id) +
                        "\nNota: aceptado no significa que WhatsApp ya lo entregó.";
                runOnUiThread(() -> { resultView.setText(feedback); resultView.setTextColor(GREEN); setBusy(false); });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    resultView.setText("✕ No se confirmó el envío.\n" + ex.getMessage() +
                            "\nSi hubo un error de red, comprueba el historial antes de reintentar para evitar duplicados.");
                    resultView.setTextColor(0xFFAD322F);
                    setBusy(false);
                });
            }
        });
    }

    private static class ApiResponse {
        final int code;
        final String body;
        ApiResponse(int code, String body) { this.code = code; this.body = body; }
        boolean isSuccess() { return code >= 200 && code < 300; }
    }
    private ApiResponse request(String path, String apiKey, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(path).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(payload == null ? "GET" : "POST");
            connection.setRequestProperty("apikey", apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setInstanceFollowRedirects(false); // Evita reenviar la clave a otro host.
            if (payload != null) {
                connection.setDoOutput(true);
                byte[] encoded = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(encoded.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(encoded); }
            }
            int code = connection.getResponseCode();
            InputStream source = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";
            if (source != null) {
                try (InputStream stream = source; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] bytes = new byte[4096];
                    int n;
                    while ((n = stream.read(bytes)) != -1 && buffer.size() < 30000) buffer.write(bytes, 0, n);
                    body = buffer.toString("UTF-8");
                }
            }
            return new ApiResponse(code, body);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
    private String httpError(ApiResponse response) {
        switch (response.code) {
            case 400: return "HTTP 400: el servidor rechazó la solicitud. Revisa la versión de Evolution API y los datos.";
            case 401: case 403: return "HTTP " + response.code + ": API Key inválida o sin permisos.";
            case 404: return "HTTP 404: revisa el nombre de la instancia y la URL del servidor.";
            case 429: return "HTTP 429: demasiadas solicitudes. Vuelve a intentarlo más adelante.";
            default:
                String extra = response.body.length() > 240 ? response.body.substring(0, 240) + "…" : response.body;
                return "HTTP " + response.code + (extra.isEmpty() ? "" : ": " + extra);
        }
    }
    private void setBusy(boolean inProgress) {
        busy = inProgress;
        checkButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        checkButton.setAlpha(busy ? .6f : 1f);
        sendButton.setAlpha(busy ? .6f : 1f);
    }

    private void saveCredentials() {
        try {
            String url = cleanBaseUrl(), instance = getInstanceName(), api = getApiKey();
            String encrypted = encrypt(api);
            preferences.edit().putString("url", url).putString("instance", instance)
                    .putString("encryptedKey", encrypted).apply();
            new AlertDialog.Builder(this).setTitle("Configuración guardada")
                    .setMessage("La API Key quedó cifrada usando el almacén de claves de Android.")
                    .setPositiveButton("Entendido", null).show();
        } catch (Exception ex) { showError("No se pudo guardar: " + ex.getMessage()); }
    }
    private void restoreCredentials() {
        urlField.setText(preferences.getString("url", ""));
        instanceField.setText(preferences.getString("instance", ""));
        String ciphered = preferences.getString("encryptedKey", "");
        if (!ciphered.isEmpty()) {
            try { apiField.setText(decrypt(ciphered)); }
            catch (Exception ex) { preferences.edit().remove("encryptedKey").apply(); }
        }
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    private String encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    private String decrypt(String data) throws Exception {
        String[] parts = data.split(":", 2);
        if (parts.length != 2) throw new Exception("Formato de credencial inválido");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    private void confirmErase() {
        new AlertDialog.Builder(this).setTitle("Eliminar credenciales")
                .setMessage("¿Quieres eliminar de este dispositivo la URL, la instancia y la API Key guardadas?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    preferences.edit().clear().apply();
                    try {
                        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
                        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
                    } catch (Exception ignored) {}
                    urlField.setText(""); instanceField.setText(""); apiField.setText("");
                    setStatus("Sin comprobar la conexión", false);
                }).show();
    }
    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
