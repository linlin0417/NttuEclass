package tw.edu.irika.nttueclass.testutil

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthResult
import tw.edu.irika.nttueclass.data.remote.billing.BillingManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseMaterialRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.IDataSyncCoordinator
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

fun createFakePrefs(sharedStore: MutableMap<String, Any?> = mutableMapOf()): SharedPreferences {
    lateinit var editorProxy: SharedPreferences.Editor

    val editorHandler = InvocationHandler { _, method, args ->
        when (method.name) {
            "putString" -> {
                sharedStore[args[0] as String] = args[1] as? String
                editorProxy
            }
            "putInt" -> {
                sharedStore[args[0] as String] = args[1] as Int
                editorProxy
            }
            "putLong" -> {
                sharedStore[args[0] as String] = args[1] as Long
                editorProxy
            }
            "putBoolean" -> {
                sharedStore[args[0] as String] = args[1] as Boolean
                editorProxy
            }
            "remove" -> {
                sharedStore.remove(args[0] as String)
                editorProxy
            }
            "clear" -> {
                sharedStore.clear()
                editorProxy
            }
            "apply", "commit" -> true
            else -> null
        }
    }

    editorProxy = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader,
        arrayOf(SharedPreferences.Editor::class.java),
        editorHandler
    ) as SharedPreferences.Editor

    val prefsHandler = InvocationHandler { _, method, args ->
        when (method.name) {
            "getString" -> sharedStore[args[0] as String] as? String ?: (args[1] as? String)
            "getInt" -> sharedStore[args[0] as String] as? Int ?: (args[1] as Int)
            "getLong" -> sharedStore[args[0] as String] as? Long ?: (args[1] as Long)
            "getBoolean" -> sharedStore[args[0] as String] as? Boolean ?: (args[1] as Boolean)
            "edit" -> editorProxy
            "contains" -> sharedStore.containsKey(args[0] as String)
            else -> null
        }
    }

    return Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
        prefsHandler
    ) as SharedPreferences
}

class FakeSecureCredentialStorage(
    val store: MutableMap<String, Any?> = mutableMapOf()
) : SecureCredentialStorage(createFakePrefs(store))

class FakeTimetableRepository : ITimetableRepository {
    val slotsFlow = MutableStateFlow<List<TimetableSlot>>(emptyList())
    var shouldThrowError = false
    var syncResult = true
    val addedSlots = mutableListOf<TimetableSlot>()
    val deletedSlots = mutableListOf<Pair<Int, Int>>()

    override fun getTimetableStream(): Flow<List<TimetableSlot>> {
        return if (shouldThrowError) {
            flow { throw IOException("Fake Timetable Error") }
        } else {
            slotsFlow
        }
    }

    override suspend fun syncTimetable(): Boolean {
        if (shouldThrowError) throw IOException("Timetable sync failed")
        return syncResult
    }

    override suspend fun addTimetableSlot(slot: TimetableSlot) {
        if (shouldThrowError) throw IOException("Failed to add slot")
        addedSlots.add(slot)
        slotsFlow.value = slotsFlow.value + slot
    }

    override suspend fun deleteTimetableSlot(dayOfWeek: Int, periodNumber: Int) {
        if (shouldThrowError) throw IOException("Failed to delete slot")
        deletedSlots.add(dayOfWeek to periodNumber)
        slotsFlow.value = slotsFlow.value.filterNot { it.dayOfWeek == dayOfWeek && it.periodNumber == periodNumber }
    }
}

class FakeCourseRepository : ICourseRepository {
    val coursesFlow = MutableStateFlow<List<Course>>(emptyList())
    var shouldThrowError = false
    var syncResult = true
    val addedCourses = mutableListOf<Course>()
    val deletedCourseIds = mutableListOf<String>()

    override fun getCoursesStream(): Flow<List<Course>> {
        return if (shouldThrowError) {
            flow { throw IOException("Fake Courses Error") }
        } else {
            coursesFlow
        }
    }

