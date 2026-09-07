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

private const val PREFS = "salary_history"
private const val KEY_HISTORY = "history_json"
private const val KEY_EXPENSES = "expenses_json"
private const val KEY_INCOME_TAX = "income_tax_rate"
private const val KEY_UNEMPLOYMENT = "unemployment_rate"
private const val KEY_TAX_FREE = "tax_free_default"

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
    val incomeTaxRate: Double,
    val unemploymentRate: Double,
    val actualNet: Double?,
    val gross: Double,
    val net: Double
)

private data class ExpenseRecord(
    val id: Long,
    val year: Int,
    val month: Int,
    val title: String,
    val category: String,
    val amount: Double,
    val paid: Boolean
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
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Зарплата", "Расходы")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Зарплата EE") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (tab) {
                0 -> SalaryScreen(context)
                else -> ExpensesScreen(context)
            }
        }
    }
}

@Composable
private fun SalaryScreen(context: Context) {
    val now = remember { Calendar.getInstance() }
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var salary by remember { mutableStateOf("950") }
    var normHours by remember { mutableStateOf("160") }
    var workedHours by remember { mutableStateOf("160") }
    var extraGross by remember { mutableStateOf("0") }
    var multiplier by remember { mutableStateOf("1.5") }
    var taxFree by remember { mutableStateOf(prefs.getString(KEY_TAX_FREE, "700") ?: "700") }
    var incomeTaxRate by remember { mutableStateOf(prefs.getString(KEY_INCOME_TAX, "22") ?: "22") }
    var unemploymentRate by remember { mutableStateOf(prefs.getString(KEY_UNEMPLOYMENT, "1.6") ?: "1.6") }
    var pensionRate by remember { mutableStateOf("0") }
    var actualNet by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }

    val calc = calculate(
        salary.toNum(), normHours.toNum(), workedHours.toNum(), extraGross.toNum(),
        multiplier.toNum().coerceAtLeast(0.0), taxFree.toNum().coerceAtLeast(0.0), pensionRate.toNum(),
        incomeTaxRate.toNum().coerceIn(0.0, 100.0), unemploymentRate.toNum().coerceIn(0.0, 100.0)
    )

    fun persistTaxDefaults() {
        prefs.edit()
            .putString(KEY_TAX_FREE, taxFree)
            .putString(KEY_INCOME_TAX, incomeTaxRate)
            .putString(KEY_UNEMPLOYMENT, unemploymentRate)
            .apply()
    }

    fun clearForm() {
        selectedYear = now.get(Calendar.YEAR)
        selectedMonth = now.get(Calendar.MONTH) + 1
        salary = "950"
        normHours = "160"
        workedHours = "160"
        extraGross = "0"
        multiplier = "1.5"
        taxFree = prefs.getString(KEY_TAX_FREE, "700") ?: "700"
        incomeTaxRate = prefs.getString(KEY_INCOME_TAX, "22") ?: "22"
        unemploymentRate = prefs.getString(KEY_UNEMPLOYMENT, "1.6") ?: "1.6"
        pensionRate = "0"
        actualNet = ""
        editingId = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Text("Калькулятор", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Норму и фактические часы вводишь вручную.", style = MaterialTheme.typography.bodySmall)
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
            Text("Налоговые ставки", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Tulumaks, %", incomeTaxRate, { incomeTaxRate = it }, Modifier.weight(1f))
                NumberField("Töötuskindlustus, %", unemploymentRate, { unemploymentRate = it }, Modifier.weight(1f))
            }
            Text("Значения сохраняются как новые значения по умолчанию при сохранении месяца.", style = MaterialTheme.typography.bodySmall)
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

        item { ResultCard(calc, incomeTaxRate.toNum(), unemploymentRate.toNum()) }
        item { NumberField("Фактически получил на руки, € (необязательно)", actualNet, { actualNet = it }) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        persistTaxDefaults()
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
                            incomeTaxRate = incomeTaxRate.toNum(),
                            unemploymentRate = unemploymentRate.toNum(),
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
            item { Text("Пока ничего не сохранено.") }
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
                        incomeTaxRate = record.incomeTaxRate.clean()
                        unemploymentRate = record.unemploymentRate.clean()
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

@Composable
private fun ExpensesScreen(context: Context) {
    val now = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Другое") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var expenses by remember { mutableStateOf(loadExpenses(context)) }
    val salaryHistory = remember(expenses) { loadHistory(context) }

    val monthExpenses = expenses
        .filter { it.year == selectedYear && it.month == selectedMonth }
        .sortedWith(compareBy<ExpenseRecord> { it.paid }.thenBy { it.category }.thenBy { it.title.lowercase() })
    val total = monthExpenses.sumOf { it.amount }
    val paid = monthExpenses.filter { it.paid }.sumOf { it.amount }
    val remaining = (total - paid).coerceAtLeast(0.0)
    val matchingSalary = salaryHistory
        .filter { it.year == selectedYear && it.month == selectedMonth }
        .maxByOrNull { it.id }
    val availableNet = matchingSalary?.actualNet ?: matchingSalary?.net

    fun clearExpenseForm() {
        title = ""
        amount = ""
        category = "Другое"
        editingId = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Text("Месячные расходы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Добавляй связь, кредиты, жильё и другие платежи и отмечай их как оплаченные.", style = MaterialTheme.typography.bodySmall)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Месяц", selectedMonth.toString(), { selectedMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: selectedMonth }, Modifier.weight(1f), integer = true)
                NumberField("Год", selectedYear.toString(), { selectedYear = it.toIntOrNull()?.coerceIn(2000, 2100) ?: selectedYear }, Modifier.weight(1f), integer = true)
            }
        }

        item {
            ExpenseSummaryCard(total, paid, remaining, availableNet)
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                label = { Text("Название, например Elisa или Inbank") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { NumberField("Сумма, €", amount, { amount = it }) }
        item {
            CategorySelector(category = category, onCategoryChange = { category = it })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val cleanTitle = title.trim()
                        val cleanAmount = amount.toNum()
                        if (cleanTitle.isNotEmpty() && cleanAmount > 0) {
                            val existingPaid = expenses.firstOrNull { it.id == editingId }?.paid ?: false
                            val record = ExpenseRecord(
                                id = editingId ?: System.currentTimeMillis(),
                                year = selectedYear,
                                month = selectedMonth,
                                title = cleanTitle,
                                category = category,
                                amount = cleanAmount,
                                paid = existingPaid
                            )
                            expenses = expenses.filterNot { it.id == record.id } + record
                            saveExpenses(context, expenses)
                            clearExpenseForm()
                        }
                    },
                    enabled = title.trim().isNotEmpty() && amount.toNum() > 0,
                    modifier = Modifier.weight(1f)
                ) { Text(if (editingId == null) "Добавить расход" else "Сохранить") }
                if (editingId != null) {
                    OutlinedButton(onClick = { clearExpenseForm() }) { Text("Отмена") }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("Платежи за ${monthName(selectedMonth)} ${selectedYear}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        if (monthExpenses.isEmpty()) {
            item { Text("Пока расходов за этот месяц нет.") }
        } else {
            items(monthExpenses, key = { it.id }) { expense ->
                ExpenseCard(
                    expense = expense,
                    onPaidChange = { checked ->
                        expenses = expenses.map { if (it.id == expense.id) it.copy(paid = checked) else it }
                        saveExpenses(context, expenses)
                    },
                    onEdit = {
                        editingId = expense.id
                        title = expense.title
                        amount = expense.amount.clean()
                        category = expense.category
                    },
                    onDelete = {
                        expenses = expenses.filterNot { it.id == expense.id }
                        saveExpenses(context, expenses)
                        if (editingId == expense.id) clearExpenseForm()
                    }
                )
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
private fun ResultCard(calc: Calculation, incomeTaxRate: Double, unemploymentRate: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Расчёт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ResultRow("Часовая ставка", "${money(calc.hourlyRate)} €/ч")
            ResultRow("Сверхурочные часы", hours(calc.overtimeHours))
            ResultRow("Оплата сверхурочных", "${money(calc.overtimePay)} €")
            HorizontalDivider()
            ResultRow("Brutto", "${money(calc.gross)} €", true)
            ResultRow("Töötuskindlustus ${rateLabel(unemploymentRate)}", "−${money(calc.unemployment)} €")
            if (calc.pension > 0) ResultRow("II sammas", "−${money(calc.pension)} €")
            ResultRow("Tulumaks ${rateLabel(incomeTaxRate)}", "−${money(calc.incomeTax)} €")
            HorizontalDivider()
            ResultRow("На руки (netto)", "${money(calc.net)} €", true)
        }
    }
}

@Composable
private fun ExpenseSummaryCard(total: Double, paid: Double, remaining: Double, availableNet: Double?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Итог месяца", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ResultRow("Всего расходов", "${money(total)} €", true)
            ResultRow("Оплачено", "${money(paid)} €")
            ResultRow("Осталось оплатить", "${money(remaining)} €")
            if (availableNet != null) {
                HorizontalDivider()
                ResultRow("Зарплата на руки", "${money(availableNet)} €")
                ResultRow("После всех расходов", signedMoney(availableNet - total), true)
            }
        }
    }
}

@Composable
private fun CategorySelector(category: String, onCategoryChange: (String) -> Unit) {
    val categories = listOf("Кредит", "Жильё", "Связь", "Еда", "Транспорт", "Подписка", "Страховка", "Другое")
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Категория: $category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onCategoryChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: ExpenseRecord,
    onPaidChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = expense.paid, onCheckedChange = onPaidChange)
                Column(Modifier.weight(1f)) {
                    Text(expense.title, fontWeight = FontWeight.Bold)
                    Text(expense.category, style = MaterialTheme.typography.bodySmall)
                }
                Text("${money(expense.amount)} €", fontWeight = FontWeight.Bold)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Редактировать") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить") }
            }
            Text(if (expense.paid) "Оплачено" else "Не оплачено", style = MaterialTheme.typography.bodySmall)
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
            Text("Tulumaks ${rateLabel(record.incomeTaxRate)} • töötuskindlustus ${rateLabel(record.unemploymentRate)} • maksuvaba ${money(record.taxFree)} €", style = MaterialTheme.typography.bodySmall)
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
    pensionRate: Double,
    incomeTaxRate: Double,
    unemploymentRate: Double
): Calculation {
    val safeNorm = normHours.coerceAtLeast(0.0)
    val hourly = if (safeNorm > 0) salary.coerceAtLeast(0.0) / safeNorm else 0.0
    val overtimeHours = (workedHours - safeNorm).coerceAtLeast(0.0)
    val overtimePay = overtimeHours * hourly * multiplier.coerceAtLeast(0.0)
    val gross = (salary + overtimePay + extraGross).coerceAtLeast(0.0)
    val unemployment = gross * (unemploymentRate.coerceIn(0.0, 100.0) / 100.0)
    val pension = gross * (pensionRate.coerceIn(0.0, 100.0) / 100.0)
    val taxable = (gross - unemployment - pension - taxFree).coerceAtLeast(0.0)
    val incomeTax = taxable * (incomeTaxRate.coerceIn(0.0, 100.0) / 100.0)
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
            put("incomeTaxRate", r.incomeTaxRate); put("unemploymentRate", r.unemploymentRate)
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
            incomeTaxRate = o.optDouble("incomeTaxRate", 22.0),
            unemploymentRate = o.optDouble("unemploymentRate", 1.6),
            actualNet = if (o.isNull("actualNet")) null else o.getDouble("actualNet"),
            gross = o.getDouble("gross"), net = o.getDouble("net")
        )
    }.sortedWith(compareByDescending<SalaryRecord> { it.year }.thenByDescending { it.month }.thenByDescending { it.id })
} catch (_: Exception) { emptyList() }

