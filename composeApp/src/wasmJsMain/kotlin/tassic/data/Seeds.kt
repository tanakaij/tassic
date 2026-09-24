package tassic.data

/**
 * First-launch seed data. Everything here is inserted as ordinary editable
 * rows — users can rename, retarget, reorder or delete any preset and add
 * their own at any time.
 */
object Seeds {

    private fun ts(): Long = T.now()

    fun todos(): List<TodoItem> = listOf(
        TodoItem(
            id = 1, title = "Personalize Tassic", notes = "Edit or delete any preset - they are yours now.",
            priority = Priority.NORMAL, tags = listOf("setup"), createdAt = ts()
        ),
        TodoItem(
            id = 2, title = "Plan the week ahead", notes = "Review goals, routines and practice targets.",
            priority = Priority.HIGH, dueEpochDay = T.today(), tags = listOf("planning"), createdAt = ts()
        )
    )

    fun goals(): List<GoalItem> = listOf(
        GoalItem(
            id = 1, title = "Ship Tassic v1", description = "PWA live on GitHub Pages + signed TWA on the Play Console.",
            horizon = Horizon.SHORT, category = "Build", progress = 60,
            targetEpochDay = T.today() + 30, createdAt = ts()
        ),
        GoalItem(
            id = 2, title = "Land a GeoDev role", description = "Portfolio with 5 mapped projects + PostGIS fluency.",
            horizon = Horizon.MEDIUM, category = "Career", progress = 25,
            targetEpochDay = T.today() + 180, createdAt = ts()
        ),
        GoalItem(
            id = 3, title = "Record a gospel album", description = "12 tracks - keys, guitar, aux textures.",
            horizon = Horizon.LONG, category = "Music", progress = 10,
            targetEpochDay = T.today() + 365, createdAt = ts()
        )
    )

    fun practice(): List<PracticeItem> {
        val now = ts()
        val rows = mutableListOf<PracticeItem>()
        var id = 0L
        var order = 0

        fun nextId() = ++id

        // ---- CAGED weekly schedule (guitar) ----
        val caged = listOf(
            Triple("D Shape", "MON", "Daily CAGED focus - D shape"),
            Triple("C Shape", "TUE", "Daily CAGED focus - C shape"),
            Triple("A Shape", "WED", "Daily CAGED focus - A shape"),
            Triple("G Shape", "THU", "Daily CAGED focus - G shape"),
            Triple("E Shape", "FRI", "Daily CAGED focus - E shape")
        )
        val subtasks = listOf("Scales", "Arpeggios", "Diatonic Chords", "Triads", "Open Chords")
        caged.forEach { (title, day, detail) ->
            val shapeId = nextId()
            rows += PracticeItem(
                id = shapeId, section = "guitar", kind = PracticeKind.SHAPE, title = title,
                detail = detail, dayTag = day, sortOrder = order++, createdAt = now
            )
            subtasks.forEach { sub ->
                rows += PracticeItem(
                    id = nextId(), section = "guitar", kind = PracticeKind.SUBTASK, title = sub,
                    dayTag = day, parentId = shapeId, sortOrder = order++, createdAt = now
                )
            }
        }
        rows += PracticeItem(
            id = nextId(), section = "guitar", kind = PracticeKind.SHAPE,
            title = "Song Application & Style Study", detail = "Weekend consolidation - apply shapes to real music",
            dayTag = "WEEKEND", sortOrder = order++, createdAt = now
        )

        // ---- Style trackers ----
        listOf(
            "Fingerpicking Drills" to "Thumb-index patterns, 10 min",
            "Country Runs" to "Chicken pickin' + double stops",
            "Neo-Soul Chord Fills" to "Passing chords & embellishments"
        ).forEach { (title, detail) ->
            rows += PracticeItem(
                id = nextId(), section = "guitar", kind = PracticeKind.STYLE, title = title,
                detail = detail, dayTag = "ALL", sortOrder = order++, createdAt = now
            )
        }

        // ---- Keyboard: modes, 12 keys, modules ----
        listOf("Ionian", "Dorian", "Phrygian", "Lydian", "Mixolydian", "Aeolian", "Locrian").forEach { mode ->
            rows += PracticeItem(
                id = nextId(), section = "piano", kind = PracticeKind.MODE, title = mode,
                detail = "Daily mode rotation", sortOrder = order++, createdAt = now
            )
        }
        listOf("C", "G", "D", "A", "E", "B", "F#", "Db", "Ab", "Eb", "Bb", "F").forEachIndexed { i, key ->
            rows += PracticeItem(
                id = nextId(), section = "piano", kind = PracticeKind.KEY, title = key,
                detail = "Weekly 12-key cycle", sortOrder = i, createdAt = now
            )
        }
        listOf(
            "Left-Hand Independence" to "Walking bass + comping drills",
            "Aux Keys & Synth Textures" to "Pads, bells, sub layers",
            "Preacher Chords" to "Movement in all 12 keys",
            "Praise Breaks" to "Fast shout progressions",
            "Tritone Substitutions" to "bII7 for V7 everywhere",
            "Rootless Voicings" to "LH shells, RH extensions",
            "Extended Chords" to "9ths · 11ths · 13ths in context"
        ).forEach { (title, detail) ->
            rows += PracticeItem(
                id = nextId(), section = "piano", kind = PracticeKind.MODULE, title = title,
                detail = detail, sortOrder = order++, createdAt = now
            )
        }
        return rows
    }

