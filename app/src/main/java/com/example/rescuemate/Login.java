package com.example.rescuemate;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.regex.Pattern;

public class Login extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private TextInputEditText emailInputView;
    private TextInputEditText passwordInputView;
    private Button loginButton;
    private String regexPattern = "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
            + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        mAuth = FirebaseAuth.getInstance();
        emailInputView = findViewById(R.id.emailInput);
        passwordInputView = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginBtn);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() != null){
            Intent intent = new Intent(this, MapsActivity.class);
            startActivity(intent);
        }
        loginButton.setOnClickListener(e->{
            String email;
            String password;
            if (emailInputView.getText() != null && passwordInputView.getText() != null){
                email = emailInputView.getText().toString();
                password = passwordInputView.getText().toString();
                if (verifyInput(email,password)) {
                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, task -> {
                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    Log.d("LOGIN", "createUserWithEmail:success");

                                    Intent intent = new Intent(this,MapsActivity.class);
                                    startActivity(intent);
                                } else {
                                    // If sign in fails, display a message to the user.
                                    Log.w("LOGIN", "createUserWithEmail:failure", task.getException());
                                    Toast.makeText(Login.this, "Authentication failed.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                }
                else{
                    Toast.makeText(this,"Email ist ungültig oder Passwort ist zu kurz",Toast.LENGTH_SHORT).show();
                }
            }
            else{
                Toast.makeText(this,"Email und Password müssen nicht leer sein",Toast.LENGTH_SHORT).show();
            }


        });
    }

    private boolean verifyInput(@NonNull String email, @NonNull String password){
        return patternMatches(email,regexPattern) && password.length() > 6;
    }

    public static boolean patternMatches(String emailAddress, String regexPattern) {
        return Pattern.compile(regexPattern)
                .matcher(emailAddress)
                .matches();
    }

}
