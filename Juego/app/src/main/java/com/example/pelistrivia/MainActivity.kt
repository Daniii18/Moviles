package com.example.pelistrivia

import android.app.Activity
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
import kotlinx.coroutines.delay

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

// --- PANTALLAS (estado interno de navegación) ---
private enum class Screen {
    MainMenu,
    Options,
    Trivia
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
    onAnswerFeedback: (Boolean) -> Unit,
    onNextQuestion: () -> Unit
) {
    var selectedAnswer by remember { mutableStateOf<Answer?>(null) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }

    // Si ya se ha respondido, espera 1.5s y pasa a la siguiente pregunta
    LaunchedEffect(isCorrect) {
        if (isCorrect != null) {
            delay(1500)
            onNextQuestion()
            // Reiniciar estado (aunque la recomposición con nueva pregunta también lo hará)
            selectedAnswer = null
            isCorrect = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Pregunta ${questionIndex + 1}/$totalQuestions",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        question.answers.forEach { answer ->
            val correctForThisAnswer = if (selectedAnswer == null) null else answer.isCorrect
            AnswerItem(
                answer = answer,
                isSelected = selectedAnswer == answer,
                isCorrectAnswer = correctForThisAnswer
            ) {
                if (selectedAnswer == null) {
                    selectedAnswer = answer
                    isCorrect = answer.isCorrect
                    onAnswerFeedback(answer.isCorrect)
                }
            }
        }

        if (isCorrect != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isCorrect == true) "✅ ¡Correcto!" else "❌ Incorrecto",
                color = if (isCorrect == true) Color(0xFF4CAF50) else Color(0xFFF44336),
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

// --- COMPOSABLE RAÍZ: maneja navegación entre MainMenu, Options y Trivia ---
@Composable
fun AppRoot() {
    // Estado de navegación y configuración (persistente a recomposiciones y rotaciones)
    var screen by rememberSaveable { mutableStateOf(Screen.MainMenu) }
    var appVolume by rememberSaveable { mutableStateOf(0.8f) }
    var gameId by rememberSaveable { mutableStateOf(0) }

    val context = LocalContext.current
    val activity = context as? Activity

    when (screen) {
        Screen.MainMenu -> MainMenu(
            onPlay = {
                gameId++              // fuerza reinicio del juego
                screen = Screen.Trivia
            },
            onOptions = { screen = Screen.Options },
            onExit = {
                // Cierra la app
                activity?.finishAffinity()
            }
        )

        Screen.Options -> OptionsScreen(
            volume = appVolume,
            onVolumeChange = { appVolume = it },
            onBack = { screen = Screen.MainMenu }
        )

        Screen.Trivia -> {
            // `key(gameId)` vuelve a crear el subtree de TriviaGame cuando cambie gameId
            key(gameId) {
                TriviaGame(
                    onQuitToMenu = { screen = Screen.MainMenu },
                    volume = appVolume
                )
            }
        }
    }
}

// --- MAIN MENU ---
@Composable
fun MainMenu(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit
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

        Button(
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = "JUGAR")
        }

        Button(
            onClick = onOptions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = "OPCIONES")
        }

        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = "SALIR")
        }
    }
}

// --- OPTIONS SCREEN: volumen con slider ---
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

        Button(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(text = "VOLVER")
        }
    }
}

// --- TRIVIA GAME (usa QuestionScreen) ---
@Composable
fun TriviaGame(
    onQuitToMenu: () -> Unit,
    volume: Float
) {
    // Las preguntas se mantienen estáticas aquí
    val questions = remember {
        listOf(
            Question(
                text = "¿Cuál de estas películas ganó el Óscar a la mejor película en 1994?",
                answers = listOf(
                    Answer("Forrest Gump", R.drawable.forrest_gump, true),
                    Answer("Pulp Fiction", R.drawable.pulp_fiction, false),
                    Answer("Cadena perpetua", R.drawable.shawshank, false),
                    Answer("Jurassic Park", R.drawable.jurassic_park, false)
                )
            ),
            Question(
                text = "¿Quién dirigió la película 'Inception'?",
                answers = listOf(
                    Answer("Christopher Nolan", R.drawable.nolan, true),
                    Answer("Steven Spielberg", R.drawable.spielberg, false),
                    Answer("Quentin Tarantino", R.drawable.tarantino, false),
                    Answer("James Cameron", R.drawable.cameron, false)
                )
            )
        )
    }

    var currentIndex by remember { mutableStateOf(0) }
    var correctAnswers by remember { mutableStateOf(0) }
    var showFinalMessage by remember { mutableStateOf(false) }

    // Layout general del juego con botón para volver al menú en cualquier momento
    Column(modifier = Modifier.fillMaxSize()) {
        // Barra superior sencilla con botón volver al menú
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onQuitToMenu) {
                Text("Menú")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Volumen: ${(volume * 100).toInt()}%",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (showFinalMessage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B5E20)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🎉 ¡Has terminado el trivia!",
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
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        // Reiniciar partida desde el principio
                        currentIndex = 0
                        correctAnswers = 0
                        showFinalMessage = false
                    }) {
                        Text("JUGAR DE NUEVO")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = onQuitToMenu) {
                        Text("VOLVER AL MENÚ")
                    }
                }
            }
        } else {
            // Mostrar la pregunta actual
            Box(modifier = Modifier.weight(1f)) {
                QuestionScreen(
                    question = questions[currentIndex],
                    questionIndex = currentIndex,
                    totalQuestions = questions.size,
                    onAnswerFeedback = { correct ->
                        if (correct) correctAnswers++
                        // (Opcional) aquí podrías reproducir un sonido usando 'volume'
                    },
                    onNextQuestion = {
                        if (currentIndex < questions.lastIndex) {
                            currentIndex++
                        } else {
                            showFinalMessage = true
                        }
                    }
                )
            }
        }
    }
}
