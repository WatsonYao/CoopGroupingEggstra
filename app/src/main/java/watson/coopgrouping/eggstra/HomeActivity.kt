package watson.coopgrouping.eggstra

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.Locale
import kotlin.math.floor

class HomeActivity : AppCompatActivity() {

  private lateinit var drawerLayout: DrawerLayout
  private lateinit var rvEvents: RecyclerView
  private lateinit var rvWaveOptions: RecyclerView
  private lateinit var rvTimeline: RecyclerView
  private lateinit var tvSelection: TextView
  private lateinit var tvWaveDrawerTitle: TextView

  private val gson = Gson()

  private val eventAdapter by lazy {
    DrawerTextAdapter { position ->
      onEventSelected(position)
      drawerLayout.closeDrawer(GravityCompat.START)
    }
  }

  private val waveAdapter by lazy {
    DrawerTextAdapter { position ->
      onWaveOptionSelected(position)
      drawerLayout.closeDrawer(GravityCompat.END)
    }
  }

  private val timelineAdapter by lazy {
    TimelineAdapter(
      colorOptions = COLOR_OPTIONS,
      colorIndexMap = monsterColorIndex
    )
  }

  private val eventLabels = (1..12).map { String.format(Locale.US, "event%02d", it) }

  private var selectedEventIndex = 11
  private var selectedWaveOptionKey: String? = null
  private var currentWaveOptions: List<WaveOption> = emptyList()

  private val monsterColorIndex: MutableMap<String, Int> = DEFAULT_MONSTER_COLOR_INDEX.toMutableMap()
  private val monsterDisplayNames: MutableMap<String, String> = DEFAULT_MONSTER_DISPLAY_NAMES.toMutableMap()

  private val repository by lazy { EggstraRepository(this) }

  private val prefs by lazy {
    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_home)