    override suspend fun syncCourses(): Boolean {
        if (shouldThrowError) throw IOException("Course sync failed")
        return syncResult
    }

    override suspend fun addCourse(course: Course) {
        if (shouldThrowError) throw IOException("Failed to add course")
        addedCourses.add(course)
        coursesFlow.value = coursesFlow.value + course
    }

    override suspend fun deleteCourse(courseId: String) {
        if (shouldThrowError) throw IOException("Failed to delete course")
        deletedCourseIds.add(courseId)
        coursesFlow.value = coursesFlow.value.filterNot { it.id == courseId }
    }

    override suspend fun crossEnrichCoursesAndTimetable() {}
    override suspend fun updateCourseCounters() {}
}

class FakeTaskRepository : ITaskRepository {
    val tasksFlow = MutableStateFlow<List<TaskItem>>(emptyList())
    var shouldThrowError = false
    var syncResult = true

    override fun getTasksStream(): Flow<List<TaskItem>> {
        return if (shouldThrowError) {
            flow { throw IOException("Fake Tasks Error") }
        } else {
            tasksFlow
        }
    }

    override suspend fun syncTasks(): Boolean {
        if (shouldThrowError) throw IOException("Tasks sync failed")
        return syncResult
    }

    override suspend fun syncCourseTasks(courseId: String, courseName: String): Result<List<TaskItem>> {
        if (shouldThrowError) return Result.failure(IOException("Failed to sync course tasks"))
        val matching = tasksFlow.value.filter { it.courseId == courseId || it.courseName == courseName }
        return Result.success(matching)
    }
}

class FakeAnnouncementRepository : IAnnouncementRepository {
    val announcementsFlow = MutableStateFlow<List<Announcement>>(emptyList())
    var shouldThrowError = false
    var syncResult = true

    override fun getAnnouncementsStream(): Flow<List<Announcement>> {
        return if (shouldThrowError) {
            flow { throw IOException("Fake Announcements Error") }
        } else {
            announcementsFlow
        }
    }

    override suspend fun syncAnnouncements(): Boolean {
        if (shouldThrowError) throw IOException("Announcements sync failed")
        return syncResult
    }

    override suspend fun syncCourseAnnouncements(courseId: String, courseName: String): Result<List<Announcement>> {
        if (shouldThrowError) return Result.failure(IOException("Failed to sync course announcements"))
        val matching = announcementsFlow.value.filter { it.courseId == courseId || it.courseName == courseName }
        return Result.success(matching)
    }
}

class FakeCourseMaterialRepository : ICourseMaterialRepository {
    val materialsFlow = MutableStateFlow<List<CourseMaterial>>(emptyList())
    var shouldThrowError = false
    val addedMaterials = mutableListOf<CourseMaterial>()
    val deletedMaterialIds = mutableListOf<String>()

    override fun getMaterialsStream(courseId: String): Flow<List<CourseMaterial>> {
        return if (shouldThrowError) {
            flow { throw IOException("Fake Materials Error") }
        } else {
            materialsFlow
        }
    }

    override suspend fun syncCourseMaterials(courseId: String): Result<List<CourseMaterial>> {
        if (shouldThrowError) return Result.failure(IOException("Material sync failed"))
        return Result.success(materialsFlow.value)
    }

    override suspend fun downloadMaterial(material: CourseMaterial, onProgress: (Float) -> Unit): Result<File> {
        if (shouldThrowError) return Result.failure(IOException("Download failed"))
        onProgress(0.5f)
        onProgress(1.0f)
        return Result.success(File("/fake/path/${material.title}"))
    }

    override suspend fun deleteDownloadedMaterial(material: CourseMaterial) {
    }

    override suspend fun addCustomMaterial(material: CourseMaterial) {
        if (shouldThrowError) throw IOException("Add custom material failed")
        addedMaterials.add(material)
        materialsFlow.value = materialsFlow.value + material
    }

    override suspend fun deleteMaterial(materialId: String) {
        if (shouldThrowError) throw IOException("Delete material failed")
        deletedMaterialIds.add(materialId)
        materialsFlow.value = materialsFlow.value.filterNot { it.id == materialId }
    }
}

