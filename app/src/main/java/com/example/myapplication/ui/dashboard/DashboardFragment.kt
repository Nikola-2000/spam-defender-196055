package com.example.myapplication.ui.dashboard

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentDashboardBinding
import com.example.myapplication.ui.GmailClient
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    private lateinit var gmailClient: GmailClient
    private lateinit var auth: FirebaseAuth
    private val binding get() = _binding!!
    private val SCOPES: List<String> = listOf(GmailScopes.GMAIL_READONLY)

    private lateinit var signInClient: SignInClient
    private var authCode: String? = ""
    private val APPLICATION_NAME = "Gmail API Java Quickstart"

    /**
     * Global instance of the JSON factory.
     */
    private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
    /**
     * Directory to store authorization tokens for this application.
     */
    private val TOKENS_DIRECTORY_PATH = "tokens"

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private val GMAIL_SCOPES = listOf(GmailScopes.GMAIL_LABELS)
    private val CREDENTIALS_FILE_PATH = "oauth_credentials.json"


    private val defaultWebClientId =
        "1042502406878-53p9jvu12ndbclghpttnn969oaokt89d.apps.googleusercontent.com"
    private val signInLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
        handleSignInResult(result.data)
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInButton: SignInButton
    private lateinit var textView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textDashboard
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = ""
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.googleButton.setOnClickListener { signIn() }

        signInClient = Identity.getSignInClient(requireContext())

        auth = Firebase.auth

        val currentUser = auth.currentUser
        if (currentUser == null) {
            oneTapSignIn()
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    private fun handleSignInResult(data: Intent?) {
        // Result returned from launching the Sign In PendingIntent
        try {
            // Google Sign In was successful, authenticate with Firebase
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try{
                val account = task.getResult(ApiException::class.java)
                authCode = account.serverAuthCode
                Log.d(TAG,"AuthCode: $authCode")
                val authCodePref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
                with (authCodePref.edit()) {
                    putString("SERVER_AUTH_TOKEN", authCode)
                    apply()
                }

            }catch (e: Exception){
                println(e)
            }
            val credential = signInClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken
            if (idToken != null) {
                Log.d(TAG, "firebaseAuthWithGoogle: ${credential.id}")
                firebaseAuthWithGoogle(idToken)
            } else {
                // Shouldn't happen.
                Log.d(TAG, "No ID token!")
            }
        } catch (e: ApiException) {
            // Google Sign In failed, update UI appropriately
            Log.w(TAG, "Google sign in failed", e)
            updateUI(null)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    val view = binding.textDashboard
                    Snackbar.make(view, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                    updateUI(null)
                }

            }
    }

    private fun signIn() {

        val signInRequest = GetSignInIntentRequest.builder()
            .setServerClientId(defaultWebClientId)
            .build()

        signInClient.getSignInIntent(signInRequest)
            .addOnSuccessListener { pendingIntent ->
                launchSignIn(pendingIntent)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Google Sign-in failed", e)
            }
    }

    private fun oneTapSignIn() {
        // Configure One Tap UI
        val oneTapRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(defaultWebClientId)
                    .build(),
            )
            .setAutoSelectEnabled(true)
            .build()

        // Display the One Tap UI
        signInClient.beginSignIn(oneTapRequest)
            .addOnSuccessListener { result ->
                launchSignIn(result.pendingIntent)
            }
            .addOnFailureListener { e ->
                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
            }
    }

    private fun launchSignIn(pendingIntent: PendingIntent) {
        try {
            val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent)
                .build()
            signInLauncher.launch(intentSenderRequest)
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Couldn't start Sign In: ${e.localizedMessage}")
        }
    }

    private fun updateUI(user: FirebaseUser?) {


        if (user != null) {

            binding.googleButton.visibility = View.GONE
            val transport = NetHttpTransport()
            val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
            val assetManager = context?.assets
            val credentialsJson = assetManager?.open("oauth_credentials.json")
//
            CoroutineScope(Dispatchers.Default).launch {
//       gmailClient.getCredentials(context,user.uid,transport)

//                val credentials = GoogleCredential.fromStream(readFromJson(context,"credentials.json")).createScoped(listOf("https://www.googleapis.com/auth/gmail.readonly"))
//                val credentials = GoogleCredential.fromStream(credentialsJson)

//                val scoped = credentials.createScoped(
//                    listOf(
//                        "https://www.googleapis.com/auth/firebase.database",
//                        "https://www.googleapis.com/auth/userinfo.email",
//                        "https://www.googleapis.com/auth/gmail.readonly"
//                    )
//                )

//                scoped.refreshToken()
//                token = scoped.accessToken
//                user.getIdToken(false).addOnSuccessListener { tokenResult ->
//                    val userToken: String? = tokenResult.token
//
//                    if (userToken != null) {
//                        token = userToken
//                    } else {
//                        Log.e(TAG, "Error getting token")
//                    }
//                }.addOnFailureListener { exception ->
//                    Log.e(TAG, "Error", exception)
//                }

                val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(credentialsJson))

                val clientId = getString(R.string.default_web_client_id)
                val secret = "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"
                if (authCode == null || authCode == "") {
                    authCode =
                        activity?.getSharedPreferences("SERVER_AUTH_TOKEN", Context.MODE_PRIVATE)
                            .toString()
                }
                val redirectUri = "https://fir-auth-3bed6.firebaseapp.com/__/auth/handler"

                val tokenResponse = GoogleAuthorizationCodeTokenRequest(
                    transport,
                    jsonFactory,
                    clientId,
//                    clientSecrets.details.clientSecret,
                    secret,
                    authCode,
//                    clientSecrets.details.redirectUris[0]
                    redirectUri
                ).execute()

                val accessToken = tokenResponse.accessToken
                val refreshToken = tokenResponse.refreshToken

                val sharedPref1 = activity?.getPreferences(Context.MODE_PRIVATE) ?: return@launch
                with (sharedPref1.edit()) {
                    putString("ACCESS_TOKEN", accessToken)
                    apply()
                }

                val sharedPref2 = activity?.getPreferences(Context.MODE_PRIVATE) ?: return@launch
                with (sharedPref2.edit()) {
                    putString("REFRESH_TOKEN", refreshToken)
                    apply()
                }


                val tokenFolder = File(
                    context?.getExternalFilesDir("")?.absolutePath + "tokens"
                )

                if (!tokenFolder.exists()) {
                    tokenFolder.mkdirs()
                }

//                val httpTransport = NetHttpTransport()
//
//                val flow = GoogleAuthorizationCodeFlow.Builder(
//                    httpTransport, jsonFactory, clientSecrets, SCOPES
//                ).setDataStoreFactory(FileDataStoreFactory(tokenFolder)).setAccessType("offline")
//                    .build()
//
//                val receiver = LocalServerReceiver.Builder()
//                    .setPort(8888)
//                    .build()

//                val authorizationCode = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

//                GoogleCredential().setAccessToken(accessToken)
                val service =
                    Gmail.Builder(transport, jsonFactory, GoogleCredential().setAccessToken(accessToken))
                        .setApplicationName("Spam defender").build()

                val userId = ""
                val response = service.users().messages().list(userId).execute()
                val messages = response.messages

                for (message in messages) {
                    val email = service.users().messages().get(userId, message.id).execute()
                    val sender = getEmailHeader(email, "From")
                    val subject = getEmailHeader(email, "Subject")
                    val body = getEmailBody(email)
                    withContext(Dispatchers.Main) {
                        binding.textDashboard.text =
                            "Email: From: $sender, Subject: $subject, Body: $body"
                    }
                }
            }
        } else {
            binding.textDashboard.text = "Login failed"
        }
    }

    private fun getEmailHeader(email: Message, headerName: String): String {
        val headers = email.payload.headers
        for (header in headers) {
            if (header.name == headerName) {
                return header.value
            }
        }
        return ""
    }

    // Helper function to retrieve email body
    private fun getEmailBody(email: Message): String {
        val parts = email.payload.parts
        for (part in parts) {
            if (part.mimeType == "text/plain") {
                return part.body.data
            }
        }
        return ""
    }

