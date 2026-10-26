package com.favicode.mymoney

// ===================== IMPORTS =====================
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pantalla del chatbot financiero de MyMoney.
 * Usa la API REST de Google Gemini (sin SDK) mediante HttpURLConnection.
 */
class ChatbotActivity : AppCompatActivity() {

    // ===================== 1. VARIABLES DE LA INTERFAZ =====================

    // Adaptador que dibuja los mensajes en el RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    // Lista de mensajes que se muestran en pantalla (usuario y bot)
    private val messages = mutableListOf<ChatMessage>()

    // Lista visual donde aparecen los mensajes
    private lateinit var rvChatMessages: RecyclerView

    // ===================== 2. CONFIGURACIÓN DE LA API =====================

    // Clave de la API de Google AI Studio (identifica y autoriza nuestra app)
    private val apiKey = "AQ.Ab8RN6Lp_94txXCOu5mBdjeuv4NRZveHQEH6-Tm3GQivxMlG1A"

    // Dirección base de la API de Gemini
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    // Modelo de IA elegido (se guarda para no buscarlo en cada mensaje)
    private var resolvedModel: String? = null

    // ===================== 3. MEMORIA DE LA CONVERSACIÓN =====================

    // Historial completo del chat. Se envía a Gemini en cada petición
    // para que el bot "recuerde" lo que se habló. Roles: "user" y "model".
    private val history = JSONArray()

    // Bandera para evitar enviar mensajes mientras la IA aún responde
    private var isLoading = false

    // Aviso fijo que se muestra mientras la IA piensa
    private val loadingText =
        "⏳ Pensando... puede tardar unos segundos. Sé paciente, este modelo de IA está en prueba."

    // ===================== 4. PERSONALIDAD DEL BOT =====================

    // Instrucciones de sistema: definen cómo debe comportarse la IA
    private val systemPrompt = """
        Eres MyMoney AI, un asistente de finanzas personales amigable dentro de la app MyMoney.
        Responde siempre en español, de forma clara, cercana y concisa.
        Da consejos prácticos sobre ahorro, presupuesto, deudas e inversión básica.
        Si te preguntan algo que no tiene relación con finanzas, responde brevemente y redirige con amabilidad.
    """.trimIndent()

    // ===================== 5. INICIO DE LA PANTALLA =====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        // --- Barra superior con botón de regresar ---
        val toolbar = findViewById<Toolbar>(R.id.toolbarChat)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "MyMoney Asistente IA"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // --- Referencias a los elementos del layout ---
        rvChatMessages = findViewById(R.id.rvChatMessages)
        val etMessageInput = findViewById<EditText>(R.id.etMessageInput)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        // Botones de preguntas rápidas
        val btnSuggestSaving = findViewById<MaterialButton>(R.id.btnSuggestSaving)
        val btnSuggestRule = findViewById<MaterialButton>(R.id.btnSuggestRule)
        val btnSuggestDebts = findViewById<MaterialButton>(R.id.btnSuggestDebts)

        // --- Configuración de la lista de mensajes ---
        chatAdapter = ChatAdapter(messages)
        rvChatMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChatMessages.adapter = chatAdapter

        // --- Mensaje de bienvenida ---
        addBotMessage(
            "¡Hola! Soy tu asistente financiero en MyMoney. ¿En qué te puedo ayudar con tus finanzas hoy?\n\n" +
                    "ℹ️ Soy un modelo de IA en prueba, así que algunas respuestas pueden tardar unos segundos."
        )

        // --- Botón enviar ---
        btnSend.setOnClickListener {
            val userText = etMessageInput.text.toString().trim()
            if (userText.isNotEmpty()) {
                if (isLoading) {
                    // Si la IA aún está respondiendo, se avisa al usuario
                    Toast.makeText(this, "Espera a que la IA termine de responder", Toast.LENGTH_SHORT).show()
                } else {
                    handleUserQuery(userText)
                    etMessageInput.text.clear()
                }
            }
        }

