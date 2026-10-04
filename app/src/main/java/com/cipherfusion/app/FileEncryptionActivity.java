package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * CipherFusion - File Encryption Module
 *
 * Handles:
 *  - Selecting files
 *  - Selecting encryption/decryption mode
 *  - Selecting output location
 *  - Running FileEncryptionEngine in a background thread
 *  - Showing progress
 *
 * The actual encryption logic lives in FileEncryptionEngine.
 */
public class FileEncryptionActivity extends Activity {

 private static final int REQUEST_OPEN_ENCRYPT = 1001;
 private static final int REQUEST_OPEN_DECRYPT = 1002;
 private static final int REQUEST_CREATE_ENCRYPT = 2001;
 private static final int REQUEST_CREATE_DECRYPT = 2002;

 private EditText encKey;
 private EditText decKey;

 private TextView selectedFile;
 private TextView progress;

 private Button encryptButton;
 private Button decryptButton;

 private Uri selected;
 private String selectedName = "";

 private boolean waitingForEncryptionOutput = false;
 private boolean waitingForDecryptionOutput = false;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  setContentView(R.layout.activity_file_encryption);

  initializeViews();
  initializeButtons();
 }

 /**
  * Find all views from activity_file_encryption.xml.
  */
 private void initializeViews() {

  encKey = findViewById(R.id.encKey);
  decKey = findViewById(R.id.decKey);

  selectedFile = findViewById(R.id.selectedFile);
  progress = findViewById(R.id.progress);

  encryptButton = findViewById(R.id.encryptButton);
  decryptButton = findViewById(R.id.decryptButton);

  progress.setText("Ready");
  selectedFile.setText("No file selected");
 }

 /**
  * Connect UI buttons to their actions.
  */
 private void initializeButtons() {

  /*
   * Encrypt file button.
   *
   * First select the source file.
   * The output file will be requested after selection.
   */
  encryptButton.setOnClickListener(v -> {

   String key = encKey.getText().toString().trim();

   if (key.isEmpty()) {
    key = "CIPHER";
    encKey.setText(key);
   }

   openFileForEncryption();
  });

  /*
   * Decrypt file button.
   */
  decryptButton.setOnClickListener(v -> {

   String key = decKey.getText().toString().trim();

   if (key.isEmpty()) {
    key = "CIPHER";
    decKey.setText(key);
   }

   openFileForDecryption();
  });
 }

 /**
  * Open Android file picker for an input file to encrypt.
  */
 private void openFileForEncryption() {

  Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType("*/*");

  waitingForEncryptionOutput = true;
  waitingForDecryptionOutput = false;

  startActivityForResult(intent, REQUEST_OPEN_ENCRYPT);
 }

 /**
  * Open Android file picker for an encrypted file.
  */
 private void openFileForDecryption() {

  Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType("*/*");

  waitingForEncryptionOutput = false;
  waitingForDecryptionOutput = true;

  startActivityForResult(intent, REQUEST_OPEN_DECRYPT);
 }

 /**
  * Receive the selected source file.
  */
 @Override
 protected void onActivityResult(
         int requestCode,
         int resultCode,
         Intent data
 ) {
  super.onActivityResult(requestCode, resultCode, data);

  if (resultCode != RESULT_OK || data == null) {
   progress.setText("Operation cancelled");
   return;
  }

  Uri uri = data.getData();

  if (uri == null) {
   progress.setText("No file selected");
   return;
  }

  /*
   * Keep the URI permission so the app can continue accessing
   * the selected document when possible.
   */
  try {
   final int takeFlags =
           data.getFlags()
                   & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                   | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

   getContentResolver().takePersistableUriPermission(
           uri,
           takeFlags
   );
  } catch (Exception ignored) {
   /*
    * Some document providers don't support persistable permissions.
    * That's okay; the current operation can still continue.
    */
  }

  selected = uri;
  selectedName = getFileName(uri);

  if (selectedName == null || selectedName.trim().isEmpty()) {
   selectedName = "selected_file";
  }

  selectedFile.setText(
          "Selected: " + selectedName
  );

  progress.setText("File selected");

  /*
   * Source file selected for encryption.
   */
  if (requestCode == REQUEST_OPEN_ENCRYPT) {

   waitingForEncryptionOutput = true;
   waitingForDecryptionOutput = false;

   createEncryptionOutput();
  }

  /*
   * Source file selected for decryption.
   */
  else if (requestCode == REQUEST_OPEN_DECRYPT) {

   waitingForEncryptionOutput = false;
   waitingForDecryptionOutput = true;

   createDecryptionOutput();
  }
 }

 /**
  * Ask the user where to save the encrypted file.
  */
 private void createEncryptionOutput() {

  String outputName = selectedName + ".cffile";

  Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType("application/octet-stream");
  intent.putExtra(
          Intent.EXTRA_TITLE,
          outputName
  );

  startActivityForResult(
          intent,
          REQUEST_CREATE_ENCRYPT
  );
 }

 /**
  * Ask the user where to save the decrypted file.
  */
 private void createDecryptionOutput() {

  String outputName = decryptedFileName(selectedName);

  Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType("*/*");
  intent.putExtra(
          Intent.EXTRA_TITLE,
          outputName
  );

  startActivityForResult(
          intent,
          REQUEST_CREATE_DECRYPT
  );
 }

 /**
  * Receive the destination file selected by the user.
  */
 private void handleOutputSelection(
         int requestCode,
         Uri outputUri
 ) {

  if (outputUri == null) {
   progress.setText("Output location not selected");
   return;
  }

  if (selected == null) {
   progress.setText("Input file is missing");
   return;
  }

  if (requestCode == REQUEST_CREATE_ENCRYPT) {

   doWork(false, outputUri);

  } else if (requestCode == REQUEST_CREATE_DECRYPT) {

   doWork(true, outputUri);
  }
 }

 /**
  * Perform encryption/decryption in the background.
  *
  * Important:
  * Variables used inside Java lambdas are final/effectively final.
  */
 private void doWork(boolean dec, Uri out) {

  String keyValue =
          (dec ? decKey : encKey)
                  .getText()
                  .toString();

  if (keyValue.trim().isEmpty()) {
   keyValue = "CIPHER";
  }

  final String finalKey = keyValue;
  final Uri inputUri = selected;
  final String inputName = selectedName;
  final boolean decryptMode = dec;
  final Uri outputUri = out;

  progress.setText("Processing…");

  /*
   * Prevent accidental double-click operations.
   */
  encryptButton.setEnabled(false);
  decryptButton.setEnabled(false);

  new Thread(() -> {

   try {

    long total = getSize(inputUri);

    try (
            InputStream in =
                    getContentResolver()
                            .openInputStream(inputUri);

            OutputStream os =
                    getContentResolver()
                            .openOutputStream(outputUri)
    ) {

     if (in == null || os == null) {
      throw new IOException(
              "Unable to open file"
      );
     }

     /*
      * ENCRYPT
      */
     if (!decryptMode) {

      String ext =
              extension(inputName);

      FileEncryptionEngine.encrypt(
              in,
              os,
              finalKey,
              ext,
              total,
              (done, totalBytes) ->
                      runOnUiThread(() ->
                              progress.setText(
                                      "Encrypting • "
                                              + percent(
                                              done,
                                              totalBytes
                                      )
                                              + "%"
                              )
                      )
      );

     }

     /*
      * DECRYPT
      */
     else {

      FileEncryptionEngine.decrypt(
              in,
              os,
              finalKey,
              total,
              (done, totalBytes) ->
                      runOnUiThread(() ->
                              progress.setText(
                                      "Decrypting • "
                                              + percent(
                                              done,
                                              totalBytes
                                      )
                                              + "%"
                              )
                      )
      );
     }
    }

    /*
     * Operation completed.
     */
    runOnUiThread(() -> {

     progress.setText("✓ Completed");

     encryptButton.setEnabled(true);
     decryptButton.setEnabled(true);

     Toast.makeText(
             this,
             decryptMode
                     ? "File decrypted"
                     : "File encrypted",
             Toast.LENGTH_LONG
     ).show();
    });

   } catch (Exception e) {

    runOnUiThread(() -> {

     String message = e.getMessage();

     if (message == null ||
             message.trim().isEmpty()) {
      message = "Unknown error";
     }

     progress.setText(
             "Operation failed: " + message
     );

     encryptButton.setEnabled(true);
     decryptButton.setEnabled(true);

     Toast.makeText(
             this,
             "Operation failed",
             Toast.LENGTH_SHORT
     ).show();
    });
   }

  }).start();
 }

 /**
  * Handle output file picker results.
  */
 private boolean isOutputRequest(int requestCode) {

  return requestCode == REQUEST_CREATE_ENCRYPT
          || requestCode == REQUEST_CREATE_DECRYPT;
 }

 /**
  * Get file size through ContentResolver.
  *
  * Returns -1 when the provider doesn't expose a size.
  */
 private long getSize(Uri uri) {

  if (uri == null) {
   return -1;
  }

  android.database.Cursor cursor = null;

  try {

   cursor = getContentResolver().query(
           uri,
           new String[]{
                   OpenableColumns.SIZE
           },
           null,
           null,
           null
   );

   if (cursor != null && cursor.moveToFirst()) {

    int index =
            cursor.getColumnIndex(
                    OpenableColumns.SIZE
            );

    if (index >= 0 && !cursor.isNull(index)) {
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

 /**
  * Return the file extension without the dot.
  */
 private String extension(String name) {

  if (name == null || name.trim().isEmpty()) {
   return "";
  }

  int dot = name.lastIndexOf('.');

  if (dot <= 0 || dot == name.length() - 1) {
   return "";
  }

  return name.substring(dot + 1);
 }

 /**
  * Create a sensible output filename for a decrypted file.
  */
 private String decryptedFileName(String name) {

  if (name == null || name.trim().isEmpty()) {
   return "decrypted_file";
  }

  /*
   * Remove .cffile if present.
   */
  if (name.toLowerCase().endsWith(".cffile")) {

   String result =
           name.substring(
                   0,
                   name.length() - ".cffile".length()
           );

   if (!result.trim().isEmpty()) {
    return result;
   }
  }

  return "decrypted_" + name;
 }

 /**
  * Get a display name for a selected document.
  */
 private String getFileName(Uri uri) {

  if (uri == null) {
   return null;
  }

  android.database.Cursor cursor = null;

  try {

   cursor = getContentResolver().query(
           uri,
           new String[]{
                   OpenableColumns.DISPLAY_NAME
           },
           null,
           null,
           null
   );

   if (cursor != null && cursor.moveToFirst()) {

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

 /**
  * Convert byte progress to a percentage.
  */
 private int percent(long done, long total) {

  if (total <= 0) {
   return 0;
  }

  long value =
          (done * 100L) / total;

  if (value < 0) {
   value = 0;
  }

  if (value > 100) {
   value = 100;
  }

  return (int) value;
 }
}