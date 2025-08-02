package watson.coopgrouping.eggstra

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

fun log(msg: String) {
  Log.i("temp", msg)
}


@Keep
data class Item(
  val Spawn: String? = null,
  val Timing: Int? = null,
  val Lesser: Boolean? = null,
  val Boss: String? = null,
  val SubSpawn: Int? = null,
  var p: Int = 0,//显示时间
)

@Keep
data class LevelData1(
  val p300: List<Item>
)

@Keep
data class LevelData2(
  val p600: List<Item>
)

@Keep
data class LevelData3(
  val p750: List<Item>,
  val p900: List<Item>
)

@Keep
data class LevelData4(
  val p900: List<Item>,
  val p1050: List<Item>,
  val p1200: List<Item>
)

@Keep
data class LevelData5(
  val p1050: List<Item>,
  val p1200: List<Item>,
  val p1350: List<Item>,
  val p1500: List<Item>,
)

@Keep
data class RootData(
  val w1: LevelData1,
  val w2: LevelData2,
  val w3: LevelData3,
  val w4: LevelData4,
  val w5: LevelData5
)

// Function to read JSON from raw resource and convert to object
fun readJsonFromRaw(context: Context, rawResId: Int): RootData? {
  return try {
    val inputStream: InputStream = context.resources.openRawResource(rawResId)
    val reader = BufferedReader(InputStreamReader(inputStream))
    val jsonString = reader.use { it.readText() }

    val gson = Gson()
    gson.fromJson(jsonString, RootData::class.java)
  } catch (e: IOException) {
    e.printStackTrace()
    null
  }
}