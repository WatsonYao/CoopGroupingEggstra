package watson.coopgrouping.eggstra

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor

class TeamActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

  private lateinit var tts: TextToSpeech

  private val clock: TextView by lazy {
    findViewById(R.id.clock)
  }
  private val current: TextView by lazy {
    findViewById(R.id.current)
  }
  private val next: TextView by lazy {
    findViewById(R.id.next)
  }
  private val radioGroup5 by lazy {
    findViewById<RadioGroup>(R.id.gW5)
  }
  private val radioGroup4 by lazy {
    findViewById<RadioGroup>(R.id.gW4)
  }
  private val radioGroup3 by lazy {
    findViewById<RadioGroup>(R.id.gW3)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_team)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    tts = TextToSpeech(this, this)
    tts.setSpeechRate(1.2f)


    findViewById<Button>(R.id.start).setOnClickListener {
      start()
    }
    findViewById<Button>(R.id.close).setOnClickListener {
      closeTTs()
    }
    findViewById<Button>(R.id.exit).setOnClickListener {
      this.finish()
    }

    lifecycleScope.launch {
      withContext(Dispatchers.IO) {
        loadRaw()
      }
    }

  }

  private var rootData: RootData? = null
  private val INFO_FINISH = "Finish!"
  private val INFO_DASH = "--"

  private fun loadRaw() {
    val rawResId = R.raw.source09 // Replace with your actual raw resource ID
    rootData = readJsonFromRaw(this, rawResId)

    if (rootData != null) {
      log(rootData.toString())
      updateRadioSelect()
    } else {
      log("Failed to parse JSON")
    }
  }

  private fun closeTTs() {
    job?.cancel()
    job = null
    tts.stop()
    clock.text = INFO_FINISH
    current.text = INFO_DASH
    next.text = INFO_DASH
  }

  private val waveHeadList = arrayListOf(
    mutableListOf<Item>(),
    mutableListOf<Item>(),
    mutableListOf<Item>(),
    mutableListOf<Item>(),
    mutableListOf<Item>(),
  )

  private fun MutableList<Item>.clearAdd(data: List<Item>) {
    val result = mutableListOf<Item>()
    data.map {
      val hasBoss = it.Boss?.isNotEmpty() == true
      val hasTime = it.Timing != null && it.Timing >= 0
      val time = it.Timing
      if (hasBoss && hasTime) {
        val showTime = floor(100.0 - time!! / 60.0).toInt() // 结果是 Int 类型
        it.p = showTime
        result.add(it)
      }
    }
    this.clear()
    this.addAll(result)
  }


  //根据radiobutton选择情况更新当前wave的list
  private fun updateRadioSelect() {
    val json = rootData ?: return
    waveHeadList[0].clearAdd(json.w1.p300)
    waveHeadList[1].clearAdd(json.w2.p600)
    checkRg3(json)
    checkRg4(json)
    checkRg5(json)
  }


  private fun start() {
    closeTTs()
    updateRadioSelect()
    startWaveTimer()
  }


  private var job: Job? = null

  private fun startWaveTimer() {
    val totalWaves = 5
    val waveDurationSeconds = 100
    val intervalDurationSeconds = 18

    job = lifecycleScope.launch {
      flow {
        for (wave in 3..totalWaves) {
          log("Wave $wave starting...")
          nextIndex = -1
          for (i in waveDurationSeconds downTo 0) {
            emit("WAVE $wave: $i")
            readyTTS(wave, i)
            delay(TimeUnit.SECONDS.toMillis(1))
          }
          if (wave < totalWaves) {
            log("Wave $wave ended. Interval starting...")
            for (i in intervalDurationSeconds downTo 0) {
              emit("ready ${wave + 1}: $i")
              readyInterval()
              delay(TimeUnit.SECONDS.toMillis(1))
            }
            log("Interval ended.")
          } else {
            allWaveCompleted()
            log("All waves completed!")
          }
        }
      }.collect { time ->
        log(time) // 在这里更新 UI 或进行其他操作
        clock.text = time
      }
    }
  }

  private fun readyTTS(wave: Int, i: Int) {
    lifecycleScope.launch {
      val waveList = waveHeadList[wave - 1]
      val finds = queryShowTimeItem(waveList, i)
      val next = waveList.getOrNull(nextIndex + 1)
      log("wave=$wave finds=$finds")
      log("next=$next")
      if (finds.isNotEmpty()) {
        speak(finds, next)
      }
    }
  }

  private fun readyInterval() {
    lifecycleScope.launch {
      current.text = INFO_DASH
      next.text = INFO_DASH
    }
  }

  private fun allWaveCompleted() {
    lifecycleScope.launch {
      clock.text = INFO_FINISH
      current.text = INFO_DASH
      next.text = INFO_DASH
    }
  }

  private var nextIndex = -1

  private suspend fun queryShowTimeItem(list: List<Item>, i: Int): List<Item> = withContext(Dispatchers.IO) {
    val find = list.filterIndexed { index, item ->
      val flag = item.p in i..<(i + 1)
      if (flag) {
        nextIndex = index
      }
      flag
    }
    find
  }

  private fun Item.show(): String = "${this.Spawn}${bossNames[this.Boss]}"
  private fun Item?.showWithTime(): String = if (this == null) {
    INFO_DASH
  } else {
    "${this.p} ${this.Spawn}${bossNames[this.Boss]}"
  }


  private fun speak(list: List<Item>, nextItem: Item?) {
    val items = list.map { it.show() }
    val text = items.joinToString(separator = ",")
    current.text = text
    next.text = nextItem.showWithTime()
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
  }


  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      tts.language = Locale.CHINA // 设置语言为中文，如果需要其他语言可以修改
    }
  }

  override fun onDestroy() {
    job?.cancel()
    tts.stop()
    tts.shutdown()
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onDestroy()
  }

  private val bossNames = mapOf(
    "SakelienShield" to "车",
    "SakelienCupTwins" to "垃圾桶",
    "Sakediver" to "鼹鼠",
    "Sakerocket" to "伞",
    "SakelienSnake" to "蛇",
    "SakelienTower" to "塔",
    "SakePillar" to "柱鱼",
    "SakeArtillery" to "铁球",
    "SakeDolphin" to "海豚",
    "SakelienBomber" to "绿帽",
    "SakeSaucer" to "锅盖",
    "SakelienGolden" to "金鲑鱼"
  )

  private fun checkRg3(json: RootData) {
    val selectedId = radioGroup3.checkedRadioButtonId
    when (selectedId) {
      R.id.w3_150 -> {
        waveHeadList[2].clearAdd(json.w3.p750)
      }

      R.id.w3_180 -> {
        waveHeadList[2].clearAdd(json.w3.p900)
      }

      else -> {}
    }
  }

  private fun checkRg4(json: RootData) {
    val selectedId = radioGroup4.checkedRadioButtonId
    when (selectedId) {
      R.id.w4_180 -> {
        waveHeadList[3].clearAdd(json.w4.p900)
      }

      R.id.w4_210 -> {
        waveHeadList[3].clearAdd(json.w4.p1050)
      }

      R.id.w4_240 -> {
        waveHeadList[3].clearAdd(json.w4.p1200)
      }

      else -> {}
    }
  }

  private fun checkRg5(json: RootData) {
    val selectedId = radioGroup5.checkedRadioButtonId
    when (selectedId) {
      R.id.w5_210 -> {
        waveHeadList[4].clearAdd(json.w5.p1050)
      }

      R.id.w5_240 -> {
        waveHeadList[4].clearAdd(json.w5.p1200)
      }

      R.id.w5_270 -> {
        waveHeadList[4].clearAdd(json.w5.p1350)
      }

      R.id.w5_300 -> {
        waveHeadList[4].clearAdd(json.w5.p1500)
      }

      else -> {}
    }
  }
}
