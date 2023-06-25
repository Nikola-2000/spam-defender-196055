package com.example.myapplication.ui.home

import android.accounts.AccountManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentHomeBinding
import com.example.myapplication.ui.TextClassificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.text.nlclassifier.BertNLClassifier
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

class HomeFragment : Fragment() {

private var _binding: FragmentHomeBinding? = null
  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding get() = _binding!!
  var predictedClass : Category = Category("", 0.0F)
  private lateinit var phishingModel: BertNLClassifier
  private lateinit var classifier: TextClassificationHelper

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    val root: View = binding.root

    classifier = context?.let { TextClassificationHelper(context = it, currentModel = "phish") }!!

//    val baseOptionsBuilder = BaseOptions.builder()

    // Use the specified hardware for running the model. Default to CPU.
    // Possible to also use a GPU delegate, but this requires that the classifier be created
    // on the same thread that is using the classifier, which is outside of the scope of this
    // sample's design.

//    try{
//      baseOptionsBuilder.useNnapi()
//    }catch(e: Exception){
//      println("Cannot use Neural Network API. Will use default")
//    }
//
//    val baseOptions = baseOptionsBuilder.build()
//
//    // Directions for generating both models can be found at
//    // https://www.tensorflow.org/lite/models/modify/model_maker/text_classification
//    val options = BertNLClassifier.BertNLClassifierOptions
//      .builder()
//      .setBaseOptions(baseOptions)
//      .build()
    val textView: TextView = binding.results
//    val modelFile = File("phish_model.lite")
////    val phishing_model = BertNLClassifier.createFromFile(modelFile)
////    BertNLClassifier.createFromFileAndOptions(modelFile, options)
//    phishingModel = BertNLClassifier.createFromFileAndOptions(
//      context,
//      "phish_model.lite",
//      options)

    homeViewModel.text.observe(viewLifecycleOwner){textView.text=""}

    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val homeViewModel =
      ViewModelProvider(this).get(HomeViewModel::class.java)

    val inputText = binding.inputText
    val buttonClassify = binding.classifyBtn
    val textView: TextView = binding.results


    buttonClassify.setOnClickListener {
      CoroutineScope(Dispatchers.Default).launch {
        val sampleText = inputText.text.toString().trim()
        val result = classifier.classify(sampleText)
        predictedClass = result.sortedByDescending { it.score }[0]

        withContext(Dispatchers.Main) {
          homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = if (predictedClass.label == "0") {
              "This URL is benign. It should be safe to go to."
            } else if (predictedClass.label == "1") {
              "This URL is suspected of phishing. Phishing is the act of pretending to be a trusted entity and creating a sense of urgency, like threatening to close or seize a victim's bank or insurance account"
            } else if (predictedClass.label == "2") {
              "This URL is suspected of defacement. The contents of this URL could be altered by hackers parties and provide malicious information"
            } else if (predictedClass.label == "3") {
              "This URL is suspected of malware. This website is suspected to contain malware which infect your device."
            } else {
              ""
            }
          }
        }
      }

    }
  }

override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}