package studio.pianyu.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var model: EditorModel
    private var video by mutableStateOf<VideoIO.Source?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        model = ViewModelProvider(this)[EditorModel::class.java]
        savedInstanceState?.getString("videoUri")?.let { Uri.parse(it) }?.let { uri ->
            val length = savedInstanceState.getLong("motionLength")
            val motion = if (length > 0) MotionPhoto.Clip(length, savedInstanceState.getLong("motionTime")) else null
            video = VideoIO.Source(uri, motion)
        }
        setContent {
            PianyuTheme {
                val source = video
                if (source != null) {
                    VideoEditor(source) { video = null }
                } else {
                    EditorScreen(
                        model = model,
                        pickPhoto = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        pickMediaFile = { pickMediaFile.launch(arrayOf("image/*", "video/*", "image/x-adobe-dng")) }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        video?.let { source ->
            outState.putString("videoUri", source.uri.toString())
            source.motion?.let { motion ->
                outState.putLong("motionLength", motion.length)
                outState.putLong("motionTime", motion.coverTimeUs)
            }
        }
        super.onSaveInstanceState(outState)
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.size == 1) importOne(uris.single()) else model.importPhotos(uris)
    }

    private val pickMediaFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importOne)
    }

    private fun importOne(uri: Uri) {
        lifecycleScope.launch {
            try {
                val source = withContext(Dispatchers.IO) { VideoIO.identify(applicationContext, uri) }
                if (source != null) video = source else model.importPhotos(listOf(uri))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                Toast.makeText(this@MainActivity, "无法打开所选文件", Toast.LENGTH_LONG).show()
            }
        }
    }
}
