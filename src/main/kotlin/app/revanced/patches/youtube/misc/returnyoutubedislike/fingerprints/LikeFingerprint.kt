package app.revanced.patches.youtube.misc.returnyoutubedislike.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.method.impl.MethodFingerprint
import org.jf.dexlib2.AccessFlags

object LikeFingerprint : MethodFingerprint(
    returnType = "V",
    access = AccessFlags.PROTECTED or AccessFlags.CONSTRUCTOR,
    strings = listOf("like/like")
)