//    googleSignInButton = view.findViewById(R.id.google_button)
//    textView = view.findViewById(R.id.text_dashboard)
//
//    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//      .requestEmail()
//      .build()
//
//    googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
//
//    googleSignInButton.setOnClickListener {
//      signInWithGoogle()
//    }


    //  private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//    if (result.resultCode == Activity.RESULT_OK) {
//      val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
//      handleSignInResult(task)
//    }else if (result.resultCode == Activity.RESULT_CANCELED){
//      val task = result.data
//      println(task)
//    }
//  }
//  private fun signInWithGoogle() {
//    val signInIntent = googleSignInClient.signInIntent
//    signInLauncher.launch(Intent.createChooser(signInIntent, "Sign in with Google"))
//  }
//  @Suppress("DEPRECATION")
//  @Deprecated("This method is deprecated")
//  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//    super.onActivityResult(requestCode, resultCode, data)
//
//    if (requestCode == RC_SIGN_IN) {
//      val task = GoogleSignIn.getSignedInAccountFromIntent(data)
//      handleSignInResult(task)
//    }
//  }
//
//  private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
//    try {
//      val account = completedTask.getResult(ApiException::class.java)
//      // Signed in successfully, handle the account
//      // ...
//      googleSignInButton.visibility = View.GONE
//
//      val account_res = account?.displayName ?: ""
//
//      textView.text = account_res
//
//    } catch (e: ApiException) {
//      // Handle sign-in failure
//      // ...
//      println("Sign in failure")
//    }
//  }
    companion object {
        private const val RC_SIGN_IN = 9001
        const val SIGN_IN_REQUEST_CODE = 1001
        private const val TAG = "DashboardFragmentKt"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}