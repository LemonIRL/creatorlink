package org.limitlesssmp.creatorlink.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;

import java.util.List;

/**
 * Captures command feedback for CommandOutcome's handleFeedback/feedbackKeys:
 * after a command with feedback handling is sent, the next matching system
 * chat message (within ~1 second) is stored into a global variable.
 */
public final class FeedbackCapture {
    private static volatile String variableName;
    private static volatile List<String> keys;
    private static volatile long deadline;

    private FeedbackCapture() {}

    public static void arm(String varName, List<String> feedbackKeys) {
        variableName = varName;
        keys = feedbackKeys;
        deadline = System.currentTimeMillis() + 1000;
    }

    /** Called for every incoming game/system message; returns true if consumed. */
    public static boolean offer(Component message) {
        String varName = variableName;
        if (varName == null || varName.isEmpty() || System.currentTimeMillis() > deadline) return false;
        List<String> wanted = keys;
        if (wanted != null && !wanted.isEmpty()) {
            String key = message.getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
            if (key == null || !wanted.contains(key)) return false;
        }
        CreatorLinkClient.RUNTIME.setGlobal(varName, message.getString());
        EngineRuntime.LOGGER.info("Captured command feedback into ${}", varName);
        variableName = null;
        return true;
    }
}
