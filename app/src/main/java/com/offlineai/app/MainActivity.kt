package com.offlineai.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ChatScreen(this) } }
    }
}

data class Msg(val text: String, val user: Boolean)

@Composable
fun ChatScreen(ctx: Context) {
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var llm by remember { mutableStateOf<LlmInference?>(null) }
    var status by remember { mutableStateOf("Model load koro") }
    var busy by remember { mutableStateOf(false) }
    var qwen by remember { mutableStateOf(false) }
    val modelFile = File(ctx.filesDir, "model.task")

    fun load() {
        scope.launch(Dispatchers.IO) {
            try {
                status = "Loading..."
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(1024)
                    .build()
                llm = LlmInference.createFromOptions(ctx, opts)
                status = "Ready (offline)"
            } catch (e: Throwable) {
                status = "Error: ${e.message}"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                status = "Model copy hocche..."
                ctx.contentResolver.openInputStream(uri)?.use { i ->
                    modelFile.outputStream().use { o -> i.copyTo(o) }
                }
                load()
            } catch (e: Throwable) {
                status = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { if (modelFile.exists()) load() }

    fun send() {
        val q = input.trim()
        val m = llm ?: return
        if (q.isEmpty() || busy) return
        msgs.add(Msg(q, true))
        input = ""
        busy = true
        val prompt = if (qwen)
            "<|im_start|>user\n$q<|im_end|>\n<|im_start|>assistant\n"
        else
            "<start_of_turn>user\n$q<end_of_turn>\n<start_of_turn>model\n"
        scope.launch(Dispatchers.Default) {
            val r = try { m.generateResponse(prompt) } catch (e: Throwable) { "Error: ${e.message}" }
            msgs.add(Msg(r.trim(), false))
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Model") }
            OutlinedButton(onClick = { qwen = !qwen }) { Text(if (qwen) "Qwen" else "Gemma") }
        }
        Text(if (busy) "Vabche..." else status, Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(msgs) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text((if (m.user) "Tumi: " else "AI: ") + m.text, Modifier.padding(10.dp))
                }
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message...") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { send() }, enabled = llm != null && !busy) { Text("Send") }
        }
    }
}
