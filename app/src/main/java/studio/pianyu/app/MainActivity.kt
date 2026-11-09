package studio.pianyu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private lateinit var model: EditorModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        model = ViewModelProvider(this)[EditorModel::class.java]
        setContent {
            PianyuTheme {
                EditorScreen(
                    model = model,
                    pickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
            }
        }
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        model.importPhotos(uris)
    }
}
