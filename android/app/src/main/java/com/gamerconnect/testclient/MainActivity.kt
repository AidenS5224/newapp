package com.gamerconnect.testclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamerconnect.testclient.feature.auth.AuthScreen
import com.gamerconnect.testclient.feature.auth.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GamerConnectTheme {
                val authViewModel: AuthViewModel = viewModel()
                val authState = authViewModel.uiState.collectAsStateWithLifecycle().value

                if (authState.isSignedIn) {
                    GamerConnectApp(
                        onSignOut = authViewModel::signOut
                    )
                } else {
                    AuthScreen(
                        onSignIn = authViewModel::signIn,
                        onSignUp = authViewModel::signUp,
                        isLoading = authState.isLoading,
                        errorMessage = authState.errorMessage
                    )
                }
            }
        }
    }
}