package nz.eloque.foss_wallet

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nz.eloque.foss_wallet.api.ImportResult
import nz.eloque.foss_wallet.parsing.PassParser
import nz.eloque.foss_wallet.persistence.InvalidPassException
import nz.eloque.foss_wallet.persistence.PassLoader
import nz.eloque.foss_wallet.shortcut.Shortcut
import nz.eloque.foss_wallet.ui.WalletApp
import nz.eloque.foss_wallet.ui.theme.WalletTheme
import nz.eloque.foss_wallet.ui.view.wallet.PassViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val passViewModel: PassViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataUri = when {
            Intent.ACTION_VIEW == intent.action -> intent.data
            Intent.ACTION_SEND == intent.action -> {
                val count = intent.clipData?.itemCount?.minus(1)?.coerceAtLeast(0)
                count?.let { intent.clipData?.getItemAt(it)?.uri }
            }
            else -> null
        }

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(dataUri) {
                if (Shortcut.SCHEME != dataUri?.scheme) {
                    coroutineScope.launch(Dispatchers.IO) {
                        dataUri?.handleIntent(passViewModel, coroutineScope, navController)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                LaunchedEffect("Permissions") {
                    coroutineScope.launch(Dispatchers.IO) { permissionState.launchPermissionRequest() }
                }
            }
            WalletTheme {
                WalletApp(
                    navController,
                )
            }
        }
    }

    private fun Uri.handleIntent(passViewModel: PassViewModel, coroutineScope: CoroutineScope, navController: NavHostController) {
        contentResolver.openInputStream(this).use {
            it?.let {
                try {
                    val loadResult = PassLoader(PassParser(this@MainActivity)).load(it)
                    val importResult = passViewModel.add(loadResult)
                    val id: String = loadResult.pass.pass.id
                    coroutineScope.launch(Dispatchers.Main) {
                        when (importResult) {
                            is ImportResult.New -> navController.navigate("pass/$id")
                            is ImportResult.Replaced -> {
                                Toast
                                    .makeText(this@MainActivity, this@MainActivity.getString(R.string.pass_already_imported), Toast.LENGTH_SHORT)
                                    .show()
                                navController.navigate("pass/$id")
                            }
                        }
                    }
                } catch (e: InvalidPassException) {
                    Log.w(TAG, "Failed to load pass from intent: $e")
                    coroutineScope.launch(Dispatchers.Main) { Toast
                        .makeText(this@MainActivity, this@MainActivity.getString(R.string.invalid_pass_toast), Toast.LENGTH_SHORT)
                        .show() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
