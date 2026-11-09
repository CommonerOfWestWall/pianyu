package studio.pianyu.app

import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconDeviceTest {
    @Test fun foregroundFitsSafeCircleAndRenderActualLauncherAndMaskVariants() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val foreground=context.getDrawable(R.drawable.brand_mark)!!
        val safe=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888)
        foreground.setBounds(0,0,108,108);foreground.draw(Canvas(safe))
        for(y in 0..107)for(x in 0..107)if(Color.alpha(safe.getPixel(x,y))>8) {
            assertTrue("mark outside safe circle at $x,$y",(x+.5f-54)*(x+.5f-54)+(y+.5f-54)*(y+.5f-54)<=33*33)
        }
        val folder=File(context.getExternalFilesDir(null),"icon-evidence-v03").apply{mkdirs()}
        fun save(name:String,bitmap:Bitmap) {File(folder,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        fun artwork(monochrome:Boolean=false):Bitmap {
            val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.drawColor(if(monochrome)Color.rgb(224,211,180)else Color.rgb(32,33,31))
            val mark=context.getDrawable(if(monochrome)R.drawable.brand_monochrome else R.drawable.brand_mark)!!.mutate()
            if(monochrome)mark.setTint(Color.rgb(47,57,44))
            canvas.translate(-128f,-128f);mark.setBounds(0,0,768,768);mark.draw(canvas)
            return bitmap
        }
        val art=artwork();save("play-icon-512.png",art)
        listOf("circle","squircle","rounded-square","teardrop","themed").forEach { name ->
            val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);val path=Path()
            when(name) {
                "circle","themed"->path.addCircle(256f,256f,256f,Path.Direction.CW)
                "teardrop"->path.addRoundRect(RectF(0f,0f,512f,512f),floatArrayOf(256f,256f,256f,256f,0f,0f,256f,256f),Path.Direction.CW)
                "squircle"->{path.moveTo(256f,0f);path.cubicTo(490f,0f,512f,22f,512f,256f);path.cubicTo(512f,490f,490f,512f,256f,512f);path.cubicTo(22f,512f,0f,490f,0f,256f);path.cubicTo(0f,22f,22f,0f,256f,0f);path.close()}
                else->path.addRoundRect(RectF(0f,0f,512f,512f),116f,116f,Path.Direction.CW)
            }
            canvas.clipPath(path);canvas.drawBitmap(if(name=="themed")artwork(true)else art,0f,0f,null);save("$name.png",bitmap)
        }
        val actual=context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(actual is AdaptiveIconDrawable)
        assertNotNull((actual as AdaptiveIconDrawable).monochrome)
        val launcher=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        actual.setBounds(0,0,512,512);actual.draw(Canvas(launcher));save("device-launcher.png",launcher)
    }
}
