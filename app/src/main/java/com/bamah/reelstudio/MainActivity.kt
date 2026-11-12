package com.bamah.reelstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Bg = Color(0xFF0B0B0D)
private val Panel = Color(0xFF171719)
private val Peach = Color(0xFFE9A17B)
private val Ink = Color(0xFFF7F4F1)
private val Muted = Color(0xFFA7A2A0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Peach, background = Bg, surface = Panel)) { BuilderScreen() } }
    }
}

@Composable
private fun BuilderScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var config by remember { mutableStateOf(ChallengeConfig()) }
    var words by remember { mutableStateOf(emptyList<WordItem>()) }
    var word by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }
    var pronunciation by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    var editIndex by remember { mutableIntStateOf(-1) }
    var previewAnswer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Add cards or load the sample challenge. All content fields are optional.") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Cannot open file")
            val (loadedConfig, loadedWords) = ChallengeJson.decode(raw)
            words = loadedWords
            if (loadedConfig != null) config = loadedConfig
            message = "Imported ${words.size} card(s)."
        } catch (e: Exception) { message = "Import failed: ${e.message ?: "Invalid JSON"}" }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(ChallengeJson.encode(config, words)) }
            message = "Challenge exported."
        } catch (e: Exception) { message = "Export failed: ${e.message}" }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && audioGranted) {
            context.startActivity(Intent(context, RecordingActivity::class.java).putExtra("config", ChallengeJson.encode(config, words)))
        } else message = "Camera and microphone permissions are required to record."
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("BaMah", color = Ink, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text("REEL STUDIO", color = Peach, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Text("NATIVE ANDROID · V2", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item {
                    SectionCard("Live card preview") {
                        ChallengeCardPreview(config, WordItem(
                            word.ifBlank { words.firstOrNull()?.word.orEmpty() },
                            definition.ifBlank { words.firstOrNull()?.definition.orEmpty() },
                            pronunciation.ifBlank { words.firstOrNull()?.pronunciation.orEmpty() },
                            emoji
                        ), 0, words.size.coerceAtLeast(1), previewAnswer, if (previewAnswer) 1f else 0.64f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !previewAnswer, onClick = { previewAnswer = false }, label = { Text("Question face") })
                            FilterChip(selected = previewAnswer, onClick = { previewAnswer = true }, label = { Text("Reveal face") })
                        }
                    }
                }
                item {
                    SectionCard("Challenge header") {
                        Field("Challenge label (optional)", config.title) { config = config.copy(title = it) }
                        Field("CTA text (optional)", config.ctaText) { config = config.copy(ctaText = it) }
                        ToggleRow("Show challenge label", config.showChallengeLabel) { config = config.copy(showChallengeLabel = it) }
                        ToggleRow("Show CTA", config.showCta) { config = config.copy(showCta = it) }
                        ToggleRow("Show peach top tab", config.showTopTab) { config = config.copy(showTopTab = it) }
                        Text("Label tint opacity · ${config.labelBackgroundOpacity}%", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Slider(value = config.labelBackgroundOpacity.toFloat(), onValueChange = { config = config.copy(labelBackgroundOpacity = it.toInt()) }, valueRange = 0f..40f)
                    }
                }
                item {
                    SectionCard("Timing and playback") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondsField("Answer seconds", config.answerSeconds, { config = config.copy(answerSeconds = it) }, Modifier.weight(1f))
                            SecondsField("Reveal seconds", config.revealSeconds, { config = config.copy(revealSeconds = it) }, Modifier.weight(1f))
                        }
                        Text("Enter 0–60 seconds. Zero skips that phase. No timing field is required.", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Audio", config.audioMode, listOf("both", "manual", "auto"), { config = config.copy(audioMode = it) }, Modifier.weight(1f))
                            Choice("Legacy theme", config.theme, listOf("reference", "bam"), { config = config.copy(theme = it) }, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    SectionCard("Appearance · main and accent colors") {
                        ColorPalette("Main color · card background", config.mainColorHex, MAIN_COLORS) {
                            config = config.copy(mainColorHex = it, cardBackgroundHex = it)
                        }
                        ColorPalette("Accent color · peach elements", config.accentColorHex, ACCENT_COLORS) {
                            config = config.copy(accentColorHex = it)
                        }
                        ColorPalette("Text color override", config.cardTextHex, TEXT_COLORS) {
                            config = config.copy(cardTextHex = it)
                        }
                        Text("The accent controls the word on reveal, label, top tab, timer, stressed syllable and active progress.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                item {
                    SectionCard("Visible card elements") {
                        ToggleRow("Show target icon / emoji", config.showIcon) { config = config.copy(showIcon = it) }
                        ToggleRow("Show upper timer line", config.showTimer) { config = config.copy(showTimer = it) }
                        ToggleRow("Show bottom challenge progress", config.showBottomProgress) { config = config.copy(showBottomProgress = it) }
                    }
                }
                item {
                    SectionCard(if (editIndex >= 0) "Edit card" else "Create a card") {
                        Field("Main word (optional)", word) { word = it }
                        Field("Definition (optional)", definition) { definition = it }
                        Field("Pronunciation (optional; uppercase stressed syllable)", pronunciation) { pronunciation = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Field("Icon / emoji (optional)", emoji) { emoji = it.take(6) } }
                            TextButton(onClick = { emoji = "" }) { Text("Hide icon") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val item = WordItem(word.trim(), definition.trim(), pronunciation.trim(), emoji.trim())
                                words = if (editIndex >= 0) words.toMutableList().also { it[editIndex] = item } else words + item
                                word = ""; definition = ""; pronunciation = ""; emoji = "🎯"; editIndex = -1
                                message = "Card saved. Empty fields are allowed."
                            }) { Text(if (editIndex >= 0) "Save card" else "Add card") }
                            OutlinedButton(onClick = { word=""; definition=""; pronunciation=""; emoji="🎯"; editIndex=-1 }) { Text("Clear fields") }
                        }
                    }
                }
                item {
                    SectionCard("Your cards · ${words.size}") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            OutlinedButton(onClick = { words = sampleWords; message = "Sample challenge loaded." }) { Text("Load sample") }
                            OutlinedButton(onClick = { exportLauncher.launch("BaMah-challenge.json") }) { Text("Export JSON") }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) { Text("Import JSON") }
                        }
                        if (words.isEmpty()) Text("No cards yet. You can add even a completely blank card.", color = Muted)
                    }
                }
                itemsIndexed(words) { index, item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${index + 1}. ${item.emoji} ${item.word.ifBlank { "(blank word)" }}", color = Ink, fontWeight = FontWeight.Bold)
                                    Text(listOf(item.pronunciation, item.definition).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "All fields empty" },
                                        color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { word=item.word; definition=item.definition; pronunciation=item.pronunciation; emoji=item.emoji; editIndex=index }) { Text("Edit") }
                                TextButton(onClick = { words = words.toMutableList().also { if (index > 0) { val x=it.removeAt(index); it.add(index-1,x) } } }, enabled = index > 0) { Text("↑") }
                                TextButton(onClick = { words = words.toMutableList().also { if (index < it.lastIndex) { val x=it.removeAt(index); it.add(index+1,x) } } }, enabled = index < words.lastIndex) { Text("↓") }
                                TextButton(onClick = { words = words.toMutableList().also { it.removeAt(index) }; if (editIndex == index) editIndex=-1 }) { Text("×") }
                            }
                        }
                    }
                }
                item { Text(message, color = Peach, style = MaterialTheme.typography.bodySmall) }
            }
            Button(
                onClick = {
                    if (words.isEmpty()) message = "Add at least one card. Its fields can all be blank."
                    else {
                        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) context.startActivity(Intent(context, RecordingActivity::class.java).putExtra("config", ChallengeJson.encode(config, words)))
                        else cameraPermission.launch(missing.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(14.dp).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = Color.Black)
            ) { Text("OPEN FULL-SCREEN RECORDING STUDIO", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Peach, unfocusedBorderColor = Color.DarkGray, focusedLabelColor = Peach, unfocusedLabelColor = Muted))
}
@Composable private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
private val MAIN_COLORS = listOf("#191817", "#101820", "#202B33", "#30202B", "#1E3329", "#FFFFFF", "#F4EBDD")
private val ACCENT_COLORS = listOf("#E9A17B", "#D98B73", "#E7B96F", "#A9C7B5", "#91B8D0", "#C6A4D8", "#F2F0ED")
private val TEXT_COLORS = listOf("#F2F0ED", "#FFFFFF", "#242424", "#D8D4D0", "#F0D6C6")
@Composable private fun SecondsField(label: String, value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = text, onValueChange = { raw ->
        val digits = raw.filter(Char::isDigit).take(2); text = digits
        digits.toIntOrNull()?.takeIf { it in 0..60 }?.let(onValue)
    }, label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Peach, unfocusedBorderColor = Color.DarkGray, focusedLabelColor = Peach, unfocusedLabelColor = Muted))
}
@Composable private fun ColorPalette(label: String, selected: String, colors: List<String>, onSelect: (String) -> Unit) {
    var customHex by remember(selected) { mutableStateOf(selected) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            colors.forEach { hex ->
                val swatch = uiColor(hex, Color.Gray)
                Box(Modifier.size(32.dp).background(swatch, CircleShape)
                    .border(if (hex.equals(selected, true)) 3.dp else 1.dp, if (hex.equals(selected, true)) Peach else Color.Gray, CircleShape)
                    .clickable { customHex = hex; onSelect(hex) })
            }
        }
        OutlinedTextField(value = customHex, onValueChange = { raw ->
            val candidate = raw.uppercase().take(7); customHex = candidate
            if (Regex("^#[0-9A-F]{6}$").matches(candidate)) onSelect(candidate)
        }, label = { Text("Custom HEX · #RRGGBB") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Peach, unfocusedBorderColor = Color.DarkGray, focusedLabelColor = Peach, unfocusedLabelColor = Muted))
    }
}
@Composable private fun Choice(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
            }
        }
    }
}
