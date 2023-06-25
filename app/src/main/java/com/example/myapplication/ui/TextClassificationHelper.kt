package com.example.myapplication.ui

import android.content.Context
import android.os.SystemClock
import com.example.myapplication.ui.home.HomeFragment
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.text.nlclassifier.BertNLClassifier
import java.lang.Exception
import java.util.concurrent.ScheduledThreadPoolExecutor

class TextClassificationHelper(
    var currentDelegate: Int = 0,
    var currentModel: String = "phish",
    val context: Context,
) {
    // There are two different classifiers here to support both the Average Word Vector
    // model (NLClassifier) and the MobileBERT model (BertNLClassifier). Model selection
    // can be changed from the UI bottom sheet.
    private lateinit var phishingModel: BertNLClassifier
    private lateinit var emailModel: BertNLClassifier

    private lateinit var executor: ScheduledThreadPoolExecutor

    init {
        initClassifier()
    }

    fun initClassifier() {
        val baseOptionsBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU.
        // Possible to also use a GPU delegate, but this requires that the classifier be created
        // on the same thread that is using the classifier, which is outside of the scope of this
        // sample's design.
        try {
            baseOptionsBuilder.useNnapi()
        }catch (e: Exception){
            println("Cannot use Neural Network API")
        }

        val baseOptions = baseOptionsBuilder.build()
        val options = BertNLClassifier.BertNLClassifierOptions
            .builder()
            .setBaseOptions(baseOptions)
            .build()
        // Directions for generating both models can be found at
        // https://www.tensorflow.org/lite/models/modify/model_maker/text_classification
        if( currentModel == "phish" ) {

            phishingModel = BertNLClassifier.createFromFileAndOptions(
                context,
                PHISH,
                options)
        } else if (currentModel == "spam") {

            emailModel = BertNLClassifier.createFromFileAndOptions(
                context,
                SPAM,
                options)
        }
    }

    fun classify(text: String): List<Category> {

        val results: List<Category>
        // inferenceTime is the amount of time, in milliseconds, that it takes to
        // classify the input text.
        var inferenceTime = SystemClock.uptimeMillis()

        // Use the appropriate classifier based on the selected model
        if(currentModel == "phish") {
            results = phishingModel.classify(text)
        } else {
            results = emailModel.classify(text)
        }

        results.sortedByDescending { it.score }
        return results
    }

    interface TextResultsListener {
        fun onError(error: String)
        fun onResult(results: List<Category>, inferenceTime: Long)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_NNAPI = 1
        const val PHISH = "phish_model.tflite"
        const val SPAM = "spam_model.tflite"
    }
}