package com.chmod777.itantra.demo

import com.chmod777.itantra.BuildConfig

/*
 * Film-demo model. Everything in com.chmod777.itantra.demo is demo-only: it is
 * compiled solely into the demoVachana / demoYash / demoVivek flavors and never
 * touches the real speech, identity or networking stacks.
 */

enum class DemoActor(val displayName: String) {
    VACHANA("Vachana"),
    YASH("Yash"),
    VIVEK("Vivek");

    companion object {
        /** Fixed per APK by the product flavor; there is no actor chooser. */
        val current: DemoActor = valueOf(BuildConfig.DEMO_ACTOR)
    }
}

data class DemoLanguage(val code: String, val englishName: String, val nativeName: String?) {
    val label: String get() = if (nativeName == null) englishName else "$englishName · $nativeName"
}

val DEMO_LANGUAGES = listOf(
    DemoLanguage("en", "English", null),
    DemoLanguage("hi", "Hindi", "हिन्दी"),
    DemoLanguage("gu", "Gujarati", "ગુજરાતી"),
    DemoLanguage("mr", "Marathi", "मराठी"),
    DemoLanguage("kn", "Kannada", "ಕನ್ನಡ"),
    DemoLanguage("ml", "Malayalam", "മലയാളം"),
    DemoLanguage("ta", "Tamil", "தமிழ்"),
    DemoLanguage("te", "Telugu", "తెలుగు"),
    DemoLanguage("or", "Odia", "ଓଡ଼ିଆ"),
    DemoLanguage("bn", "Bengali", "বাংলা"),
)

fun demoLanguage(code: String): DemoLanguage = DEMO_LANGUAGES.firstOrNull { it.code == code } ?: DEMO_LANGUAGES.first()

/** SOS_REPLY is a message inside an already-active SOS (responder ↔ requester). */
enum class MessageMode { NORMAL, URGENT, SOS, SOS_REPLY;
    val isSos: Boolean get() = this == SOS || this == SOS_REPLY
}

enum class Direction { INCOMING, OUTGOING }

enum class SosResponse { NONE, ACCEPTED, DECLINED }

enum class CaptureKind { PTT, HANDS_FREE }

data class DemoMessage(
    val id: String,
    val direction: Direction,
    /** Incoming: who sent it. Outgoing: who it was sent to (null for an SOS broadcast). */
    val peer: String?,
    val body: String,
    val mode: MessageMode,
    val timestamp: Long,
    val unread: Boolean,
    val audioUri: String? = null,
    val languageCode: String = "en",
    val sosResponse: SosResponse = SosResponse.NONE,
)

/** What the director console will inject next (also persisted as the form's last values). */
data class IncomingSpec(
    val sender: String,
    val body: String,
    val mode: MessageMode,
    val audioUri: String?,
    val delaySeconds: Int,
    val notify: Boolean,
)

data class ArmedEvent(val id: String, val fireAtMillis: Long, val spec: IncomingSpec)

data class IncomingPreset(val label: String, val sender: String, val body: String, val mode: MessageMode)

private const val MILK = "Hey, remember to bring milk on your way back."
private const val SOUTH_GATE = "I need help near the south gate."
private const val GOT_IT = "Yep, got it."
private const val COMING = "I'm coming. Stay where you are."

/** Per-role defaults. Everything here is editable at runtime from the director console. */
object RoleDefaults {

    fun trustedContacts(actor: DemoActor): List<String> = when (actor) {
        // Vachana never lists herself, and Vivek is the untrusted nearby responder.
        DemoActor.VACHANA -> listOf("Yash", "Trisha", "Utkarsh Keshri", "Vaishnavi M H")
        DemoActor.YASH -> listOf("Vachana", "Trisha", "Utkarsh Keshri", "Vaishnavi M H")
        DemoActor.VIVEK -> listOf("Trisha", "Utkarsh Keshri", "Vaishnavi M H")
    }

    fun presets(actor: DemoActor): List<IncomingPreset> = when (actor) {
        DemoActor.VACHANA -> listOf(
            IncomingPreset("Yash's reply", "Yash", GOT_IT, MessageMode.NORMAL),
            IncomingPreset("Vivek's SOS reply", "Vivek", COMING, MessageMode.SOS_REPLY),
        )
        DemoActor.YASH -> listOf(
            IncomingPreset("Vachana · milk", "Vachana", MILK, MessageMode.NORMAL),
        )
        DemoActor.VIVEK -> listOf(
            IncomingPreset("Vachana · SOS", "Vachana", SOUTH_GATE, MessageMode.SOS),
        )
    }

