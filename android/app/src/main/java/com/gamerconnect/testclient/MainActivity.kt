package com.gamerconnect.testclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                val authState =
                    authViewModel.uiState.collectAsStateWithLifecycle().value

                when {
                    !authState.isInitialized -> {
                        StartupLoadingScreen()
                    }

                    authState.isSignedIn -> {
                        GamerConnectApp(
                            onSignOut = authViewModel::signOut
                        )
                    }

                    else -> {
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
}

@Composable
private fun StartupLoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = "Gamer Connect",
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Loading your session...",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
    }
}
