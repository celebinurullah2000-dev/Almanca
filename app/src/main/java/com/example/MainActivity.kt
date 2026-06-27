package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Word
import com.example.data.WordDatabase
import com.example.data.WordRepository
import com.example.ui.WordViewModel
import com.example.ui.WordViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WordApp()
            }
        }
    }
}

@Composable
fun WordApp() {
    val context = LocalContext.current

    // Initialize Room components safely using the standard application context
    val database = remember { WordDatabase.getDatabase(context.applicationContext) }
    val repository = remember { WordRepository(database.wordDao()) }
    val viewModel: WordViewModel = viewModel(
        factory = WordViewModelFactory(repository)
    )

    // State bindings
    val words by viewModel.wordListState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }

    // TTS Setup
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = ttsInstance?.setLanguage(Locale.GERMAN)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true
                }
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Coroutine scope and SnackBar host state for Undo Deletion functionality
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(8.dp)
                    .testTag("add_word_fab"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Kelime Ekle"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kelime Ekle",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Elegant Header Area with Brush Gradient and Stats Card
            HeaderSection(totalWords = words.size)

            // Search Bar Component
            SearchBarSection(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) }
            )

            // Word List or Empty States
            if (words.isEmpty()) {
                EmptyStateSection(
                    isSearching = searchQuery.isNotBlank(),
                    onClearSearch = { viewModel.onSearchQueryChanged("") },
                    onAddFirstWord = { showAddDialog = true }
                )
            } else {
                val groupedWords = remember(words) {
                    words.groupBy { it.german.firstOrNull()?.uppercaseChar() ?: '?' }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .testTag("word_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = WindowInsets.safeDrawing.asPaddingValues()
                ) {
                    groupedWords.forEach { (initial, wordList) ->
                        item(key = "header_$initial") {
                            AlphabeticDivider(letter = initial)
                        }
                        items(wordList, key = { it.id }) { word ->
                            WordCardItem(
                                word = word,
                                onSpeak = {
                                    if (isTtsReady) {
                                        ttsInstance?.speak(
                                            word.german,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "german_tts"
                                        )
                                    }
                                },
                                onDelete = {
                                    // Save word reference for Undo, then delete
                                    val targetWord = word
                                    viewModel.deleteWord(targetWord)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "\"${targetWord.german}\" defterden silindi.",
                                            actionLabel = "Geri Al",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.addWord(targetWord.german, targetWord.turkish)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Beautiful Add Word Modal Dialog
        if (showAddDialog) {
            AddWordDialog(
                onDismiss = { showAddDialog = false },
                onSave = { german, turkish ->
                    viewModel.addWord(german, turkish)
                    showAddDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "\"$german\" başarıyla kaydedildi.",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun AlphabeticDivider(letter: Char) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Giant Serif initial behind
        Text(
            text = letter.toString(),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 72.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        )
        // Accent separator and horizontal line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$letter Harfi",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
fun HeaderSection(totalWords: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
    ) {
        // Giant background typographic element
        Text(
            text = "A",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 120.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 4.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ALMANCA KELİME LİSTESİ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kelime Defterim",
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 38.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
            Text(
                text = "Okuduğunuz kitapların kelime hazinesi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Stats badge styled using Bold Typography theme conventions
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📚",
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "TOPLAM SÖZCÜK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "$totalWords Kelime",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Sözcük veya anlamını aratın...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Ara",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Temizle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field")
        )
    }
}

@Composable
fun WordCardItem(
    word: Word,
    onSpeak: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("word_card_${word.german}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Word content (Clean typographic layout)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = word.german,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = word.turkish,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Interactive Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Speak Button (TTS)
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.testTag("play_button_${word.german}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Almanca Telaffuz",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_button_${word.german}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Kelimeyi Sil",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateSection(
    isSearching: Boolean,
    onClearSearch: () -> Unit,
    onAddFirstWord: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSearching) "🔍" else "📖",
                fontSize = 56.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isSearching) "Kelime Bulunamadı" else "Kelime Defteriniz Boş",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearching) {
                "Arama kriterlerinize uyan bir Almanca veya Türkçe sözcük bulunamadı. Lütfen başka bir kelime deneyin."
            } else {
                "Defterinizde henüz hiç kelime bulunmuyor. Okuduğunuz kitaplardaki kelimeleri buraya ekleyebilirsiniz."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isSearching) {
            Button(
                onClick = onClearSearch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Aramayı Temizle",
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onAddFirstWord,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "İlk Kelimemi Ekle",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddWordDialog(
    onDismiss: () -> Unit,
    onSave: (german: String, turkish: String) -> Unit
) {
    var germanText by remember { mutableStateOf("") }
    var turkishText by remember { mutableStateOf("") }

    var germanError by remember { mutableStateOf(false) }
    var turkishError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Yeni Kelime Ekle",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Almanca kelimeyi ve Türkçe karşılığını girerek defterinize kaydedin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // German word text field
                OutlinedTextField(
                    value = germanText,
                    onValueChange = {
                        germanText = it
                        if (it.isNotBlank()) germanError = false
                    },
                    label = { Text("Almanca Kelime") },
                    placeholder = { Text("Örn: Sehnsucht") },
                    isError = germanError,
                    singleLine = true,
                    supportingText = {
                        if (germanError) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Hata",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lütfen Almanca kelimeyi girin")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("german_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Turkish word text field
                OutlinedTextField(
                    value = turkishText,
                    onValueChange = {
                        turkishText = it
                        if (it.isNotBlank()) turkishError = false
                    },
                    label = { Text("Türkçe Karşılığı") },
                    placeholder = { Text("Örn: Özlem, hasret") },
                    isError = turkishError,
                    singleLine = true,
                    supportingText = {
                        if (turkishError) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Hata",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lütfen Türkçe anlamını girin")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("turkish_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isGermanEmpty = germanText.isBlank()
                    val isTurkishEmpty = turkishText.isBlank()

                    germanError = isGermanEmpty
                    turkishError = isTurkishEmpty

                    if (!isGermanEmpty && !isTurkishEmpty) {
                        onSave(germanText, turkishText)
                    }
                },
                modifier = Modifier.testTag("dialog_save_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("İptal", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