    bindViews()
    loadMonsterColors()
    loadMonsterDisplayNames()
    setupRecyclerViews()
    setupButtons()
    restoreState()
    bindEventList()
    rebuildWaveOptionsAndTimeline(keepPreviousWaveSelection = true)
  }

  private fun bindViews() {
    drawerLayout = findViewById(R.id.drawerLayout)
    rvEvents = findViewById(R.id.rvEvents)
    rvWaveOptions = findViewById(R.id.rvWaveOptions)
    rvTimeline = findViewById(R.id.rvTimeline)
    tvSelection = findViewById(R.id.tvSelection)
    tvWaveDrawerTitle = findViewById(R.id.tvWaveDrawerTitle)
  }

  private fun setupRecyclerViews() {
    rvEvents.layoutManager = LinearLayoutManager(this)
    rvEvents.adapter = eventAdapter

    rvWaveOptions.layoutManager = LinearLayoutManager(this)
    rvWaveOptions.adapter = waveAdapter

    rvTimeline.layoutManager = LinearLayoutManager(this)
    rvTimeline.adapter = timelineAdapter
  }

  private fun setupButtons() {
    findViewById<View>(R.id.btnOpenEventDrawer).setOnClickListener {
      drawerLayout.openDrawer(GravityCompat.START)
    }
    findViewById<View>(R.id.btnOpenWaveDrawer).setOnClickListener {
      drawerLayout.openDrawer(GravityCompat.END)
    }
    findViewById<View>(R.id.btnSettings).setOnClickListener {
      showSettingsDialog()
    }
    findViewById<View>(R.id.btnAbout).setOnClickListener {
      showAboutDialog()
    }
  }

  private fun restoreState() {
    val savedEventIndex = prefs.getInt(KEY_SELECTED_EVENT_INDEX, 11)
    selectedEventIndex = savedEventIndex.coerceIn(0, eventLabels.lastIndex)
    selectedWaveOptionKey = prefs.getString(KEY_SELECTED_WAVE_KEY, null)
  }

  private fun bindEventList() {
    eventAdapter.submitItems(eventLabels)
    eventAdapter.setSelectedIndex(selectedEventIndex)
  }

  private fun onEventSelected(position: Int) {
    if (position == selectedEventIndex) {
      return
    }
    selectedEventIndex = position
    eventAdapter.setSelectedIndex(position)
    rebuildWaveOptionsAndTimeline(keepPreviousWaveSelection = false)
  }

  private fun onWaveOptionSelected(position: Int) {
    val option = currentWaveOptions.getOrNull(position) ?: return
    selectedWaveOptionKey = option.key
    waveAdapter.setSelectedIndex(position)
    renderTimeline(option)
    updateSelectionTitle(option)
    persistState()
  }

  private fun rebuildWaveOptionsAndTimeline(keepPreviousWaveSelection: Boolean) {
    val eventNo = selectedEventIndex + 1
    val data = repository.loadEvent(eventNo)

    val options = buildWaveOptions(eventNo, data)
    currentWaveOptions = options

    val selectedIndex = when {
      options.isEmpty() -> -1
      keepPreviousWaveSelection && selectedWaveOptionKey != null -> {
        options.indexOfFirst { it.key == selectedWaveOptionKey }.takeIf { it >= 0 } ?: 0
      }

      else -> 0
    }

    waveAdapter.submitItems(options.map { it.label })
    waveAdapter.setSelectedIndex(selectedIndex)
    rvWaveOptions.scrollToPosition(0)

    tvWaveDrawerTitle.text = "${eventLabels[selectedEventIndex]} Wave + Difficulty"

    if (selectedIndex >= 0) {
      val option = options[selectedIndex]
      selectedWaveOptionKey = option.key
      renderTimeline(option)
      updateSelectionTitle(option)
    } else {
      selectedWaveOptionKey = null
      timelineAdapter.submitRows(emptyList())
      tvSelection.text = "${eventLabels[selectedEventIndex]} | No valid wave"
    }

    persistState()
  }

  private fun buildWaveOptions(eventNo: Int, data: EggstraEventData): List<WaveOption> {
    val options = mutableListOf<WaveOption>()

    data.waves.forEachIndexed { index, waveCode ->
      val waveNumber = index + 1
      if (waveCode !in ALLOWED_WAVE_CODES) {
        return@forEachIndexed
      }

      val waveSpawns = data.spawns[waveNumber.toString()] ?: return@forEachIndexed
      val allDifficulties = waveSpawns.keys
        .mapNotNull { it.toIntOrNull() }
        .sorted()

      val limitedDifficulties = when (waveNumber) {
        3 -> allDifficulties.sortedDescending().take(2).sorted()
        4 -> allDifficulties.sortedDescending().take(3).sorted()
        5 -> allDifficulties.sortedDescending().take(4).sorted()
        else -> allDifficulties
      }

      limitedDifficulties.forEach { difficulty ->
        val difficultyKey = difficulty.toString()
        val entries = waveSpawns[difficultyKey] ?: emptyList()
        val key = "e${eventNo}-w${waveNumber}-d${difficulty}"
        val label = "W${waveNumber} ${waveCodeLabel(waveCode)} [ID${waveCode}] ${(difficulty * 0.2).toInt()}%"
        options.add(
          WaveOption(
            key = key,
            waveNumber = waveNumber,
            difficultyPercent = (difficulty * 0.2).toInt(),
            label = label,
            entries = entries
          )
        )
      }
    }

    return options.sortedWith(
      compareBy<WaveOption> { it.waveNumber }
        .thenBy { it.difficultyPercent }
    )
  }

  private fun renderTimeline(option: WaveOption) {
    val filteredBosses = option.entries
      .filter { entry ->
        entry.lesser != true && !entry.boss.isNullOrBlank() && !entry.spawn.isNullOrBlank() && entry.timing != null
      }
      .sortedBy { it.timing }

    val monstersBySecondBySpawn = mutableMapOf<Int, MutableMap<String, MutableList<MonsterRender>>>()
    val eventSeconds = mutableSetOf<Int>()

    filteredBosses.forEach { entry ->
      val showTime = floor(100.0 - (entry.timing!! / 60.0)).toInt()
      if (showTime !in 25..100) return@forEach

      val spawn = entry.spawn!!.uppercase(Locale.US)
      if (spawn !in SPAWN_COLUMNS) return@forEach

      val bossKey = entry.boss!!
      val monster = MonsterRender(
        bossKey = bossKey,
        displayName = monsterDisplayName(bossKey)
      )
      eventSeconds.add(showTime)
      val spawnMap = monstersBySecondBySpawn.getOrPut(showTime) { mutableMapOf() }
      val monsters = spawnMap.getOrPut(spawn) { mutableListOf() }
      monsters.add(monster)
    }

    val baseSeconds = (100 downTo 25).toMutableSet()
    baseSeconds.addAll(eventSeconds)

    val rows = baseSeconds
      .sortedDescending()
      .map { second ->
        val bySpawn = monstersBySecondBySpawn[second]
        TimelineRow(
          second = second,
          isEventSecond = second in eventSeconds,
          a = bySpawn?.get("A") ?: emptyList(),
          b = bySpawn?.get("B") ?: emptyList(),
          c = bySpawn?.get("C") ?: emptyList()
        )
      }

    timelineAdapter.updateColorMap(monsterColorIndex)
    timelineAdapter.submitRows(rows)
    rvTimeline.scrollToPosition(0)
  }

  private fun updateSelectionTitle(option: WaveOption) {
    tvSelection.text = "${eventLabels[selectedEventIndex]} | ${option.label}"
  }

  private fun refreshCurrentTimeline() {
    val key = selectedWaveOptionKey ?: return
    val option = currentWaveOptions.firstOrNull { it.key == key } ?: return
    renderTimeline(option)
  }

  private fun persistState() {
    prefs.edit()
      .putInt(KEY_SELECTED_EVENT_INDEX, selectedEventIndex)
      .putString(KEY_SELECTED_WAVE_KEY, selectedWaveOptionKey)
      .apply()
  }

  private fun loadMonsterColors() {
    val saved = prefs.getString(KEY_MONSTER_COLORS_JSON, null) ?: return
    val type = object : TypeToken<Map<String, Int>>() {}.type
    runCatching {
      gson.fromJson<Map<String, Int>>(saved, type)
    }.getOrNull()?.forEach { (bossKey, colorIndex) ->
      if (bossKey in DEFAULT_MONSTER_DISPLAY_NAMES && colorIndex in COLOR_OPTIONS.indices) {
        monsterColorIndex[bossKey] = colorIndex
      }
    }
  }

  private fun loadMonsterDisplayNames() {
    val saved = prefs.getString(KEY_MONSTER_NAMES_JSON, null) ?: return
    val type = object : TypeToken<Map<String, String>>() {}.type
    runCatching {
      gson.fromJson<Map<String, String>>(saved, type)
    }.getOrNull()?.forEach { (bossKey, name) ->
      val trimmed = name.trim()
      if (bossKey in DEFAULT_MONSTER_DISPLAY_NAMES && trimmed.isNotEmpty()) {
        monsterDisplayNames[bossKey] = trimmed
      }
    }
  }

  private fun saveMonsterColors() {
    val json = gson.toJson(monsterColorIndex)
    prefs.edit().putString(KEY_MONSTER_COLORS_JSON, json).apply()
  }

  private fun saveMonsterDisplayNames() {
    val json = gson.toJson(monsterDisplayNames)
    prefs.edit().putString(KEY_MONSTER_NAMES_JSON, json).apply()
  }

  private fun showSettingsDialog() {
    val scrollView = ScrollView(this)
    scrollView.setBackgroundColor(Color.BLACK)
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.BLACK)
      setPadding(12.dp(this@HomeActivity), 8.dp(this@HomeActivity), 12.dp(this@HomeActivity), 8.dp(this@HomeActivity))
    }

    DEFAULT_MONSTER_DISPLAY_NAMES.forEach { (bossKey, _) ->
      val displayName = monsterDisplayName(bossKey)
      val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 8.dp(this@HomeActivity), 0, 8.dp(this@HomeActivity))
      }

      val labelView = TextView(this).apply {
        text = displayName
        setTextColor(Color.WHITE)
        textSize = 14f
        setTypeface(Typeface.DEFAULT_BOLD)
        gravity = android.view.Gravity.CENTER
        setPadding(6.dp(this@HomeActivity), 3.dp(this@HomeActivity), 6.dp(this@HomeActivity), 3.dp(this@HomeActivity))
        val initColorIndex = monsterColorIndex[bossKey] ?: 0
        background = settingsLabelDrawable(COLOR_OPTIONS[initColorIndex])
        setOnClickListener {
          showRenameDialog(bossKey, this)
        }
      }
      row.addView(
        labelView,
        LinearLayout.LayoutParams(84.dp(this), ViewGroup.LayoutParams.WRAP_CONTENT).also { lp ->
          lp.marginEnd = 12.dp(this)
        }
      )

      val optionRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
      }

      val swatches = mutableListOf<View>()

      COLOR_OPTIONS.forEachIndexed { colorIndex, colorOption ->
        val swatch = View(this).apply {
          val size = 28.dp(this@HomeActivity)
          layoutParams = LinearLayout.LayoutParams(size, size).also { lp ->
            lp.marginEnd = 8.dp(this@HomeActivity)
          }
          background = swatchDrawable(
            option = colorOption,
            selected = monsterColorIndex[bossKey] == colorIndex
          )
          setOnClickListener {
            monsterColorIndex[bossKey] = colorIndex
            labelView.background = settingsLabelDrawable(colorOption)
            swatches.forEachIndexed { idx, view ->
              view.background = swatchDrawable(
                option = COLOR_OPTIONS[idx],
                selected = idx == colorIndex
              )
            }
            saveMonsterColors()
            timelineAdapter.updateColorMap(monsterColorIndex)
            refreshCurrentTimeline()
          }
        }
        swatches.add(swatch)
        optionRow.addView(swatch)
      }

      row.addView(
        optionRow,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      )
      container.addView(row)
    }

    scrollView.addView(container)

    AlertDialog.Builder(this)
      .setTitle("Color Settings")
      .setView(scrollView)
      .setNeutralButton("Reset") { _, _ ->
        monsterColorIndex.clear()
        monsterColorIndex.putAll(DEFAULT_MONSTER_COLOR_INDEX)
        saveMonsterColors()
        timelineAdapter.updateColorMap(monsterColorIndex)
        refreshCurrentTimeline()
      }
      .setPositiveButton("Close", null)
      .show()
      .also { dialog ->
        dialog.window?.decorView?.setBackgroundColor(Color.BLACK)
        dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
      }
  }

  private fun showAboutDialog() {
    data class EventLink(val label: String, val url: String)

    val links = (1..12).map { eventNo ->
      val label = String.format(Locale.US, "event%02d", eventNo)
      val url = String.format(
        Locale.US,
        "https://leanny.github.io/eggstra_work/coop_event_%02d.html",
        eventNo
      )
      EventLink(label = label, url = url)
    }

    val adapter = object : ArrayAdapter<String>(
      this,
      android.R.layout.simple_list_item_1,
      links.map { "${it.label}  ${it.url}" }
    ) {
      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.setTextColor(Color.WHITE)
        textView.textSize = 13f
        textView.setPadding(
          12.dp(this@HomeActivity),
          10.dp(this@HomeActivity),
          12.dp(this@HomeActivity),
          10.dp(this@HomeActivity)
        )
        view.setBackgroundColor(Color.parseColor("#111111"))
        return view
      }
    }

    AlertDialog.Builder(this)
      .setTitle("About")
      .setAdapter(adapter) { _, which ->
        val link = links.getOrNull(which) ?: return@setAdapter
        runCatching {
          startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
        }.onFailure {
          android.widget.Toast.makeText(
            this,
            "Cannot open browser",
            android.widget.Toast.LENGTH_SHORT
          ).show()
        }
      }
      .setPositiveButton("Close", null)
      .show()
      .also { dialog ->
        dialog.window?.decorView?.setBackgroundColor(Color.BLACK)
        dialog.listView?.setBackgroundColor(Color.BLACK)
        dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
      }
  }

  private fun showRenameDialog(bossKey: String, labelView: TextView) {
    val input = EditText(this).apply {
      setText(monsterDisplayName(bossKey))
      setSelection(text.length)
      setTextColor(Color.WHITE)
      setHintTextColor(Color.parseColor("#8A8A8A"))
      setBackgroundColor(Color.parseColor("#1E1E1E"))
      setPadding(10.dp(this@HomeActivity), 8.dp(this@HomeActivity), 10.dp(this@HomeActivity), 8.dp(this@HomeActivity))
    }
    val dialog = AlertDialog.Builder(this)
      .setTitle("Rename")
      .setView(input)
      .setPositiveButton("Save", null)
      .setNegativeButton("Cancel", null)
      .create()

    dialog.setOnShowListener {
      dialog.window?.decorView?.setBackgroundColor(Color.BLACK)
      dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(Color.WHITE)
      dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
      dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
        val newName = input.text?.toString()?.trim().orEmpty()
        if (newName.isEmpty()) {
          input.error = "不能为空"
          return@setOnClickListener
        }
        monsterDisplayNames[bossKey] = newName
        labelView.text = newName
        saveMonsterDisplayNames()
        refreshCurrentTimeline()
        dialog.dismiss()
      }
    }
    dialog.show()
  }

  private fun waveCodeLabel(code: Int): String {
    return when (code) {
      0 -> "Normal(Mid)"
      1 -> "Normal(Low)"
      2 -> "Normal(High)"
      5 -> "Fog(Mid)"
      6 -> "Fog(Low)"
      7 -> "Fog(High)"
      10 -> "Cohock Charge(Low)"
      else -> "Unknown"
    }
  }

  private fun monsterDisplayName(bossKey: String): String {
    return monsterDisplayNames[bossKey] ?: bossKey
  }

  companion object {
    private const val PREFS_NAME = "home_state"
    private const val KEY_SELECTED_EVENT_INDEX = "selected_event_index"
    private const val KEY_SELECTED_WAVE_KEY = "selected_wave_key"
    private const val KEY_MONSTER_COLORS_JSON = "monster_colors_json"
    private const val KEY_MONSTER_NAMES_JSON = "monster_names_json"

    private val ALLOWED_WAVE_CODES = setOf(0, 1, 2, 5, 6, 7, 10)
    private val SPAWN_COLUMNS = setOf("A", "B", "C")

    private val DEFAULT_MONSTER_DISPLAY_NAMES = linkedMapOf(
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

    private val COLOR_OPTIONS = listOf(
      ColorOption("Red", "#FF6B6B", "#E55A5A", "#CC4444"),
      ColorOption("Orange", "#FFB366", "#FF9A47", "#E08033"),
      ColorOption("Yellow", "#FFEB66", "#FFE033", "#E6CC00"),
      ColorOption("Green", "#66D966", "#4CAF50", "#388E3C"),
      ColorOption("Blue", "#66B3FF", "#4DA6FF", "#3399FF"),
      ColorOption("Indigo", "#9966CC", "#8A47CC", "#7033B8"),
      ColorOption("Purple", "#CC66FF", "#B347FF", "#9933E6")
    )

    private val DEFAULT_MONSTER_COLOR_INDEX = mapOf(
      "SakelienShield" to 3,
      "SakelienCupTwins" to 0,
      "Sakediver" to 3,
      "Sakerocket" to 4,
      "SakelienSnake" to 4,
      "SakelienTower" to 0,
      "SakePillar" to 4,
      "SakeArtillery" to 0,
      "SakeDolphin" to 4,
      "SakelienBomber" to 4,
      "SakeSaucer" to 4,
      "SakelienGolden" to 1
    )
  }
}

