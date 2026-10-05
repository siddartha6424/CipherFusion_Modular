package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class DashboardActivity extends Activity {

 @Override
 protected void onCreate(Bundle b) {
  super.onCreate(b);

  setContentView(R.layout.activity_dashboard);

  // Text Encryption
  findViewById(R.id.moduleText).setOnClickListener(
          v -> startActivity(
                  new Intent(this, MainActivity.class)
          )
  );

  // File & Media Encryption
  findViewById(R.id.moduleFile).setOnClickListener(
          v -> startActivity(
                  new Intent(this, FileMediaEncryptionActivity.class)
          )
  );

  // Logout
  findViewById(R.id.logoutButton).setOnClickListener(v -> {

   getSharedPreferences("session", MODE_PRIVATE)
           .edit()
           .clear()
           .apply();

   startActivity(
           new Intent(this, LoginActivity.class)
   );

   finish();
  });
 }
}