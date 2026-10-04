package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class LoginActivity extends Activity {
    private EditText username, password;
    private TextView status;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        username=findViewById(R.id.username); password=findViewById(R.id.password); status=findViewById(R.id.loginStatus);
        findViewById(R.id.loginButton).setOnClickListener(v -> login());
        password.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }
    private void login(){
        String u=username.getText().toString().trim(), p=password.getText().toString();
        boolean ok=(u.equals("Cipher")&&p.equals("Encrypt"))||(u.equals("user")&&p.equals("user123"))||(u.equals("demo")&&p.equals("demo123"));
        if(ok){ getSharedPreferences("session",MODE_PRIVATE).edit().putBoolean("logged_in",true).apply(); startActivity(new Intent(this,DashboardActivity.class)); finish(); }
        else status.setText("Invalid username or password");
    }
}
