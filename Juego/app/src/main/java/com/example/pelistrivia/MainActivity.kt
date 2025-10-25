package com.example.pelistrivia

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pelistrivia.ui.theme.PelisTriviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView // <-- La clase que está dando error
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView // <-- El envoltorio Composable
import java.util.*

// --- MODELOS DE DATOS ---
data class Answer(
    val text: String,
    @DrawableRes val imageResId: Int? = null,
    val isCorrect: Boolean
)

data class Question(
    val text: String,
    val answers: List<Answer>,
    val explanation: String? = null,
    val audioResId: Int? = null, //preguntas de audio
    val type: String = "normal" // "normal" o "audio"
)

// --- NUEVO ESTADO DE NAVEGACIÓN ---
private enum class Screen {
    VideoIntro,
    Splash,
    MainMenu,
    Options,
    EnterName,
    Trivia,
    Ranking,
    Credits
}

// --- COMPOSABLE DE RESPUESTA ---
// --- COMPOSABLE DE RESPUESTA ---
@Composable
fun AnswerItem(
    answer: Answer,
    isSelected: Boolean,
    isCorrectAnswer: Boolean?,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && isCorrectAnswer == true -> Color(0xFF4CAF50) // Verde
        isSelected && isCorrectAnswer == false -> Color(0xFFF44336) // Rojo
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = isCorrectAnswer == null) { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mostrar imagen solo si existe
            answer.imageResId?.let { resId ->
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(end = 16.dp)
                )
            }
            Text(
                text = answer.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
// --- PANTALLA DE PREGUNTA DE AUDIO MODIFICADA ---
@Composable
fun AudioQuestionScreen(
    question: Question,
    questionIndex: Int,
    totalQuestions: Int,
    onAnswerFeedback: (Boolean) -> Unit,
    onNextQuestion: () -> Unit,
    playSound: (Boolean) -> Unit,
    sfxVolume: Float,
    audioQuestionsVolume: Float // <- NUEVO PARÁMETRO
) {
    val context = LocalContext.current
    var selectedAnswer by remember { mutableStateOf<Answer?>(null) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var remaining by remember { mutableStateOf(15) }
    var timeUp by remember { mutableStateOf(false) }
    var showContinueButton by remember { mutableStateOf(false) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasAudio by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Inicializar el reproductor de audio con el volumen específico
    LaunchedEffect(questionIndex) {
        question.audioResId?.let { audioResId ->
            if (audioResId != 0) {
                try {
                    audioPlayer = MediaPlayer.create(context, audioResId)?.apply {
                        setVolume(audioQuestionsVolume, audioQuestionsVolume) // <- USAR VOLUMEN ESPECÍFICO
                        hasAudio = true
                    }
                } catch (e: Exception) {
                    hasAudio = false
                    println("Error creando MediaPlayer: ${e.message}")
                }
            } else {
                hasAudio = false
            }
        } ?: run {
            hasAudio = false
        }

        // DEBUG
        println("Audio question - hasAudio: $hasAudio, volume: $audioQuestionsVolume")
    }

    // Temporizador y lógica de audio
    LaunchedEffect(key1 = questionIndex) {
        remaining = 15
        selectedAnswer = null
        isCorrect = null
        timeUp = false
        showContinueButton = false
        isPlaying = false

        // Reproducir audio automáticamente al inicio si existe
        if (hasAudio) {
            audioPlayer?.start()
            isPlaying = true
            println("Audio started automatically")
        } else {
            println("No audio to play")
        }

        // Temporizador principal
        while (remaining > 0 && selectedAnswer == null && !timeUp) {
            delay(1000)
            remaining -= 1

            // Detener audio después de 10 segundos si no se ha respondido
            if (remaining == 5 && isPlaying && hasAudio) {
                audioPlayer?.pause()
                isPlaying = false
                println("Audio paused automatically after 10 seconds")
            }
        }

        if (!timeUp && selectedAnswer == null) {
            timeUp = true
            isCorrect = false
            onAnswerFeedback(false)
            playSound(false)
            delay(1500)
            onNextQuestion()
        }
    }

    // Limpiar recursos
    DisposableEffect(questionIndex) {
        onDispose {
            audioPlayer?.release()
            println("Audio resources released")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con información de la pregunta
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pregunta ${questionIndex + 1} / $totalQuestions",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (!timeUp) "$remaining s" else "⏰",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = when {
                        remaining > 10 -> Color.White
                        remaining > 5 -> Color.Yellow
                        else -> Color(0xFFFF6B6B)
                    },
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Indicador de audio - solo mostrar si hay audio disponible
        if (hasAudio) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_media_play),
                        contentDescription = "Audio",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (remaining > 10) "Escuchando... (${remaining-10}s restantes)"
                        else if (remaining > 5) "Tiempo para escuchar: ${remaining-5}s"
                        else "¡Responde rápido!",
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        modifier = Modifier.weight(1f)
                    )

                    // Botón para reproducir/pausar manualmente
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                audioPlayer?.pause()
                                isPlaying = false
                            } else {
                                audioPlayer?.start()
                                isPlaying = true
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause
                                else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            // Mensaje cuando no hay audio disponible
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000).copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_dialog_info),
                        contentDescription = "Info",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Pregunta de audio - Selecciona la respuesta correcta",
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        // Grid de respuestas (2 columnas para las 10 opciones)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(question.answers) { answer ->
                AudioAnswerItem(
                    answer = answer,
                    isSelected = selectedAnswer == answer,
                    isCorrectAnswer = if (selectedAnswer == null) null else answer.isCorrect,
                    onClick = {
                        if (selectedAnswer == null && !timeUp) {
                            selectedAnswer = answer
                            isCorrect = answer.isCorrect
                            onAnswerFeedback(answer.isCorrect)
                            playSound(answer.isCorrect)

                            // Detener audio al responder si existe
                            if (hasAudio) {
                                audioPlayer?.pause()
                                isPlaying = false
                            }

                            scope.launch {
                                if (answer.isCorrect) {
                                    showContinueButton = true
                                } else {
                                    delay(1500)
                                    onNextQuestion()
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mensajes de feedback
        when {
            isCorrect == true -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "¡Correcto!",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        if (showContinueButton) {
                            // Botón de continuar más compacto y arriba
                            Button(
                                onClick = { onNextQuestion() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                ),
                                modifier = Modifier
                                    .height(44.dp) // <- Un poco más compacto
                                    .padding(top = 6.dp) // <- Alineado con el texto
                            ) {
                                Text(
                                    "Continuar",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            isCorrect == false && !timeUp -> Text(
                text = "Incorrecto",
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            timeUp -> Text(
                text = "Se ha acabado el tiempo",
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// Item de respuesta para preguntas de audio
@Composable
fun AudioAnswerItem(
    answer: Answer,
    isSelected: Boolean,
    isCorrectAnswer: Boolean?,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && isCorrectAnswer == true -> Color(0xFF4CAF50) // Verde
        isSelected && isCorrectAnswer == false -> Color(0xFFF44336) // Rojo
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable(enabled = isCorrectAnswer == null) { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mostrar imagen solo si existe, sino mostrar un indicador de texto
            if (answer.imageResId != null) {
                Image(
                    painter = painterResource(id = answer.imageResId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 8.dp)
                )
            } else {
                // Placeholder cuando no hay imagen
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 8.dp)
                        .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎬",
                        fontSize = 16.sp
                    )
                }
            }
            Text(
                text = answer.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


fun loadAudioQuestions(context: Context): List<Question> {
    return listOf(
        Question(
            text = "¿De qué película es esta banda sonora?",
            answers = listOf(
                Answer("Mamá", R.drawable.mama, true),
                Answer("Harry Potter y las reliquias de la Muerte: parte 2", R.drawable.harry_potter_parte2, false),
                Answer("El Orfanato", R.drawable.orfanato, false),
                Answer("Expediente Warren: The Conjuring", R.drawable.expediente_warren, false),
                Answer("La Cumbre Escarlata", R.drawable.cumbre_escarlata, false),
                Answer("Insidious", R.drawable.insidious, false),
                Answer("Déjame Salir", R.drawable.dejame_salir, false),
                Answer("Babadook", R.drawable.babadook, false)
            ),
            audioResId = context.resources.getIdentifier("audio1", "raw", context.packageName),
            type = "audio"
        ),
        Question(
            text = "Identifica la película por su música",
            answers = listOf(
                Answer("Star Wars: Una Nueva Esperanza", R.drawable.starwars_newhope, false),
                Answer("E.T. El Extraterrestre", R.drawable.et, false),
                Answer("Hook", R.drawable.hook, false),
                Answer("Jurassic Park", R.drawable.jurassic_park, false),
                Answer("Harry Potter y el prisionero de Azkaban", R.drawable.harry_potter_prisoner, true),
                Answer("Las Crónicas de Narnia: El León, la Bruja y el Armario", R.drawable.narnia, false),
                Answer("Willow", R.drawable.willow, false),
                Answer("Gremlins", R.drawable.gremlins, false)
            ),
            audioResId = context.resources.getIdentifier("audio2", "raw", context.packageName),
            type = "audio"
        ),
        Question(
            text = "¿A qué película pertenece este tema musical?",
            answers = listOf(
                Answer("Pesadilla Antes de Navidad", R.drawable.pesadilla, false),
                Answer("Beetlejuice", R.drawable.beetlejuice, false),
                Answer("Eduardo Manostijeras", R.drawable.eduardo, true),
                Answer("Sleepy Hollow", R.drawable.sleepy, false),
                Answer("Alicia en el País de las Maravillas (2010)", R.drawable.alicia, false),
                Answer("Charlie y la Fábrica de Chocolate", R.drawable.charlie, false),
                Answer("El Gran Pez", R.drawable.pez, false),
                Answer("Coraline", R.drawable.coraline, false)
            ),
            audioResId = context.resources.getIdentifier("eduardo", "raw", context.packageName),
            type = "audio"
        ),
        Question(
            text = "Reconoce la película por su soundtrack",
            answers = listOf(
                Answer("Cadena Perpetua", R.drawable.cadena, false),
                Answer("Milagros Inesperados", R.drawable.milagros, false),
                Answer("Big Fish", R.drawable.pez, false),
                Answer("El Curioso Caso de Benjamin Button", R.drawable.benjamin, false),
                Answer("La Milla Verde", R.drawable.milla_verde, false),
                Answer("El Show de Truman", R.drawable.truman, false),
                Answer("Forrest Gump", R.drawable.forrest_gump, true),
                Answer("Patch Adams", R.drawable.patch_adams, false)
            ),
            audioResId = context.resources.getIdentifier("forrest", "raw", context.packageName),
            type = "audio"
        ),
        Question(
            text = "¿De qué peli es esta famosa melodía?",
            answers = listOf(
                Answer("Érase una vez en América", R.drawable.erase_una_vez_en_america, false),
                Answer("El bueno, el feo y el malo", R.drawable.bueno_feo_malo, false),
                Answer("Muerte en Venecia", R.drawable.muerte_en_venecia, false),
                Answer("Cinema Paradiso", R.drawable.cinema_paradiso, false),
                Answer("Novecento", R.drawable.novecento, false),
                Answer("Los Intocables de Eliot Ness", R.drawable.intocables, false),
                Answer("La Misión", R.drawable.mision, false),
                Answer("El padrino", R.drawable.padrino, true)
            ),
            audioResId = context.resources.getIdentifier("padrino", "raw", context.packageName),
            type = "audio"
        ),
        Question(
            text = "Y esta canción es de...",
            answers = listOf(
                Answer("El Rey Arturo: La Leyenda de la Espada", R.drawable.arturo, false),
                Answer("El Último Samurai", R.drawable.ultimo_samurai, false),
                Answer("Troya", R.drawable.troya, false),
                Answer("Gladiador", R.drawable.gladiador, true),
                Answer("Piratas del Caribe: La Maldición de la Perla Negra", R.drawable.caribe, false),
                Answer("Braveheart", R.drawable.braveheart, false),
                Answer("El Reino de los Cielos", R.drawable.reino_cielos, false),
                Answer("300", R.drawable.los_300, false),
            ),
            audioResId = context.resources.getIdentifier("gladiador", "raw", context.packageName),
            type = "audio"
        ),
        Question(
            text = "Esta banda sonora pertenece a...",
            answers = listOf(
                Answer("La lista de Schindler", R.drawable.schindler, true),
                Answer("El Pianista", R.drawable.pianista, false),
                Answer("El Niño con el Pijama de Rayas", R.drawable.pijama_rayas, false),
                Answer("La Vida es Bella", R.drawable.vida_bella, false),
                Answer("Munich", R.drawable.munich, false),
                Answer("Salvar al Soldado Ryan", R.drawable.ryan, false),
                Answer("El Último Emperador", R.drawable.empereador, false),
                Answer("Bailando con Lobos", R.drawable.bailando_con_lobos, false),
            ),
            audioResId = context.resources.getIdentifier("schindler", "raw", context.packageName),
            type = "audio"
        )
    )
}

// --- PANTALLA DE PREGUNTA MODIFICADA ---
@Composable
fun QuestionScreen(
    question: Question,
    questionIndex: Int,
    totalQuestions: Int,
    timeLimitSeconds: Int = 10,
    onAnswerFeedback: (Boolean) -> Unit,
    onNextQuestion: () -> Unit,
    playSound: (Boolean) -> Unit
) {
    var selectedAnswer by remember { mutableStateOf<Answer?>(null) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var remaining by remember { mutableStateOf(timeLimitSeconds) }
    var timeUp by remember { mutableStateOf(false) }
    var showContinueButton by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(questionIndex) {
        remaining = timeLimitSeconds
        selectedAnswer = null
        isCorrect = null
        timeUp = false
        showContinueButton = false
    }

    LaunchedEffect(key1 = questionIndex) {
        remaining = 10
        timeUp = false

        while (remaining > 0 && selectedAnswer == null && !timeUp) {
            delay(1000)
            remaining -= 1
        }

        if (!timeUp && selectedAnswer == null) {
            timeUp = true
            isCorrect = false
            onAnswerFeedback(false)
            playSound(false)
            delay(1500)
            onNextQuestion()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Texto en blanco para que contraste con el fondo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pregunta ${questionIndex + 1} / $totalQuestions",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (!timeUp) "$remaining s" else "⏰",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (remaining > 5) Color.White else Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Renderiza las respuestas (se mantienen igual)
        question.answers.forEach { answer ->
            val correctForThisAnswer = if (selectedAnswer == null) null else answer.isCorrect
            AnswerItem(
                answer = answer,
                isSelected = selectedAnswer == answer,
                isCorrectAnswer = correctForThisAnswer
            ) {
                if (selectedAnswer == null && !timeUp) {
                    selectedAnswer = answer
                    isCorrect = answer.isCorrect
                    onAnswerFeedback(answer.isCorrect)
                    playSound(answer.isCorrect)

                    scope.launch {
                        if (answer.isCorrect) {
                            showContinueButton = true
                        } else {
                            delay(1500)
                            onNextQuestion()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mensajes de feedback en blanco
        when {
            isCorrect == true -> {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "¡Correcto!",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp

                        )

                        if (showContinueButton) {
                            // Botón de continuar más compacto y arriba
                            Button(
                                onClick = { onNextQuestion() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                ),
                                modifier = Modifier
                                    .height(44.dp) // <- Un poco más compacto
                                    .padding(top = 4.dp) // <- Alineado con el texto
                            ) {
                                Text(
                                    "Continuar",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!question.explanation.isNullOrBlank()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                        ) {
                            Text(
                                text = question.explanation ?: "",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            isCorrect == false && !timeUp -> Text(
                text = "Incorrecto",
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            timeUp -> Text(
                text = "Se ha acabado el tiempo",
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// --- NUEVA PANTALLA SPLASH ---
@Composable
fun SplashScreen(onContinue: () -> Unit) {
    var alpha by remember { mutableStateOf(1f) }

    // Animación de parpadeo
    LaunchedEffect(Unit) {
        while (true) {
            alpha = 0.3f
            delay(800)
            alpha = 1f
            delay(800)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onContinue() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Aquí va la imagen de portada del juego
            // Image(painter = painterResource(R.drawable.game_cover), contentDescription = "Portada")

            Text(
                text = "MOVIZ",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Text(
                text = "Pulsa la pantalla para continuar",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White
                ),
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}

// --- ACTIVITY PRINCIPAL ---
class TriviaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PelisTriviaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

// --- COMPOSABLE RAÍZ: maneja navegación ---
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(Screen.VideoIntro) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var playerName by rememberSaveable { mutableStateOf("") }
    var gameId by rememberSaveable { mutableStateOf(0) }

    var musicVolume by rememberSaveable { mutableStateOf(0.8f) }
    var sfxVolume by rememberSaveable { mutableStateOf(0.8f) }
    var audioQuestionsVolume by rememberSaveable { mutableStateOf(0.8f) }
    var screenBrightness by rememberSaveable { mutableStateOf(0.5f) }

    val backgroundMusicPlayer = remember {
        val resId = context.resources.getIdentifier("background_music", "raw", context.packageName)
        if (resId != 0) MediaPlayer.create(context, resId) else null
    }

    // SOLO UN LaunchedEffect para cargar preguntas
    LaunchedEffect(Unit) {
        try {
            val jsonQuestions = loadQuestionsFromAssets(context)
            val audioQuestions = loadAudioQuestions(context)
            questions = jsonQuestions + audioQuestions // Las de audio van después

            // DEBUG: Verificar carga
            println("=== DEBUG: CARGANDO PREGUNTAS ===")
            println("Preguntas del JSON: ${jsonQuestions.size}")
            println("Preguntas de audio: ${audioQuestions.size}")
            println("Total preguntas: ${questions.size}")

            // Verificar algunas preguntas específicas
            questions.forEachIndexed { index, question ->
                if (index >= jsonQuestions.size) {
                    println("Pregunta ${index + 1} (AUDIO): ${question.text} - Tipo: ${question.type}")
                }
            }

        } catch (e: Exception) {
            println("ERROR cargando preguntas: ${e.message}")
            e.printStackTrace()
            questions = listOf(
                Question("Error cargando preguntas", answers = listOf(Answer("OK", null, true)))
            )
        }

        // Cargar configuraciones de volumen
        try { /* Carga musicVolume */ } catch (_: Exception) { musicVolume = 0.8f }
        try { /* Carga sfxVolume */ } catch (_: Exception) { sfxVolume = 0.8f }
        try { /* Carga screenBrightness */ } catch (_: Exception) { screenBrightness = 0.5f }
    }
    // Efecto para gestionar el ciclo de vida del reproductor de música
    DisposableEffect(backgroundMusicPlayer) {
        backgroundMusicPlayer?.apply {
            isLooping = true
            setVolume(musicVolume, musicVolume)
        }

        onDispose {
            backgroundMusicPlayer?.stop()
            backgroundMusicPlayer?.release()
        }
    }

    // Efecto para actualizar el volumen si el usuario lo cambia en Opciones
    LaunchedEffect(musicVolume) {
        backgroundMusicPlayer?.setVolume(musicVolume, musicVolume)
    }

    LaunchedEffect(screenBrightness) {
        val layoutParams = activity?.window?.attributes
        layoutParams?.screenBrightness = screenBrightness
        activity?.window?.attributes = layoutParams
    }

    val startMusic: () -> Unit = {
        backgroundMusicPlayer?.start()
    }

    when (screen) {
        Screen.VideoIntro -> VideoIntroScreen(
            onVideoFinished = {
                screen = Screen.Splash
                startMusic()
            }
        )
        Screen.Splash -> SplashScreen(
            onContinue = { screen = Screen.MainMenu }
        )
        Screen.MainMenu -> MainMenu(
            onPlay = { screen = Screen.EnterName },
            onOptions = { screen = Screen.Options },
            onExit = { activity?.finishAffinity() },
            onRanking = { screen = Screen.Ranking },
            onCredits = { screen = Screen.Credits }
        )
        // En el when(screen) del AppRoot, actualiza OptionsScreen:
        Screen.Options -> OptionsScreen(
            musicVolume = musicVolume,
            sfxVolume = sfxVolume,
            audioQuestionsVolume = audioQuestionsVolume, // <- NUEVO
            brightness = screenBrightness,
            onMusicVolumeChange = { newV ->
                musicVolume = newV
                scope.launch { /* Guarda musicVolume */ }
            },
            onSfxVolumeChange = { newV ->
                sfxVolume = newV
                scope.launch { /* Guarda sfxVolume */ }
            },
            onAudioQuestionsVolumeChange = { newV -> // <- NUEVO
                audioQuestionsVolume = newV
                scope.launch { /* Guarda audioQuestionsVolume */ }
            },
            onBrightnessChange = { newV ->
                screenBrightness = newV
                scope.launch { /* Guarda screenBrightness */ }
            },
            onBack = { screen = Screen.MainMenu }
        )
        Screen.EnterName -> NameEntryScreen(
            onNameSubmitted = { name ->
                playerName = name
                gameId++
                screen = Screen.Trivia
            },
            onBack = { screen = Screen.MainMenu }
        )
        Screen.Trivia -> {
            key(gameId) {
                TriviaGame(
                    questions = questions,
                    sfxVolume = sfxVolume,
                    audioQuestionsVolume = audioQuestionsVolume, // <- NUEVO
                    playerName = playerName,
                    onQuitToMenu = { screen = Screen.MainMenu },
                    onLeaderboard = { screen = Screen.Ranking },
                    backgroundMusicPlayer = backgroundMusicPlayer, // <- NUEVO
                    onPauseBackgroundMusic = { // <- NUEVO
                        backgroundMusicPlayer?.pause()
                    },
                    onResumeBackgroundMusic = { // <- NUEVO
                        if (musicVolume > 0) {
                            backgroundMusicPlayer?.start()
                        }
                    }
                )
            }
        }
        Screen.Ranking -> RankingScreen(onBack = { screen = Screen.MainMenu })
        Screen.Credits -> CreditsScreen(onBack = { screen = Screen.MainMenu })
    }
}

// --- NUEVA PANTALLA DE INTRODUCCIÓN CON VIDEO ---
@Composable
fun VideoIntroScreen(onVideoFinished: () -> Unit) {
    val context = LocalContext.current
    val videoId = remember {
        context.resources.getIdentifier("intro_video", "raw", context.packageName)
    }

    if (videoId == 0) {
        LaunchedEffect(Unit) {
            onVideoFinished()
        }
        return
    }

    // 1. Usa BoxWithConstraints para obtener las dimensiones exactas de la pantalla
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = this.constraints.maxWidth // Ancho disponible en píxeles
        val heightPx = this.constraints.maxHeight // Alto disponible en píxeles

        AndroidView(
            factory = {
                VideoView(it).apply {
                    val path = "android.resource://" + context.packageName + "/" + videoId
                    setVideoURI(Uri.parse(path))

                    setOnCompletionListener {
                        onVideoFinished()
                    }

                    // 2. Establece las dimensiones exactas del contenedor View (layoutParams)
                    layoutParams = ViewGroup.LayoutParams(
                        widthPx,  // Usamos el ancho obtenido
                        heightPx  //Usamos el alto obtenido
                    )

                    // 3. Agrega un listener para forzar el redimensionamiento del video
                    setOnPreparedListener { mp ->
                        //Forzamos el redimensionamiento del video para que ocupe
                        //las dimensiones de la pantalla (widthPx, heightPx).
                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        mp.start()
                    }
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// --- OPTIONS SCREEN MODIFICADA ---
@Composable
fun OptionsScreen(
    musicVolume: Float,
    sfxVolume: Float,
    audioQuestionsVolume: Float, // <- NUEVO PARÁMETRO
    brightness: Float,
    onMusicVolumeChange: (Float) -> Unit,
    onSfxVolumeChange: (Float) -> Unit,
    onAudioQuestionsVolumeChange: (Float) -> Unit, // <- NUEVO CALLBACK
    onBrightnessChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Imagen de fondo
        Image(
            painter = painterResource(id = R.drawable.sprite_background),
            contentDescription = "Fondo de opciones",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay oscuro para mejor legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Contenido de opciones
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Opciones",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Música de fondo: ${(musicVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
            )
            Slider(
                value = musicVolume,
                onValueChange = onMusicVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Efectos de sonido (SFX): ${(sfxVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
            )
            Slider(
                value = sfxVolume,
                onValueChange = onSfxVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            // --- NUEVO: Control de volumen para audios de preguntas ---
            Text(
                text = "Audios de preguntas: ${(audioQuestionsVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
            )
            Slider(
                value = audioQuestionsVolume,
                onValueChange = onAudioQuestionsVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            // --- Control de Brillo ---
            Text(
                text = "Brillo: ${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
            )
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.1f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de volver con sprite
            Image(
                painter = painterResource(id = R.drawable.sprite_button_back),
                contentDescription = "Volver",
                modifier = Modifier
                    .size(120.dp, 48.dp)
                    .align(Alignment.CenterHorizontally)
                    .clickable { onBack() }
            )
        }
    }
}

// --- MAIN MENU MODIFICADO CON SPRITES Y FONDO ---
@Composable
fun MainMenu(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    onRanking: () -> Unit,
    onCredits: () -> Unit // <- Agregar este parámetro
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Imagen de fondo
        Image(
            painter = painterResource(id = R.drawable.sprite_background),
            contentDescription = "Fondo del menú principal",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay oscuro para mejor legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Contenido del menú
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MOVIZ",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Botones con sprites
            Image(
                painter = painterResource(id = R.drawable.sprite_button_play),
                contentDescription = "Jugar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 8.dp)
                    .clickable { onPlay() }
            )

            Image(
                painter = painterResource(id = R.drawable.sprite_button_options),
                contentDescription = "Opciones",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 8.dp)
                    .clickable { onOptions() }
            )

            Image(
                painter = painterResource(id = R.drawable.sprite_button_ranking),
                contentDescription = "Ranking",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 8.dp)
                    .clickable { onRanking() }
            )

            Image(
                painter = painterResource(id = R.drawable.sprite_creditos),
                contentDescription = "Créditos",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 8.dp)
                    .clickable { onCredits() }
            )

            Image(
                painter = painterResource(id = R.drawable.sprite_button_exit),
                contentDescription = "Salir",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 8.dp)
                    .clickable { onExit() }
            )
        }
    }
}
// --- PANTALLA DE CRÉDITOS ---
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Imagen de fondo
        Image(
            painter = painterResource(id = R.drawable.sprite_background),
            contentDescription = "Fondo de créditos",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay oscuro para mejor legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Contenido de créditos - CENTRADO
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CRÉDITOS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Lista de nombres centrada
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                CreditName("María de Andrés Jarandilla")
                Spacer(modifier = Modifier.height(24.dp))
                CreditName("Daniel Duque Rodríguez")
                Spacer(modifier = Modifier.height(24.dp))
                CreditName("Iván de Castilla Guitián")
                Spacer(modifier = Modifier.height(24.dp))
                CreditName("Javier San Juan Ledesma")
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Botón Volver con sprite
            Image(
                painter = painterResource(id = R.drawable.sprite_button_back),
                contentDescription = "Volver",
                modifier = Modifier
                    .size(120.dp, 48.dp)
                    .clickable { onBack() }
            )
        }
    }
}

// Componente reutilizable para mostrar cada nombre de crédito
@Composable
fun CreditName(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleLarge.copy(
            color = Color.White,
            fontSize = 22.sp
        ),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

// --- PANTALLA DE ENTRADA DE NOMBRE MODIFICADA ---
@Composable
fun NameEntryScreen(onNameSubmitted: (String) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val maxChars = 10

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Imagen de fondo
        Image(
            painter = painterResource(id = R.drawable.sprite_background),
            contentDescription = "Fondo de entrada de nombre",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay oscuro para mejor legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Contenido de entrada de nombre
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Introduce tu nombre (máx. $maxChars):",
                style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TextField(
                value = name,
                onValueChange = { newValue ->
                    if (newValue.length <= maxChars) name = newValue
                },
                singleLine = true,
                supportingText = {
                    Text(
                        text = "${name.length} / $maxChars",
                        color = Color.White
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.2f),
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botón Comenzar con sprite
            Image(
                painter = painterResource(id = R.drawable.sprite_button_submit),
                contentDescription = "Comenzar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = name.isNotBlank()) { onNameSubmitted(name.trim()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Botón Volver con sprite
            Image(
                painter = painterResource(id = R.drawable.sprite_button_back),
                contentDescription = "Volver",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onBack() }
            )
        }
    }
}

// --- TRIVIA GAME MODIFICADO ---
@Composable
fun TriviaGame(
    questions: List<Question>,
    sfxVolume: Float,
    audioQuestionsVolume: Float, // <- NUEVO PARÁMETRO
    playerName: String,
    onQuitToMenu: () -> Unit,
    onLeaderboard: () -> Unit,
    backgroundMusicPlayer: MediaPlayer?, // <- NUEVO: Recibir el reproductor de música
    onPauseBackgroundMusic: () -> Unit, // <- NUEVO: Callback para pausar música
    onResumeBackgroundMusic: () -> Unit // <- NUEVO: Callback para reanudar música
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var currentIndex by rememberSaveable { mutableStateOf(0) }
    var correctAnswers by rememberSaveable { mutableStateOf(0) }
    var showFinalMessage by rememberSaveable { mutableStateOf(false) }

    // Controlar la música de fondo basado en el tipo de pregunta
    LaunchedEffect(currentIndex) {
        val currentQuestion = questions.getOrNull(currentIndex)
        if (currentQuestion?.type == "audio") {
            // Pausar música de fondo para preguntas de audio
            onPauseBackgroundMusic()
        } else {
            // Reanudar música de fondo para preguntas normales
            onResumeBackgroundMusic()
        }
    }

    // Asegurarse de reanudar la música al salir del juego
    DisposableEffect(Unit) {
        onDispose {
            onResumeBackgroundMusic()
        }
    }

    // DEBUG: Verificar qué preguntas se están mostrando
    LaunchedEffect(currentIndex) {
        println("=== DEBUG TRIVIAGAME ===")
        println("Current index: $currentIndex")
        println("Total questions: ${questions.size}")
        if (currentIndex < questions.size) {
            val currentQuestion = questions[currentIndex]
            println("Question ${currentIndex + 1}: ${currentQuestion.text}")
            println("Question type: ${currentQuestion.type}")
            println("Has audio: ${currentQuestion.audioResId != null}")
        }
    }

    LaunchedEffect(showFinalMessage) {
        if (showFinalMessage && correctAnswers > 0) {
            scope.launch(Dispatchers.IO) {
                db.scoreDao().insert(
                    ScoreEntity(
                        playerName = playerName,
                        score = correctAnswers,
                        dateMillis = Date().time
                    )
                )
            }
        }
    }

    fun playSound(correct: Boolean) {
        try {
            val resId = if (correct) context.resources.getIdentifier("correct", "raw", context.packageName)
            else context.resources.getIdentifier("wrong", "raw", context.packageName)
            if (resId != 0) {
                val mp = MediaPlayer.create(context, resId)
                mp.setVolume(sfxVolume, sfxVolume)
                mp.start()
                mp.setOnCompletionListener { m -> m.release() }
            }
        } catch (_: Exception) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Imagen de fondo para toda la pantalla del juego
        Image(
            painter = painterResource(id = R.drawable.sprite_background),
            contentDescription = "Fondo del juego",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay oscuro para mejor legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Contenido del juego
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra superior con información del jugador
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sprite_button_exit),
                    contentDescription = "Salir",
                    modifier = Modifier
                        .size(80.dp, 36.dp)
                        .clickable { onQuitToMenu() }
                )

                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Jugador: $playerName",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Pregunta: ${currentIndex + 1}/${questions.size}",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
            }

            if (questions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cargando preguntas…",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                }
            } else if (showFinalMessage) {
                // Pantalla final
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "¡Has terminado el trivia, $playerName!",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aciertos: $correctAnswers/${questions.size}",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Image(
                            painter = painterResource(id = R.drawable.sprite_volver_a_jugar),
                            contentDescription = "Jugar de nuevo",
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(48.dp)
                                .clickable {
                                    val finalScore = correctAnswers
                                    scope.launch {
                                        db.scoreDao().insert(
                                            ScoreEntity(
                                                playerName = playerName,
                                                score = finalScore,
                                                dateMillis = Date().time
                                            )
                                        )
                                    }
                                    currentIndex = 0
                                    correctAnswers = 0
                                    showFinalMessage = false
                                }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Image(
                            painter = painterResource(id = R.drawable.sprite_button_ranking),
                            contentDescription = "Ver ranking",
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(48.dp)
                                .clickable { onLeaderboard() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Image(
                            painter = painterResource(id = R.drawable.sprite_button_exit),
                            contentDescription = "Volver al menú",
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(48.dp)
                                .clickable { onQuitToMenu() }
                        )
                    }
                }
            } else {
                // Área de preguntas - DETERMINAR TIPO DE PREGUNTA
                Box(modifier = Modifier.weight(1f)) {
                    val currentQuestion = questions[currentIndex]

                    if (currentQuestion.type == "audio") {
                        AudioQuestionScreen(
                            question = currentQuestion,
                            questionIndex = currentIndex,
                            totalQuestions = questions.size,
                            onAnswerFeedback = { correct -> if (correct) correctAnswers++ },
                            onNextQuestion = {
                                if (currentIndex < questions.lastIndex) currentIndex++ else showFinalMessage = true
                            },
                            playSound = { correct -> playSound(correct) },
                            sfxVolume = sfxVolume,
                            audioQuestionsVolume = audioQuestionsVolume // <- NUEVO
                        )
                    } else {
                        QuestionScreen(
                            question = currentQuestion,
                            questionIndex = currentIndex,
                            totalQuestions = questions.size,
                            onAnswerFeedback = { correct -> if (correct) correctAnswers++ },
                            onNextQuestion = {
                                if (currentIndex < questions.lastIndex) currentIndex++ else showFinalMessage = true
                            },
                            playSound = { correct -> playSound(correct) }
                        )
                    }
                }
            }
        }
    }
}

// --- RANKING SCREEN MODIFICADA ---
@Composable
fun RankingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var scores by remember { mutableStateOf<List<ScoreEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        scope.launch {
            val list = db.scoreDao().topScores(10)
            scores = list
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Imagen de fondo
        Image(
            painter = painterResource(id = R.drawable.sprite_background),
            contentDescription = "Fondo del ranking",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay oscuro para mejor legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Contenido del ranking - CENTRADO
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center, // ← Centrado vertical
            horizontalAlignment = Alignment.CenterHorizontally // ← Centrado horizontal
        ) {
            Text(
                text = "Ranking (Top 10)",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 24.dp) // Más espacio abajo
            )

            // Contenedor para las puntuaciones con scroll por si hay muchas
            Column(
                modifier = Modifier
                    .weight(1f, false) // No ocupa todo el espacio disponible
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scores.isEmpty()) {
                    Text(
                        "No hay puntuaciones aún.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        textAlign = TextAlign.Center // Texto centrado
                    )
                } else {
                    scores.forEachIndexed { idx, s ->
                        Text(
                            text = "${idx + 1}. ${s.playerName} — ${s.score}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White,
                                fontSize = 18.sp // Un poco más grande
                            ),
                            modifier = Modifier.padding(vertical = 8.dp) // Espacio entre items
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp)) // Más espacio antes del botón

            // Botón Volver con sprite
            Image(
                painter = painterResource(id = R.drawable.sprite_button_back),
                contentDescription = "Volver",
                modifier = Modifier
                    .size(120.dp, 48.dp)
                    .clickable { onBack() }
            )
        }
    }
}