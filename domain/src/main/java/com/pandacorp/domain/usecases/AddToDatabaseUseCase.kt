package com.pandacorp.domain.usecases

import com.pandacorp.domain.models.ListItem
import com.pandacorp.domain.repositories.DataRepositoryInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddToDatabaseUseCase(private val dataRepositoryInterface: DataRepositoryInterface) {
    operator fun invoke(listItem: ListItem) {
        CoroutineScope(Dispatchers.IO).launch {
            dataRepositoryInterface.add(listItem)
            
        }
    }
}