class FakeDataSyncCoordinator : IDataSyncCoordinator {
    var syncResult: Result<Unit> = Result.success(Unit)
    var syncCalls = 0
    var clearCalls = 0

    override suspend fun syncAllData(): Result<Unit> {
        syncCalls++
        return syncResult
    }

    override suspend fun clearAllData() {
        clearCalls++
    }
}

class FakeBillingManager : BillingManager(context = null, storage = null) {
    val isConnectedFlow = MutableStateFlow(true)
    val donationDataFlow = MutableStateFlow(DonationData())
    val statusMessageFlow = MutableStateFlow("Google Play 商店已連線")
    var lastPurchasedProductId: String? = null
    var revalidateResult: Pair<Boolean, String> = true to "驗證完成"
    var endConnectionCalls = 0

    override val isConnected get() = isConnectedFlow.asStateFlow()
    override val donationData get() = donationDataFlow.asStateFlow()
    override val statusMessage get() = statusMessageFlow.asStateFlow()

    override fun launchPurchase(activity: Activity, productId: String, isSubscription: Boolean) {
        lastPurchasedProductId = productId
    }

    override fun revalidatePurchases(onResult: ((Boolean, String) -> Unit)?) {
        onResult?.invoke(revalidateResult.first, revalidateResult.second)
    }

    override fun endConnection() {
        endConnectionCalls++
    }
}

class FakeAuthManager(
    val fakeStorage: FakeSecureCredentialStorage = FakeSecureCredentialStorage(),
    val fakeLockout: AuthLockoutManager = AuthLockoutManager(createFakePrefs())
) : AuthManager(
    client = OkHttpClient(),
    cookieJar = NttuCookieJar(),
    lockoutManager = fakeLockout,
    secureStorage = fakeStorage
) {
    var loginResult: AuthResult = AuthResult.Success("11411188")
    var silentRefreshResult = true
    var logoutCalls = 0

    override suspend fun login(studentId: String, password: String): AuthResult {
        return loginResult
    }

    override fun logout() {
        logoutCalls++
        fakeStorage.clearCredentials()
    }

    override suspend fun silentRefreshSession(): Boolean {
        return silentRefreshResult
    }
}

open class FakeContext : android.content.ContextWrapper(null) {
    private val prefsStore = mutableMapOf<String, Any?>()
    override fun getApplicationContext(): android.content.Context = this
    override fun getPackageName(): String = "tw.edu.irika.nttueclass"
    override fun getSystemService(name: String): Any? = null
    override fun getExternalFilesDir(type: String?): File? = null
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = createFakePrefs(prefsStore)
}

open class FakeActivity : Activity()

open class FakeIntent(
    private var actionStr: String? = null
) : Intent() {
    private val stringExtras = mutableMapOf<String, String>()
    private val intExtras = mutableMapOf<String, Int>()

    override fun getAction(): String? = actionStr
    override fun setAction(action: String?): Intent {
        this.actionStr = action
        return this
    }

    override fun putExtra(name: String, value: String?): Intent {
        if (value != null) stringExtras[name] = value else stringExtras.remove(name)
        return this
    }

    override fun putExtra(name: String, value: Int): Intent {
        intExtras[name] = value
        return this
    }

    override fun getStringExtra(name: String): String? = stringExtras[name]

    override fun getIntExtra(name: String, defaultValue: Int): Int = intExtras[name] ?: defaultValue
}

open class FakeSessionManager(
    storage: SecureCredentialStorage = FakeSecureCredentialStorage(),
    var htmlToReturn: String = ""
) : tw.edu.irika.nttueclass.data.repository.SessionManager(
    client = OkHttpClient(),
    cookieJar = NttuCookieJar(),
    storage = storage,
    authManager = FakeAuthManager(storage as? FakeSecureCredentialStorage ?: FakeSecureCredentialStorage())
) {
    override suspend fun fetchHtmlWithAuth(url: String): String = htmlToReturn
}

