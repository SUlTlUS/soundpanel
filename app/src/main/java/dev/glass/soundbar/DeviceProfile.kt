package dev.glass.soundbar

import android.os.Build
import java.util.Locale

/** Contracts verified against each device's SystemUI 17.99.02 APK. */
internal enum class DeviceProfile(
    val backgroundOwner: String?,
    val backgroundField: String,
    val visualBoundsMethod: String,
    val usesLegacyEdgeStroke: Boolean,
) {
    ACE_6(null, "mVolumeBackgroundBlurDrawable", "resolveVisualCapsuleInto", true),
    ONEPLUS_15("blurHostHelper", "panelBackground", "resolveVisualShapeInto", false);

    companion object {
        @Volatile var current: DeviceProfile? = null
            private set
        var detectionSummary: String = "not detected"
            private set

        fun detect(loader: ClassLoader): DeviceProfile? {
            val attempts = mutableListOf<String>()
            current = select(Build.MODEL) { profile ->
                runCatching {
                    val view = Class.forName("com.oplus.systemui.volume.view.OplusVolumeDialogView", false, loader)
                    val slider = Class.forName("com.oplus.systemui.volume.OplusVolumeSeekBar", false, loader)
                    val host = fieldType(slider, "mMaterialHost")
                    listOf("mDialogView", "mVolumeRecyclerview", "volumeListAdapter", "volumeInteractor", "mMoreRowStreamLl").forEach {
                        fieldType(view, it)
                    }
                    verifyContract(profile, view, host)
                    if (profile.usesLegacyEdgeStroke) {
                        Class.forName("com.oplus.systemui.volume.utils.material.OplusVolumeSettingsButtonMaterialHost", false, loader)
                    }
                }.fold(
                    onSuccess = { attempts += "$profile matched"; true },
                    onFailure = { attempts += "$profile rejected: ${it.javaClass.simpleName}: ${it.message}"; false }
                )
            }
            val source = if (current != null && current == forModel(Build.MODEL)) "model+contract" else "contract-search"
            detectionSummary = "model=${Build.MODEL} source=$source selected=$current; ${attempts.joinToString("; ")}"
            return current
        }

        /** Prefer the named device, then try the other verified contract. */
        fun select(model: String, matches: (DeviceProfile) -> Boolean): DeviceProfile? {
            val preferred = forModel(model)
            val candidates = (listOfNotNull(preferred) + listOf(ONEPLUS_15, ACE_6)).distinct()
            return candidates.firstOrNull(matches)
        }

        fun verifyContract(profile: DeviceProfile, view: Class<*>, host: Class<*>) {
            val owner = profile.backgroundOwner?.let { fieldType(view, it) } ?: view
            fieldType(owner, profile.backgroundField)
            host.getMethod(profile.visualBoundsMethod, android.graphics.Rect::class.java)
        }

        private fun fieldType(owner: Class<*>, name: String): Class<*> {
            var type: Class<*>? = owner
            while (type != null) {
                try { return type.getDeclaredField(name).type }
                catch (_: NoSuchFieldException) { type = type.superclass }
            }
            throw NoSuchFieldException("${owner.name}.$name")
        }

        fun forModel(model: String): DeviceProfile? = when (
            model.trim().lowercase(Locale.ROOT).replace(" ", "")
        ) {
            "plq110", "oneplusace6", "一加ace6" -> ACE_6
            "plk110", "oneplus15", "一加15" -> ONEPLUS_15
            else -> null
        }
    }
}
