package com.ascend.app.domain

import com.ascend.app.data.local.ChecklistItemEntity
import com.ascend.app.data.local.ProfileEntity
import com.ascend.app.data.local.RoadmapPhaseEntity
import com.ascend.app.data.local.RoadmapTopicEntity
import com.ascend.app.data.local.TaskTemplateEntity
import com.ascend.app.domain.Category.*

private fun t(h: Int, m: Int) = h * 60 + m

object SeedData {

    val profile = ProfileEntity(
        name = "Ascender",
        heightCm = 173.0,
        startWeightKg = 63.0,
        careerGoal = "Applied AI/ML Engineer",
        education = "B.Tech CSE",
        collegeStartMin = t(8, 0),
        collegeEndMin = t(14, 0),
        commuteMinMinutes = 30,
        commuteMaxMinutes = 60,
        gymTime = "Evening",
        sleepStartMin = ScheduleRules.SLEEP_MIN,
        wakeMin = ScheduleRules.WAKE_MIN,
        careerHoursDaily = 4.0,
        gymDaysPerWeek = 5,
        proteinMinG = 100,
        proteinMaxG = 130,
        waterTargetMl = 3000,
        diet = "Primarily vegetarian, occasional non-veg",
    )

    /** ISO day-of-week (1 = Monday) to training split name. */
    val split = mapOf(
        1 to "Push", 2 to "Pull", 3 to "Legs + Abs", 4 to "Recovery",
        5 to "Upper", 6 to "Lower + Arms", 7 to "Rest",
    )

    private data class Block(val start: Int, val end: Int, val title: String, val cat: Category, val reminder: Boolean = false)

    private fun weekday(dow: Int): List<Block> {
        val gym = if (dow == 4) Block(t(17, 30), t(18, 30), "Active recovery & mobility", FITNESS, true)
        else Block(t(17, 30), t(18, 30), "Gym — ${split.getValue(dow)}", FITNESS, true)
        return listOf(
            Block(t(6, 30), t(6, 40), "Wake up & hydrate", HEALTH),
            Block(t(6, 40), t(7, 0), "Posture & mobility", POSTURE, true),
            Block(t(7, 0), t(7, 30), "Breakfast & grooming", GROOMING),
            Block(t(7, 30), t(8, 0), "Commute to college", COMMUTE),
            Block(t(8, 0), t(14, 0), "College", COLLEGE),
            Block(t(14, 0), t(14, 30), "Commute home", COMMUTE),
            Block(t(14, 30), t(15, 15), "Lunch & recovery", HEALTH),
            Block(t(15, 15), t(17, 15), "Career learning", CAREER, true),
            Block(t(17, 15), t(17, 30), "Pre-workout snack", HEALTH),
            gym,
            Block(t(18, 30), t(19, 15), "Recovery & dinner", HEALTH),
            Block(t(19, 15), t(20, 0), "Academics", ACADEMICS),
            Block(t(20, 0), t(22, 0), "Career project development", CAREER, true),
            Block(t(22, 0), t(22, 15), "Communication practice", COMMUNICATION),
            Block(t(22, 15), t(22, 30), "Night routine", GROOMING, true),
        )
    }

    private val saturday = listOf(
        Block(t(6, 30), t(6, 40), "Wake up & hydrate", HEALTH),
        Block(t(6, 40), t(7, 0), "Posture & mobility", POSTURE),
        Block(t(7, 0), t(7, 45), "Breakfast & grooming", GROOMING),
        Block(t(8, 0), t(10, 0), "Career learning", CAREER, true),
        Block(t(10, 15), t(11, 15), "Academic study", ACADEMICS),
        Block(t(11, 30), t(12, 30), "Communication practice", COMMUNICATION),
        Block(t(12, 30), t(13, 30), "Lunch & rest", HEALTH),
        Block(t(14, 0), t(16, 0), "Career projects", CAREER),
        Block(t(17, 30), t(18, 30), "Gym — Lower + Arms", FITNESS, true),
        Block(t(18, 30), t(19, 15), "Recovery & dinner", HEALTH),
        Block(t(19, 30), t(21, 30), "Social time", SOCIAL),
        Block(t(22, 15), t(22, 30), "Night routine", GROOMING, true),
    )

