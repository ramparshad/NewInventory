package com.example.inventoryapp.data

import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await

// --- Interface ---
interface InventoryRepository {
    suspend fun getAllItems(limit: Int = 20, startAfter: String? = null): Result<List<InventoryItem>>
    suspend fun addOrUpdateItem(serial: String, item: InventoryItem): Result<Unit>
    suspend fun deleteItem(serial: String): Result<Unit>
    suspend fun getTransactionsForSerial(serial: String, limit: Int = 20, startAfter: String? = null): Result<List<Transaction>>
    suspend fun addTransaction(serial: String, transaction: Transaction): Result<Unit>
    suspend fun getAllTransactions(limit: Int = 20, startAfter: String? = null): Result<List<Transaction>>
    suspend fun addBatchTransactions(transactions: List<Transaction>): Result<Unit>
    suspend fun addBatchInventory(items: List<InventoryItem>): Result<Unit>
    suspend fun getItemBySerial(serial: String): InventoryItem?
    suspend fun getAllModels(): List<String>

    // --- Validation helpers for transaction screen ---
    suspend fun serialExists(serial: String): Boolean
    suspend fun wasSoldPreviously(serial: String): Boolean

    // --- New helper for transaction forms/screens ---
    suspend fun wasSerialSold(serial: String): Boolean
}

// --- Firebase implementation ---
class FirebaseInventoryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : InventoryRepository {

    override suspend fun getAllItems(limit: Int, startAfter: String?): Result<List<InventoryItem>> = try {
        var query = db.collection("inventory").orderBy("serial").limit(limit.toLong())
        if (startAfter != null && startAfter.isNotBlank()) {
            val snapshot = db.collection("inventory").document(startAfter).get().await()
            query = query.startAfter(snapshot)
        }
        val result = query.get().await()
        val items = result.documents.mapNotNull { it.toObject<InventoryItem>() }
        Result.Success(items)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun addOrUpdateItem(serial: String, item: InventoryItem): Result<Unit> = try {
        db.collection("inventory").document(serial).set(item).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun deleteItem(serial: String): Result<Unit> = try {
        db.collection("inventory").document(serial).delete().await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun getTransactionsForSerial(serial: String, limit: Int, startAfter: String?): Result<List<Transaction>> = try {
        var query = db.collection("transactions")
            .whereEqualTo("serial", serial)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (startAfter != null && startAfter.isNotBlank()) {
            val snapshot = db.collection("transactions").document(startAfter).get().await()
            query = query.startAfter(snapshot)
        }
        val result = query.get().await()
        val txs = result.documents.mapNotNull { it.toObject<Transaction>() }
        Result.Success(txs)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun addTransaction(serial: String, transaction: Transaction): Result<Unit> = try {
        db.collection("transactions").add(transaction).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun getAllTransactions(limit: Int, startAfter: String?): Result<List<Transaction>> = try {
        var query = db.collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (startAfter != null && startAfter.isNotBlank()) {
            val snapshot = db.collection("transactions").document(startAfter).get().await()
            query = query.startAfter(snapshot)
        }
        val result = query.get().await()
        val txs = result.documents.mapNotNull { it.toObject<Transaction>() }
        Result.Success(txs)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun addBatchTransactions(transactions: List<Transaction>): Result<Unit> = try {
        db.runBatch { batch ->
            transactions.forEach { tx ->
                val ref = db.collection("transactions").document()
                batch.set(ref, tx)
            }
        }.await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun addBatchInventory(items: List<InventoryItem>): Result<Unit> = try {
        db.runBatch { batch ->
            items.forEach { item ->
                val ref = db.collection("inventory").document(item.serial)
                batch.set(ref, item)
            }
        }.await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    override suspend fun getItemBySerial(serial: String): InventoryItem? {
        val doc = db.collection("inventory").document(serial).get().await()
        return doc.toObject<InventoryItem>()
    }

    override suspend fun getAllModels(): List<String> {
        val itemsResult = getAllItems(limit = 1000)
        return if (itemsResult is Result.Success) {
            itemsResult.data.mapNotNull { it.model }.distinct()
        } else {
            emptyList()
        }
    }

    // --- Validation helpers ---

    override suspend fun serialExists(serial: String): Boolean {
        val doc = db.collection("inventory").document(serial).get().await()
        return doc.exists()
    }

    override suspend fun wasSoldPreviously(serial: String): Boolean {
        val txSnapshot = db.collection("transactions")
            .whereEqualTo("serial", serial)
            .whereIn("type", listOf("Sale", "Sell"))
            .limit(1)
            .get()
            .await()
        return !txSnapshot.isEmpty
    }

    // --- New helper for transaction forms/screens ---
    override suspend fun wasSerialSold(serial: String): Boolean {
        val txSnapshot = db.collection("transactions")
            .whereEqualTo("serial", serial)
            .whereEqualTo("type", "Sale")
            .limit(1)
            .get()
            .await()
        return !txSnapshot.isEmpty
    }
}