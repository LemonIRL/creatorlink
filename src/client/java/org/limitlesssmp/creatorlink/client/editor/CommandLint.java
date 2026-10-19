package org.limitlesssmp.creatorlink.client.editor;

/**
 * Flags command NBT written for older Minecraft versions so imported CCI
 * configs that silently stopped working on 26.2 are visible in the editor.
 */
public final class CommandLint {
    private CommandLint() {}

    /** Why the command won't work on 26.2, or null if it looks fine. */
    public static String legacyReason(String command) {
        if (command == null) return null;
        if (command.contains("ArmorItems:") || command.contains("HandItems:")
                || command.contains("ArmorDropChances:") || command.contains("HandDropChances:")) {
            return "pre-1.21.5 equipment NBT — use equipment:{head:{...},mainhand:{...}} and drop_chances:{...}";
        }
        if (command.contains("Enchantments:[")) {
            return "pre-1.20.5 item NBT — use components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}";
        }
        if (command.contains("tag:{")) {
            return "pre-1.20.5 item tag:{} NBT — items use components:{...} now";
        }
        if (command.contains("CustomName:\"{") || command.contains("CustomName:'{")) {
            return "pre-1.21.5 CustomName JSON string — write CustomName:{text:\"...\"} directly";
        }
        return null;
    }
}
