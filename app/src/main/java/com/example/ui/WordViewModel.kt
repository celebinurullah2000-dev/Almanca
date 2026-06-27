package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Word
import com.example.data.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordViewModel(private val repository: WordRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val wordListState: StateFlow<List<Word>> = repository.allWordsAlphabetical
        .combine(_searchQuery) { words, query ->
            if (query.isBlank()) {
                words
            } else {
                words.filter {
                    it.german.contains(query, ignoreCase = true) ||
                    it.turkish.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Pre-populate with some beautiful starter words if empty
        viewModelScope.launch {
            val currentWords = repository.allWordsAlphabetical.first()
            if (currentWords.isEmpty()) {
                val starterWords = listOf(
                    Word(german = "Sehnsucht", turkish = "Hasret, özlem, can atma"),
                    Word(german = "Ausgezeichnet", turkish = "Mükemmel, harika"),
                    Word(german = "Wortschatz", turkish = "Kelime hazinesi, sözcük dağarcığı"),
                    Word(german = "Schmetterling", turkish = "Kelebek"),
                    Word(german = "Entscheidung", turkish = "Karar, seçim"),
                    Word(german = "Selbstverständlich", turkish = "Tabii ki, kendiliğinden anlaşılan"),
                    Word(german = "Zukunft", turkish = "Gelecek"),
                    Word(german = "Fernweh", turkish = "Uzak diyarları özleme duygusu"),
                    Word(german = "Gegenteil", turkish = "Zıtlık, karşıtlık"),
                    Word(german = "Vergessen", turkish = "Unutmak")
                )
                starterWords.forEach { repository.insert(it) }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun addWord(german: String, turkish: String) {
        viewModelScope.launch {
            if (german.isNotBlank() && turkish.isNotBlank()) {
                repository.insert(Word(german = german.trim(), turkish = turkish.trim()))
            }
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            repository.deleteById(word.id)
        }
    }
}

class WordViewModelFactory(private val repository: WordRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WordViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
