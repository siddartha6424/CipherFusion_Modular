package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class FileEncryptionActivity extends Activity {

 private static final int REQUEST_OPEN_ENCRYPT = 1001;
 private static final int REQUEST_OPEN_DECRYPT = 1002;
 private static final int REQUEST_CREATE_ENCRYPT = 2001;
 private static final int REQUEST_CREATE_DECRYPT = 2002;

 private EditText encKey;
 private EditText decKey;
 private TextView selectedFile;
 private TextView result;
 private ProgressBar progress;

 private Button selectFileButton;
 private Button encryptButton;
 private Button decryptButton;

 private Uri selectedFileUri;

 private final Handler mainHandler =
         new Handler(Looper.getMainLooper());

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  setContentView(R.layout.activity_file_encryption);

  encKey = findViewById(R.id.encKey);
  decKey = findViewById(R.id.decKey);
  selectedFile = findViewById(R.id.selectedFile);
  result = findViewById(R.id.result);
  progress = findViewById(R.id.progress);

  selectFileButton = findViewById(R.id.selectFileButton);
  encryptButton = findViewById(R.id.encryptButton);
  decryptButton = findViewById(R.id.decryptButton);

  progress.setVisibility(View.GONE);
  result.setVisibility(View.GONE);

  selectFileButton.setOnClickListener(
          v -> openFilePicker()
  );

  encryptButton.setOnClickListener(
          v -> startEncryption()
  );

  decryptButton.setOnClickListener(
          v -> startDecryption()
  );
 }

 // =========================================================
 // FILE PICKER
 // =========================================================

 private void openFilePicker() {

  Intent intent =
          new Intent(Intent.ACTION_OPEN_DOCUMENT);

  intent.addCategory(
          Intent.CATEGORY_OPENABLE
  );

  intent.setType("*/*");

  startActivityForResult(
          intent,
          REQUEST_OPEN_ENCRYPT
  );
 }

 // =========================================================
 // START ENCRYPTION
 // =========================================================

 private void startEncryption() {

  if (selectedFileUri == null) {

   Toast.makeText(
           this,
           "Please select a file first",
           Toast.LENGTH_SHORT
   ).show();

   return;
  }

  String key =
          encKey.getText()
                  .toString()
                  .trim();

  if (key.isEmpty()) {
   key = "CIPHER";
  }

  createEncryptedOutput(key);
 }

 // =========================================================
 // START DECRYPTION
 // =========================================================

 private void startDecryption() {

  if (selectedFileUri == null) {

   Toast.makeText(
           this,
           "Please select a file first",
           Toast.LENGTH_SHORT
   ).show();

   return;
  }

  String key =
          decKey.getText()
                  .toString()
                  .trim();

  if (key.isEmpty()) {
   key = "CIPHER";
  }

  createDecryptedOutput(key);
 }

 // =========================================================
 // CREATE ENCRYPTED OUTPUT
 // =========================================================

 private void createEncryptedOutput(String key) {

  String originalName =
          getFileName(selectedFileUri);

  if (originalName == null ||
          originalName.trim().isEmpty()) {

   originalName = "encrypted_file";
  }

  String outputName =
          originalName + ".cffile";

  Intent intent =
          new Intent(Intent.ACTION_CREATE_DOCUMENT);

  intent.addCategory(
          Intent.CATEGORY_OPENABLE
  );

  intent.setType(
          "application/octet-stream"
  );

  intent.putExtra(
          Intent.EXTRA_TITLE,
          outputName
  );

  startActivityForResult(
          intent,
          REQUEST_CREATE_ENCRYPT
  );
 }

 // =========================================================
 // CREATE DECRYPTED OUTPUT
 // =========================================================

 private void createDecryptedOutput(String key) {

  String originalName =
          getFileName(selectedFileUri);

  if (originalName == null ||
          originalName.trim().isEmpty()) {

   originalName = "decrypted_file";
  }

  String outputName =
          decryptedFileName(originalName);

  Intent intent =
          new Intent(Intent.ACTION_CREATE_DOCUMENT);

  intent.addCategory(
          Intent.CATEGORY_OPENABLE
  );

  intent.setType(
          "application/octet-stream"
  );

  intent.putExtra(
          Intent.EXTRA_TITLE,
          outputName
  );

  startActivityForResult(
          intent,
          REQUEST_CREATE_DECRYPT
  );
 }

 // =========================================================
 // ACTIVITY RESULT
 // =========================================================

 @Override
 protected void onActivityResult(
         int requestCode,
         int resultCode,
         Intent data) {

  super.onActivityResult(
          requestCode,
          resultCode,
          data
  );

  if (resultCode != RESULT_OK ||
          data == null ||
          data.getData() == null) {

   return;
  }

  Uri uri = data.getData();

  // -----------------------------------------------------
  // FILE SELECTED
  // -----------------------------------------------------

  if (requestCode == REQUEST_OPEN_ENCRYPT ||
          requestCode == REQUEST_OPEN_DECRYPT) {

   selectedFileUri = uri;

   String name =
           getFileName(uri);

   if (name == null) {
    name = "Selected file";
   }

   selectedFile.setText(
           "✓ " + name
   );

   result.setVisibility(
           View.GONE
   );

   return;
  }

  // -----------------------------------------------------
  // ENCRYPTION OUTPUT SELECTED
  // -----------------------------------------------------

  if (requestCode == REQUEST_CREATE_ENCRYPT) {

   String key =
           encKey.getText()
                   .toString()
                   .trim();

   if (key.isEmpty()) {
    key = "CIPHER";
   }

   doEncryption(
           selectedFileUri,
           key,
           uri
   );

   return;
  }

  // -----------------------------------------------------
  // DECRYPTION OUTPUT SELECTED
  // -----------------------------------------------------

  if (requestCode == REQUEST_CREATE_DECRYPT) {

   String key =
           decKey.getText()
                   .toString()
                   .trim();

   if (key.isEmpty()) {
    key = "CIPHER";
   }

   doDecryption(
           selectedFileUri,
           key,
           uri
   );
  }
 }

 // =========================================================
 // ENCRYPTION
 // =========================================================

 private void doEncryption(
         Uri inputUri,
         String key,
         Uri outputUri) {

  setProcessing(true);

  new Thread(() -> {

   InputStream inputStream = null;
   OutputStream outputStream = null;

   try {

    inputStream =
            getContentResolver()
                    .openInputStream(
                            inputUri
                    );

    outputStream =
            getContentResolver()
                    .openOutputStream(
                            outputUri
                    );

    if (inputStream == null) {

     throw new Exception(
             "Unable to open input file."
     );
    }

    if (outputStream == null) {

     throw new Exception(
             "Unable to create output file."
     );
    }

    long totalSize =
            getSize(inputUri);

    String extension =
            extension(
                    getFileName(inputUri)
            );

    final String finalKey = key;

    FileEncryptionEngine.encrypt(
            inputStream,
            outputStream,
            finalKey,
            extension,
            totalSize,

            // PROGRESS CALLBACK
            (processed, total) -> {

             int percent = 0;

             if (total > 0) {

              percent =
                      (int) Math.min(
                              100L,
                              (processed * 100L)
                                      / total
                      );
             }

             updateProgress(
                     percent
             );
            }
    );

    closeQuietly(inputStream);
    closeQuietly(outputStream);

    inputStream = null;
    outputStream = null;

    String inputName =
            getFileName(inputUri);

    String outputName =
            getFileName(outputUri);

    String cipherPreview =
            createCipherPreview(
                    outputUri
            );

    showEncryptionResult(
            inputName,
            outputName,
            cipherPreview
    );

   } catch (Exception e) {

    closeQuietly(inputStream);
    closeQuietly(outputStream);

    showError(
            e.getMessage()
    );
   }

  }).start();
 }

 // =========================================================
 // DECRYPTION
 // =========================================================

 private void doDecryption(
         Uri inputUri,
         String key,
         Uri outputUri) {

  setProcessing(true);

  new Thread(() -> {

   InputStream inputStream = null;
   OutputStream outputStream = null;

   try {

    inputStream =
            getContentResolver()
                    .openInputStream(
                            inputUri
                    );

    outputStream =
            getContentResolver()
                    .openOutputStream(
                            outputUri
                    );

    if (inputStream == null) {

     throw new Exception(
             "Unable to open encrypted file."
     );
    }

    if (outputStream == null) {

     throw new Exception(
             "Unable to create restored file."
     );
    }

    long totalSize =
            getSize(inputUri);

    final String finalKey = key;

    FileEncryptionEngine.decrypt(
            inputStream,
            outputStream,
            finalKey,
            totalSize,

            // PROGRESS CALLBACK
            (processed, total) -> {

             int percent = 0;

             if (total > 0) {

              percent =
                      (int) Math.min(
                              100L,
                              (processed * 100L)
                                      / total
                      );
             }

             updateProgress(
                     percent
             );
            }
    );

    closeQuietly(inputStream);
    closeQuietly(outputStream);

    inputStream = null;
    outputStream = null;

    String encryptedName =
            getFileName(inputUri);

    String restoredName =
            getFileName(outputUri);

    showDecryptionResult(
            encryptedName,
            restoredName
    );

   } catch (Exception e) {

    closeQuietly(inputStream);
    closeQuietly(outputStream);

    showError(
            e.getMessage()
    );
   }

  }).start();
 }

 // =========================================================
 // CIPHERTEXT HEX PREVIEW
 // =========================================================

 private String createCipherPreview(
         Uri outputUri) {

  InputStream input = null;

  try {

   input =
           getContentResolver()
                   .openInputStream(
                           outputUri
                   );

   if (input == null) {

    return "Unable to read ciphertext preview.";
   }

   byte[] buffer =
           new byte[128];

   int read =
           input.read(buffer);

   if (read <= 0) {

    return "No ciphertext data available.";
   }

   StringBuilder hex =
           new StringBuilder();

   for (int i = 0; i < read; i++) {

    if (i > 0) {

     if (i % 16 == 0) {
      hex.append("\n");
     } else {
      hex.append(" ");
     }
    }

    hex.append(
            String.format(
                    Locale.US,
                    "%02X",
                    buffer[i] & 0xFF
            )
    );
   }

   if (read == 128) {

    hex.append(
            "\n\n... PREVIEW ONLY ..."
    );
   }

   return hex.toString();

  } catch (Exception e) {

   return "Unable to generate ciphertext preview.";

  } finally {

   closeQuietly(input);
  }
 }

 // =========================================================
 // ENCRYPTION RESULT
 // =========================================================

 private void showEncryptionResult(
         String inputName,
         String outputName,
         String cipherPreview) {

  mainHandler.post(() -> {

   setProcessing(false);

   result.setVisibility(
           View.VISIBLE
   );

   result.setText(
           "✓ ENCRYPTION COMPLETE\n\n" +

                   "INPUT FILE\n" +
                   safeName(inputName) +
                   "\n\n" +

                   "OUTPUT FILE\n" +
                   safeName(outputName) +
                   "\n\n" +

                   "CIPHERTEXT PREVIEW (HEX)\n\n" +

                   cipherPreview +
                   "\n\n" +

                   "FILE SECURED SUCCESSFULLY"
   );
  });
 }

 // =========================================================
 // DECRYPTION RESULT
 // =========================================================

 private void showDecryptionResult(
         String encryptedName,
         String restoredName) {

  mainHandler.post(() -> {

   setProcessing(false);

   result.setVisibility(
           View.VISIBLE
   );

   result.setText(
           "✓ DECRYPTION COMPLETE\n\n" +

                   "ENCRYPTED FILE\n" +
                   safeName(encryptedName) +
                   "\n\n" +

                   "RESTORED FILE\n" +
                   safeName(restoredName) +
                   "\n\n" +

                   "FILE RESTORED SUCCESSFULLY"
   );
  });
 }

 // =========================================================
 // ERROR RESULT
 // =========================================================

 private void showError(String message) {

  final String errorMessage =
          (message == null ||
                  message.trim().isEmpty())
                  ? "Unknown error occurred."
                  : message;

  mainHandler.post(() -> {

   setProcessing(false);

   result.setVisibility(
           View.VISIBLE
   );

   result.setText(
           "✕ OPERATION FAILED\n\n" +
                   errorMessage
   );
  });
 }

 // =========================================================
 // PROCESSING STATE
 // =========================================================

 private void setProcessing(
         boolean processing) {

  mainHandler.post(() -> {

   if (processing) {

    progress.setVisibility(
            View.VISIBLE
    );

    progress.setProgress(0);

    selectFileButton.setEnabled(
            false
    );

    encryptButton.setEnabled(
            false
    );

    decryptButton.setEnabled(
            false
    );

   } else {

    progress.setProgress(100);

    progress.setVisibility(
            View.GONE
    );

    selectFileButton.setEnabled(
            true
    );

    encryptButton.setEnabled(
            true
    );

    decryptButton.setEnabled(
            true
    );
   }
  });
 }

 // =========================================================
 // UPDATE PROGRESS
 // =========================================================

 private void updateProgress(
         int percent) {

  mainHandler.post(() -> {

   int safePercent =
           Math.max(
                   0,
                   Math.min(
                           100,
                           percent
                   )
           );

   progress.setProgress(
           safePercent
   );
  });
 }

 // =========================================================
 // GET FILE SIZE
 // =========================================================

 private long getSize(Uri uri) {

  Cursor cursor = null;

  try {

   cursor =
           getContentResolver()
                   .query(
                           uri,
                           new String[]{
                                   OpenableColumns.SIZE
                           },
                           null,
                           null,
                           null
                   );

   if (cursor != null &&
           cursor.moveToFirst()) {

    int index =
            cursor.getColumnIndex(
                    OpenableColumns.SIZE
            );

    if (index >= 0 &&
            !cursor.isNull(index)) {

     return cursor.getLong(index);
    }
   }

  } catch (Exception ignored) {

  } finally {

   if (cursor != null) {
    cursor.close();
   }
  }

  return -1;
 }

 // =========================================================
 // GET FILE NAME
 // =========================================================

 private String getFileName(Uri uri) {

  if (uri == null) {
   return null;
  }

  Cursor cursor = null;

  try {

   cursor =
           getContentResolver()
                   .query(
                           uri,
                           new String[]{
                                   OpenableColumns.DISPLAY_NAME
                           },
                           null,
                           null,
                           null
                   );

   if (cursor != null &&
           cursor.moveToFirst()) {

    int index =
            cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
            );

    if (index >= 0) {

     return cursor.getString(index);
    }
   }

  } catch (Exception ignored) {

  } finally {

   if (cursor != null) {
    cursor.close();
   }
  }

  return null;
 }

 // =========================================================
 // FILE EXTENSION
 // =========================================================

 private String extension(
         String name) {

  if (name == null) {
   return "";
  }

  int dot =
          name.lastIndexOf('.');

  if (dot < 0 ||
          dot == name.length() - 1) {

   return "";
  }

  return name.substring(
          dot + 1
  );
 }

 // =========================================================
 // DECRYPTED FILE NAME
 // =========================================================

 private String decryptedFileName(
         String name) {

  if (name == null ||
          name.trim().isEmpty()) {

   return "decrypted_file";
  }

  if (name.toLowerCase(Locale.US)
          .endsWith(".cffile")) {

   String stripped =
           name.substring(
                   0,
                   name.length() - 7
           );

   if (!stripped.isEmpty()) {
    return stripped;
   }
  }

  return "decrypted_" + name;
 }

 // =========================================================
 // SAFE FILE NAME
 // =========================================================

 private String safeName(
         String name) {

  if (name == null ||
          name.trim().isEmpty()) {

   return "Unknown file";
  }

  return name;
 }

 // =========================================================
 // CLOSE INPUT STREAM
 // =========================================================

 private void closeQuietly(
         InputStream input) {

  if (input != null) {

   try {
    input.close();
   } catch (Exception ignored) {
   }
  }
 }

 // =========================================================
 // CLOSE OUTPUT STREAM
 // =========================================================

 private void closeQuietly(
         OutputStream output) {

  if (output != null) {

   try {
    output.close();
   } catch (Exception ignored) {
   }
  }
 }
}