private data class WaveOption(
  val key: String,
  val waveNumber: Int,
  val difficultyPercent: Int,
  val label: String,
  val entries: List<SpawnEntry>
)

private data class MonsterRender(
  val bossKey: String,
  val displayName: String
)

private data class TimelineRow(
  val second: Int,
  val isEventSecond: Boolean,
  val a: List<MonsterRender>,
  val b: List<MonsterRender>,
  val c: List<MonsterRender>
)

private data class ColorOption(
  val name: String,
  val primary: String,
  val secondary: String,
  val dark: String
)

private class DrawerTextAdapter(
  private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<DrawerTextAdapter.VH>() {

  private val items = mutableListOf<String>()
  private var selectedIndex = -1

  fun submitItems(newItems: List<String>) {
    items.clear()
    items.addAll(newItems)
    notifyDataSetChanged()
  }

  fun setSelectedIndex(index: Int) {
    selectedIndex = index
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
    val view = layoutInflater(parent).inflate(R.layout.item_drawer_text, parent, false)
    return VH(view as TextView)
  }

  override fun onBindViewHolder(holder: VH, position: Int) {
    holder.bind(items[position], position == selectedIndex)
    holder.itemView.setOnClickListener { onClick(position) }
  }

  override fun getItemCount(): Int = items.size

  class VH(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
    fun bind(text: String, selected: Boolean) {
      textView.text = text
      textView.setBackgroundColor(
        if (selected) 0xFF315A45.toInt() else 0xFF1A2A22.toInt()
      )
      textView.setTextColor(
        if (selected) 0xFFFFFFFF.toInt() else 0xFFD9EADF.toInt()
      )
    }
  }
}

private class TimelineAdapter(
  private val colorOptions: List<ColorOption>,
  colorIndexMap: Map<String, Int>
) : RecyclerView.Adapter<TimelineAdapter.VH>() {

  private val rows = mutableListOf<TimelineRow>()
  private var monsterColorMap: Map<String, Int> = colorIndexMap.toMap()

  fun submitRows(newRows: List<TimelineRow>) {
    rows.clear()
    rows.addAll(newRows)
    notifyDataSetChanged()
  }

  fun updateColorMap(newMap: Map<String, Int>) {
    monsterColorMap = newMap.toMap()
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
    val view = layoutInflater(parent).inflate(R.layout.item_timeline_row, parent, false)
    return VH(view)
  }

  override fun onBindViewHolder(holder: VH, position: Int) {
    holder.bind(rows[position], colorOptions, monsterColorMap)
  }

  override fun getItemCount(): Int = rows.size

  class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val rowRoot = itemView.findViewById<LinearLayout>(R.id.rowRoot)
    private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
    private val zoneA = itemView.findViewById<LinearLayout>(R.id.zoneA)
    private val zoneB = itemView.findViewById<LinearLayout>(R.id.zoneB)
    private val zoneC = itemView.findViewById<LinearLayout>(R.id.zoneC)

    fun bind(row: TimelineRow, colorOptions: List<ColorOption>, colorMap: Map<String, Int>) {
      val context = itemView.context

      tvTime.text = "${row.second}s"
      if (row.isEventSecond && row.second % 5 != 0) {
        tvTime.setTextColor(Color.parseColor("#FFAB91"))
        tvTime.setTypeface(Typeface.DEFAULT_BOLD)
      } else {
        tvTime.setTextColor(Color.parseColor("#B7CDBF"))
        tvTime.setTypeface(Typeface.DEFAULT)
      }

      rowRoot.setBackgroundColor(
        if (row.second % 5 == 0) Color.parseColor("#121F19") else Color.parseColor("#101A15")
      )

      zoneA.background = zoneBackground("#66D966")
      zoneB.background = zoneBackground("#66B3FF")
      zoneC.background = zoneBackground("#FFB366")

      bindZone(zoneA, row.a, colorOptions, colorMap, context)
      bindZone(zoneB, row.b, colorOptions, colorMap, context)
      bindZone(zoneC, row.c, colorOptions, colorMap, context)
    }

    private fun bindZone(
      container: LinearLayout,
      monsters: List<MonsterRender>,
      colorOptions: List<ColorOption>,
      colorMap: Map<String, Int>,
      context: Context
    ) {
      container.removeAllViews()
      monsters.forEachIndexed { index, monster ->
        val colorIndex = colorMap[monster.bossKey] ?: 0
        val palette = colorOptions.getOrElse(colorIndex) { colorOptions.first() }

        val chip = TextView(context).apply {
          text = monster.displayName
          setTextColor(Color.WHITE)
          setTypeface(Typeface.DEFAULT_BOLD)
          textSize = 10f
          gravity = android.view.Gravity.CENTER
          background = monsterChipDrawable(palette)
          maxLines = 1
          includeFontPadding = false
          setPadding(5.dp(context), 1.dp(context), 5.dp(context), 1.dp(context))
        }
        container.addView(
          chip,
          LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
          ).also { lp ->
            if (index > 0) lp.marginStart = 3.dp(context)
          }
        )
      }
    }
  }
}

