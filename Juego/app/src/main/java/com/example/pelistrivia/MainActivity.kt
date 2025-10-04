package com.example.pelistrivia

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    // ✅ Este LaunchedEffect está bien dentro de un @Composable
    LaunchedEffect(isCorrect) {
        if (isCorrect != null) {
            delay(1500)
            onNextQuestion()
            // Reiniciar estado al avanzar
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

        // ✅ Ahora sí: cada pregunta se puede responder correctamente
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
                    TriviaGame()
                }
            }
        }
    }
}

// --- LÓGICA DEL JUEGO ---
@Composable
fun TriviaGame() {
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

    if (showFinalMessage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1B5E20)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎉 ¡Has terminado el trivia!\nAciertos: $correctAnswers/${questions.size}",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        QuestionScreen(
            question = questions[currentIndex],
            questionIndex = currentIndex,
            totalQuestions = questions.size,
            onAnswerFeedback = { correct ->
                if (correct) correctAnswers++
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
