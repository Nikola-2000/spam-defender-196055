package com.example.myapplication.ui

import android.accounts.AccountManager
import android.content.Context
import android.content.Context.ACCOUNT_SERVICE
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Label
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class GmailClient{

    private val MAX_FETCH_THREADS = Runtime.getRuntime().availableProcessors()

    val executors = Executors.newFixedThreadPool(MAX_FETCH_THREADS)

    val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executors.execute(block)
        }
    }

    companion object {
        private const val APPLICATION_NAME = "Gmail API Kotlin"
        private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
        private const val TOKENS_DIRECTORY_PATH = "tokens"
        private val SCOPES: List<String> = listOf(GmailScopes.GMAIL_READONLY)
        private const val CREDENTIALS_FILE_PATH = "credentials.json"
    }


    private fun readFromJson(context: Context?, filename: String): InputStream {
        val assetManager = context?.assets
        if (assetManager != null) {
            return assetManager.open(filename)
        }else{
            throw NullPointerException("Context was null")
        }
    }

     fun getCredentials(context: Context?, user: String, httpTransport: NetHttpTransport): Credential {
        val inputStream = readFromJson(context,CREDENTIALS_FILE_PATH)
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inputStream))

        val tokenFolder = File(
            context?.getExternalFilesDir("")?.absolutePath + TOKENS_DIRECTORY_PATH
        )

        if (!tokenFolder.exists()) {
            tokenFolder.mkdirs()
        }

        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport, JSON_FACTORY, clientSecrets, SCOPES
        ).setDataStoreFactory(FileDataStoreFactory(tokenFolder)).setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()

        return AuthorizationCodeInstalledApp(flow, receiver).authorize(user)
    }


    private tailrec fun Gmail.processMessages(
        user: String,
        label: Label,
        nextPageToken: String? = null,
        process: (Message) -> Unit
    ) {

        val messages = users().messages().list(user).apply {
            labelIds = listOf(label.id)
            pageToken = nextPageToken
            includeSpamTrash = true
        }.execute()

        messages.messages.forEach { message ->
            process(message)
        }

        if (messages.nextPageToken != null) {
            processMessages(user, label, messages.nextPageToken, process)
        }
    }


    private fun String.parseAddress(): String {
        return if (contains("<")) {
            substringAfter("<").substringBefore(">")
        } else {
            this
        }
    }


    private fun Gmail.processFroms(
        user: String,
        label: Label,
        process: (String) -> Unit
    ) {
        runBlocking(dispatcher) {
            processMessages(user, label) { m ->
                launch {
                    fun fetchAndProcess() {
                        try {
                            val message = users().messages().get(user, m.id).apply { format = "METADATA" }.execute()
                            message.payload.headers.find { it.name == "From" }?.let { from ->
                                process(from.value.parseAddress())
                            }
                        } catch (e: SocketTimeoutException) {
                            // Process eventual failures.
                            // Restart request on socket timeout.
                            e.printStackTrace()
                            fetchAndProcess()
                        } catch (e: Exception) {
                            // Process eventual failures.
                            e.printStackTrace()
                        }
                    }
                    fetchAndProcess()
                }
            }
        }
    }

    suspend fun extract(context: Context?): List<Message> {

        val manager = context?.getSystemService(ACCOUNT_SERVICE) as AccountManager
        val list = manager.accounts
        var gmail: String? = null

        for (account in list){
            if(account.type.equals("com.google", ignoreCase = true)){
                gmail = account.name
                break
            }
        }

        // Build a new authorized API client service.
        val httpTransport = NetHttpTransport()

        val service = Gmail.Builder(
            httpTransport, JSON_FACTORY, getCredentials(
                context,
                "user",
                httpTransport
            )
        )
            .setApplicationName(APPLICATION_NAME)
            .build()

        // Find the requested label
        val user = "me"
        val labelList: ListMessagesResponse = service.users().messages().list(gmail).execute()

        return labelList.messages
    }
//        val label = labelList.labels
//            .find { it.name == labelName } ?: error("Label `$labelName` is unknown.")
//
//
//        // Process all From headers.
//        val senders = mutableSetOf<String>()
//        service.processFroms(user, label) {
//            senders += it
//        }
//
//        senders.forEach(::println)


//    fun listEmails(context: Context?): List<Message> {
//        val httpTransport = NetHttpTransport()
//        val credentials = getCredentials(context, httpTransport)
//        val service = Gmail.Builder(httpTransport, JSON_FACTORY, credentials)
//            .setApplicationName(APPLICATION_NAME).build()
//
//        val user = "me"
//        val response: ListMessagesResponse = service.users().messages().list(user).execute()
//
//        return response.messages
//    }
}

