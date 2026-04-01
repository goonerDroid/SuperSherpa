package com.sublime.supersherpa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.core.ai.SherpaSmokeTestResult
import com.sublime.supersherpa.core.ai.SherpaSmokeTestRunner
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuperSherpaTheme {
                SuperSherpaApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun SuperSherpaApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> SherpaSmokeTestScreen(
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.FAVORITES -> Greeting(
                    name = "Favorites",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.PROFILE -> Greeting(
                    name = "Profile",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun SherpaSmokeTestScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<SherpaSmokeTestResult?>(null) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    val runSmokeTest: () -> Unit = {
        scope.launch {
            isRunning = true
            try {
                result = withContext(Dispatchers.Default) {
                    SherpaSmokeTestRunner.run()
                }
            } finally {
                isRunning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runSmokeTest()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Sherpa AAR smoke test")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "This validates the bundled JNI layer before transcription wiring.")
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = runSmokeTest,
            enabled = !isRunning,
        ) {
            Text(if (isRunning) "Running..." else "Run smoke test")
        }
        Spacer(modifier = Modifier.height(20.dp))
        result?.let { smokeResult ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = if (smokeResult.passed) "PASS" else "FAIL")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Version: ${smokeResult.version}")
                    Text(text = "Native version: ${smokeResult.nativeVersion}")
                    Text(text = "Git SHA1: ${smokeResult.gitSha1}")
                    Text(text = "Native Git SHA1: ${smokeResult.nativeGitSha1}")
                    Text(text = "Feature config ready: ${smokeResult.featureConfigReady}")
                    Text(text = "Recognizer config ready: ${smokeResult.recognizerConfigReady}")
                    Text(text = "Model config ready: ${smokeResult.modelConfigReady}")
                    smokeResult.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Error: $error")
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SuperSherpaTheme {
        Greeting("Android")
    }
}