    fun initialIncoming(actor: DemoActor): IncomingSpec {
        val p = presets(actor).first()
        return IncomingSpec(p.sender, p.body, p.mode, audioUri = null, delaySeconds = 5, notify = true)
    }

    /** Default mock transcript for [actor], [kind] and language [code]. */
    fun transcript(actor: DemoActor, kind: CaptureKind, code: String): String {
        val line = when (actor) {
            DemoActor.VACHANA -> if (kind == CaptureKind.PTT) MILK_LINES else SOUTH_GATE_LINES
            DemoActor.YASH -> GOT_IT_LINES
            DemoActor.VIVEK -> COMING_LINES
        }
        return line[code] ?: line.getValue("en")
    }

    private val MILK_LINES = mapOf(
        "en" to MILK,
        "hi" to "अरे, वापस आते समय दूध लाना याद रखना।",
        "gu" to "અરે, પાછા આવતી વખતે દૂધ લાવવાનું યાદ રાખજે.",
        "mr" to "अरे, परत येताना दूध आणायला विसरू नकोस.",
        "kn" to "ಹೇ, ವಾಪಸ್ ಬರುವಾಗ ಹಾಲು ತರುವುದನ್ನು ಮರೆಯಬೇಡ.",
        "ml" to "ഹേയ്, തിരികെ വരുമ്പോൾ പാൽ വാങ്ങാൻ മറക്കരുത്.",
        "ta" to "ஏய், திரும்பி வரும்போது பால் வாங்கி வர மறக்காதே.",
        "te" to "హేయ్, తిరిగి వచ్చేటప్పుడు పాలు తీసుకురావడం మర్చిపోకు.",
        "or" to "ହେ, ଫେରିବା ବେଳେ କ୍ଷୀର ଆଣିବାକୁ ମନେ ରଖିବ।",
        "bn" to "এই, ফেরার পথে দুধ আনতে ভুলো না।",
    )

    private val SOUTH_GATE_LINES = mapOf(
        "en" to SOUTH_GATE,
        "hi" to "मुझे दक्षिण गेट के पास मदद चाहिए।",
        "gu" to "મને દક્ષિણ ગેટ પાસે મદદ જોઈએ છે.",
        "mr" to "मला दक्षिण गेटजवळ मदत हवी आहे.",
        "kn" to "ನನಗೆ ದಕ್ಷಿಣ ಗೇಟ್ ಬಳಿ ಸಹಾಯ ಬೇಕು.",
        "ml" to "എനിക്ക് തെക്കേ ഗേറ്റിനടുത്ത് സഹായം വേണം.",
        "ta" to "எனக்கு தெற்கு வாசல் அருகே உதவி தேவை.",
        "te" to "నాకు దక్షిణ గేటు దగ్గర సహాయం కావాలి.",
        "or" to "ମୋତେ ଦକ୍ଷିଣ ଗେଟ ପାଖରେ ସାହାଯ୍ୟ ଦରକାର।",
        "bn" to "আমার দক্ষিণ গেটের কাছে সাহায্য দরকার।",
    )

    private val GOT_IT_LINES = mapOf(
        "en" to GOT_IT,
        "hi" to "हाँ, समझ गया।",
        "gu" to "હા, સમજી ગયો.",
        "mr" to "हो, समजलं.",
        "kn" to "ಹೌದು, ಗೊತ್ತಾಯ್ತು.",
        "ml" to "ശരി, മനസ്സിലായി.",
        "ta" to "சரி, புரிஞ்சுது.",
        "te" to "సరే, అర్థమైంది.",
        "or" to "ହଁ, ବୁଝିଗଲି।",
        "bn" to "হ্যাঁ, বুঝেছি।",
    )

    private val COMING_LINES = mapOf(
        "en" to COMING,
        "hi" to "मैं आ रहा हूँ। जहाँ हो वहीं रहो।",
        "gu" to "હું આવું છું. જ્યાં છો ત્યાં જ રહો.",
        "mr" to "मी येतोय. आहेस तिथेच थांब.",
        "kn" to "ನಾನು ಬರುತ್ತಿದ್ದೇನೆ. ಇದ್ದಲ್ಲೇ ಇರಿ.",
        "ml" to "ഞാൻ വരുന്നു. നിങ്ങൾ അവിടെത്തന്നെ നിൽക്കൂ.",
        "ta" to "நான் வருகிறேன். இருக்கும் இடத்திலேயே இருங்கள்.",
        "te" to "నేను వస్తున్నాను. ఉన్న చోటే ఉండండి.",
        "or" to "ମୁଁ ଆସୁଛି। ଯେଉଁଠି ଅଛ ସେଇଠି ରୁହ।",
        "bn" to "আমি আসছি। যেখানে আছ সেখানেই থাকো।",
    )
}
