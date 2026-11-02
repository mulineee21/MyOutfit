package com.example.myoutfit.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.backup.BackupManager
import com.example.myoutfit.data.backup.BackupStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val currentStats: BackupStats = BackupStats(),
    val isProcessing: Boolean = false,
    val progressMessage: String? = null,
    val resultStats: BackupStats? = null,
    val lastSuccessType: String? = null, // "export" or "import"
    val userMessage: String? = null,
    val replaceAllOnImport: Boolean = true
)

class BackupViewModel(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    private val _progressMessage = MutableStateFlow<String?>(null)
    private val _resultStats = MutableStateFlow<BackupStats?>(null)
    private val _lastSuccessType = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _replaceAllOnImport = MutableStateFlow(true)

    val uiState: StateFlow<BackupUiState> = combine(
        backupManager.getStatsFlow(),
        _isProcessing,
        _progressMessage,
        _resultStats,
        _lastSuccessType,
        _userMessage,
        _replaceAllOnImport
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        BackupUiState(
            currentStats = args[0] as BackupStats,
            isProcessing = args[1] as Boolean,
            progressMessage = args[2] as String?,
            resultStats = args[3] as BackupStats?,
            lastSuccessType = args[4] as String?,
            userMessage = args[5] as String?,
            replaceAllOnImport = args[6] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BackupUiState()
    )

    fun setReplaceAllOnImport(replace: Boolean) {
        _replaceAllOnImport.value = replace
    }

    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _progressMessage.value = "Exportando prendas, fotos y lista de deseos..."
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalArgumentException("No se pudo escribir en la ubicación seleccionada")

                val stats = outputStream.use { stream ->
                    backupManager.exportBackup(stream)
                }

                _resultStats.value = stats
                _lastSuccessType.value = "export"
                _userMessage.value = "Copia de seguridad guardada exitosamente"
            } catch (e: Exception) {
                _userMessage.value = "Error al exportar copia de seguridad: ${e.message}"
            } finally {
                _isProcessing.value = false
                _progressMessage.value = null
            }
        }
    }

    fun shareBackup(context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _progressMessage.value = "Empaquetando datos y fotos para compartir..."
            try {
                val (zipFile, stats) = backupManager.createShareableBackupZip()
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Copia de Seguridad MyOutfit")
                    putExtra(Intent.EXTRA_TEXT, "Aquí tienes tu copia de seguridad completa de MyOutfit (.zip) con tus prendas, fotos y lista de deseos.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Compartir copia de seguridad")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                _resultStats.value = stats
                _userMessage.value = "Copia generada lista para compartir"
            } catch (e: Exception) {
                _userMessage.value = "Error al generar copia para compartir: ${e.message}"
            } finally {
                _isProcessing.value = false
                _progressMessage.value = null
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _progressMessage.value = "Desempaquetando fotos y restaurando base de datos..."
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("No se pudo leer el archivo seleccionado")

                val stats = inputStream.use { stream ->
                    backupManager.importBackup(stream, _replaceAllOnImport.value)
                }

                _resultStats.value = stats
                _lastSuccessType.value = "import"
                _userMessage.value = "¡Copia de seguridad restaurada con éxito!"
            } catch (e: Exception) {
                _userMessage.value = "Error al restaurar copia: ${e.message}"
            } finally {
                _isProcessing.value = false
                _progressMessage.value = null
            }
        }
    }

    fun dismissResultDialog() {
        _lastSuccessType.value = null
        _resultStats.value = null
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}

class BackupViewModelFactory(
    private val backupManager: BackupManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            return BackupViewModel(backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
