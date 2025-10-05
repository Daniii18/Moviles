package com.example.pelistrivia

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    val answers: List<Answer>
)

// --- NUEVO ESTADO DE NAVEGACIÓN ---
private enum class Screen {
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

// --- PANTALLA DE PREGUNTA ---
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

    val scope = rememberCoroutineScope()

    // Reinicia el temporizador cuando cambia la pregunta
    LaunchedEffect(questionIndex) {
        remaining = timeLimitSeconds
        selectedAnswer = null
        isCorrect = null
        timeUp = false
    }

    // Lógica del temporizador
    LaunchedEffect(remaining, selectedAnswer) {
        if (selectedAnswer == null && !timeUp) {
            if (remaining > 0) {
                delay(1000)
                remaining -= 1
            } else {
                timeUp = true
                isCorrect = false
                onAnswerFeedback(false)
                playSound(false)
                delay(1500)
                onNextQuestion()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pregunta ${questionIndex + 1} / $totalQuestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (!timeUp) "$remaining s" else "⏰",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (remaining > 5) Color.Black else Color.Red,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                        delay(1500)
                        onNextQuestion()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isCorrect == true -> Text(
                text = "✅ ¡Correcto!",
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
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

    var screen by rememberSaveable { mutableStateOf(Screen.MainMenu) }
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

// --- OPTIONS SCREEN ---
@Composable
fun OptionsScreen(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Opciones",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(text = "Volumen: ${(volume * 100).toInt()}%", modifier = Modifier.padding(bottom = 8.dp))
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("VOLVER")
        }
    }
}


// --- MAIN MENU ---
@Composable
fun MainMenu(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    onRanking: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PELIS TRIVIA",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("JUGAR") }
        Button(onClick = onOptions, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("OPCIONES") }
        Button(onClick = onRanking, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("RANKING") }
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("SALIR") }
    }
}

// --- PANTALLA DE ENTRADA DE NOMBRE ---
@Composable
fun NameEntryScreen(onNameSubmitted: (String) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val maxChars = 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Introduce tu nombre (máx. $maxChars):",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        TextField(
            value = name,
            onValueChange = { newValue ->
                if (newValue.length <= maxChars) name = newValue
            },
            singleLine = true,
            supportingText = { Text(text = "${name.length} / $maxChars") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onNameSubmitted(name.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("COMENZAR") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("VOLVER") }
    }
}

// --- TRIVIA GAME ---
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
    // Guarda automáticamente el resultado al terminar la partida
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onQuitToMenu) { Text("Salir") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Jugador: $playerName",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(text = "Volumen: ${(volume * 100).toInt()}%")
        }

        if (questions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando preguntas…", style = MaterialTheme.typography.titleMedium)
            }
        } else if (showFinalMessage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B5E20)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🎉 ¡Has terminado el trivia, $playerName!",
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

                    // 🔹 Aquí guardamos la puntuación correcta ANTES de reiniciar
                    Button(onClick = {
                        val finalScore = correctAnswers  // 👈 cambio importante aquí
                        scope.launch {
                            db.scoreDao().insert(
                                ScoreEntity(
                                    playerName = playerName,
                                    score = finalScore,   // se guarda el valor antes del reset
                                    dateMillis = Date().time
                                )
                            )
                        }
                        // ahora sí, reiniciamos los valores del juego
                        currentIndex = 0
                        correctAnswers = 0
                        showFinalMessage = false
                    }) { Text("JUGAR DE NUEVO") }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onLeaderboard) { Text("VER RANKING") }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onQuitToMenu) { Text("VOLVER AL MENÚ") }
                }
            }
        } else {
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


// --- RANKING SCREEN ---
@Composable
fun RankingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var scores by remember { mutableStateOf<List<ScoreEntity>>(emptyList()) }

    // 🔹 Cargar las puntuaciones al iniciar la pantalla
    LaunchedEffect(Unit) {
        scope.launch {
            val list = db.scoreDao().topScores(10)
            scores = list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Ranking (Top 10)",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (scores.isEmpty()) {
            Text("No hay puntuaciones aún.")
        } else {
            scores.forEachIndexed { idx, s ->
                Text(
                    text = "${idx + 1}. ${s.playerName} — ${s.score}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onBack) {
            Text("VOLVER")
        }
    }
}


