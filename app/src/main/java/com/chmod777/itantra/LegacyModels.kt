package com.chmod777.itantra

// Message model and view constants for the pre-demo screens in LegacyScreens.kt.
// Both files go away as each demo screen lands; see the step table in the UI plan.

import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.time.format.DateTimeFormatter

internal enum class Screen { MAIN, HANDS_FREE, NEW_MESSAGE, LOGS, MESSAGE_DETAIL }

internal enum class MessageDirection { SENT, RECEIVED }

internal data class Message(
    val text: String,
    val date: String,
    val timestamp: String,
    val direction: MessageDirection,
    val isRead: Boolean,
    val languageCode: String,
    val transportMessageId: Long? = null,
    val deliveryState: MessageDeliveryState? = null,
    /** v1 networking bundle handle for sent messages; null for legacy RFCOMM rows. */
    val networkBundleId: String? = null,
    /** Trusted-contact name on this phone (recipient for sent, sender for received). */
    val peerName: String? = null,
)

internal val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
internal val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal const val LATENCY_TAG = "ITANTRA_LATENCY"

// Spoken-language options for transcription/TTS. Codes and display names
// per docs/CONTRACTS.md's language table.
internal val SUPPORTED_LANGUAGES = listOf(
    "en" to "English",
    "hi" to "Hindi",
    "gu" to "Gujarati",
    "mr" to "Marathi",
    "ta" to "Tamil",
    "te" to "Telugu",
    "or" to "Odia",
    "bn" to "Bengali",
    "kn" to "Kannada",
    "ml" to "Malayalam"
)

// Sample text per language for the temporary Test TTS control. Typing Devanagari
// or Bengali on the test phone needs a matching keyboard, so the control offers a
// ready sentence whenever the operator has not typed their own.
internal val TTS_SAMPLE_TEXT = mapOf(
    "en" to "this is a test message",
    "hi" to "यह एक परीक्षण संदेश है",
    "gu" to "આ એક પરીક્ષણ સંદેશ છે",
    "mr" to "हा एक चाचणी संदेश आहे",
    "ta" to "இது ஒரு சோதனை செய்தி",
    "te" to "ఇది ఒక పరీక్ష సందేశం",
    "or" to "ଏହା ଏକ ପରୀକ୍ଷା ବାର୍ତ୍ତା",
    "bn" to "এটি একটি পরীক্ষা বার্তা",
    "kn" to "ಇದು ಒಂದು ಪರೀಕ್ಷಾ ಸಂದೇಶ",
    "ml" to "നദിയിലെ വെള്ളം വേഗത്തിൽ ഉയരുന്നു. സുരക്ഷിത സ്ഥലത്തേക്ക് പോകുക."
)

// The temporary Test TTS control lists the same languages as the picker.
internal val TTS_TEST_LANGUAGES = SUPPORTED_LANGUAGES

internal val MessageListSaver = Saver<SnapshotStateList<Message>, ArrayList<Bundle>>(
    save = { messages ->
        ArrayList(messages.map { message ->
            Bundle().apply {
                putString("text", message.text)
                putString("date", message.date)
                putString("timestamp", message.timestamp)
                putString("direction", message.direction.name)
                putBoolean("isRead", message.isRead)
                putString("languageCode", message.languageCode)
                message.transportMessageId?.let { putLong("transportMessageId", it) }
                putString("deliveryState", message.deliveryState?.name)
                putString("networkBundleId", message.networkBundleId)
                putString("peerName", message.peerName)
            }
        })
    },
    restore = { savedMessages ->
        mutableStateListOf<Message>().apply {
            savedMessages.forEach { saved ->
                add(
                    Message(
                        text = checkNotNull(saved.getString("text")),
                        date = checkNotNull(saved.getString("date")),
                        timestamp = checkNotNull(saved.getString("timestamp")),
                        direction = MessageDirection.valueOf(checkNotNull(saved.getString("direction"))),
                        isRead = saved.getBoolean("isRead"),
                        languageCode = checkNotNull(saved.getString("languageCode")),
                        transportMessageId = if (saved.containsKey("transportMessageId")) {
                            saved.getLong("transportMessageId")
                        } else {
                            null
                        },
                        deliveryState = saved.getString("deliveryState")?.let(MessageDeliveryState::valueOf),
                        networkBundleId = saved.getString("networkBundleId"),
                        peerName = saved.getString("peerName"),
                    )
                )
            }
        }
    }
)
