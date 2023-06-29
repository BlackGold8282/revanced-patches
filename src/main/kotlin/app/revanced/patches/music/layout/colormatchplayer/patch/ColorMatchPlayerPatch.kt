package app.revanced.patches.music.layout.colormatchplayer.patch

import app.revanced.extensions.toErrorResult
import app.revanced.patcher.annotation.Description
import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.method.impl.MethodFingerprint.Companion.resolve
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchResult
import app.revanced.patcher.patch.PatchResultSuccess
import app.revanced.patcher.patch.annotations.DependsOn
import app.revanced.patcher.patch.annotations.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.music.layout.colormatchplayer.fingerprints.ColorMatchPlayerFingerprint
import app.revanced.patches.music.utils.annotations.MusicCompatibility
import app.revanced.patches.music.utils.fingerprints.ColorMatchPlayerParentFingerprint
import app.revanced.patches.music.utils.settings.resource.patch.SettingsPatch
import app.revanced.util.enum.CategoryType
import app.revanced.util.integrations.Constants.MUSIC_LAYOUT
import org.jf.dexlib2.iface.instruction.Instruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction

@Patch
@Name("enable-color-match-player")
@Description("Matches the color of the mini player and the fullscreen player.")
@DependsOn([SettingsPatch::class])
@MusicCompatibility
@Version("0.0.1")
class ColorMatchPlayerPatch : BytecodePatch(
    listOf(ColorMatchPlayerParentFingerprint)
) {
    override fun execute(context: BytecodeContext): PatchResult {

        ColorMatchPlayerParentFingerprint.result?.let { parentResult ->
            ColorMatchPlayerFingerprint.also {
                it.resolve(
                    context,
                    parentResult.classDef
                )
            }.result?.let {
                it.mutableMethod.apply {
                    targetMethod = parentResult.mutableMethod

                    val insertIndex = it.scanResult.patternScanResult!!.startIndex + 1
                    val jumpInstruction = getInstruction<Instruction>(insertIndex)

                    val type = it.classDef.type

                    addInstructionsWithLabels(
                        insertIndex, """
                            invoke-static {}, $MUSIC_LAYOUT->enableColorMatchPlayer()Z
                            move-result v2
                            if-eqz v2, :off
                            iget v0, p0, ${descriptor(4)}
                            if-eq v0, v2, :abswitch
                            iput v2, p0, ${descriptor(4)}
                            iget-object v0, p0, ${descriptor(5)}
                            invoke-virtual {v0, v2, p2, p3}, ${descriptor(6)}
                            :abswitch
                            iget v0, p0, ${descriptor(10)}
                            if-eq v0, v1, :exit
                            iput v1, p0, ${descriptor(10)}
                            iget-object v0, p0, ${descriptor(11)}
                            invoke-virtual {v0, v1, p2, p3}, ${descriptor(12)}
                            goto :exit
                            :off
                            invoke-direct {p0}, ${type}->${parentResult.mutableMethod.name}()V
                            """, ExternalLabel("exit", jumpInstruction)
                    )
                    removeInstruction(insertIndex - 1)
                }
            } ?: return ColorMatchPlayerFingerprint.toErrorResult()
        } ?: return ColorMatchPlayerParentFingerprint.toErrorResult()

        SettingsPatch.addMusicPreference(
            CategoryType.LAYOUT,
            "revanced_enable_color_match_player",
            "true"
        )

        return PatchResultSuccess()
    }

    private companion object {
        private lateinit var targetMethod: MutableMethod

        fun descriptor(index: Int): String {
            return targetMethod.getInstruction<ReferenceInstruction>(index).reference.toString()
        }
    }
}