private fun saveExpenses(context: Context, records: List<ExpenseRecord>) {
    val arr = JSONArray()
    records.forEach { r ->
        arr.put(JSONObject().apply {
            put("id", r.id); put("year", r.year); put("month", r.month)
            put("title", r.title); put("category", r.category); put("amount", r.amount); put("paid", r.paid)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EXPENSES, arr.toString()).apply()
}

private fun loadExpenses(context: Context): List<ExpenseRecord> = try {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EXPENSES, "[]") ?: "[]"
    val arr = JSONArray(raw)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ExpenseRecord(
            id = o.getLong("id"), year = o.getInt("year"), month = o.getInt("month"),
            title = o.getString("title"), category = o.optString("category", "Другое"),
            amount = o.getDouble("amount"), paid = o.optBoolean("paid", false)
        )
    }
} catch (_: Exception) { emptyList() }

private fun String.toNum(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun String.toNumOrNull(): Double? = replace(',', '.').toDoubleOrNull()
private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
private val df = DecimalFormat("0.00")
private fun money(v: Double): String = df.format(v).replace(',', '.')
private fun hours(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else DecimalFormat("0.##").format(v)
private fun signedMoney(v: Double): String = (if (v >= 0) "+" else "−") + money(kotlin.math.abs(v)) + " €"
private fun rateLabel(v: Double): String = if (v % 1.0 == 0.0) "${v.toInt()}%" else "${DecimalFormat("0.##").format(v)}%"
private fun monthName(month: Int): String = listOf("Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь").getOrElse(month - 1) { "Месяц" }
