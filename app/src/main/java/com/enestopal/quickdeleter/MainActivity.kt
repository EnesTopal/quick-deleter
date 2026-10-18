package com.enestopal.quickdeleter

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.enestopal.quickdeleter.ui.browser.BrowserScreen
import com.enestopal.quickdeleter.ui.browser.BrowserViewModel
import com.enestopal.quickdeleter.ui.permission.PermissionScreen
import com.enestopal.quickdeleter.ui.permission.StoragePermission
import com.enestopal.quickdeleter.ui.theme.QuickdeleterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickdeleterTheme {
                val viewModel: BrowserViewModel = viewModel(factory = browserViewModelFactory)
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Re-check on every resume: the user grants access in system settings and comes back.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.onPermissionChecked(StoragePermission.isGranted())
                }

                if (state.hasPermission) BrowserScreen(viewModel) else PermissionScreen()
            }
        }
    }

    private companion object {
        val browserViewModelFactory = viewModelFactory {
            initializer {
                BrowserViewModel(
                    savedState = createSavedStateHandle(),
                    rootDirectory = Environment.getExternalStorageDirectory(),
                )
            }
        }
    }
}
