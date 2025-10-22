package com.example.pelistrivia

import android.app.Activity
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
import java.util.*

// --- MODELOS DE DATOS ---
data class Answer(
    val text: String,
    @DrawableRes val imageResId: Int,
    val isCorrect: Boolean
)

data class Question(
    val text: String,
    val answers: List<Answer>,
    val explanation: String? = null
)

// --- NUEVO ESTADO DE NAVEGACIÓN ---
private enum class Screen {
    Splash, // Nueva pantalla de inicio
    MainMenu,
    Options,
    EnterName,
    Trivia,
    Ranking
}

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
            Image(
                painter = painterResource(id = answer.imageResId),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )
            Text(
                text = answer.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
            )
        }
    }
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
                            text = "✅ ¡Correcto!",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )

                        if (showContinueButton) {
                            Image(
                                painter = painterResource(id = R.drawable.sprite_button_continue),
                                contentDescription = "Continuar",
                                modifier = Modifier
                                    .size(120.dp, 48.dp)
                                    .clickable { onNextQuestion() }
                            )
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            isCorrect == false && !timeUp -> Text(
                text = "❌ Incorrecto",
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            timeUp -> Text(
                text = "⏰ Se ha acabado el tiempo",
                color = Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
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

    var screen by rememberSaveable { mutableStateOf(Screen.Splash) } // Comienza con Splash
    var appVolume by rememberSaveable { mutableStateOf(0.8f) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var playerName by rememberSaveable { mutableStateOf("") }
    var gameId by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try { appVolume = loadVolume(context) } catch (_: Exception) { appVolume = 0.8f }
    }

    LaunchedEffect(Unit) {
        try { questions = loadQuestionsFromAssets(context) } catch (_: Exception) {
            questions = listOf(
                Question("Error cargando preguntas", answers = listOf(Answer("OK", android.R.drawable.ic_menu_info_details, true)))
            )
        }
    }

    when (screen) {
        Screen.Splash -> SplashScreen(
            onContinue = { screen = Screen.MainMenu }
        )
        Screen.MainMenu -> MainMenu(
            onPlay = { screen = Screen.EnterName },
            onOptions = { screen = Screen.Options },
            onExit = { activity?.finishAffinity() },
            onRanking = { screen = Screen.Ranking }
        )
        Screen.Options -> OptionsScreen(
            volume = appVolume,
            onVolumeChange = { newV ->
                appVolume = newV
                scope.launch { saveVolume(context, newV) }
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
                    volume = appVolume,
                    playerName = playerName,
                    onQuitToMenu = { screen = Screen.MainMenu },
                    onLeaderboard = { screen = Screen.Ranking }
                )
            }
        }
        Screen.Ranking -> RankingScreen(onBack = { screen = Screen.MainMenu })
    }
}

// --- OPTIONS SCREEN MODIFICADA CON SPRITES ---
@Composable
fun OptionsScreen(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
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
                text = "Volumen: ${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
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
    onRanking: () -> Unit
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
// --- PANTALLA DE ENTRADA DE NOMBRE MODIFICADA ---
@Composable
fun NameEntryScreen(onNameSubmitted: (String) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val maxChars = 5

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
    volume: Float,
    playerName: String,
    onQuitToMenu: () -> Unit,
    onLeaderboard: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var currentIndex by rememberSaveable { mutableStateOf(0) }
    var correctAnswers by rememberSaveable { mutableStateOf(0) }
    var showFinalMessage by rememberSaveable { mutableStateOf(false) }

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
                mp.setVolume(volume, volume)
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
                    text = "Volumen: ${(volume * 100).toInt()}%",
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
                // Pantalla final con la misma imagen de fondo
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
                            painter = painterResource(id = R.drawable.sprite_button_play),
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
                // Área de preguntas con fondo transparente para que se vea la imagen
                Box(modifier = Modifier.weight(1f)) {
                    QuestionScreen(
                        question = questions[currentIndex],
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