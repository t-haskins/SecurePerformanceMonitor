package com.example.secureperformancemonitor;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/*
Class for user password input
Author: Tyler Haskins
*/

public class PasswordPopupActivity extends AppCompatActivity {
    // tag used for methods to write logs
    private final static String TAG = "PasswordPopup";

    private EditText passwordInput;
    private Button submitButton;
    private String password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // create popup window
        setTitle("Enter Password");
        setContentView(R.layout.activity_password_popup);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        getWindow().setLayout((int)(width*0.8), (int)(height*0.4));
        // do not allow window to be closed when touched outside border
        this.setFinishOnTouchOutside(false);

        // get password popup UI
        passwordInput = findViewById(R.id.passwordInput);
        submitButton = findViewById(R.id.submitButton);
        // submit button functionality
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                password = passwordInput.getText().toString();
                // if correct password close activity, else alert user
                if (AppPasswordService.validateAppUser(password)) {
                    //Toast.makeText(PasswordPopupActivity.this, R.string.correct_password, Toast.LENGTH_SHORT).show();
                    PerformanceMetricsActivity.password = password;
                    PerformanceMetricsActivity.passwordEntered = true;
                    finish();
                } else {
                    Toast.makeText(PasswordPopupActivity.this, R.string.incorrect_password, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