    private val sunday = listOf(
        Block(t(6, 30), t(6, 40), "Wake up & hydrate", HEALTH),
        Block(t(7, 0), t(7, 45), "Breakfast & grooming", GROOMING),
        Block(t(8, 0), t(10, 0), "Career revision", CAREER, true),
        Block(t(10, 30), t(11, 30), "Academic revision", ACADEMICS),
        Block(t(11, 30), t(12, 30), "Walking & mobility", POSTURE),
        Block(t(12, 30), t(13, 30), "Lunch with family", PERSONAL),
        Block(t(15, 0), t(17, 0), "Career planning & project review", CAREER),
        Block(t(17, 0), t(19, 0), "Family & personal time", PERSONAL),
        Block(t(19, 0), t(19, 45), "Dinner", HEALTH),
        Block(t(20, 0), t(20, 30), "Weekly review", PERSONAL, true),
        Block(t(22, 15), t(22, 30), "Night routine", GROOMING, true),
    )

    fun templates(): List<TaskTemplateEntity> = (1..7).flatMap { dow ->
        val blocks = when (dow) {
            6 -> saturday
            7 -> sunday
            else -> weekday(dow)
        }
        blocks.map {
            TaskTemplateEntity(
                dayOfWeek = dow, title = it.title, category = it.cat.name,
                startMin = it.start, endMin = it.end, reminder = it.reminder,
            )
        }
    }

    val phases = listOf(
        RoadmapPhaseEntity(1, "Foundations", "Jan – Mar", 1, 3, "Python, data tooling, the maths under ML, and classic ML."),
        RoadmapPhaseEntity(2, "Deep Learning", "Apr – Jun", 4, 6, "PyTorch from first principles through transformers, NLP and vision."),
        RoadmapPhaseEntity(3, "LLM Systems", "Jul – Sep", 7, 9, "LLMs, retrieval, agents, and shipping models behind real APIs."),
        RoadmapPhaseEntity(4, "Launch", "Oct – Dec", 10, 12, "Portfolio, GitHub, resume, interviews and applications."),
    )

    private val topicSpec: Map<Int, List<Pair<String, String>>> = mapOf(
        1 to listOf(
            "Python (idioms, OOP, typing)" to "Write a CLI tool with tests",
            "NumPy" to "Vectorise a loop-heavy script",
            "Pandas" to "Clean and analyse a messy public dataset",
            "Linear algebra" to "Implement PCA from scratch",
            "Calculus for ML" to "Derive gradients for logistic regression",
            "Probability" to "Simulate Bayes' rule problems",
            "Statistics" to "Run and interpret an A/B test",
            "SQL" to "Solve 30 SQL problems",
            "DSA" to "Solve 60 array/hash/tree problems",
            "Classic ML (scikit-learn)" to "End-to-end model with cross-validation",
            "Milestone: Foundations project" to "Publish an EDA + ML notebook on GitHub",
        ),
        2 to listOf(
            "PyTorch basics" to "Train an MLP on MNIST",
            "Neural networks" to "Build a 2-layer net in NumPy",
            "Backpropagation" to "Implement autograd for scalars",
            "Deep learning practice" to "Tune optimiser, init, regularisation",
            "CNNs" to "CIFAR-10 classifier above 85%",
            "Transformers" to "Code attention from scratch",
            "NLP" to "Fine-tune a text classifier",
            "Computer vision" to "Object detection demo",
            "Milestone: DL project" to "Ship a trained model with a write-up",
        ),
        3 to listOf(
            "LLM fundamentals" to "Compare prompting strategies on a task",
            "RAG" to "Build retrieval over your own notes",
            "Vector databases" to "Benchmark two vector stores",
            "Agents" to "Tool-using agent with evals",
            "FastAPI" to "Serve a model with FastAPI",
            "Docker" to "Containerise the API",
            "Deployment" to "Deploy publicly with monitoring",
            "Milestone: LLM app" to "Live RAG/agent app with README",
        ),
        4 to listOf(
            "Portfolio projects" to "3 polished projects pinned",
            "GitHub presence" to "Clean READMEs and contribution streak",
            "Resume" to "One-page resume reviewed twice",
            "Interview prep" to "10 mock interviews",
            "Applications" to "Track 40 applications",
            "Milestone: Offer-ready" to "Applied AI/ML Engineer applications sent",
        ),
    )

