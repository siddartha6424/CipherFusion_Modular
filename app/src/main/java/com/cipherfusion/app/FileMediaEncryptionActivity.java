package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileMediaEncryptionActivity extends Activity {

    private static final int REQUEST_INPUT = 4101;
    private static final int REQUEST_ENCRYPT_OUTPUT = 4102;
    private static final int REQUEST_DECRYPT_OUTPUT = 4103;

    private Button selectFileButton;
    private TextView selectedFile;
    private EditText encKey;
    private EditText decKey;
    private Button encryptButton;
    private Button decryptButton;
    private ProgressBar progress;
    private TextView result;

    private Uri selectedUri;
    private String selectedName = "file";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_media_encryption);

        selectFileButton = findViewById(R.id.selectFileButton);
        selectedFile = findViewById(R.id.selectedFile);
        encKey = findViewById(R.id.encKey);
        decKey = findViewById(R.id.decKey);
        encryptButton = findViewById(R.id.encryptButton);
        decryptButton = findViewById(R.id.decryptButton);
        progress = findViewById(R.id.progress);
        result = findViewById(R.id.result);

        selectFileButton.setOnClickListener(v -> openFilePicker());
        encryptButton.setOnClickListener(v -> startEncryption());
        decryptButton.setOnClickListener(v -> startDecryption());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_INPUT);
    }

    private void startEncryption() {
        if (selectedUri == null) {
            showError("Select a file first.");
            return;
        }

        String key = encKey.getText().toString();
        if (key.isEmpty()) {
            showError("Enter an encryption key.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, selectedName + ".cffile");
        startActivityForResult(intent, REQUEST_ENCRYPT_OUTPUT);
    }

    private void startDecryption() {
        if (selectedUri == null) {
            showError("Select a CFF2 encrypted file first.");
            return;
        }

        String key = decKey.getText().toString();
        if (key.isEmpty()) {
            showError("Enter a decryption key.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, removeExtension(selectedName));
        startActivityForResult(intent, REQUEST_DECRYPT_OUTPUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == REQUEST_INPUT) {
            selectedUri = uri;
            selectedName = getFileName(uri);
            selectedFile.setText(
                    selectedName + "\n" + formatType(uri));
            result.setVisibility(View.GONE);
        } else if (requestCode == REQUEST_ENCRYPT_OUTPUT) {
            encryptTo(uri);
        } else if (requestCode == REQUEST_DECRYPT_OUTPUT) {
            decryptTo(uri);
        }
    }

    private void encryptTo(Uri outputUri) {
        final String key = encKey.getText().toString();

        setProcessing(true, "Encrypting...");

        executor.execute(() -> {
            try {
                FileEncryptionEngine.encrypt(
                        this,
                        selectedUri,
                        outputUri,
                        key,
                        selectedName,
                        (processed, total) ->
                                updateProgress(processed, total));

                mainHandler.post(() -> {
                    setProcessing(false, null);
                    result.setVisibility(View.VISIBLE);
                    result.setText(
                            "✓ ENCRYPTION COMPLETE\n\n" +
                            "Input: " + selectedName + "\n" +
                            "Format: CFF2 / AES-256-GCM\n" +
                            "Memory mode: streaming\n\n" +
                            "Encrypted file created successfully."
                    );
                });
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private void decryptTo(Uri outputUri) {
        final String key = decKey.getText().toString();

        setProcessing(true, "Decrypting...");

        executor.execute(() -> {
            try {
                FileEncryptionEngine.decrypt(
                        this,
                        selectedUri,
                        outputUri,
                        key,
                        (processed, total) ->
                                updateProgress(processed, total));

                mainHandler.post(() -> {
                    setProcessing(false, null);
                    result.setVisibility(View.VISIBLE);
                    result.setText(
                            "✓ DECRYPTION COMPLETE\n\n" +
                            "Source: " + selectedName + "\n" +
                            "Authentication: verified\n" +
                            "Memory mode: streaming\n\n" +
                            "Decrypted file created successfully."
                    );
                });
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private void updateProgress(long processed, long total) {
        mainHandler.post(() -> {
            if (total > 0) {
                int percent = (int) Math.min(
                        100L, (processed * 100L) / total);
                progress.setProgress(percent);
            } else {
                progress.setIndeterminate(true);
            }
        });
    }

    private void setProcessing(boolean processing, String message) {
        mainHandler.post(() -> {
            progress.setVisibility(processing ? View.VISIBLE : View.GONE);
            if (processing) {
                progress.setIndeterminate(false);
                progress.setProgress(0);
                result.setVisibility(View.VISIBLE);
                result.setText(message == null ? "Processing..." : message);
            }
            selectFileButton.setEnabled(!processing);
            encryptButton.setEnabled(!processing);
            decryptButton.setEnabled(!processing);
        });
    }

    private void showError(String message) {
        final String errorMessage =
                (message == null || message.trim().isEmpty())
                        ? "Unknown error occurred."
                        : message;

        mainHandler.post(() -> {
            setProcessing(false, null);
            result.setVisibility(View.VISIBLE);
            result.setText("✕ OPERATION FAILED\n\n" + errorMessage);
            Toast.makeText(
                    FileMediaEncryptionActivity.this,
                    errorMessage,
                    Toast.LENGTH_LONG).show();
        });
    }

    private String getFileName(Uri uri) {
        String name = null;

        try (android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {}

        if (name == null || name.trim().isEmpty()) {
            name = "selected_file";
        }

        return name;
    }

    private String formatType(Uri uri) {
        String type = getContentResolver().getType(uri);
        return "Type: " + (type == null ? "unknown" : type);
    }

    private String removeExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