    fun albums(): List<AlbumGoal> = listOf(
        AlbumGoal(id = 1, album = "Sunday Sound Vol. 1", artist = "Tassic Worship Collective", totalTracks = 12, learnedTracks = 3, createdAt = ts())
    )

    fun recovery(): List<RecoveryHabit> = listOf(
        RecoveryHabit(id = 1, name = "Doomscrolling", startEpochDay = T.today(), createdAt = ts())
    )

    fun workouts(): List<WorkoutItem> {
        val now = ts()
        return listOf(
            WorkoutItem(id = 1, name = "Push-ups", sets = 4, reps = 20, unit = "reps", sortOrder = 0, createdAt = now),
            WorkoutItem(id = 2, name = "Pike Push-ups", sets = 3, reps = 12, unit = "reps", sortOrder = 1, createdAt = now),
            WorkoutItem(id = 3, name = "Squats", sets = 4, reps = 30, unit = "reps", sortOrder = 2, createdAt = now),
            WorkoutItem(id = 4, name = "Lunges", sets = 3, reps = 16, unit = "reps / leg", sortOrder = 3, createdAt = now),
            WorkoutItem(id = 5, name = "Plank", sets = 3, reps = 60, unit = "seconds", sortOrder = 4, createdAt = now)
        )
    }

    fun career(): List<CareerItem> {
        val now = ts()
        val rows = mutableListOf<CareerItem>()
        var id = 0L

        fun stage(stage: String, stageOrder: Int, milestones: List<Pair<String, String>>) {
            milestones.forEachIndexed { i, (title, url) ->
                rows += CareerItem(
                    id = ++id, path = "GeoDev Roadmap", stage = stage, stageOrder = stageOrder,
                    title = title, url = url, sortOrder = i, createdAt = now
                )
            }
        }

        stage("Web Fundamentals", 1, listOf(
            "HTML & semantic structure" to "https://developer.mozilla.org/en-US/docs/Learn/HTML",
            "CSS layout - Flexbox & Grid" to "https://web.dev/learn/css/",
            "JavaScript essentials" to "https://javascript.info/",
            "Kotlin/Wasm & WebAssembly basics" to "https://kotlinlang.org/docs/wasm-overview.html"
        ))
        stage("Web Mapping", 2, listOf(
            "Leaflet.js quick start" to "https://leafletjs.com/examples/quick-start/",
            "OpenLayers workshop" to "https://openlayers.org/workshop/",
            "Mapbox GL JS fundamentals" to "https://docs.mapbox.com/mapbox-gl-js/guides/"
        ))
        stage("Python for Spatial Analysis", 3, listOf(
            "GeoPandas getting started" to "https://geopandas.org/en/stable/getting_started.html",
            "Shapely geometry operations" to "https://shapely.readthedocs.io/en/stable/",
            "PyQGIS developer cookbook" to "https://docs.qgis.org/latest/en/docs/pyqgis_developer_cookbook/",
            "GDAL/OGR command-line tools" to "https://gdal.org/programs/index.html"
        ))
        stage("Spatial Databases", 4, listOf(
            "PostgreSQL fundamentals" to "https://www.postgresql.org/docs/current/tutorial.html",
            "PostGIS spatial queries workshop" to "https://postgis.net/workshops/postgis-intro/",
            "Spatial SQL - joins, indexes, tuning" to "https://postgis.net/documentation/"
        ))
        stage("Cloud Remote Sensing", 5, listOf(
            "Google Earth Engine - JS API" to "https://developers.google.com/earth-engine/tutorials/tutorial_js_01",
            "GEE Python API with geemap" to "https://geemap.org/",
            "Earth Engine data catalogs" to "https://developers.google.com/earth-engine/datasets"
        ))
        return rows
    }

