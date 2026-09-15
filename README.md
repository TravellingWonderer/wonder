# Wonder

An AI-native Android travel companion for **planning a trip and then living it** — on your own,
or with the people you're going with.

There is no dashboard, no tab bar and no menu. There is a conversation. You ask, out loud or in
writing, and Wonder answers — assembling whatever view of the trip the answer needs.

Built with **Kotlin**, **Jetpack Compose**, and **Material 3**.

## Two modes

A trip has two halves that want completely different things from an app, so Wonder has two
temperaments. The mode is picked up from the dates and switched with a single chip in the header.

### Planning

Before you go. Wonder thinks about the shape of the whole trip: which days are still empty, what
hasn't been booked yet, and what the plan will cost if you build it as written. Every price is an
estimate until you book something — at which point it becomes a real figure and lands in the
expense tracker automatically.

### Wandering

Once you're out there. Wonder thinks in hours rather than days: what's happening now, what's next
and how far away it is. It nudges you thirty minutes before anything starts and gives you a
briefing each morning. It reads the gaps in your day and offers things nearby that fit the time
you actually have, the budget you have left, and what your group said they were into. Planning
doesn't go away — you can still reshape any day by asking.

Wandering also unlocks the **expense tracker**, a dashboard for everything the trip has cost:
spend against budget, planned versus actual by category, the unplanned extras that crept in, and
whether you're heading for an overrun or coming in under.

## How it works

### One surface

The app opens straight into a conversation that already knows where you are in the trip. The only
permanent controls are the text field, the microphone, the mode chip, and a single icon for
choosing which model Wonder thinks with.

### Generative views

Wonder doesn't navigate you anywhere — it builds the view into its reply. Ask what's next and the
next two things assemble under the answer; ask about money and the budget appears. Figures always
come from your trip, never from the model.

Cards Wonder can compose: the trip at a glance, a day in detail, now-and-next, budget, the expense
log, recommendations nearby, a drafted day you can accept, and what's still unbooked.

### Minimal, but not shallow

The conversation stays uncluttered, but nothing is out of reach. Tap anything Wonder shows you and
the full plan opens at that day, where every item can be edited down to the detail: exact time,
duration, location, cost per-person or per-group, how settled it is, what was actually paid, the
booking reference, who's coming, and notes.

### Talk to it

Tap the microphone and the interface recedes to a single listening orb that swells with your
voice, transcribing as you speak. Spoken questions get spoken answers.

### Itineraries by conversation

"Plan a relaxed food day in Sintra" is the whole interface. Wonder reads the city, interests, pace,
budget and group size out of the sentence and drafts a day you can look over before it touches the
real trip.

### Works before you connect anything

Wonder has a built-in on-device engine that reads intent and answers from the trip directly, so the
app is fully conversational with no API key.

**Free on this phone** — in model settings, under *On this phone*:

- **Wonder** — built in, offline, no setup.
- **Gemma on-device** — run a Gemma `.litertlm` bundle locally via [Google AI Edge Gallery](https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery). Wonder does not download the model; it walks you through installing Gallery, fetching Gemma there, then attaching the same file here. An optional Hugging Face token is only needed for gated downloads.

**Cloud** — connect an **OpenAI**, **Gemini**, **Anthropic** or **Mistral** key and Wonder runs on
your subscription. Keys are stored encrypted on the device. If a key or the network fails
mid-conversation, Wonder falls back without breaking the thread.

## Getting started

### Prerequisites

- Android Studio Otter (2025.2) or newer (AGP 9 support)
- **JDK 17–26** for running Gradle
- Android SDK 36 / Build Tools 36.0.0

> **Note:** Gradle runs on your installed JDK. Android app bytecode still targets **JVM 17** —
> the maximum the Android runtime supports on device.

### Run the app

1. Open the project folder in Android Studio
2. Let Gradle sync complete
3. Run on an emulator or device (API 26+)

Or from the command line:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The app opens on a demo trip that is deliberately mid-flight — day three of eight in Lisbon — so
both modes have something real to show. Voice input needs `RECORD_AUDIO`, requested the first time
you tap the microphone. Schedule nudges need `POST_NOTIFICATIONS`, requested the first time you're
in Wandering mode.

## Project structure

```
app/src/main/java/com/wonder/provider/
├── MainActivity.kt              # Conversation, plus the three surfaces behind it
├── AppContainer.kt              # Manual dependency graph
├── ai/
│   ├── WonderAgent.kt           # Routes a turn to a model or to the on-device engine
│   ├── AgentContract.kt         # System prompt, trip briefing, card intents, reply parsing
│   ├── LocalConversationEngine.kt  # On-device intent reading, mode-aware answers
│   ├── LlmProviders.kt          # OpenAI, Gemini, Anthropic, Mistral
│   ├── LiteRtLmChatProvider.kt  # Gemma via LiteRT-LM (Edge Gallery bundle)
│   ├── OnDeviceModelStore.kt    # Model file validation & Gallery links
│   ├── AiTourService.kt         # Day drafting
│   └── AiSettingsRepository.kt  # Encrypted key storage
├── data/
│   ├── TripRepository.kt        # The trip, the money, the mode
│   ├── Recommendations.kt       # Suggestions from gaps, budget and taste
│   ├── CityCatalog.kt           # Points of interest per city
│   └── SampleTrip.kt            # The demo trip
├── notify/                      # Time-based schedule nudges (Wandering only)
├── voice/
│   ├── SpeechController.kt      # Listening exposed as state
│   └── VoiceSpeaker.kt          # Replies read aloud
├── model/                       # Trip, itinerary, expenses, conversation turns, card types
└── ui/
    ├── conversation/            # The app: orb, turns, cards, composer, voice overlay
    ├── plan/                    # The full itinerary and the detail editor
    ├── expenses/                # The expense dashboard
    ├── screens/                 # Model settings
    └── theme/                   # Palette, aurora gradients, typography
```

## Design philosophy

Interfaces made of buttons force you to know where things live. Wonder assumes you only know what
you want. The plan, the money and the day are all still there — they just arrive as part of an
answer instead of somewhere you have to go, and the detailed surfaces open from whatever Wonder
just showed you.

Replies unfold word by word and cards land a beat later, so the app reads as something thinking
rather than something loading. The palette is deep and atmospheric with a teal-to-indigo aurora
reserved for Wonder's own presence.

## Next steps

- Persistence, and more than one trip
- Splitting costs between travellers and settling up at the end
- Live location so recommendations know where you actually are, not just which city
- Token streaming from the provider APIs for true incremental replies
- Booking straight from the conversation rather than recording it afterwards
- Wake word and hands-free mode for use mid-walk

## License

MIT
