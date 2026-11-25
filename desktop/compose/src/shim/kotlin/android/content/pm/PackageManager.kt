@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content.pm

import android.content.ComponentName
import android.content.Intent

class PackageManager {
    class NameNotFoundException(msg: String? = null) : Exception(msg)
    fun getPackageInfo(pkg: String, flags: Int): PackageInfo = PackageInfo().also { if (pkg != "com.behnoud.spicylyrics") throw NameNotFoundException(pkg) }
    fun getPackageInfo(pkg: String, flags: PackageInfoFlags): PackageInfo = getPackageInfo(pkg, 0)
    fun getApplicationInfo(pkg: String, flags: Int): ApplicationInfo = ApplicationInfo()
    fun getApplicationLabel(info: ApplicationInfo): CharSequence = info.packageName
    fun getApplicationIcon(pkg: String): Any? = null
    fun getLaunchIntentForPackage(pkg: String): Intent? = null
    fun queryIntentActivities(intent: Intent, flags: Int): List<ResolveInfo> = emptyList()
    fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? = null
    fun setComponentEnabledSetting(c: ComponentName, state: Int, flags: Int) {}
    fun getComponentEnabledSetting(c: ComponentName): Int = COMPONENT_ENABLED_STATE_DEFAULT
    fun hasSystemFeature(name: String): Boolean = false
    fun getResourcesForApplication(pkg: String): android.content.res.Resources = android.content.res.Resources.getSystem()
    fun getResourcesForApplication(info: ApplicationInfo): android.content.res.Resources = android.content.res.Resources.getSystem()
    val canRequestPackageInstalls: Boolean get() = false
    fun canRequestPackageInstalls(): Boolean = false
    class PackageInfoFlags { companion object { fun of(v: Long) = PackageInfoFlags() } }
    companion object {
        internal val instance = PackageManager()
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
        const val GET_META_DATA = 128
        const val MATCH_DEFAULT_ONLY = 0x10000
        const val COMPONENT_ENABLED_STATE_DEFAULT = 0
        const val COMPONENT_ENABLED_STATE_ENABLED = 1
        const val COMPONENT_ENABLED_STATE_DISABLED = 2
        const val DONT_KILL_APP = 1
        const val FEATURE_LEANBACK = "android.software.leanback"
        const val FEATURE_TELEVISION = "android.hardware.type.television"
        const val FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture"
    }
}
open class PackageInfo { var versionName: String? = null; var longVersionCode: Long = 0; var packageName: String = ""; var firstInstallTime: Long = 0; var lastUpdateTime: Long = 0 }
open class ApplicationInfo { var packageName: String = ""; var flags: Int = 0; var enabled = true }
open class ResolveInfo { var activityInfo: ActivityInfo? = null }
open class ActivityInfo { var packageName: String = ""; var name: String = ""; companion object { const val SCREEN_ORIENTATION_UNSPECIFIED = -1; const val SCREEN_ORIENTATION_PORTRAIT = 1; const val SCREEN_ORIENTATION_LANDSCAPE = 0; const val SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6; const val SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7; const val SCREEN_ORIENTATION_USER = 2; const val SCREEN_ORIENTATION_FULL_SENSOR = 10 } }
class ActivityInfoOrientation
object ServiceInfo { const val FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2; const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000; const val FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION = 32 }