    fun routines(): List<FaithRoutine> {
        val now = ts()
        return listOf(
            FaithRoutine(id = 1, title = "Daily Bible Reading", cadence = "Daily", reminderHour = 7, reminderOn = true, createdAt = now),
            FaithRoutine(id = 2, title = "Thursday Fasting", cadence = "Weekly", dayTag = "THU", reminderHour = 6, reminderOn = true, createdAt = now),
            FaithRoutine(id = 3, title = "Praying Mountain Trip", cadence = "Monthly", reminderHour = 5, createdAt = now)
        )
    }

    fun prayers(): List<PrayerPoint> = listOf(
        PrayerPoint(id = 1, title = "Provision for the family", details = "Trust for daily bread and open doors.", category = "Family", createdAt = ts())
    )

    /**
     * Starter habits. Chosen to demonstrate each cadence the tracker supports —
     * a plain daily tick, a counted target, a weekday-only habit and a
     * "three times a week" one — so the shape of the feature is visible before
     * the user has entered anything of their own.
     */
    fun habits(): List<Habit> {
        val now = ts()
        return listOf(
            Habit(
                id = 1, name = "Read 20 minutes", icon = "book", color = "violet",
                cadence = "DAILY", targetPerDay = 1, timeOfDay = "EVENING",
                sortOrder = 0, createdAt = now
            ),
            Habit(
                id = 2, name = "Drink water", icon = "water", color = "blue",
                cadence = "DAILY", targetPerDay = 8, unit = "glasses",
                sortOrder = 1, createdAt = now
            ),
            Habit(
                id = 3, name = "Morning stretch", icon = "sun", color = "amber",
                cadence = "WEEKDAYS", targetPerDay = 1, timeOfDay = "MORNING",
                sortOrder = 2, createdAt = now
            ),
            Habit(
                id = 4, name = "Practice sight-reading", icon = "music", color = "green",
                cadence = "WEEKLY_COUNT", timesPerWeek = 3, targetPerDay = 1,
                sortOrder = 3, createdAt = now
            )
        )
    }

    /**
     * Starter growth areas.
     *
     * One per dimension would be overwhelming, so this seeds four — the ones
     * most people would actually name if asked what they'd like to be better
     * at — with the intention left blank on purpose. A pre-written intention
     * would be the app deciding who someone is trying to become, which is the
     * one thing here it has no business doing.
     */
    fun growth(): List<GrowthArea> {
        val now = ts()
        return listOf(
            GrowthArea(
                id = 1, name = "Patience under pressure", dimension = "CHARACTER",
                practices = listOf("Pause before answering when irritated", "Name the feeling before acting on it"),
                evidence = "Fewer sharp replies I'd take back.",
                sortOrder = 0, createdAt = now
            ),
            GrowthArea(
                id = 2, name = "Present with the people in front of me", dimension = "RELATIONSHIPS",
                practices = listOf("Phone face-down at meals", "Ask a second question before talking about myself"),
                evidence = "People finish their sentences with me.",
                sortOrder = 1, createdAt = now
            ),
            GrowthArea(
                id = 3, name = "Honest with myself", dimension = "SPIRIT",
                practices = listOf("Write the thing I'm avoiding", "Say the harder true version out loud"),
                evidence = "Less gap between what I know and what I admit.",
                sortOrder = 2, createdAt = now
            ),
            GrowthArea(
                id = 4, name = "Generous without being asked", dimension = "SERVICE",
                practices = listOf("Notice one need a month and meet it quietly"),
                evidence = "Giving that costs me something.",
                sortOrder = 3, createdAt = now
            )
        )
    }

    fun wishlist(): List<WishItem> {
        val now = ts()
        return listOf(
            WishItem(id = 1, name = "Audio interface (2-in)", category = "Music Gear", price = 149.0, priority = Priority.HIGH, createdAt = now),
            WishItem(id = 2, name = "Mechanical keyboard", category = "Electronics", price = 89.0, priority = Priority.NORMAL, createdAt = now),
            WishItem(id = 3, name = "QGIS/PostGIS course bundle", category = "Software License", price = 39.0, priority = Priority.LOW, url = "https://www.udemy.com/", createdAt = now)
        )
    }
}
