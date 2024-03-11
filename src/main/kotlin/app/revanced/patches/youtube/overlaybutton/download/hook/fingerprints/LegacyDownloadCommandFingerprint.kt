package app.revanced.patches.youtube.overlaybutton.download.hook.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * For spoofing to older versions.  Also called if download playlist is pressed for any version.
 */
internal object LegacyDownloadCommandFingerprint : MethodFingerprint(
    accessFlags = AccessFlags.PRIVATE or AccessFlags.FINAL,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "L", "L"),
    strings = listOf(""),
    opcodes = listOf(
        Opcode.MOVE_OBJECT_FROM16,
        null, // Opcode.MOVE_OBJECT_FROM16 or Opcode.IGET_OBJECT
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_DIRECT,
        Opcode.IGET_OBJECT,
        Opcode.IF_NEZ,
        Opcode.SGET_OBJECT,
    )
)