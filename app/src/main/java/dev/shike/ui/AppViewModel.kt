package dev.shike.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shike.data.*
import dev.shike.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Notice(val message: String, val undo: Item? = null, val undoCompletion: Boolean = false)

class AppViewModel(val repo: Repository) : ViewModel() {
    val data = repo.data
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 8)
    var undoLayouts = ArrayDeque<List<Workspace>>()
    var redoLayouts = ArrayDeque<List<Workspace>>()

    init {
        run { repo.initialize() }
    }

    fun run(message: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
                if (message != null) notices.emit(Notice(message))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notices.emit(Notice(e.message?.take(180) ?: "操作失败，请重试"))
            }
        }
    }

    fun save(item: Item, links: List<String>? = null) {
        run("已保存") { repo.save(item, links) }
    }

    fun trash(item: Item) {
        run {
            repo.trash(item)
            notices.emit(Notice("已移入回收站", item))
        }
    }

    fun layouts(workspaces: List<Workspace>) {
        val previous = data.value.workspaces
        undoLayouts.addLast(previous)
        while (undoLayouts.size > 50) undoLayouts.removeFirst()
        redoLayouts.clear()
        run { repo.saveWorkspaces(workspaces) }
    }

    fun undo() {
        if (undoLayouts.isNotEmpty()) {
            redoLayouts.addLast(data.value.workspaces)
            val old = undoLayouts.removeLast()
            run { repo.saveWorkspaces(old) }
        }
    }

    fun redo() {
        if (redoLayouts.isNotEmpty()) {
            undoLayouts.addLast(data.value.workspaces)
            val next = redoLayouts.removeLast()
            run { repo.saveWorkspaces(next) }
        }
    }
}
