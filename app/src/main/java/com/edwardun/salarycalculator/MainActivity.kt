package com.edwardun.salarycalculator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale

private const val PREFS = "salary_history"
private const val KEY_HISTORY = "history_json"

private data class SalaryRecord(
    val id: Long,
    val year: Int,
    val month: Int,
    val salary: Double,
    val normHours: Double,
    val workedHours: Double,
    val extraGross: Double,
    val overtimeMultiplier: Double,
    val taxFree: Double,
    val pensionRate: Double,
    val actualNet: Double?,
    val gross: Double,
    val net: Double
)

private data class Calculation(
    val hourlyRate: Double,
    val overtimeHours: Double,
    val overtimePay: Double,
    val gross: Double,
    val unemployment: Double,
    val pension: Double,
    val incomeTax: Double,
    val net: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                SalaryApp(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalaryApp(context: Context) {
    val now = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var salary by remember { mutableStateOf("950") }
    var normHours by remember { mutableStateOf("160") }
    var workedHours by remember { mutableStateOf("160") }
    var extraGross by remember { mutableStateOf("0") }
    var multiplier by remember { mutableStateOf("1.5") }
    var taxFree by remember { mutableStateOf("700") }
    var pensionRate by remember { mutableStateOf("0") }
    var actualNet by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }

    val calc = calculate(
        salary.toNum(), normHours.toNum(), workedHours.toNum(), extraGross.toNum(),
        multiplier.toNum().coerceAtLeast(0.0), taxFree.toNum().coerceAtLeast(0.0), pensionRate.toNum()
    )

    fun clearForm() {
        selectedYear = now.get(Calendar.YEAR)
        selectedMonth = now.get(Calendar.MONTH) + 1
        salary = "950"
        normHours = "160"
        workedHours = "160"
        extraGross = "0"
        multiplier = "1.5"
        taxFree = "700"
        pensionRate = "0"
        actualNet = ""
        editingId = null
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Зарплата EE") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text("Калькулятор", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Для плавающего графика: норму и фактические часы вводишь вручную.", style = MaterialTheme.typography.bodySmall)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Месяц", selectedMonth.toString(), { selectedMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: selectedMonth }, Modifier.weight(1f), integer = true)
                    NumberField("Год", selectedYear.toString(), { selectedYear = it.toIntOrNull()?.coerceIn(2000, 2100) ?: selectedYear }, Modifier.weight(1f), integer = true)
                }
            }

            item { NumberField("Оклад brutto, €", salary, { salary = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Норма часов", normHours, { normHours = it }, Modifier.weight(1f))
                    NumberField("Отработано", workedHours, { workedHours = it }, Modifier.weight(1f))
                }
            }
            item { NumberField("Доплата / премия brutto, €", extraGross, { extraGross = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Сверхурочные ×", multiplier, { multiplier = it }, Modifier.weight(1f))
                    NumberField("Maksuvaba tulu, €", taxFree, { taxFree = it }, Modifier.weight(1f))
                }
            }

            item {
                Text("II пенсионная ступень", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.0, 2.0, 4.0, 6.0).forEach { rate ->
                        FilterChip(
                            selected = pensionRate.toNum() == rate,
                            onClick = { pensionRate = rate.toInt().toString() },
                            label = { Text(if (rate == 0.0) "Нет" else "${rate.toInt()}%") }
                        )
                    }
                }
            }

            item { ResultCard(calc) }
            item { NumberField("Фактически получил на руки, € (необязательно)", actualNet, { actualNet = it }) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val record = SalaryRecord(
                                id = editingId ?: System.currentTimeMillis(),
                                year = selectedYear,
                                month = selectedMonth,
                                salary = salary.toNum(),
                                normHours = normHours.toNum(),
                                workedHours = workedHours.toNum(),
                                extraGross = extraGross.toNum(),
                                overtimeMultiplier = multiplier.toNum(),
                                taxFree = taxFree.toNum(),
                                pensionRate = pensionRate.toNum(),
                                actualNet = actualNet.toNumOrNull(),
                                gross = calc.gross,
                                net = calc.net
                            )
                            history = (history.filterNot { it.id == record.id } + record)
                                .sortedWith(compareByDescending<SalaryRecord> { it.year }.thenByDescending { it.month }.thenByDescending { it.id })
                            saveHistory(context, history)
                            clearForm()
                        },
                        enabled = normHours.toNum() > 0 && salary.toNum() >= 0 && workedHours.toNum() >= 0,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (editingId == null) "Сохранить месяц" else "Сохранить изменения") }
                    if (editingId != null) {
                        OutlinedButton(onClick = { clearForm() }) { Text("Отмена") }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("История зарплат", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }

            if (history.isEmpty()) {
                item { Text("Пока ничего не сохранено.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(history, key = { it.id }) { record ->
                    HistoryCard(
                        record = record,
                        onEdit = {
                            editingId = record.id
                            selectedYear = record.year
                            selectedMonth = record.month
                            salary = record.salary.clean()
                            normHours = record.normHours.clean()
                            workedHours = record.workedHours.clean()
                            extraGross = record.extraGross.clean()
                            multiplier = record.overtimeMultiplier.clean()
                            taxFree = record.taxFree.clean()
                            pensionRate = record.pensionRate.clean()
                            actualNet = record.actualNet?.clean() ?: ""
                        },
                        onDelete = {
                            history = history.filterNot { it.id == record.id }
                            saveHistory(context, history)
                            if (editingId == record.id) clearForm()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    integer: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = if (integer) raw.filter { it.isDigit() } else raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
            onValueChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun ResultCard(calc: Calculation) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Расчёт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ResultRow("Часовая ставка", "${money(calc.hourlyRate)} €/ч")
            ResultRow("Сверхурочные часы", hours(calc.overtimeHours))
            ResultRow("Оплата сверхурочных", "${money(calc.overtimePay)} €")
            HorizontalDivider()
            ResultRow("Brutto", "${money(calc.gross)} €", true)
            ResultRow("Töötuskindlustus 1,6%", "−${money(calc.unemployment)} €")
            if (calc.pension > 0) ResultRow("II sammas", "−${money(calc.pension)} €")
            ResultRow("Tulumaks 22%", "−${money(calc.incomeTax)} €")
            HorizontalDivider()
            ResultRow("На руки (netto)", "${money(calc.net)} €", true)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun HistoryCard(record: SalaryRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val monthName = monthName(record.month)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$monthName ${record.year}", fontWeight = FontWeight.Bold)
                    Text("${hours(record.workedHours)} ч • норма ${hours(record.normHours)} ч", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Редактировать") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить") }
            }
            ResultRow("Brutto", "${money(record.gross)} €")
            ResultRow("Расчётное netto", "${money(record.net)} €", true)
            record.actualNet?.let {
                ResultRow("Получено фактически", "${money(it)} €")
                ResultRow("Разница", signedMoney(it - record.net))
            }
        }
    }
}

private fun calculate(
    salary: Double,
    normHours: Double,
    workedHours: Double,
    extraGross: Double,
    multiplier: Double,
    taxFree: Double,
    pensionRate: Double
): Calculation {
    val safeNorm = normHours.coerceAtLeast(0.0)
    val hourly = if (safeNorm > 0) salary.coerceAtLeast(0.0) / safeNorm else 0.0
    val overtimeHours = (workedHours - safeNorm).coerceAtLeast(0.0)
    val overtimePay = overtimeHours * hourly * multiplier.coerceAtLeast(0.0)
    val gross = (salary + overtimePay + extraGross).coerceAtLeast(0.0)
    val unemployment = gross * 0.016
    val pension = gross * (pensionRate.coerceIn(0.0, 100.0) / 100.0)
    val taxable = (gross - unemployment - pension - taxFree).coerceAtLeast(0.0)
    val incomeTax = taxable * 0.22
    val net = (gross - unemployment - pension - incomeTax).coerceAtLeast(0.0)
    return Calculation(hourly, overtimeHours, overtimePay, gross, unemployment, pension, incomeTax, net)
}

private fun saveHistory(context: Context, records: List<SalaryRecord>) {
    val arr = JSONArray()
    records.forEach { r ->
        arr.put(JSONObject().apply {
            put("id", r.id); put("year", r.year); put("month", r.month)
            put("salary", r.salary); put("normHours", r.normHours); put("workedHours", r.workedHours)
            put("extraGross", r.extraGross); put("overtimeMultiplier", r.overtimeMultiplier)
            put("taxFree", r.taxFree); put("pensionRate", r.pensionRate)
            if (r.actualNet != null) put("actualNet", r.actualNet) else put("actualNet", JSONObject.NULL)
            put("gross", r.gross); put("net", r.net)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, arr.toString()).apply()
}

private fun loadHistory(context: Context): List<SalaryRecord> = try {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HISTORY, "[]") ?: "[]"
    val arr = JSONArray(raw)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        SalaryRecord(
            id = o.getLong("id"), year = o.getInt("year"), month = o.getInt("month"),
            salary = o.getDouble("salary"), normHours = o.getDouble("normHours"), workedHours = o.getDouble("workedHours"),
            extraGross = o.getDouble("extraGross"), overtimeMultiplier = o.getDouble("overtimeMultiplier"),
            taxFree = o.getDouble("taxFree"), pensionRate = o.getDouble("pensionRate"),
            actualNet = if (o.isNull("actualNet")) null else o.getDouble("actualNet"),
            gross = o.getDouble("gross"), net = o.getDouble("net")
        )
    }.sortedWith(compareByDescending<SalaryRecord> { it.year }.thenByDescending { it.month }.thenByDescending { it.id })
} catch (_: Exception) { emptyList() }

private fun String.toNum(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun String.toNumOrNull(): Double? = replace(',', '.').toDoubleOrNull()
private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
private val df = DecimalFormat("0.00")
private fun money(v: Double): String = df.format(v).replace(',', '.')
private fun hours(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else DecimalFormat("0.##").format(v)
private fun signedMoney(v: Double): String = (if (v >= 0) "+" else "−") + money(kotlin.math.abs(v)) + " €"
private fun monthName(month: Int): String = listOf("Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь").getOrElse(month - 1) { "Месяц" }