    fun topics(): List<RoadmapTopicEntity> = topicSpec.flatMap { (phase, list) ->
        list.mapIndexed { i, (title, check) ->
            RoadmapTopicEntity(
                phaseId = phase, title = title.removePrefix("Milestone: "), practicalCheck = check,
                isMilestone = title.startsWith("Milestone"), order = i,
            )
        }
    }

    /** name, muscle group, equipment */
    val exercises = listOf(
        Triple("Barbell bench press", "Chest", "Barbell"),
        Triple("Incline dumbbell press", "Chest", "Dumbbell"),
        Triple("Push-up", "Chest", "Bodyweight"),
        Triple("Cable fly", "Chest", "Cable"),
        Triple("Overhead press", "Shoulders", "Barbell"),
        Triple("Lateral raise", "Shoulders", "Dumbbell"),
        Triple("Face pull", "Shoulders", "Cable"),
        Triple("Triceps pushdown", "Triceps", "Cable"),
        Triple("Overhead triceps extension", "Triceps", "Dumbbell"),
        Triple("Pull-up", "Back", "Bodyweight"),
        Triple("Lat pulldown", "Back", "Cable"),
        Triple("Barbell row", "Back", "Barbell"),
        Triple("Seated cable row", "Back", "Cable"),
        Triple("Deadlift", "Back", "Barbell"),
        Triple("Barbell curl", "Biceps", "Barbell"),
        Triple("Hammer curl", "Biceps", "Dumbbell"),
        Triple("Back squat", "Legs", "Barbell"),
        Triple("Romanian deadlift", "Legs", "Barbell"),
        Triple("Leg press", "Legs", "Machine"),
        Triple("Walking lunge", "Legs", "Dumbbell"),
        Triple("Leg curl", "Legs", "Machine"),
        Triple("Standing calf raise", "Legs", "Machine"),
        Triple("Hanging leg raise", "Core", "Bodyweight"),
        Triple("Plank", "Core", "Bodyweight"),
        Triple("Cable crunch", "Core", "Cable"),
        Triple("Easy cardio", "Recovery", "Any"),
        Triple("Mobility flow", "Recovery", "Bodyweight"),
    )

    /** routine day -> list of (exercise name, sets, reps) */
    val routines: Map<Int, List<Triple<String, Int, String>>> = mapOf(
        1 to listOf(
            Triple("Barbell bench press", 4, "6-8"), Triple("Incline dumbbell press", 3, "8-10"),
            Triple("Overhead press", 3, "8-10"), Triple("Lateral raise", 3, "12-15"),
            Triple("Triceps pushdown", 3, "10-12"),
        ),
        2 to listOf(
            Triple("Pull-up", 4, "6-10"), Triple("Barbell row", 3, "8-10"), Triple("Seated cable row", 3, "10-12"),
            Triple("Face pull", 3, "15"), Triple("Barbell curl", 3, "10-12"),
        ),
        3 to listOf(
            Triple("Back squat", 4, "6-8"), Triple("Romanian deadlift", 3, "8-10"), Triple("Leg press", 3, "10-12"),
            Triple("Standing calf raise", 4, "12-15"), Triple("Hanging leg raise", 3, "12"), Triple("Plank", 3, "45s"),
        ),
        4 to listOf(Triple("Easy cardio", 1, "25 min"), Triple("Mobility flow", 1, "15 min")),
        5 to listOf(
            Triple("Incline dumbbell press", 3, "8-10"), Triple("Lat pulldown", 3, "10-12"),
            Triple("Overhead press", 3, "8-10"), Triple("Seated cable row", 3, "10-12"), Triple("Cable fly", 3, "12-15"),
        ),
        6 to listOf(
            Triple("Deadlift", 3, "5"), Triple("Walking lunge", 3, "10/leg"), Triple("Leg curl", 3, "10-12"),
            Triple("Hammer curl", 3, "10-12"), Triple("Overhead triceps extension", 3, "10-12"),
        ),
    )

