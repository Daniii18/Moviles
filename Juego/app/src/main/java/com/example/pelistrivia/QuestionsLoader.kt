package com.example.pelistrivia

import android.content.Context
import android.util.Log
import org.json.JSONArray

// Lee questions.json de assets y devuelve List<Question>
fun loadQuestionsFromAssets(context: Context): List<Question> {
    val json = context.assets.open("questions.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(json)
    val list = mutableListOf<Question>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val text = obj.getString("text")
        val answersArray = obj.getJSONArray("answers")
        val answers = mutableListOf<Answer>()
        for (j in 0 until answersArray.length()) {
            val a = answersArray.getJSONObject(j)
            val aText = a.getString("text")
            val imgName = a.optString("image", "")
            val isCorrect = a.getBoolean("isCorrect")
            // intenta obtener el id; si no existe usa un fallback
            var resId = 0
            try {
                if (imgName.isNotBlank()) {
                    resId = context.resources.getIdentifier(imgName, "drawable", context.packageName)
                }
            } catch (e: Exception) {
                Log.w("loadQuestions", "Error al obtener drawable '$imgName' para la respuesta '$aText': ${e.message}")
            }
            if (resId == 0) {
                // fallback a un drawable del sistema (si prefieres, crea uno en tu res/drawable y pon su nombre aquí)
                resId = android.R.drawable.ic_menu_report_image
                Log.w("loadQuestions", "Drawable no encontrado para '$imgName'. Usando fallback (android.R.drawable.ic_menu_report_image).")
            }
            answers.add(Answer(aText, resId, isCorrect))
        }
        val explanation = if (obj.has("explanation")) obj.getString("explanation") else null
        list.add(Question(text, answers, explanation))
    }
    return list
}
