package com.example.pelistrivia

import android.content.Context
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
            val imgName = a.getString("image") // nombre del drawable sin extensión
            val isCorrect = a.getBoolean("isCorrect")
            val resId = context.resources.getIdentifier(imgName, "drawable", context.packageName)
            answers.add(Answer(aText, resId, isCorrect))
        }
        list.add(Question(text, answers))
    }
    return list
}