        // --- Botones de preguntas rápidas (envían una pregunta predefinida) ---
        btnSuggestSaving.setOnClickListener { handleUserQuery("¿Cómo puedo ahorrar más dinero?") }
        btnSuggestRule.setOnClickListener { handleUserQuery("¿Qué es la regla 50/30/20?") }
        btnSuggestDebts.setOnClickListener { handleUserQuery("¿Cómo puedo controlar mis deudas?") }
    }

    // ===================== 6. MANEJO DE LA PREGUNTA DEL USUARIO =====================

    private fun handleUserQuery(userQuery: String) {
        // Evita enviar otra pregunta mientras se espera respuesta
        if (isLoading) {
            Toast.makeText(this, "Espera a que la IA termine de responder", Toast.LENGTH_SHORT).show()
            return
        }

        // Muestra la pregunta del usuario en el chat
        addUserMessage(userQuery)

        // Verifica que haya internet antes de llamar a la API
        if (!isNetworkAvailable()) {
            addBotMessage("⚠️ No tienes conexión a internet. Por favor activa el Wi-Fi o los datos.")
        } else {
            fetchGeminiApiResponse(userQuery)
        }
    }

    // ===================== 7. FUNCIONES PARA MOSTRAR MENSAJES =====================

    // Agrega un mensaje del usuario (burbuja de la derecha)
    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(text, isUser = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChatMessages.smoothScrollToPosition(messages.size - 1)
    }

    // Agrega un mensaje del bot (burbuja de la izquierda)
    private fun addBotMessage(text: String) {
        messages.add(ChatMessage(text, isUser = false))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChatMessages.smoothScrollToPosition(messages.size - 1)
    }

    // Muestra el aviso "Pensando..." y devuelve el mensaje para poder quitarlo después
    private fun showLoadingMessage(): ChatMessage {
        val msg = ChatMessage(loadingText, isUser = false)
        messages.add(msg)
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChatMessages.smoothScrollToPosition(messages.size - 1)
        return msg
    }

    // Quita el aviso "Pensando..." cuando ya llegó la respuesta o un error
    private fun removeLoadingMessage(msg: ChatMessage) {
        val index = messages.indexOf(msg)
        if (index >= 0) {
            messages.removeAt(index)
            chatAdapter.notifyItemRemoved(index)
        }
    }

    // ===================== 8. LLAMADA PRINCIPAL A GEMINI =====================

    private fun fetchGeminiApiResponse(prompt: String) {
        // Guarda la pregunta en el historial (rol "user")
        history.put(turn("user", prompt))

        // Bloquea nuevos envíos y muestra el aviso de espera
        isLoading = true
        val loadingMsg = showLoadingMessage()

        // Corrutina: permite hacer la llamada de red sin congelar la app
        lifecycleScope.launch {
            try {
                // La llamada de red se ejecuta en un hilo secundario (IO)
                val reply = withContext(Dispatchers.IO) { generateWithRetry() }

                // Guarda la respuesta en el historial (rol "model") para dar memoria al bot
                history.put(turn("model", reply))
                removeLoadingMessage(loadingMsg)
                addBotMessage(reply)

            } catch (e: ApiException) {
                // Error devuelto por Google: se quita la pregunta fallida del historial
                history.remove(history.length() - 1)
                Log.e("Gemini", "HTTP ${e.code}: ${e.message}")
                val detalle = (e.message ?: "").take(250)
                removeLoadingMessage(loadingMsg)

                // Mensaje amigable según el código de error
                addBotMessage(
                    when (e.code) {
                        400, 401, 403 -> "⚠️ Problema con la API key o permisos (código ${e.code}).\n\n$detalle"
                        404 -> "⚠️ No se encontró un modelo disponible (404).\n\n$detalle"
                        500, 503 -> "⏳ La IA está saturada en este momento. Intenta de nuevo en unos segundos."
                        429 -> "⏳ Demasiadas solicitudes o cuota agotada. Espera un momento e intenta de nuevo."
                        else -> "⚠️ La IA respondió con error ${e.code}.\n\n$detalle"
                    }
                )

            } catch (e: Exception) {
                // Error de conexión u otro fallo inesperado
                history.remove(history.length() - 1)
                Log.e("Gemini", "Excepción", e)
                removeLoadingMessage(loadingMsg)
                addBotMessage("⚠️ No pude conectar con la IA: ${e.localizedMessage}")

            } finally {
                // Siempre se ejecuta: quita el aviso y desbloquea el envío
                removeLoadingMessage(loadingMsg)
                isLoading = false
            }
        }
    }

    // Excepción propia para guardar el código HTTP del error (404, 503, etc.)
    private class ApiException(val code: Int, message: String) : Exception(message)

    // Construye un turno de conversación en el formato JSON que pide Gemini
    private fun turn(role: String, text: String) = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    // ===================== 9. LÓGICA DE REINTENTOS Y MODELOS DE RESPALDO =====================
    // (Todo lo de abajo corre en un hilo secundario: Dispatchers.IO)

    private fun generateWithRetry(): String {
        // Modelo 1: el que la app descubre automáticamente (Gemini Flash)
        val primero = resolvedModel ?: discoverModel().also { resolvedModel = it }

        // Lista de modelos en orden de prioridad:
        // principal -> flash-latest -> flash-lite-latest -> 2.5-flash-lite
        val candidatos = mutableListOf(primero)
        for (alt in listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash-lite")) {
            if (alt !in candidatos) candidatos.add(alt)
        }

        var ultimoError: ApiException? = null

        modelos@ for (modelo in candidatos) {
            // 3 intentos por cada modelo, esperando más tiempo en cada intento
            for (intento in 1..3) {
                try {
                    val respuesta = callGemini(modelo)
                    resolvedModel = modelo // recordamos el que funcionó
                    return respuesta
                } catch (e: ApiException) {
                    ultimoError = e
                    Log.w("Gemini", "Modelo $modelo, intento $intento: HTTP ${e.code}")
                    when (e.code) {
                        // Servidor saturado: esperar y reintentar (1.5s, 3s, 4.5s)
                        503, 500, 429 -> Thread.sleep(1500L * intento)
                        // El modelo no existe: saltar al siguiente de la lista
                        404 -> continue@modelos
                        // Problema de clave o permisos: no tiene sentido reintentar
                        else -> throw e
                    }
                }
            }
        }
        // Si ningún modelo funcionó, se lanza el último error
        throw ultimoError ?: ApiException(503, "No se pudo obtener respuesta.")
    }

    // ===================== 10. DESCUBRIR EL MODELO DISPONIBLE =====================
    // Le pregunta a Google qué modelos tiene disponibles esta API key
    // y elige automáticamente el mejor modelo "flash".

    private fun discoverModel(): String {
        val conn = URL("$baseUrl/models?pageSize=200").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("x-goog-api-key", apiKey)

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw ApiException(code, err)
            }

            // Lee la lista de modelos que devuelve Google
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = json.optJSONArray("models")
                ?: throw ApiException(404, "Google no devolvió ningún modelo.")

            // Filtra solo los modelos Gemini que sirven para generar texto
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                var ok = false
                for (j in 0 until methods.length()) {
                    if (methods.getString(j) == "generateContent") ok = true
                }
                val name = m.getString("name").removePrefix("models/")
                if (ok && name.startsWith("gemini")) names.add(name)
            }
            Log.d("Gemini", "Modelos disponibles: $names")

            // Descarta modelos especiales (voz, imágenes, embeddings, etc.)
            val excluir = listOf("tts", "image", "embedding", "live", "audio", "robotics", "computer", "vision", "native")
            val buenos = names.filter { n -> excluir.none { n.contains(it) } }

            // Prioridad de elección:
            // 1) gemini-flash-latest  2) el flash estable más nuevo  3) cualquier flash  4) cualquiera
            val elegido = buenos.firstOrNull { it == "gemini-flash-latest" }
                ?: buenos.filter {
                    it.contains("flash") && !it.contains("lite") && !it.contains("preview") && !it.contains("exp")
                }.maxOrNull()
                ?: buenos.firstOrNull { it.contains("flash") }
                ?: buenos.firstOrNull()
                ?: throw ApiException(404, "Tu clave no tiene modelos Gemini con generateContent. Modelos: $names")

            // Este log muestra qué modelo se usó realmente (ver en Logcat, filtro "Gemini")
            Log.d("Gemini", "Modelo elegido: $elegido")
            return elegido
        } finally {
            conn.disconnect()
        }
    }

    // ===================== 11. PETICIÓN A GEMINI (generateContent) =====================
    // Envía el historial + la personalidad y recibe la respuesta de la IA.

    private fun callGemini(model: String): String {
        // Solo se envían los últimos 20 turnos para no gastar demasiados tokens
        val recent = JSONArray()
        for (i in maxOf(0, history.length() - 20) until history.length()) {
            recent.put(history.get(i))
        }

        // Construcción del JSON que se envía a Gemini
        val body = JSONObject()
            // systemInstruction: la personalidad del bot (separada de los mensajes)
            .put(
                "systemInstruction", JSONObject()
                    .put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            // contents: el historial de la conversación (da memoria al bot)
            .put("contents", recent)
            // temperature: 0 = respuestas exactas, 1 = más creativas
            .put("generationConfig", JSONObject().put("temperature", 0.7))

        val conn = URL("$baseUrl/models/$model:generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000   // tiempo máximo para conectar
            conn.readTimeout = 60_000      // tiempo máximo esperando la respuesta
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("x-goog-api-key", apiKey) // la clave va en el header

            // Envía el JSON a Google
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            // Si la respuesta no es 200 (OK), se lanza el error con su código
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw ApiException(code, err)
            }

            // Lee y procesa la respuesta JSON
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return "No pude generar una respuesta para eso. ¿Puedes reformular la pregunta?"
            }
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                return "No pude generar una respuesta para eso. ¿Puedes reformular la pregunta?"
            }

            // Une todos los fragmentos de texto de la respuesta
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            return sb.toString().trim()
        } finally {
            conn.disconnect() // cierra la conexión siempre
        }
    }

    // ===================== 12. VERIFICAR INTERNET =====================

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}