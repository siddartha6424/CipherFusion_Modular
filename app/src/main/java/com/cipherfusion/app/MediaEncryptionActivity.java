package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MediaEncryptionActivity extends Activity {

 private static final int REQUEST_OPEN = 3001;
 private static final int REQUEST_CREATE = 3002;

 private EditText key;
 private TextView selectedFile;
 private TextView progress;

 private Button encryptButton;
 private Button decryptButton;

 private Uri selected;
 private String selectedName = "";

 private boolean decrypt = false;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  setContentView(R.layout.activity_media_encryption);

  initializeViews();
  initializeButtons();
 }

 private void initializeViews() {

  key = findViewById(R.id.key);
  selectedFile = findViewById(R.id.selectedFile);
  progress = findViewById(R.id.progress);

  encryptButton = findViewById(R.id.encryptButton);
  decryptButton = findViewById(R.id.decryptButton);

  progress.setText("Ready");
  selectedFile.setText("No media selected");
 }

 private void initializeButtons() {

  encryptButton.setOnClickListener(v -> {

   decrypt = false;

   openMedia();
  });

  decryptButton.setOnClickListener(v -> {

   decrypt = true;

   openMedia();
  });
 }

 private void openMedia() {

  Intent intent =
          new Intent(Intent.ACTION_OPEN_DOCUMENT);

  intent.addCategory(
          Intent.CATEGORY_OPENABLE
  );

  /*
   * Accept photos, videos and audio.
   */
  intent.setType("*/*");

  startActivityForResult(
          intent,
          REQUEST_OPEN
  );
 }

 @Override
 protected void onActivityResult(
         int requestCode,
         int resultCode,
         Intent data
 ) {
  super.onActivityResult(
          requestCode,
          resultCode,
          data
  );

  if (resultCode != RESULT_OK || data == null) {

   progress.setText(
           "Operation cancelled"
   );

   return;
  }

  /*
   * The second picker is the output-file picker.
   * Handle it before treating the selected URI
   * as the source media file.
   */
  if (requestCode == REQUEST_CREATE) {

   handleOutput(data.getData());

   return;
  }

  /*
   * The first picker is the source-media picker.
   */
  Uri uri = data.getData();

  if (uri == null) {

   progress.setText(
           "No media selected"
   );

   return;
  }

  selected = uri;

  selectedName = getFileName(uri);

  if (selectedName == null ||
          selectedName.trim().isEmpty()) {

   selectedName = "media_file";
  }

  selectedFile.setText(
          "Selected: " + selectedName
  );

  progress.setText(
          decrypt
                  ? "Media selected for decryption"
                  : "Media selected for encryption"
  );

  createOutputFile();
 }

 private void createOutputFile() {

  String outputName;

  if (decrypt) {

   outputName =
           decryptedFileName(selectedName);

  } else {

   outputName =
           selectedName + ".cffile";
  }

  Intent intent =
          new Intent(
                  Intent.ACTION_CREATE_DOCUMENT
          );

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
          REQUEST_CREATE
  );
 }

 private void handleOutput(Uri outputUri) {

  if (outputUri == null) {

   progress.setText(
           "Output location not selected"
   );

   return;
  }

  if (selected == null) {

   progress.setText(
           "No input file selected"
   );

   return;
  }

  doWork(outputUri);
 }

 private void doWork(Uri out) {

  String keyValue =
          key.getText()
                  .toString();

  if (keyValue.trim().isEmpty()) {
   keyValue = "CIPHER";
  }

  /*
   * Values used inside lambdas must be final
   * or effectively final.
   */
  final String finalKey =
          keyValue;

  final Uri inputUri =
          selected;

  final Uri outputUri =
          out;

  final String inputName =
          selectedName;

  final boolean decryptMode =
          decrypt;

  progress.setText(
          "Processing..."
  );

  encryptButton.setEnabled(false);
  decryptButton.setEnabled(false);

  new Thread(() -> {

   try {

    long total =
            size(inputUri);

    try (
            InputStream in =
                    getContentResolver()
                            .openInputStream(
                                    inputUri
                            );

            OutputStream os =
                    getContentResolver()
                            .openOutputStream(
                                    outputUri
                            )
    ) {

     if (in == null ||
             os == null) {

      throw new IOException(
              "Unable to open file"
      );
     }

     /*
      * DECRYPT
      */
     if (decryptMode) {

      FileEncryptionEngine.decrypt(
              in,
              os,
              finalKey,
              total,
              (done, totalBytes) ->
                      runOnUiThread(() ->
                              progress.setText(
                                      "Decrypting - "
                                              + pct(
                                              done,
                                              totalBytes
                                      )
                                              + "%"
                              )
                      )
      );

     }

     /*
      * ENCRYPT
      */
     else {

      FileEncryptionEngine.encrypt(
              in,
              os,
              finalKey,
              extension(inputName),
              total,
              (done, totalBytes) ->
                      runOnUiThread(() ->
                              progress.setText(
                                      "Encrypting - "
                                              + pct(
                                              done,
                                              totalBytes
                                      )
                                              + "%"
                              )
                      )
      );
     }
    }

    runOnUiThread(() -> {

     progress.setText(
             "Completed"
     );

     encryptButton.setEnabled(true);
     decryptButton.setEnabled(true);

     Toast.makeText(
             this,
             decryptMode
                     ? "Media decrypted"
                     : "Media encrypted",
             Toast.LENGTH_LONG
     ).show();
    });

   } catch (Exception e) {

    runOnUiThread(() -> {

     String message =
             e.getMessage();

     if (message == null ||
             message.trim().isEmpty()) {

      message =
              "Unknown error";
     }

     progress.setText(
             "Operation failed: "
                     + message
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

 private long size(Uri uri) {

  if (uri == null) {
   return -1;
  }

  Cursor cursor = null;

  try {

   cursor =
           getContentResolver().query(
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

 private String getFileName(Uri uri) {

  if (uri == null) {
   return null;
  }

  Cursor cursor = null;

  try {

   cursor =
           getContentResolver().query(
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

     return cursor.getString(
             index
     );
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

 private String extension(String name) {

  if (name == null ||
          name.trim().isEmpty()) {

   return "";
  }

  int dot =
          name.lastIndexOf('.');

  if (dot <= 0 ||
          dot == name.length() - 1) {

   return "";
  }

  return name.substring(
          dot + 1
  );
 }

 private String decryptedFileName(
         String name
 ) {

  if (name == null ||
          name.trim().isEmpty()) {

   return "decrypted_media";
  }

  if (name.toLowerCase()
          .endsWith(".cffile")) {

   String result =
           name.substring(
                   0,
                   name.length()
                           - ".cffile".length()
           );

   if (!result.trim().isEmpty()) {
    return result;
   }
  }

  return "decrypted_" + name;
 }

 private int pct(
         long done,
         long total
 ) {

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