private class EggstraRepository(
  private val context: Context
) {

  private val gson = Gson()
  private val cache = mutableMapOf<Int, EggstraEventData>()

  fun loadEvent(eventNo: Int): EggstraEventData {
    return cache.getOrPut(eventNo) {
      val rawName = String.format(Locale.US, "eggstrawork%02d", eventNo)
      val rawResId = context.resources.getIdentifier(rawName, "raw", context.packageName)
      check(rawResId != 0) { "Missing raw resource: $rawName" }
      val jsonText = context.resources.openRawResource(rawResId).bufferedReader(Charsets.UTF_8).use { it.readText() }

      val root = JsonParser.parseString(jsonText).asJsonObject
      val waves = root.get("Waves")
        ?.takeIf(JsonElement::isJsonArray)
        ?.asJsonArray
        ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asInt }
        ?: emptyList()
      val spawns = parseSpawns(root)
      EggstraEventData(waves = waves, spawns = spawns)
    }
  }

  private fun parseSpawns(root: JsonObject): Map<String, Map<String, List<SpawnEntry>>> {
    val result = mutableMapOf<String, Map<String, List<SpawnEntry>>>()
    val spawnsElement = root.get("Spawns") ?: return emptyMap()
    if (!spawnsElement.isJsonObject) return emptyMap()

    val spawnsObject = spawnsElement.asJsonObject

    for ((waveKey, waveValue) in spawnsObject.entrySet()) {
      if (!waveValue.isJsonObject) continue
      val difficultyObj = waveValue.asJsonObject
      val difficultyMap = mutableMapOf<String, List<SpawnEntry>>()

      for ((difficultyKey, difficultyValue) in difficultyObj.entrySet()) {
        if (!difficultyValue.isJsonArray) continue
        val entries = difficultyValue.asJsonArray.mapNotNull { item ->
          if (item.isJsonObject) gson.fromJson(item, SpawnEntry::class.java) else null
        }
        difficultyMap[difficultyKey] = entries
      }

      result[waveKey] = difficultyMap
    }
    return result
  }
}

