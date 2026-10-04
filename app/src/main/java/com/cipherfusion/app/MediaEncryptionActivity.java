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

 private static final int REQUEST_SELECT_MEDIA = 3001;
 private static final int REQUEST_CREATE_OUTPUT = 3002;

 private EditText key;

 private TextView selectedFile;
 private TextView progress;
 private TextView result;

 private Button selectMediaButton;
 private Button encryptButton;
 private Button decryptButton;

 private Uri selected;
 private String selectedName = "";

 /*
  * true  = encryption
  * false = decryption
  */
 private boolean pendingEncryption = true;

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
  result = findViewById(R.id.result);

  selectMediaButton =
          findViewById(R.id.selectMediaButton);

  encryptButton =
          findViewById(R.id.encryptButton);

  decryptButton =
          findViewById(R.id.decryptButton);

  selectedFile.setText(
          "No media selected"
  );

  progress.setText("Ready");

  result.setText(
          "No operation completed yet.\n\n" +
                  "Select media and choose Encrypt or Decrypt."
  );
 }

 private void initializeButtons() {

  /*
   * SELECT MEDIA
   */
  selectMediaButton.setOnClickListener(
          v -> openMediaPicker()
  );

  /*
   * ENCRYPT MEDIA
   */
  encryptButton.setOnClickListener(v -> {

   if (selected == null) {

    Toast.makeText(
            this,
            "Please select media first",
            Toast.LENGTH_SHORT
    ).show();

    progress.setText(
            "Please select media"
    );

    return;
   }

   String keyValue =
           key.getText()
                   .toString()
                   .trim();

   if (keyValue.isEmpty()) {

    keyValue = "CIPHER";

    key.setText(keyValue);
   }

   pendingEncryption = true;

   createOutputFile();
  });

  /*
   * DECRYPT MEDIA
   */
  decryptButton.setOnClickListener(v -> {

   if (selected == null) {

    Toast.makeText(
            this,
            "Please select encrypted media first",
            Toast.LENGTH_SHORT
    ).show();

    progress.setText(
            "Please select encrypted media"
    );

    return;
   }

   String keyValue =
           key.getText()
                   .toString()
                   .trim();

   if (keyValue.isEmpty()) {

    keyValue = "CIPHER";

    key.setText(keyValue);
   }

   pendingEncryption = false;

   createOutputFile();
  });
 }

 /**
  * Open Android document picker.
  *
  * We accept all file types because encrypted media
  * uses the .cffile extension and Android may not
  * classify it as image, video, or audio.
  */
 private void openMediaPicker() {

  Intent intent =
          new Intent(
                  Intent.ACTION_OPEN_DOCUMENT
          );

  intent.addCategory(
          Intent.CATEGORY_OPENABLE
  );

  intent.setType("*/*");

  intent.putExtra(
          Intent.EXTRA_MIME_TYPES,
          new String[]{
                  "image/*",
                  "video/*",
                  "audio/*",
                  "application/octet-stream"
          }
  );

  try {

   intent.addFlags(
           Intent.FLAG_GRANT_READ_URI_PERMISSION
   );

   intent.addFlags(
           Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
   );

  } catch (Exception ignored) {
  }

  startActivityForResult(
          intent,
          REQUEST_SELECT_MEDIA
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

  if (resultCode != RESULT_OK ||
          data == null) {

   if (requestCode ==
           REQUEST_SELECT_MEDIA) {

    progress.setText(
            "Media selection cancelled"
    );

   } else if (requestCode ==
           REQUEST_CREATE_OUTPUT) {

    progress.setText(
            "Output location cancelled"
    );
   }

   return;
  }

  Uri uri = data.getData();

  if (uri == null) {

   progress.setText(
           "No media selected"
   );

   return;
  }

  /*
   * SOURCE MEDIA
   */
  if (requestCode ==
          REQUEST_SELECT_MEDIA) {

   try {

    final int takeFlags =
            data.getFlags()
                    & (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

    getContentResolver()
            .takePersistableUriPermission(
                    uri,
                    takeFlags
            );

   } catch (Exception ignored) {
   }

   selected = uri;

   selectedName =
           getFileName(uri);

   if (selectedName == null ||
           selectedName.trim().isEmpty()) {

    selectedName =
            "selected_media";
   }

   selectedFile.setText(
           "✓ " + selectedName
   );

   progress.setText(
           "Media selected • Ready"
   );

   result.setText(
           "Media selected successfully.\n\n" +
                   "Input:\n" +
                   selectedName +
                   "\n\nChoose Encrypt or Decrypt."
   );

   return;
  }

  /*
   * OUTPUT FILE
   */
  if (requestCode ==
          REQUEST_CREATE_OUTPUT) {

   handleOutputSelection(uri);
  }
 }

 /**
  * Ask user where to save encrypted/decrypted media.
  */
 private void createOutputFile() {

  String outputName;

  if (pendingEncryption) {

   outputName =
           selectedName + ".cffile";

  } else {

   outputName =
           decryptedFileName(
                   selectedName
           );
  }

  Intent intent =
          new Intent(
                  Intent.ACTION_CREATE_DOCUMENT
          );

  intent.addCategory(
          Intent.CATEGORY_OPENABLE
  );

  if (pendingEncryption) {

   intent.setType(
           "application/octet-stream"
   );

  } else {

   intent.setType("*/*");
  }

  intent.putExtra(
          Intent.EXTRA_TITLE,
          outputName
  );

  startActivityForResult(
          intent,
          REQUEST_CREATE_OUTPUT
  );
 }

 /**
  * Handle destination selection.
  */
 private void handleOutputSelection(
         Uri outputUri
 ) {

  if (outputUri == null) {

   progress.setText(
           "Output location not selected"
   );

   return;
  }

  if (selected == null) {

   progress.setText(
           "Input media is missing"
   );

   return;
  }

  doWork(
          !pendingEncryption,
          outputUri
  );
 }

 /**
  * Run encryption/decryption.
  */
 private void doWork(
         boolean decryptMode,
         Uri outputUri
 ) {

  String keyValue =
          key.getText()
                  .toString();

  if (keyValue.trim().isEmpty()) {
   keyValue = "CIPHER";
  }

  final String finalKey =
          keyValue;

  final Uri inputUri =
          selected;

  final String inputName =
          selectedName;

  final Uri finalOutputUri =
          outputUri;

  final boolean finalDecryptMode =
          decryptMode;

  /*
   * Disable controls while processing.
   */
  selectMediaButton.setEnabled(false);
  encryptButton.setEnabled(false);
  decryptButton.setEnabled(false);

  progress.setText(
          finalDecryptMode
                  ? "Preparing decryption..."
                  : "Preparing encryption..."
  );

  result.setText(
          finalDecryptMode
                  ? "DECRYPTION IN PROGRESS\n\n" +
                  "Please wait..."
                  : "ENCRYPTION IN PROGRESS\n\n" +
                  "Please wait..."
  );

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
                                    finalOutputUri
                            )
    ) {

     if (in == null ||
             os == null) {

      throw new IOException(
              "Unable to open media"
      );
     }

     /*
      * DECRYPT
      */
     if (finalDecryptMode) {

      FileEncryptionEngine.decrypt(
              in,
              os,
              finalKey,
              total,
              (done, totalBytes) ->
                      runOnUiThread(() ->
                              progress.setText(
                                      "Decrypting • "
                                              +
                                              pct(
                                                      done,
                                                      totalBytes
                                              )
                                              +
                                              "%"
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
                                      "Encrypting • "
                                              +
                                              pct(
                                                      done,
                                                      totalBytes
                                              )
                                              +
                                              "%"
                              )
                      )
      );
     }
    }

    /*
     * Find output filename.
     */
    String outputName =
            getFileName(
                    finalOutputUri
            );

    if (outputName == null ||
            outputName.trim().isEmpty()) {

     outputName =
             finalDecryptMode
                     ? decryptedFileName(
                     inputName
             )
                     : inputName +
                     ".cffile";
    }

    final String finalOutputName =
            outputName;

    /*
     * SUCCESS
     */
    runOnUiThread(() -> {

     progress.setText(
             finalDecryptMode
                     ? "✓ Decryption completed"
                     : "✓ Encryption completed"
     );

     result.setText(
             (
                     finalDecryptMode
                             ? "✓ DECRYPTION COMPLETE"
                             : "✓ ENCRYPTION COMPLETE"
             )
                     +
                     "\n\nInput Media:\n"
                     +
                     inputName
                     +
                     "\n\nOutput File:\n"
                     +
                     finalOutputName
                     +
                     "\n\nStatus:\n"
                     +
                     (
                             finalDecryptMode
                                     ? "MEDIA RESTORED SUCCESSFULLY"
                                     : "MEDIA SECURED SUCCESSFULLY"
                     )
     );

     selectMediaButton.setEnabled(true);
     encryptButton.setEnabled(true);
     decryptButton.setEnabled(true);

     Toast.makeText(
             this,
             finalDecryptMode
                     ? "Media decrypted successfully"
                     : "Media encrypted successfully",
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
             "✕ Operation failed"
     );

     result.setText(
             "✕ OPERATION FAILED\n\n"
                     +
                     message
     );

     selectMediaButton.setEnabled(true);
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
  * Get media size.
  */
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

 /**
  * Get selected filename.
  */
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

 /**
  * Get extension without dot.
  */
 private String extension(
         String name
 ) {

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

 /**
  * Create decrypted filename.
  */
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

 /**
  * Convert progress to percentage.
  */
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