    const val GROOMING_AM = "grooming_am"
    const val GROOMING_PM = "grooming_pm"
    const val POSTURE_LIST = "posture"
    const val HABITS = "habits"

    data class PostureMove(val title: String, val dose: String, val steps: String)

    val postureMoves = listOf(
        PostureMove("Chin tucks", "2 × 10, hold 3 s",
            "Sit or stand tall. Glide your head straight back as if making a double chin, keeping eyes level. Hold, then relax. Do not tilt the head down."),
        PostureMove("Wall slides", "2 × 10",
            "Stand with back, head and arms against a wall, elbows bent at 90°. Slowly slide arms up while keeping contact with the wall, then lower. Stop before pain."),
        PostureMove("Thoracic extensions", "2 × 8",
            "Lie with a foam roller (or rolled towel) under the upper back, hands supporting the head. Gently arch back over the roller, breathe out, return. Move the roller a few cm and repeat."),
        PostureMove("Doorway chest stretch", "3 × 30 s",
            "Place forearms on a door frame at shoulder height. Step one foot through until you feel a gentle stretch across the chest. Keep shoulders down, breathe slowly."),
        PostureMove("Hip flexor stretch", "2 × 30 s per side",
            "Half-kneel with one knee down. Tuck the pelvis slightly, squeeze the glute of the down leg and shift forward until the front of the hip stretches. Keep the torso upright."),
        PostureMove("Dead bugs", "2 × 8 per side",
            "Lie on your back, arms up, hips and knees at 90°. Press the lower back into the floor. Slowly extend the opposite arm and leg, return, switch. Keep the back flat."),
    )

    fun checklists(): List<ChecklistItemEntity> {
        val am = listOf("Gentle cleanser", "Moisturiser", "Sunscreen SPF 30+", "Brush & floss", "Hair styled", "Beard / shave check")
        val pm = listOf("Cleanse", "Treatment / serum", "Moisturiser", "Brush & floss", "Lay out tomorrow's outfit")
        val habits = listOf("8 glasses of water", "Read 20 minutes", "No phone in bed", "10-minute walk outside")
        return am.mapIndexed { i, s -> ChecklistItemEntity(listKey = GROOMING_AM, title = s, order = i) } +
            pm.mapIndexed { i, s -> ChecklistItemEntity(listKey = GROOMING_PM, title = s, order = i) } +
            postureMoves.mapIndexed { i, m -> ChecklistItemEntity(listKey = POSTURE_LIST, title = m.title, detail = m.dose, order = i) } +
            habits.mapIndexed { i, s -> ChecklistItemEntity(listKey = HABITS, title = s, order = i) }
    }

    val speakingPrompts = listOf(
        "Explain what a neural network is to a 12-year-old in 90 seconds.",
        "Describe your ideal workday in 2028.",
        "Argue for and against remote college classes.",
        "Teach one SQL concept you learned this week.",
        "Tell a 2-minute story about a time you failed and what changed.",
        "Pitch your current project in 60 seconds.",
        "Summarise an article you read today without notes.",
        "Describe a photo on your phone in vivid detail.",
        "Explain backpropagation using only an analogy.",
        "Give a 1-minute toast for a friend's success.",
    )

    val socialChallenges = listOf(
        "Start a conversation with a classmate you haven't spoken to.",
        "Ask a question in class or a lecture.",
        "Compliment someone sincerely on their work.",
        "Make a phone call instead of texting.",
        "Join a club, meetup or online community event.",
        "Ask a senior or professional for 10 minutes of career advice.",
        "Share something you built with a friend and ask for feedback.",
        "Greet the gym staff or a regular by name.",
        "Invite a friend for a walk or study session.",
        "Introduce two people who should know each other.",
        "Hold eye contact and smile with three strangers today.",
        "Speak up first in a group discussion.",
    )
}
