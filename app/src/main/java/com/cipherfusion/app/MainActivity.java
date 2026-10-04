package com.cipherfusion.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ObjectAnimator;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private EditText encryptMessage;
    private EditText encryptKey;
    private EditText decryptMessage;
    private EditText decryptKey;

    private TextView encryptResult;
    private TextView decryptResult;
    private TextView encryptTime;
    private TextView decryptTime;
    private TextView encryptCounter;
    private TextView decryptCounter;

    private View homeScreen;
    private View encryptScreen;
    private View decryptScreen;
    private View aboutScreen;

    private TextView navHome;
    private TextView navEncrypt;
    private TextView navDecrypt;
    private TextView navAbout;
    private TextView status;

    private static final int TABLE_SIZE = 256;
    private static final int ANIMATION_MS = 220;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupNavigation();
        setupButtons();
        setupCounters();
        setupKeyEyes();
        setupPressAnimations();
        startStatusPulse();
        showScreen("home", false);
    }

    private void initializeViews() {
        encryptMessage = findViewById(R.id.encryptMessage);
        encryptKey = findViewById(R.id.encryptKey);
        decryptMessage = findViewById(R.id.decryptMessage);
        decryptKey = findViewById(R.id.decryptKey);
        encryptResult = findViewById(R.id.encryptResult);
        decryptResult = findViewById(R.id.decryptResult);
        encryptTime = findViewById(R.id.encryptTime);
        decryptTime = findViewById(R.id.decryptTime);
        encryptCounter = findViewById(R.id.encryptCounter);
        decryptCounter = findViewById(R.id.decryptCounter);
        homeScreen = findViewById(R.id.homeScreen);
        encryptScreen = findViewById(R.id.encryptScreen);
        decryptScreen = findViewById(R.id.decryptScreen);
        aboutScreen = findViewById(R.id.aboutScreen);
        navHome = findViewById(R.id.navHome);
        navEncrypt = findViewById(R.id.navEncrypt);
        navDecrypt = findViewById(R.id.navDecrypt);
        navAbout = findViewById(R.id.navAbout);
        status = findViewById(R.id.status);
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> showScreen("home", true));
        navEncrypt.setOnClickListener(v -> showScreen("encrypt", true));
        navDecrypt.setOnClickListener(v -> showScreen("decrypt", true));
        navAbout.setOnClickListener(v -> showScreen("about", true));
        findViewById(R.id.goEncrypt).setOnClickListener(v -> showScreen("encrypt", true));
    }

    private void setupButtons() {
        findViewById(R.id.random).setOnClickListener(v -> {
            String key = randomKey();
            encryptKey.setText(key);
            decryptKey.setText(key);
            hideKey(encryptKey);
            hideKey(decryptKey);
            pulse(v);
            Toast.makeText(this, "New encryption key generated", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.encrypt).setOnClickListener(v -> encrypt());
        findViewById(R.id.decrypt).setOnClickListener(v -> decrypt());
        findViewById(R.id.copyEncrypt).setOnClickListener(v -> copyText(encryptResult.getText().toString()));
        findViewById(R.id.copyDecrypt).setOnClickListener(v -> copyText(decryptResult.getText().toString()));

        findViewById(R.id.clearEncrypt).setOnClickListener(v -> {
            encryptMessage.setText("");
            encryptResult.setText("Encrypted output will appear here.");
            encryptTime.setText("Execution time: 0.000 ms");
            revealResult(encryptResult);
            updateCounters();
        });

        findViewById(R.id.clearDecrypt).setOnClickListener(v -> {
            decryptMessage.setText("");
            decryptResult.setText("Decrypted message will appear here.");
            decryptTime.setText("Execution time: 0.000 ms");
            revealResult(decryptResult);
            updateCounters();
        });
    }

    private void setupCounters() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateCounters(); }
            public void afterTextChanged(android.text.Editable s) { }
        };
        encryptMessage.addTextChangedListener(watcher);
        decryptMessage.addTextChangedListener(watcher);
        updateCounters();
    }

    private void updateCounters() {
        encryptCounter.setText(encryptMessage.getText().length() + " characters");
        decryptCounter.setText(decryptMessage.getText().length() + " characters");
    }

    private void setupKeyEyes() {
        setupEye(encryptKey);
        setupEye(decryptKey);
        hideKey(encryptKey);
        hideKey(decryptKey);
    }

    private void setupEye(EditText field) {
        field.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            if (field.getCompoundDrawablesRelative()[2] == null) return false;
            int iconWidth = field.getCompoundDrawablesRelative()[2].getBounds().width();
            boolean onIcon = event.getX() >= field.getWidth() - field.getPaddingEnd() - iconWidth - 16;
            if (onIcon) {
                toggleKey(field);
                return true;
            }
            return false;
        });
    }

    private void toggleKey(EditText field) {
        boolean hidden = field.getTransformationMethod() instanceof PasswordTransformationMethod;
        int cursor = field.getSelectionStart();
        if (hidden) {
            field.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            field.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_eye, 0);
        } else {
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            field.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
        }
        field.setSelection(Math.max(0, cursor));
        field.animate().scaleX(1.02f).scaleY(1.02f).setDuration(90).withEndAction(() ->
                field.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        ).start();
    }

    private void hideKey(EditText field) {
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
        field.setSelection(field.length());
    }

    private void setupPressAnimations() {
        int[] ids = {R.id.goEncrypt, R.id.random, R.id.encrypt, R.id.decrypt,
                R.id.copyEncrypt, R.id.clearEncrypt, R.id.copyDecrypt, R.id.clearDecrypt,
                R.id.navHome, R.id.navEncrypt, R.id.navDecrypt, R.id.navAbout};
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) addPressAnimation(v);
        }
    }

    private void addPressAnimation(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(.97f).scaleY(.97f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });
    }

    private void showScreen(String screen, boolean animate) {
        View target;
        switch (screen) {
            case "encrypt": target = encryptScreen; break;
            case "decrypt": target = decryptScreen; break;
            case "about": target = aboutScreen; break;
            default: target = homeScreen; break;
        }

        homeScreen.setVisibility(View.GONE);
        encryptScreen.setVisibility(View.GONE);
        decryptScreen.setVisibility(View.GONE);
        aboutScreen.setVisibility(View.GONE);
        resetNavigation();

        target.setVisibility(View.VISIBLE);
        target.setAlpha(animate ? 0f : 1f);
        target.setTranslationY(animate ? 24f : 0f);
        if (animate) {
            target.animate().alpha(1f).translationY(0f).setDuration(ANIMATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }

        if (screen.equals("encrypt")) selectNavigation(navEncrypt);
        else if (screen.equals("decrypt")) selectNavigation(navDecrypt);
        else if (screen.equals("about")) selectNavigation(navAbout);
        else selectNavigation(navHome);
    }

    private void resetNavigation() {
        TextView[] navs = {navHome, navEncrypt, navDecrypt, navAbout};
        for (TextView nav : navs) {
            nav.setTextColor(Color.rgb(127, 141, 170));
            nav.setBackgroundResource(R.drawable.bg_nav);
        }
    }

    private void selectNavigation(TextView selected) {
        selected.setTextColor(Color.WHITE);
        selected.setBackgroundResource(R.drawable.bg_nav_selected);
        selected.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).withEndAction(() ->
                selected.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ).start();
    }

    private void startStatusPulse() {
        if (status == null) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(status, View.ALPHA, 1f, .55f, 1f);
        pulse.setDuration(1600);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
    }

    private void pulse(View view) {
        view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(90).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        ).start();
    }

    private void revealResult(TextView result) {
        result.setAlpha(0f);
        result.setTranslationY(12f);
        result.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }

    private void encrypt() {
        String message = encryptMessage.getText().toString();
        String key = cleanKey(encryptKey.getText().toString());
        if (message.isEmpty()) {
            Toast.makeText(this, "Enter a message first", Toast.LENGTH_SHORT).show();
            return;
        }
        encryptKey.setText(key);
        hideKey(encryptKey);
        long start = System.nanoTime();
        try {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] table = createTable(keyBytes);
            byte[] transformed = vigenere(messageBytes, keyBytes, true);
            String ciphertext = bytesToHexWithTable(transformed, table);
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            encryptResult.setText(ciphertext);
            encryptTime.setText(String.format(Locale.US, "Execution time: %.3f ms", ms));
            revealResult(encryptResult);
            Toast.makeText(this, "Vigenere + Polybius encryption complete", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            encryptResult.setText("Encryption error.");
            revealResult(encryptResult);
        }
    }

    private void decrypt() {
        String ciphertext = decryptMessage.getText().toString().trim();
        String key = cleanKey(decryptKey.getText().toString());
        if (ciphertext.isEmpty()) {
            Toast.makeText(this, "Enter ciphertext first", Toast.LENGTH_SHORT).show();
            return;
        }
        decryptKey.setText(key);
        hideKey(decryptKey);
        long start = System.nanoTime();
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] table = createTable(keyBytes);
            byte[] transformed = hexWithTableToBytes(ciphertext, table);
            byte[] original = vigenere(transformed, keyBytes, false);
            String message = decodeUtf8Strict(original);
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            decryptResult.setText(message);
            decryptTime.setText(String.format(Locale.US, "Execution time: %.3f ms", ms));
            revealResult(decryptResult);
            Toast.makeText(this, "Decryption complete", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            decryptResult.setText("Invalid ciphertext or incorrect key.");
            decryptTime.setText("Decryption failed");
            revealResult(decryptResult);
        }
    }

    // Generalized Vigenere over the full 0..255 byte alphabet.
    private byte[] vigenere(byte[] data, byte[] key, boolean encrypt) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            int d = data[i] & 0xFF;
            int k = key[i % key.length] & 0xFF;
            int value = encrypt ? (d + k) & 0xFF : (d - k + 256) & 0xFF;
            result[i] = (byte) value;
        }
        return result;
    }

    // A keyed 16x16 Polybius square containing every possible byte exactly once.
    private byte[] createTable(byte[] key) {
        byte[] table = new byte[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) table[i] = (byte) i;
        long seed = 1469598103934665603L;
        for (byte b : key) {
            seed ^= (b & 0xFF);
            seed *= 1099511628211L;
        }
        Random random = new Random(seed);
        for (int i = TABLE_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            byte tmp = table[i];
            table[i] = table[j];
            table[j] = tmp;
        }
        return table;
    }

    // Polybius coordinate encoding: each byte becomes one 16x16 position, written as two hex digits.
    private String bytesToHexWithTable(byte[] data, byte[] table) {
        int[] positions = new int[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) positions[table[i] & 0xFF] = i;
        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte value : data) result.append(String.format(Locale.US, "%02X", positions[value & 0xFF]));
        return result.toString();
    }

    private byte[] hexWithTableToBytes(String hex, byte[] table) {
        hex = hex.replaceAll("\\s+", "");
        if (hex.length() == 0 || (hex.length() & 1) != 0) throw new IllegalArgumentException("Invalid ciphertext");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int position = Integer.parseInt(hex.substring(i, i + 2), 16);
            if (position < 0 || position >= TABLE_SIZE) throw new IllegalArgumentException("Invalid Polybius coordinate");
            result[i / 2] = table[position];
        }
        return result;
    }

    private String decodeUtf8Strict(byte[] data) throws CharacterCodingException {
        CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data));
        return chars.toString();
    }

    private String cleanKey(String key) {
        key = key.trim();
        return key.isEmpty() ? "CIPHER" : key;
    }

    private String randomKey() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 12; i++) result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return result.toString();
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("CipherFusion", text));
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }
}
