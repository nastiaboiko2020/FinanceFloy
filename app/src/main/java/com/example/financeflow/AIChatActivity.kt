package com.example.financeflow

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AIChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIChatScreen(this) { finish() } // Обработка кнопки "Назад"
        }
    }
}

@Composable
fun AIChatScreen(context: Context, onBackPressed: () -> Unit) {
    var userInput by remember { mutableStateOf("") }
    val chatHistory = remember { mutableStateListOf("Привет! Как я могу помочь?") }
    val isLoading = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFFF5F5F5))
    ) {
        Text(
            text = "AI Ассистент",
            style = TextStyle(
                color = Color(0xFF1A3D62),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            chatHistory.forEach { message ->
                Text(
                    text = message,
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (isLoading.value) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("Введите запрос") },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        chatHistory.add("Вы: $userInput")
                        coroutineScope.launch {
                            handleUserInput(userInput, chatHistory, isLoading, context)
                        }
                        userInput = ""
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text("🗨️", fontSize = 24.sp)
            }
        }

        Button(
            onClick = onBackPressed,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3D62))
        ) {
            Text("Назад", color = Color.White)
        }
    }
}

suspend fun handleUserInput(
    input: String,
    chatHistory: MutableList<String>,
    isLoading: MutableState<Boolean>,
    context: Context
) {
    val apiKey = BuildConfig.GROK_API_KEY
    val client = OkHttpClient()

    val messages = listOf(
        mapOf("role" to "system", "content" to "Вы - умный ассистент."),
        mapOf("role" to "user", "content" to input)
    )

    val json = Gson().toJson(mapOf(
        "messages" to messages,
        "model" to "grok-2-latest", // Використовуйте потрібну модель
        "stream" to false,
        "temperature" to 0
    ))

    val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
    val request = Request.Builder()
        .url("https://api.x.ai/v1/chat/completions") // Замінюємо URL на потрібний
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build()

    isLoading.value = true

    try {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        Log.d("AIChatActivity", "Ответ от API: $responseBody")

        if (response.isSuccessful && responseBody != null) {
            val parsedResponse = parseGrokResponse(responseBody)
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: $parsedResponse")
            }
        } else {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Ошибка при подключении.")
                Toast.makeText(context, "Ошибка подключения к AI.", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            chatHistory.add("AI: Произошла ошибка.")
            Log.e("AIChatActivity", "Ошибка API-запроса: ", e)
        }
    } finally {
        isLoading.value = false
    }
}

fun parseGrokResponse(responseBody: String): String {
    val jsonResponse: Map<String, Any> = Gson().fromJson(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
    val choices = jsonResponse["choices"] as? List<Map<String, Any>> ?: return "Неизвестный ответ"
    val message = choices.firstOrNull()?.get("message") as? Map<*, *>
    return message?.get("content") as? String ?: "Неизвестный ответ"
}