private data class EggstraEventData(
  @SerializedName("Waves")
  val waves: List<Int> = emptyList(),
  @SerializedName("Spawns")
  val spawns: Map<String, Map<String, List<SpawnEntry>>> = emptyMap()
)

private data class SpawnEntry(
  @SerializedName("Spawn")
  val spawn: String? = null,
  @SerializedName("Timing")
  val timing: Int? = null,
  @SerializedName("Lesser")
  val lesser: Boolean? = null,
  @SerializedName("Boss")
  val boss: String? = null,
  @SerializedName("SubSpawn")
  val subSpawn: Int? = null
)

private fun layoutInflater(parent: ViewGroup) =
  android.view.LayoutInflater.from(parent.context)

private fun Int.dp(context: Context): Int =
  (this * context.resources.displayMetrics.density).toInt()

private fun zoneBackground(colorHex: String): GradientDrawable {
  val base = Color.parseColor(colorHex)
  val fill = Color.argb(24, Color.red(base), Color.green(base), Color.blue(base))
  return GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = 6f
    setColor(fill)
    setStroke(1, Color.argb(64, Color.red(base), Color.green(base), Color.blue(base)))
  }
}

private fun monsterChipDrawable(option: ColorOption): GradientDrawable {
  return GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(Color.parseColor(option.primary), Color.parseColor(option.secondary))
  ).apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = 6f
    setStroke(1, Color.parseColor(option.dark))
  }
}

private fun swatchDrawable(option: ColorOption, selected: Boolean): GradientDrawable {
  return GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(Color.parseColor(option.primary), Color.parseColor(option.secondary))
  ).apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = 8f
    setStroke(
      if (selected) 3 else 1,
      if (selected) Color.WHITE else Color.parseColor(option.dark)
    )
  }
}

private fun settingsLabelDrawable(option: ColorOption): GradientDrawable {
  return GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(Color.parseColor(option.primary), Color.parseColor(option.secondary))
  ).apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = 8f
    setStroke(1, Color.parseColor(option.dark))
  }
}
