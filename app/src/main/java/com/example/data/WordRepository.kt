package com.example.data

import kotlinx.coroutines.flow.Flow

class WordRepository(private val wordDao: WordDao) {
    val allWordsAlphabetical: Flow<List<Word>> = wordDao.getAllWordsAlphabetical()

    suspend fun insert(word: Word) {
        wordDao.insertWord(word)
    }

    suspend fun deleteById(id: Int) {
        wordDao.deleteWordById(id)
    }
}
