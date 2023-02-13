package app.revanced.patches.youtube.layout.general.autopopuppanels.bytecode.patch

import app.revanced.extensions.toErrorResult
import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchResult
import app.revanced.patcher.patch.PatchResultSuccess
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.annotation.YouTubeCompatibility
import app.revanced.patches.youtube.layout.general.autopopuppanels.bytecode.fingerprints.EngagementPanelControllerFingerprint
import app.revanced.util.integrations.Constants.GENERAL_LAYOUT

@Name("hide-auto-player-popup-panels-bytecode-patch")
@YouTubeCompatibility
@Version("0.0.1")
class PlayerPopupPanelsBytecodePatch : BytecodePatch(
    listOf(
        EngagementPanelControllerFingerprint
    )
) {
    override fun execute(context: BytecodeContext): PatchResult {

        EngagementPanelControllerFingerprint.result?.mutableMethod?.let {
            it.addInstructions(
                0, """
                    invoke-static {}, $GENERAL_LAYOUT->hideAutoPlayerPopupPanels()Z
                    move-result v0
                    if-eqz v0, :player_popup_panels_shown
                    if-eqz p4, :player_popup_panels_shown
                    const/4 v0, 0x0
                    return-object v0
                """, listOf(ExternalLabel("player_popup_panels_shown", it.instruction(0)))
            )
        } ?: return EngagementPanelControllerFingerprint.toErrorResult()

        return PatchResultSuccess()
    }
}
