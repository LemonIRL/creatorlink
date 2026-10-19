package org.limitlesssmp.creatorlink.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.limitlesssmp.creatorlink.client.CreatorLinkClient;
import org.limitlesssmp.creatorlink.engine.ConfigIO;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.condition.Condition;
import org.limitlesssmp.creatorlink.engine.condition.MathConditions;
import org.limitlesssmp.creatorlink.engine.condition.StringConditions;
import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;
import org.limitlesssmp.creatorlink.engine.config.Configuration;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;
import org.limitlesssmp.creatorlink.engine.outcome.CoreOutcomes;
import org.limitlesssmp.creatorlink.engine.outcome.GameOutcomes;
import org.limitlesssmp.creatorlink.engine.outcome.Outcome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Guided setup that writes configs for you: channel point reward → mob spawn
 * (with gear pickers and no-drops), effect, command, or chat message — plus
 * the Twitch chat display mode. No JSON editing required.
 */
public final class SimpleSetupScreen extends Screen {
    private static final String REDEMPTION_FAMILY = "channel.channel_points_custom_reward_redemption";
    private static final List<String> WEAPONS = List.of("none",
            "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword",
            "iron_axe", "diamond_axe", "netherite_axe", "bow", "crossbow", "trident", "mace");
    private static final List<String> ARMOR_MATERIALS =
            List.of("none", "leather", "chainmail", "golden", "iron", "diamond", "netherite");
    private static final List<String> EFFECTS = List.of("speed", "slowness", "jump_boost", "regeneration",
            "poison", "wither", "blindness", "glowing", "levitation", "darkness", "nausea",
            "strength", "weakness", "instant_health", "instant_damage", "fire_resistance", "invisibility");
    // per-slot enchant menus; group != 0 = mutually exclusive, like the game
    private static final List<EnchantScreen.Ench> WEAPON_ENCH = List.of(
            new EnchantScreen.Ench("sharpness", 5, 1),
            new EnchantScreen.Ench("smite", 5, 1),
            new EnchantScreen.Ench("bane_of_arthropods", 5, 1),
            new EnchantScreen.Ench("knockback", 2, 0),
            new EnchantScreen.Ench("fire_aspect", 2, 0),
            new EnchantScreen.Ench("looting", 3, 0),
            new EnchantScreen.Ench("unbreaking", 3, 0));

    private static List<EnchantScreen.Ench> armorEnchOptions(int slot) {
        List<EnchantScreen.Ench> out = new ArrayList<>(List.of(
                new EnchantScreen.Ench("protection", 4, 1),
                new EnchantScreen.Ench("fire_protection", 4, 1),
                new EnchantScreen.Ench("blast_protection", 4, 1),
                new EnchantScreen.Ench("projectile_protection", 4, 1),
                new EnchantScreen.Ench("thorns", 3, 0),
                new EnchantScreen.Ench("unbreaking", 3, 0)));
        if (slot == 0) {
            out.add(new EnchantScreen.Ench("respiration", 3, 0));
            out.add(new EnchantScreen.Ench("aqua_affinity", 1, 0));
        }
        if (slot == 2) {
            out.add(new EnchantScreen.Ench("swift_sneak", 3, 0));
        }
        if (slot == 3) {
            out.add(new EnchantScreen.Ench("feather_falling", 4, 0));
            out.add(new EnchantScreen.Ench("depth_strider", 3, 2));
            out.add(new EnchantScreen.Ench("frost_walker", 2, 2));
            out.add(new EnchantScreen.Ench("soul_speed", 3, 0));
        }
        return out;
    }
    private static final List<String> ACTIONS = List.of("Spawn mob", "Potion effect", "Custom command", "Chat message");
    private static final List<String> CHAT_MODES = List.of("Toasts", "Chat messages", "Both", "Off");

    // One table drives both surfaces: § codes color chat lines, names color toasts.
    private static final List<String> COLOR_LABELS = List.of("§fWhite", "§7Gray", "§8Dark Gray", "§0Black",
            "§cRed", "§4Dark Red", "§6Gold", "§eYellow", "§aGreen", "§2Dark Green",
            "§bAqua", "§3Dark Aqua", "§9Blue", "§1Dark Blue", "§dPink", "§5Purple");
    private static final char[] COLOR_CODES = {'f', '7', '8', '0', 'c', '4', '6', 'e', 'a', '2', 'b', '3', '9', '1', 'd', '5'};
    private static final String[] COLOR_NAMES = {"white", "gray", "dark_gray", "black",
            "red", "dark_red", "gold", "yellow", "green", "dark_green",
            "aqua", "dark_aqua", "blue", "dark_blue", "light_purple", "dark_purple"};

    // Stream events (beyond channel points): where each one lives per source.
    // Indices >= YT_START are YouTube-only and route into chat.json (platform youtube).
    private static final String[] STREAM_EVENTS = {"Follow", "Raid", "Cheer (bits)", "Subscription", "Resub",
            "YouTube member", "YouTube likes"};
    private static final int YT_START = 5;
    private static final String[] YT_TYPES = {"newSponsorEvent", "likes"};
    private static final String[][] ES_ROUTE = {
            {"channel", "follow"}, {"channel.raid", "incoming"}, {"channel", "cheer"},
            {"channel", "subscribe"}, {"channel.subscription", "message"}, null, null};
    private static final String[] SE_TYPES = {"follow", "raid", "cheer", "subscriber", "subscriber", null, null};
    private static final String[] ES_COUNT_VAR = {null, "$viewers", "$bits", null, "$cumulative_months",
            null, "$newLikes"};
    private static final List<String> SOURCES = List.of("Twitch EventSub", "StreamElements");
    private static final String[] MULTIPLIER_LABEL = {null, "one per raider", "per X bits", null, "one per month",
            null, "one per like"};
    private static final String[] ARMOR_SLOTS = {"Helmet", "Chestplate", "Leggings", "Boots"};
    private static final String[] ARMOR_SLOT_NBT = {"head", "chest", "legs", "feet"};
    private static final String[] ARMOR_SLOT_SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};

    private final Screen parent;
    private final Path profileDir;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private SetupList list;

    // wizard state
    private String rewardTitle = "";
    private int action; // index into ACTIONS
    private String mob = "minecraft:zombie";
    private boolean spawnAsBaby;
    private String count = "1";
    private String weapon = "none";
    private final String[] armor = {"none", "none", "none", "none"};
    private boolean persistent;
    private final Map<String, Integer> weaponEnchants = new java.util.LinkedHashMap<>();
    private final List<Map<String, Integer>> armorEnchants = List.of(
            new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
            new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>());
    private boolean nameAfterRedeemer = true;
    private boolean disableDrops = true;
    private boolean announceInChat = true;
    private String effect = "regeneration";
    private String effectSeconds = "20";
    private String effectLevel = "1";
    private String customCommand = "";
    private String chatMessage = "[Twitch] $user_name redeemed $reward_title!";
    private String announceMessage = "[Twitch] $user_name redeemed $reward_title!";
    private int twitchChatMode = -1;   // lazily detected from chat.json
    private int youtubeChatMode = -1;
    private String twitchChatFormat = "§7[Twitch] §r$user: $message";
    private String youtubeChatFormat = "§7[YouTube] §r$authorDetails_displayName: $displayMessage";
    private int twitchNameColor;   // indices into COLOR_LABELS; 0 = white
    private int twitchMsgColor;
    private int youtubeNameColor;
    private int youtubeMsgColor;
    /** Index into the redemption "add" list of the reward being edited, or -1 when adding new. */
    private int editingIndex = -1;
    /** Which stream event (index into STREAM_EVENTS) the form edits, or -1 for reward mode. */
    private int streamEvent = -1;
    /** Reward-mode source: 0 = Twitch EventSub ($reward_title), 1 = StreamElements ($redemption). */
    private int rewardSource;
    private String editingFile = "twitcheventsub";
    /** When editing an imported (unmarked) event: its index + source list, so Save replaces it. */
    private int importedEventIndex = -1;
    private int importedEventSource = -1;
    private int eventSource; // index into SOURCES
    private boolean multiply;
    private String perBits = "100";
    private String maxSpawns = "";

    /**
     * simpleEditable == false: shown in the list but only editable in the full
     * editor. file is the config the event lives in — EventSub rewards in
     * twitcheventsub.json, CCI-imported StreamElements ones in streamelements.json.
     */
    private record RewardRef(int index, String title, ConfigEvent event, boolean simpleEditable, String file) {}

    private record StreamEventRef(int source, ConfigEvent event, int index) {}

    public SimpleSetupScreen(Screen parent) {
        super(Component.literal("CreatorLink Simple Setup"));
        this.parent = parent;
        this.profileDir = CreatorLinkClient.activeProfileDir();
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        if (twitchChatMode < 0) {
            twitchChatMode = detectChatMode("twitch", "message");
            youtubeChatMode = detectChatMode("youtube", "textMessageEvent");
        }
        list = addRenderableWidget(new SetupList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("Full editor"), b ->
                minecraft.gui.setScreen(new EditorScreen(parent, profileDir, "twitcheventsub"))).width(85).build());
        footer.addChild(Button.builder(Component.literal("API keys"), b ->
                minecraft.gui.setScreen(new SettingsScreen(this))).width(75).build());
        footer.addChild(Button.builder(Component.literal("Events"), b ->
                minecraft.gui.setScreen(new EventViewerScreen(this))).width(65).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(65).build());
        layout.visitWidgets(w -> {
            if (w != list) addRenderableWidget(w);
        });
        repositionElements();
        list.rebuild();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) list.updateSize(width, layout);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onFilesDrop(java.util.List<Path> paths) {
        CreatorLinkClient.importDropped(paths);
        if (list != null) list.rebuild();
    }

    // ---------------------------------------------------- reward list + edit

    /** All redemption events that match on $reward_title, in list order. */
    private List<RewardRef> loadRewards() {
        List<RewardRef> out = new ArrayList<>();
        List<ConfigEvent> add = redemptionList(loadOrCreate("twitcheventsub"), false);
        if (add != null) {
            for (int i = 0; i < add.size(); i++) {
                ConfigEvent e = add.get(i);
                if (e == null) continue;
                // events matching on reward id etc. list too, editable in the full editor
                out.add(new RewardRef(i, labelOf(e, i, "$reward_title"), e,
                        titleOf(e, "$reward_title") != null, "twitcheventsub"));
            }
        }
        // CCI's StreamElements redemptions (channelPointsRedemption, $redemption var)
        List<ConfigEvent> se = seRedemptionList(loadOrCreate("streamelements"));
        if (se != null) {
            for (int i = 0; i < se.size(); i++) {
                ConfigEvent e = se.get(i);
                if (e == null) continue;
                out.add(new RewardRef(i, labelOf(e, i, "$redemption"), e,
                        titleOf(e, "$redemption") != null, "streamelements"));
            }
        }
        return out;
    }

    private static String labelOf(ConfigEvent e, int index, String var) {
        String title = titleOf(e, var);
        if (title != null) return title;
        if (e.name != null && !e.name.isEmpty()) return e.name;
        if (e.conditions == null || e.conditions.isEmpty()) return "any redemption (no condition)";
        return "unnamed event " + (index + 1);
    }

    private static String rewardTitleOf(ConfigEvent e) {
        return titleOf(e, "$reward_title");
    }

    /** Equals or contains, either direction — CCI configs write this several ways. */
    private static String titleOf(ConfigEvent e, String var) {
        if (e == null || e.conditions == null) return null;
        for (Condition c : e.conditions) {
            if (!(c instanceof StringConditions.Equals) && !(c instanceof StringConditions.Contains)) continue;
            StringConditions.SourceTarget st = (StringConditions.SourceTarget) c;
            if (st.source != null && st.source.contains(var) && st.target != null) return st.target;
            if (st.target != null && st.target.contains(var) && st.source != null) return st.source;
        }
        return null;
    }

    private List<ConfigEvent> seRedemptionList(EventConfiguration cfg) {
        for (Configuration c : cfg.configs) {
            if (c.types != null && c.types.get("channelPointsRedemption") != null) {
                return c.types.get("channelPointsRedemption");
            }
        }
        return null;
    }

    private List<ConfigEvent> redemptionList(EventConfiguration cfg, boolean create) {
        for (Configuration c : cfg.configs) {
            if (REDEMPTION_FAMILY.equalsIgnoreCase(c.forPlatform)) {
                if (c.types == null) c.types = new java.util.LinkedHashMap<>();
                return create ? c.types.computeIfAbsent("add", k -> new ArrayList<>()) : c.types.get("add");
            }
        }
        if (!create) return null;
        Configuration c = new Configuration();
        c.forPlatform = REDEMPTION_FAMILY;
        cfg.configs.add(0, c);
        return c.types.computeIfAbsent("add", k -> new ArrayList<>());
    }

    /** One-line description shown in the reward list. */
    private static String summarize(ConfigEvent e) {
        Outcome main = firstMainOutcome(e);
        if (main instanceof CoreOutcomes.Repeat r) {
            return "×" + r.times + " " + summarizeOutcome(r.outcome);
        }
        return summarizeOutcome(main);
    }

    private static String summarizeOutcome(Outcome o) {
        if (o instanceof GameOutcomes.Command c && c.command != null) {
            String cmd = c.command;
            int summon = cmd.indexOf("summon ");
            if (summon >= 0) {
                String mob = cmd.substring(summon + 7).split(" ")[0];
                return "spawn " + (cmd.contains("IsBaby") ? "baby " : "") + mob.replace("minecraft:", "");
            }
            if (cmd.startsWith("/effect give")) {
                String[] parts = cmd.split(" ");
                return parts.length > 3 ? "effect " + parts[3].replace("minecraft:", "") : "effect";
            }
            return cmd.length() > 30 ? cmd.substring(0, 30) + "…" : cmd;
        }
        if (o instanceof GameOutcomes.Chat) return "chat message";
        return o == null ? "?" : o.getClass().getSimpleName();
    }

    private static Outcome firstMainOutcome(ConfigEvent e) {
        if (e.outcomes == null) return null;
        for (Outcome o : e.outcomes) {
            // chat announces and tally notes are bookkeeping; the real action is the spawn/command
            if (o instanceof GameOutcomes.Chat || o instanceof CoreOutcomes.Note) continue;
            return o;
        }
        return e.outcomes.isEmpty() ? null : e.outcomes.get(0);
    }

    /** Loads an existing reward back into the form (best effort; falls back to custom command). */
    private void loadIntoForm(RewardRef ref) {
        resetForm();
        editingIndex = ref.index();
        editingFile = ref.file();
        rewardSource = "streamelements".equals(ref.file()) ? 1 : 0;
        rewardTitle = ref.title();
        ConfigEvent e = ref.event();
        announceInChat = false;
        if (e.outcomes != null) {
            for (Outcome o : e.outcomes) {
                // the announce line only counts when it accompanies another outcome
                if (o instanceof GameOutcomes.Chat chat && e.outcomes.size() > 1) {
                    announceInChat = true;
                    if (chat.message != null) announceMessage = chat.message;
                }
            }
        }
        Outcome main = firstMainOutcome(e);
        if (main instanceof CoreOutcomes.Repeat r) {
            count = r.times == null ? "1" : r.times;
            main = r.outcome;
        }
        parseMainOutcome(main);
    }

    /** Fills the action fields from an outcome; falls back to Custom command. */
    private void parseMainOutcome(Outcome main) {
        if (main instanceof GameOutcomes.Chat chat) {
            action = 3;
            chatMessage = chat.message == null ? "" : chat.message;
            return;
        }
        if (!(main instanceof GameOutcomes.Command cmdOutcome) || cmdOutcome.command == null) {
            action = 2;
            return;
        }
        String cmd = cmdOutcome.command;
        try {
            if (cmd.contains("summon ")) {
                action = 0;
                String rest = cmd.substring(cmd.indexOf("summon ") + 7);
                mob = rest.split(" ")[0];
                spawnAsBaby = cmd.contains("IsBaby");
                persistent = cmd.contains("PersistenceRequired");
                nameAfterRedeemer = cmd.contains("CustomName");
                disableDrops = cmd.contains("DeathLootTable");
                weapon = "none";
                java.util.Arrays.fill(armor, "none");
                parseEnchants(slotSection(cmd, "mainhand"), weaponEnchants);
                for (int s = 0; s < 4; s++) {
                    parseEnchants(slotSection(cmd, ARMOR_SLOT_NBT[s]), armorEnchants.get(s));
                }
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(mainhand|head|chest|legs|feet):\\{id:\\\\?\"minecraft:([a-z_]+)\\\\?\"")
                        .matcher(cmd);
                while (m.find()) {
                    String slot = m.group(1);
                    String item = m.group(2);
                    if (slot.equals("mainhand")) {
                        weapon = WEAPONS.contains(item) ? item : "none";
                    } else {
                        for (int i = 0; i < 4; i++) {
                            if (item.endsWith(ARMOR_SLOT_SUFFIX[i])) {
                                String material = item.substring(0, item.length() - ARMOR_SLOT_SUFFIX[i].length());
                                if (ARMOR_MATERIALS.contains(material)) armor[i] = material;
                            }
                        }
                    }
                }
                return;
            }
            if (cmd.startsWith("/effect give")) {
                String[] parts = cmd.trim().split("\\s+");
                if (parts.length >= 4) {
                    action = 1;
                    effect = parts[3].replace("minecraft:", "");
                    effectSeconds = parts.length > 4 ? parts[4] : "20";
                    effectLevel = parts.length > 5 ? String.valueOf(intOr(parts[5], 0) + 1) : "1";
                    return;
                }
            }
        } catch (Exception ignored) {}
        action = 2;
        customCommand = cmd;
    }

    // --------------------------------------------- stream events (follows etc.)

    private String userVar() {
        if (streamEvent >= YT_START) return "$authorDetails_displayName";
        if (streamEvent >= 0) return eventSource == 1 ? "$user" : "$user_name";
        return rewardSource == 1 ? "$username" : "$user_name";
    }

    private String countVar() {
        if (streamEvent < 0) return null;
        if (streamEvent >= YT_START) return ES_COUNT_VAR[streamEvent];
        return eventSource == 1 ? "$amount" : ES_COUNT_VAR[streamEvent];
    }

    private static String markerName(int type) {
        return "Simple: " + STREAM_EVENTS[type];
    }

    /** Generic list access; family == null matches the catch-all Configuration (StreamElements). */
    private List<ConfigEvent> routeList(EventConfiguration cfg, String family, String type, boolean create) {
        Configuration target = null;
        for (Configuration c : cfg.configs) {
            boolean match = family == null ? c.forPlatform == null : family.equalsIgnoreCase(c.forPlatform);
            if (match) {
                target = c;
                break;
            }
        }
        if (target == null) {
            if (!create) return null;
            target = new Configuration();
            target.forPlatform = family;
            cfg.configs.add(0, target);
        }
        if (target.types == null) target.types = new java.util.LinkedHashMap<>();
        return create ? target.types.computeIfAbsent(type, k -> new ArrayList<>()) : target.types.get(type);
    }

    private StreamEventRef findStreamEvent(int type) {
        String marker = markerName(type);
        if (type >= YT_START) {
            List<ConfigEvent> yt = routeList(loadOrCreate("chat"), "youtube", YT_TYPES[type - YT_START], false);
            if (yt == null) return null;
            for (int i = 0; i < yt.size(); i++) {
                ConfigEvent e = yt.get(i);
                if (e != null && marker.equals(e.name)) return new StreamEventRef(2, e, i);
            }
            StreamEventRef ytFirst = null;
            for (int i = 0; i < yt.size(); i++) {
                ConfigEvent e = yt.get(i);
                if (e == null) continue;
                if (isActionEvent(e)) return new StreamEventRef(2, e, i);
                if (ytFirst == null) ytFirst = new StreamEventRef(2, e, i);
            }
            return ytFirst;
        }
        List<ConfigEvent> es = routeList(loadOrCreate("twitcheventsub"), ES_ROUTE[type][0], ES_ROUTE[type][1], false);
        if (es != null) {
            for (int i = 0; i < es.size(); i++) {
                ConfigEvent e = es.get(i);
                if (e != null && marker.equals(e.name)) return new StreamEventRef(0, e, i);
            }
        }
        List<ConfigEvent> se = routeList(loadOrCreate("streamelements"), null, SE_TYPES[type], false);
        if (se != null) {
            for (int i = 0; i < se.size(); i++) {
                ConfigEvent e = se.get(i);
                if (e != null && marker.equals(e.name)) return new StreamEventRef(1, e, i);
            }
        }
        // no marker event — fall back to an imported/hand-written event of this
        // type so CCI imports show up in Simple and get replaced on save.
        // Prefer the event that does something (spawns/commands) over chat-only.
        StreamEventRef first = null;
        StreamEventRef action = null;
        for (int s = 0; s < 2; s++) {
            List<ConfigEvent> list = s == 0 ? es : se;
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                ConfigEvent e = list.get(i);
                if (e == null) continue;
                if (first == null) first = new StreamEventRef(s, e, i);
                if (action == null && isActionEvent(e)) action = new StreamEventRef(s, e, i);
            }
        }
        return action != null ? action : first;
    }

    /** True when the event's main outcome is more than a chat message. */
    private static boolean isActionEvent(ConfigEvent e) {
        Outcome main = firstMainOutcome(e);
        return main != null && !(main instanceof GameOutcomes.Chat);
    }

    private void saveStreamEvent() {
        int type = streamEvent;
        ConfigEvent event = new ConfigEvent();
        event.name = markerName(type);
        event.conditions = new ArrayList<>();
        event.outcomes = new ArrayList<>();
        event.allowsOtherEventsToTrigger = true;

        Outcome main = switch (action) {
            case 1 -> command("/effect give $streamer minecraft:" + effect + " "
                    + intOr(effectSeconds, 20) + " " + Math.max(intOr(effectLevel, 1) - 1, 0));
            case 2 -> command(customCommand);
            case 3 -> chatOutcome(chatMessage);
            default -> spawnOutcome();
        };
        if (main == null) {
            toast("Fill in the action fields first");
            return;
        }
        String count = countVar();
        if (multiply && count != null && action != 3) {
            String times = count;
            if (type == 2) { // per X bits
                MathConditions.Arithmetic div = new MathConditions.Arithmetic();
                div.calc = "/";
                div.value1 = count;
                div.value2 = String.valueOf(Math.max(intOr(perBits, 100), 1));
                div.variableName = "cl_units";
                MathConditions.Arithmetic floor = new MathConditions.Arithmetic();
                floor.calc = "floor";
                floor.value1 = "$cl_units";
                floor.value2 = "0";
                floor.variableName = "cl_count";
                event.conditions.add(div);
                event.conditions.add(floor);
                times = "$cl_count";
            }
            int cap = intOr(maxSpawns, 0);
            if (cap > 0) {
                MathConditions.Arithmetic min = new MathConditions.Arithmetic();
                min.calc = "min";
                min.value1 = times;
                min.value2 = String.valueOf(cap);
                min.variableName = "cl_capped";
                event.conditions.add(min);
                times = "$cl_capped";
            }
            CoreOutcomes.Repeat repeat = new CoreOutcomes.Repeat();
            repeat.times = times;
            repeat.outcome = main;
            main = repeat;
        }
        event.outcomes.add(main);
        if (announceInChat && action != 3 && !announceMessage.isEmpty()) {
            event.outcomes.add(chatOutcome(announceMessage));
        }

        // write to the chosen source; remove our event from the other to avoid double triggers
        String marker = markerName(type);
        if (type >= YT_START) {
            EventConfiguration chat = loadOrCreate("chat");
            List<ConfigEvent> ytList = routeList(chat, "youtube", YT_TYPES[type - YT_START], true);
            if (importedEventIndex >= 0 && importedEventIndex < ytList.size()) {
                ConfigEvent old = ytList.get(importedEventIndex);
                if (old != null && !marker.equals(old.name)) ytList.remove(importedEventIndex);
            }
            ytList.removeIf(e -> e != null && marker.equals(e.name));
            ytList.add(0, event);
            write("chat", chat);
            toast(STREAM_EVENTS[type] + " saved");
            resetForm();
            list.rebuild();
            return;
        }
        EventConfiguration es = loadOrCreate("twitcheventsub");
        EventConfiguration se = loadOrCreate("streamelements");
        List<ConfigEvent> esList = routeList(es, ES_ROUTE[type][0], ES_ROUTE[type][1], true);
        List<ConfigEvent> seList = routeList(se, null, SE_TYPES[type], true);
        // editing started from an imported (unmarked) event — replace it, don't duplicate
        if (importedEventIndex >= 0) {
            List<ConfigEvent> owner = importedEventSource == 0 ? esList : seList;
            if (importedEventIndex < owner.size()) {
                ConfigEvent old = owner.get(importedEventIndex);
                if (old != null && !marker.equals(old.name)) owner.remove(importedEventIndex);
            }
        }
        esList.removeIf(e -> e != null && marker.equals(e.name));
        seList.removeIf(e -> e != null && marker.equals(e.name));
        (eventSource == 0 ? esList : seList).add(0, event);
        write("twitcheventsub", es);
        write("streamelements", se);
        toast(STREAM_EVENTS[type] + " saved via " + SOURCES.get(eventSource));
        warnIfSourceUnavailable(eventSource);
        resetForm();
        list.rebuild();
    }

    private void deleteStreamEvent(int type) {
        String marker = markerName(type);
        if (type >= YT_START) {
            EventConfiguration chat = loadOrCreate("chat");
            List<ConfigEvent> ytList = routeList(chat, "youtube", YT_TYPES[type - YT_START], false);
            if (ytList != null) ytList.removeIf(e -> e != null && marker.equals(e.name));
            write("chat", chat);
            toast(STREAM_EVENTS[type] + " removed");
            if (streamEvent == type) resetForm();
            list.rebuild();
            return;
        }
        EventConfiguration es = loadOrCreate("twitcheventsub");
        EventConfiguration se = loadOrCreate("streamelements");
        List<ConfigEvent> esList = routeList(es, ES_ROUTE[type][0], ES_ROUTE[type][1], false);
        List<ConfigEvent> seList = routeList(se, null, SE_TYPES[type], false);
        if (esList != null) esList.removeIf(e -> e != null && marker.equals(e.name));
        if (seList != null) seList.removeIf(e -> e != null && marker.equals(e.name));
        write("twitcheventsub", es);
        write("streamelements", se);
        toast(STREAM_EVENTS[type] + " removed");
        if (streamEvent == type) resetForm();
        list.rebuild();
    }

    private void loadStreamEventIntoForm(int type) {
        resetForm();
        streamEvent = type;
        StreamEventRef ref = findStreamEvent(type);
        if (ref == null) {
            announceMessage = defaultAnnounce(type);
            return;
        }
        eventSource = ref.source() == 2 ? 0 : ref.source();
        ConfigEvent e = ref.event();
        boolean isMarker = markerName(type).equals(e.name);
        importedEventIndex = isMarker ? -1 : ref.index();
        importedEventSource = isMarker ? -1 : ref.source();
        announceInChat = false;
        if (e.outcomes != null) {
            for (Outcome o : e.outcomes) {
                if (o instanceof GameOutcomes.Chat chat && e.outcomes.size() > 1) {
                    announceInChat = true;
                    if (chat.message != null) announceMessage = chat.message;
                }
            }
        }
        Outcome main = firstMainOutcome(e);
        if (main instanceof CoreOutcomes.Repeat r) {
            multiply = true;
            if (e.conditions != null) {
                for (Condition c : e.conditions) {
                    if (c instanceof MathConditions.Arithmetic math && math.value2 != null) {
                        if ("/".equals(math.calc)) perBits = math.value2;
                        if ("min".equals(math.calc)) maxSpawns = math.value2;
                    }
                }
            }
            main = r.outcome;
        }
        parseMainOutcome(main);
        if (announceMessage.isEmpty()) announceMessage = defaultAnnounce(type);
    }

    private String defaultAnnounce(int type) {
        String user = eventSource == 1 ? "$user" : "$user_name";
        return switch (type) {
            case 1 -> "[Twitch] " + user + " is raiding with their crew!";
            case 2 -> "[Twitch] " + user + " cheered " + (eventSource == 1 ? "$amount" : "$bits") + " bits!";
            case 3 -> "[Twitch] " + user + " just subscribed!";
            case 4 -> "[Twitch] " + user + " resubscribed for "
                    + (eventSource == 1 ? "$amount" : "$cumulative_months") + " months!";
            case 5 -> "[YouTube] $authorDetails_displayName just became a member!";
            case 6 -> "[YouTube] $newLikes new like(s) — $likes total!";
            default -> "[Twitch] " + user + " just followed!";
        };
    }

    private void resetForm() {
        editingIndex = -1;
        streamEvent = -1;
        eventSource = 0;
        rewardSource = 0;
        editingFile = "twitcheventsub";
        importedEventIndex = -1;
        importedEventSource = -1;
        multiply = false;
        perBits = "100";
        maxSpawns = "";
        rewardTitle = "";
        action = 0;
        mob = "minecraft:zombie";
        spawnAsBaby = false;
        count = "1";
        weapon = "none";
        java.util.Arrays.fill(armor, "none");
        persistent = false;
        weaponEnchants.clear();
        for (Map<String, Integer> m : armorEnchants) m.clear();
        nameAfterRedeemer = true;
        disableDrops = true;
        announceInChat = true;
        customCommand = "";
        chatMessage = "[Twitch] $user_name redeemed $reward_title!";
    }

    private void deleteReward(RewardRef ref) {
        boolean se = "streamelements".equals(ref.file());
        EventConfiguration cfg = loadOrCreate(ref.file());
        List<ConfigEvent> events = se ? seRedemptionList(cfg) : redemptionList(cfg, false);
        String var = se ? "$redemption" : "$reward_title";
        boolean stillThere = events != null && ref.index() < events.size()
                && ref.title().equals(labelOf(events.get(ref.index()), ref.index(), var));
        if (stillThere) {
            events.remove(ref.index());
            write(ref.file(), cfg);
            toast("Deleted reward '" + ref.title() + "'");
        }
        if (!se && editingIndex == ref.index()) resetForm();
        list.rebuild();
    }

    // ------------------------------------------------------------ writers

    /** Builds the reward event and inserts it at the top of the redemption list. */
    private void saveReward() {
        if (rewardTitle.trim().isEmpty()) {
            toast("Set the reward title first (must match Twitch exactly)");
            return;
        }
        String var = rewardSource == 1 ? "$redemption" : "$reward_title";
        ConfigEvent event = new ConfigEvent();
        event.name = rewardTitle.trim();
        StringConditions.Equals match = new StringConditions.Equals();
        match.source = var;
        match.target = rewardTitle.trim();
        event.conditions = new ArrayList<>(List.of(match));
        event.outcomes = new ArrayList<>();
        event.allowsOtherEventsToTrigger = false;

        Outcome main = switch (action) {
            case 1 -> command("/effect give $streamer minecraft:" + effect + " "
                    + intOr(effectSeconds, 20) + " " + Math.max(intOr(effectLevel, 1) - 1, 0));
            case 2 -> command(customCommand);
            case 3 -> chatOutcome(chatMessage);
            default -> spawnOutcome();
        };
        if (main == null) {
            toast("Fill in the action fields first");
            return;
        }
        event.outcomes.add(main);
        if (announceInChat && action != 3) {
            String fallbackMsg = rewardSource == 1
                    ? "[Twitch] $username redeemed $redemption!"
                    : "[Twitch] $user_name redeemed $reward_title!";
            event.outcomes.add(chatOutcome(announceMessage.isEmpty() ? fallbackMsg : announceMessage));
        }

        EventConfiguration es = loadOrCreate("twitcheventsub");
        EventConfiguration se = loadOrCreate("streamelements");
        List<ConfigEvent> esList = redemptionList(es, true);
        List<ConfigEvent> seList = routeList(se, null, "channelPointsRedemption", true);
        List<ConfigEvent> target = rewardSource == 1 ? seList : esList;
        List<ConfigEvent> other = rewardSource == 1 ? esList : seList;
        String otherVar = rewardSource == 1 ? "$reward_title" : "$redemption";
        String title = rewardTitle.trim();

        boolean sameFile = editingFile.equals(rewardSource == 1 ? "streamelements" : "twitcheventsub");
        if (editingIndex >= 0 && sameFile && editingIndex < target.size()) {
            target.set(editingIndex, event); // keep its position in the list
        } else {
            if (editingIndex >= 0 && !sameFile) {
                // source switched while editing — pull the old copy out of the other file
                List<ConfigEvent> old = "streamelements".equals(editingFile) ? seList : esList;
                if (editingIndex < old.size()) old.remove(editingIndex);
            }
            // same title = update in place, never duplicate
            int existing = -1;
            for (int i = 0; i < target.size(); i++) {
                if (title.equals(titleOf(target.get(i), var))) {
                    existing = i;
                    break;
                }
            }
            if (existing >= 0) {
                target.set(existing, event);
            } else {
                target.add(0, event); // top of the list so it wins over catch-alls
            }
        }
        // same title on the other source would double-fire — remove it there
        other.removeIf(e -> e != null && title.equals(titleOf(e, otherVar)));
        write("twitcheventsub", es);
        write("streamelements", se);
        toast("Reward '" + title + "' saved via " + SOURCES.get(rewardSource));
        warnIfSourceUnavailable(rewardSource);
        resetForm();
        list.rebuild();
    }

    /**
     * Saving a reward keyed to a source with no configured connection is the
     * silent killer: the event sits in the right file but nothing ever
     * dispatches it (e.g. an SE-only setup saving via EventSub). Say so.
     */
    private void warnIfSourceUnavailable(int source) {
        String need = source == 1 ? "StreamElements" : "TwitchEventSub";
        boolean have = CreatorLinkClient.sockets.sockets().stream().anyMatch(s -> s.name().equals(need));
        if (!have) {
            toast("⚠ No " + need + " connection is configured — this event can NEVER trigger. "
                    + "Add the token in Settings, or switch Source to the service you use.");
        }
    }

    private Outcome spawnOutcome() {
        StringBuilder nbt = new StringBuilder("{");
        // Miniatures: Hostile is per-entity (the config default never applies to summoned ones); owner = skin
        if (mob.startsWith("miniatures:")) nbt.append("Hostile:1,owner:\"").append(userVar()).append("\",");
        if (nameAfterRedeemer) nbt.append("CustomName:{text:\"").append(userVar()).append("\"},");
        // zombie-family mobs read IsBaby, ageable mobs read Age; each ignores the other tag
        if (spawnAsBaby) nbt.append("IsBaby:1b,Age:-2147483648,");
        if (persistent) nbt.append("PersistenceRequired:1b,");
        StringBuilder equipment = new StringBuilder();
        StringBuilder dropChances = new StringBuilder();
        if (!weapon.equals("none")) {
            equipment.append("mainhand:").append(itemNbt(weapon, weaponEnchants)).append(",");
            dropChances.append("mainhand:0.0f,");
        }
        for (int i = 0; i < 4; i++) {
            if (armor[i].equals("none")) continue;
            equipment.append(ARMOR_SLOT_NBT[i]).append(":")
                    .append(itemNbt(armor[i] + ARMOR_SLOT_SUFFIX[i], armorEnchants.get(i))).append(",");
            dropChances.append(ARMOR_SLOT_NBT[i]).append(":0.0f,");
        }
        if (equipment.length() > 0) {
            equipment.setLength(equipment.length() - 1);
            nbt.append("equipment:{").append(equipment).append("},");
        }
        if (disableDrops) {
            if (dropChances.length() > 0) {
                dropChances.setLength(dropChances.length() - 1);
                nbt.append("drop_chances:{").append(dropChances).append("},");
            }
            nbt.append("DeathLootTable:\"\",");
        }
        if (nbt.length() > 1) nbt.setLength(nbt.length() - 1);
        nbt.append("}");
        String cmd = "/execute at $streamer run summon " + mob + " ~2 ~ ~"
                + (nbt.length() > 2 ? " " + nbt : "");
        Outcome spawn = command(cmd);
        int n = intOr(count, 1);
        if (n <= 1) return spawn;
        CoreOutcomes.Repeat repeat = new CoreOutcomes.Repeat();
        repeat.times = String.valueOf(n);
        repeat.outcome = spawn;
        return repeat;
    }

    /** Item NBT for an equipment slot; enchants use the flattened component format (1.21.5+). */
    private static String itemNbt(String id, Map<String, Integer> enchants) {
        StringBuilder sb = new StringBuilder("{id:\"minecraft:").append(id).append("\",count:1");
        if (!enchants.isEmpty()) {
            sb.append(",components:{\"minecraft:enchantments\":{");
            boolean first = true;
            for (Map.Entry<String, Integer> en : enchants.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"minecraft:").append(en.getKey()).append("\":").append(Math.max(en.getValue(), 1));
                first = false;
            }
            sb.append("}}");
        }
        return sb.append("}").toString();
    }

    /** The {...} blob after "slot:{", brace-matched (items nest a components object). */
    private static String slotSection(String cmd, String slot) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(slot + ":\\{").matcher(cmd);
        if (!m.find()) return "";
        int start = m.end() - 1;
        int depth = 0;
        for (int i = start; i < cmd.length(); i++) {
            char ch = cmd.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return cmd.substring(start, i + 1);
        }
        return cmd.substring(start);
    }

    /** All "minecraft:<ench>":<level> pairs within one item's section. */
    private static void parseEnchants(String section, Map<String, Integer> into) {
        into.clear();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\\\?\"minecraft:([a-z_]+)\\\\?\":(\\d+)").matcher(section);
        while (m.find()) into.put(m.group(1), Integer.parseInt(m.group(2)));
    }

    private static GameOutcomes.Command command(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return null;
        GameOutcomes.Command o = new GameOutcomes.Command();
        o.command = cmd.trim().startsWith("/") ? cmd.trim() : "/" + cmd.trim();
        o.executeAsSelf = true;
        return o;
    }

    private static GameOutcomes.Chat chatOutcome(String message) {
        GameOutcomes.Chat o = new GameOutcomes.Chat();
        o.message = message;
        return o;
    }

    /** Rewrites the no-condition display events for both platforms in chat.json. */
    private void saveChatMode() {
        EventConfiguration cfg = loadOrCreate("chat");
        applyChatDisplay(cfg, "twitch", "message", twitchChatMode, "$user", "$message", twitchChatFormat,
                COLOR_NAMES[twitchNameColor], COLOR_NAMES[twitchMsgColor]);
        applyChatDisplay(cfg, "youtube", "textMessageEvent", youtubeChatMode,
                "$authorDetails_displayName", "$displayMessage", youtubeChatFormat,
                COLOR_NAMES[youtubeNameColor], COLOR_NAMES[youtubeMsgColor]);
        write("chat", cfg);
        toast("Chat display — Twitch: " + CHAT_MODES.get(twitchChatMode)
                + ", YouTube: " + CHAT_MODES.get(youtubeChatMode));
    }

    private void applyChatDisplay(EventConfiguration cfg, String platform, String type, int mode,
                                  String userVar, String messageVar, String format,
                                  String nameColor, String messageColor) {
        Configuration target = null;
        for (Configuration c : cfg.configs) {
            if (platform.equalsIgnoreCase(c.forPlatform)) {
                target = c;
                break;
            }
        }
        if (target == null) {
            target = new Configuration();
            target.forPlatform = platform;
            cfg.configs.add(target);
        }
        if (target.types == null) target.types = new java.util.LinkedHashMap<>();
        List<ConfigEvent> messages = target.types.computeIfAbsent(type, k -> new ArrayList<>());
        // only the condition-less display event is ours to manage; custom events stay
        messages.removeIf(e -> e != null && (e.conditions == null || e.conditions.isEmpty()));

        if (mode != 3) {
            ConfigEvent display = new ConfigEvent();
            display.name = "Chat Display";
            display.conditions = new ArrayList<>();
            display.outcomes = new ArrayList<>();
            display.allowsOtherEventsToTrigger = true;
            if (mode == 0 || mode == 2) {
                GameOutcomes.Toast t = new GameOutcomes.Toast();
                t.title = userVar;
                t.subtitle = messageVar;
                t.titleColor = nameColor;
                t.subtitleColor = messageColor;
                display.outcomes.add(t);
            }
            if (mode == 1 || mode == 2) {
                display.outcomes.add(chatOutcome(format == null || format.isEmpty()
                        ? userVar + ": " + messageVar : format));
            }
            messages.add(0, display);
        }
    }

    /** Sets the §-code right before {@code var} in the format, replacing any codes already there. */
    private static String recolor(String format, String var, char code) {
        int i = format == null ? -1 : format.indexOf(var);
        if (i < 0) return format;
        int start = i;
        while (start >= 2 && format.charAt(start - 2) == '§') start -= 2;
        return format.substring(0, start) + "§" + code + format.substring(i);
    }

    /** Index into COLOR_CODES of the color immediately before {@code var}, or -1. */
    private static int colorIndexBefore(String text, String var) {
        int i = text == null ? -1 : text.indexOf(var);
        if (i < 0) return -1;
        int p = i - 2;
        while (p >= 0 && text.charAt(p) == '§') {
            char c = Character.toLowerCase(text.charAt(p + 1));
            for (int k = 0; k < COLOR_CODES.length; k++) {
                if (COLOR_CODES[k] == c) return k;
            }
            p -= 2;
        }
        return -1;
    }

    private static int colorIndexOfName(String name) {
        if (name == null) return -1;
        for (int k = 0; k < COLOR_NAMES.length; k++) {
            if (COLOR_NAMES[k].equalsIgnoreCase(name)) return k;
        }
        return -1;
    }

    private void setDetectedColors(String platform, int nameIdx, int msgIdx) {
        if (platform.equals("twitch")) {
            if (nameIdx >= 0) twitchNameColor = nameIdx;
            if (msgIdx >= 0) twitchMsgColor = msgIdx;
        } else {
            if (nameIdx >= 0) youtubeNameColor = nameIdx;
            if (msgIdx >= 0) youtubeMsgColor = msgIdx;
        }
    }

    /** Reads chat.json to show a platform's current display mode. */
    private int detectChatMode(String platform, String type) {
        EventConfiguration cfg = ConfigIO.loadEventConfiguration(profileDir.resolve("chat.json"));
        if (cfg == null || cfg.configs == null) return 0;
        for (Configuration c : cfg.configs) {
            if (!platform.equalsIgnoreCase(c.forPlatform) || c.types == null) continue;
            List<ConfigEvent> messages = c.types.get(type);
            if (messages == null) return 3;
            for (ConfigEvent e : messages) {
                if (e == null || e.conditions != null && !e.conditions.isEmpty() || e.outcomes == null) continue;
                boolean hasToast = false;
                boolean hasChat = false;
                String userVar = platform.equals("twitch") ? "$user" : "$authorDetails_displayName";
                String msgVar = platform.equals("twitch") ? "$message" : "$displayMessage";
                for (Outcome o : e.outcomes) {
                    if (o instanceof GameOutcomes.Toast t) {
                        hasToast = true;
                        setDetectedColors(platform, colorIndexOfName(t.titleColor), colorIndexOfName(t.subtitleColor));
                    }
                    if (o instanceof GameOutcomes.Chat chat) {
                        hasChat = true;
                        // remember the existing format so editing doesn't reset it
                        if (chat.message != null && !chat.message.isEmpty()) {
                            if (platform.equals("twitch")) twitchChatFormat = chat.message;
                            else youtubeChatFormat = chat.message;
                            setDetectedColors(platform, colorIndexBefore(chat.message, userVar),
                                    colorIndexBefore(chat.message, msgVar));
                        }
                    }
                }
                if (hasToast && hasChat) return 2;
                if (hasToast) return 0;
                if (hasChat) return 1;
            }
            return 3;
        }
        return 0;
    }

    private EventConfiguration loadOrCreate(String name) {
        EventConfiguration cfg = Files.exists(profileDir.resolve(name + ".json"))
                ? ConfigIO.loadEventConfiguration(profileDir.resolve(name + ".json")) : null;
        if (cfg == null) {
            cfg = new EventConfiguration();
            cfg.type = name;
        }
        if (cfg.configs == null) cfg.configs = new ArrayList<>();
        return cfg;
    }

    private void write(String name, EventConfiguration cfg) {
        try {
            Files.createDirectories(profileDir);
            Files.writeString(profileDir.resolve(name + ".json"), ConfigIO.GSON.toJson(cfg));
            CreatorLinkClient.reloadActiveProfile();
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Simple setup write failed", e);
            toast("Save FAILED: " + e.getMessage());
        }
    }

    private void toast(String message) {
        CreatorLinkClient.RUNTIME.bridge().toast(3, "CreatorLink", message, null, null);
    }

    private static int intOr(String s, int fallback) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<String> mobIds() {
        List<String> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (type.getCategory() == MobCategory.MISC && !id.startsWith("miniatures:")) continue;
            out.add(id);
        }
        out.sort(String::compareTo);
        return out;
    }

    // ------------------------------------------------------------------ UI

    private class SetupList extends ContainerObjectSelectionList<SetupList.Row> {
        SetupList(Minecraft mc) {
            super(mc, SimpleSetupScreen.this.width, SimpleSetupScreen.this.layout.getContentHeight(),
                    SimpleSetupScreen.this.layout.getHeaderHeight(), 24);
        }

        void rebuild() {
            double scroll = scrollAmount();
            clearEntries();

            List<RewardRef> rewards = loadRewards();
            addEntry(new Row("§6— Your rewards (" + rewards.size() + ") —", List.of()));
            if (rewards.isEmpty()) {
                addEntry(new Row("§7none yet — add one below", List.of()));
            }
            for (RewardRef ref : rewards) {
                boolean seRow = "streamelements".equals(ref.file());
                Button edit = ref.simpleEditable()
                        ? Button.builder(Component.literal("Edit"), b -> {
                            loadIntoForm(ref);
                            rebuild();
                        }).bounds(0, 0, 45, 20).build()
                        : Button.builder(Component.literal("Editor"), b ->
                            minecraft.gui.setScreen(new EditorScreen(SimpleSetupScreen.this, profileDir, ref.file(),
                                    seRow ? null : REDEMPTION_FAMILY,
                                    seRow ? "channelPointsRedemption" : "add", ref.index())))
                            .bounds(0, 0, 45, 20).build();
                Button delete = Button.builder(Component.literal("§cDel"), b -> deleteReward(ref))
                        .bounds(0, 0, 40, 20).build();
                String marker = ref.file().equals(editingFile) && editingIndex == ref.index() ? "§e✎ " : "";
                String note = (seRow ? " §b[SE]" : "") + (ref.simpleEditable() ? "" : " §8(advanced)");
                addEntry(new Row(marker + ref.title() + note + " §8→ " + summarize(ref.event()),
                        List.of(edit, delete), true));
            }

            addEntry(new Row("§6— Stream events —", List.of()));
            for (int i = 0; i < STREAM_EVENTS.length; i++) {
                int type = i;
                StreamEventRef ref = findStreamEvent(type);
                String label = (streamEvent == type ? "§e✎ " : "") + STREAM_EVENTS[type]
                        + (ref != null
                        ? " §8[" + (ref.source() == 2 ? "YouTube" : SOURCES.get(ref.source()))
                        + "] → " + summarize(ref.event())
                        : " §8not set");
                List<GuiEventListener> buttons = new ArrayList<>();
                buttons.add(Button.builder(Component.literal(ref != null ? "Edit" : "Set up"), b -> {
                    loadStreamEventIntoForm(type);
                    rebuild();
                }).bounds(0, 0, 55, 20).build());
                if (ref != null) {
                    buttons.add(Button.builder(Component.literal("§cDel"), b -> deleteStreamEvent(type))
                            .bounds(0, 0, 40, 20).build());
                }
                addEntry(new Row(label, buttons, true));
            }

            String formHeader = streamEvent >= 0
                    ? "§6— Editing: " + STREAM_EVENTS[streamEvent] + " —"
                    : editingIndex >= 0 ? "§6— Editing: " + rewardTitle + " —" : "§6— Add a reward —";
            addEntry(new Row(formHeader, List.of()));
            if (editingIndex >= 0 || streamEvent >= 0) {
                addEntry(new Row("", List.of(Button.builder(Component.literal("Cancel / back to new reward"), b -> {
                    resetForm();
                    rebuild();
                }).bounds(0, 0, 170, 20).build())));
            }
            if (streamEvent >= 0) {
                if (streamEvent < YT_START) {
                    addEntry(new Row("Source", List.of(cycle(SOURCES, eventSource, i -> {
                        eventSource = i;
                        announceMessage = defaultAnnounce(streamEvent);
                        rebuild();
                    }))));
                }
                if (MULTIPLIER_LABEL[streamEvent] != null) {
                    addEntry(new Row(MULTIPLIER_LABEL[streamEvent], List.of(toggle(multiply, v -> {
                        multiply = v;
                        rebuild();
                    }))));
                    if (multiply && streamEvent == 2) {
                        addEntry(new Row("X bits per trigger", List.of(text(perBits, 60, s -> perBits = s))));
                    }
                    if (multiply) {
                        addEntry(new Row("Max repeats (blank = no cap)", List.of(text(maxSpawns, 60, s -> maxSpawns = s))));
                    }
                }
            } else {
                addEntry(new Row("Reward title (must match Twitch)", List.of(text(rewardTitle, 200, s -> rewardTitle = s))));
                addEntry(new Row("Source", List.of(cycle(SOURCES, rewardSource, i -> {
                    rewardSource = i;
                    String esDef = "[Twitch] $user_name redeemed $reward_title!";
                    String seDef = "[Twitch] $username redeemed $redemption!";
                    if (announceMessage.equals(esDef) || announceMessage.equals(seDef)) {
                        announceMessage = i == 1 ? seDef : esDef;
                    }
                    if (chatMessage.equals(esDef) || chatMessage.equals(seDef)) {
                        chatMessage = i == 1 ? seDef : esDef;
                    }
                    rebuild();
                }))));
            }
            addEntry(new Row("When redeemed", List.of(cycle(ACTIONS, action, i -> {
                action = i;
                rebuild();
            }))));
            switch (action) {
                case 0 -> {
                    addEntry(new Row("Mob", List.of(picker(mob, "Pick a mob", mobIds(), s -> mob = s))));
                    addEntry(new Row("Spawn as baby", List.of(toggle(spawnAsBaby, v -> spawnAsBaby = v))));
                    addEntry(new Row("How many", List.of(text(count, 50, s -> count = s))));
                    addEntry(new Row("Weapon", List.of(picker(weapon, "Pick a weapon", WEAPONS, s -> weapon = s))));
                    if (!weapon.equals("none")) {
                        addEntry(new Row("Weapon enchants", List.of(
                                enchantButton(weaponEnchants, "Weapon enchants", WEAPON_ENCH))));
                    }
                    for (int i = 0; i < 4; i++) {
                        int slot = i;
                        addEntry(new Row(ARMOR_SLOTS[i], List.of(cycle(ARMOR_MATERIALS,
                                ARMOR_MATERIALS.indexOf(armor[i]),
                                v -> {
                                    armor[slot] = ARMOR_MATERIALS.get(v);
                                    rebuild();
                                }))));
                        if (!armor[i].equals("none")) {
                            addEntry(new Row(ARMOR_SLOTS[i] + " enchants", List.of(
                                    enchantButton(armorEnchants.get(i), ARMOR_SLOTS[i] + " enchants",
                                            armorEnchOptions(i)))));
                        }
                    }
                    addEntry(new Row("Name mob after redeemer", List.of(toggle(nameAfterRedeemer, v -> nameAfterRedeemer = v))));
                    addEntry(new Row("Turn off drops", List.of(toggle(disableDrops, v -> disableDrops = v))));
                    addEntry(new Row("No despawning (persistent)", List.of(toggle(persistent, v -> persistent = v))));
                }
                case 1 -> {
                    addEntry(new Row("Effect", List.of(picker(effect, "Pick an effect", EFFECTS, s -> effect = s))));
                    addEntry(new Row("Duration (seconds)", List.of(text(effectSeconds, 50, s -> effectSeconds = s))));
                    addEntry(new Row("Level (1 = I)", List.of(text(effectLevel, 50, s -> effectLevel = s))));
                }
                case 2 -> addEntry(new Row("Command", List.of(text(customCommand, 240, s -> customCommand = s))));
                case 3 -> addEntry(new Row("Message", List.of(text(chatMessage, 240, s -> chatMessage = s))));
            }
            if (action != 3) {
                addEntry(new Row("Announce redemption in chat", List.of(toggle(announceInChat, v -> {
                    announceInChat = v;
                    rebuild();
                }))));
                if (announceInChat) {
                    addEntry(new Row("Announce message", List.of(text(announceMessage, 240, s -> announceMessage = s))));
                }
            }
            String saveLabel = streamEvent >= 0 ? "§aSave " + STREAM_EVENTS[streamEvent]
                    : editingIndex >= 0 ? "§aSave changes" : "§a+ Add reward to config";
            addEntry(new Row("", List.of(Button.builder(Component.literal(saveLabel), b -> {
                if (streamEvent >= 0) {
                    saveStreamEvent();
                } else {
                    saveReward();
                }
            }).bounds(0, 0, 170, 20).build())));

            addEntry(new Row("§6— Chat display —", List.of()));
            addEntry(new Row("Twitch chat shows as", List.of(cycle(CHAT_MODES, twitchChatMode, i -> {
                twitchChatMode = i;
                rebuild();
            }))));
            if (twitchChatMode != 3) {
                addEntry(new Row("Twitch name color", List.of(cycle(COLOR_LABELS, twitchNameColor, i -> {
                    twitchNameColor = i;
                    twitchChatFormat = recolor(twitchChatFormat, "$user", COLOR_CODES[i]);
                    rebuild();
                }))));
                addEntry(new Row("Twitch message color", List.of(cycle(COLOR_LABELS, twitchMsgColor, i -> {
                    twitchMsgColor = i;
                    twitchChatFormat = recolor(twitchChatFormat, "$message", COLOR_CODES[i]);
                    rebuild();
                }))));
            }
            if (twitchChatMode == 1 || twitchChatMode == 2) {
                addEntry(new Row("Twitch chat format", List.of(text(twitchChatFormat, 240, s -> twitchChatFormat = s))));
            }
            addEntry(new Row("YouTube chat shows as", List.of(cycle(CHAT_MODES, youtubeChatMode, i -> {
                youtubeChatMode = i;
                rebuild();
            }))));
            if (youtubeChatMode != 3) {
                addEntry(new Row("YouTube name color", List.of(cycle(COLOR_LABELS, youtubeNameColor, i -> {
                    youtubeNameColor = i;
                    youtubeChatFormat = recolor(youtubeChatFormat, "$authorDetails_displayName", COLOR_CODES[i]);
                    rebuild();
                }))));
                addEntry(new Row("YouTube message color", List.of(cycle(COLOR_LABELS, youtubeMsgColor, i -> {
                    youtubeMsgColor = i;
                    youtubeChatFormat = recolor(youtubeChatFormat, "$displayMessage", COLOR_CODES[i]);
                    rebuild();
                }))));
            }
            if (youtubeChatMode == 1 || youtubeChatMode == 2) {
                addEntry(new Row("YouTube chat format", List.of(text(youtubeChatFormat, 240, s -> youtubeChatFormat = s))));
            }
            addEntry(new Row("", List.of(Button.builder(Component.literal("§aApply chat display"), b -> saveChatMode())
                    .bounds(0, 0, 170, 20).build())));
            setScrollAmount(scroll);
        }

        private EditBox text(String value, int width, java.util.function.Consumer<String> onChange) {
            EditBox box = new EditBox(font, 0, 0, width, 18, Component.empty());
            box.setMaxLength(4000);
            box.setValue(value);
            box.setResponder(onChange);
            return box;
        }

        private Button toggle(boolean value, java.util.function.Consumer<Boolean> onChange) {
            return Button.builder(Component.literal(value ? "§aON" : "§cOFF"), b -> {
                boolean now = !b.getMessage().getString().contains("ON");
                b.setMessage(Component.literal(now ? "§aON" : "§cOFF"));
                onChange.accept(now);
            }).bounds(0, 0, 60, 20).build();
        }

        private Button cycle(List<String> values, int current, java.util.function.IntConsumer onChange) {
            int start = Math.max(current, 0);
            return Button.builder(Component.literal(values.get(start)), b -> {
                int idx = (values.indexOf(b.getMessage().getString()) + 1) % values.size();
                b.setMessage(Component.literal(values.get(idx)));
                onChange.accept(idx);
            }).bounds(0, 0, 130, 20).build();
        }

        private Button enchantButton(Map<String, Integer> current, String title, List<EnchantScreen.Ench> options) {
            StringBuilder label = new StringBuilder();
            for (Map.Entry<String, Integer> e : current.entrySet()) {
                if (label.length() > 0) label.append(", ");
                label.append(e.getKey()).append(" ").append(e.getValue());
            }
            String show = label.length() == 0 ? "none"
                    : label.length() > 26 ? label.substring(0, 26) + "…" : label.toString();
            return Button.builder(Component.literal(show), b ->
                    minecraft.gui.setScreen(new EnchantScreen(SimpleSetupScreen.this,
                            Component.literal(title), options, current, this::rebuild))).bounds(0, 0, 170, 20).build();
        }

        private Button picker(String current, String title, List<String> options, java.util.function.Consumer<String> onPick) {
            String label = current.startsWith("minecraft:") ? current.substring("minecraft:".length()) : current;
            return Button.builder(Component.literal(label), b ->
                    minecraft.gui.setScreen(new PickerScreen(SimpleSetupScreen.this,
                            Component.literal(title), options, picked -> {
                        onPick.accept(picked);
                        rebuild();
                    }))).bounds(0, 0, 170, 20).build();
        }

        @Override
        public int getRowWidth() {
            return Math.min(getWidth() - 20, 460);
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String label;
            private final List<? extends GuiEventListener> widgets;
            private final boolean rightAlign;

            Row(String label, List<? extends GuiEventListener> widgets) {
                this(label, widgets, false);
            }

            /** rightAlign pins the widgets to the row's right edge so long labels don't collide. */
            Row(String label, List<? extends GuiEventListener> widgets, boolean rightAlign) {
                this.label = label;
                this.widgets = widgets;
                this.rightAlign = rightAlign;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                graphics.text(font, label, getContentX(), getContentYMiddle() - 4, 0xFFFFFFFF);
                int x;
                if (rightAlign) {
                    int total = 0;
                    for (GuiEventListener w : widgets) {
                        if (w instanceof AbstractWidget widget) total += widget.getWidth() + 4;
                    }
                    x = getContentX() + getContentWidth() - Math.max(total - 4, 0);
                } else {
                    x = getContentX() + (label.isEmpty() ? 0 : Math.max(getContentWidth() * 2 / 5, 150));
                }
                for (GuiEventListener w : widgets) {
                    if (w instanceof AbstractWidget widget) {
                        widget.setPosition(x, getContentY() - 1);
                        widget.extractRenderState(graphics, mouseX, mouseY, partial);
                        x += widget.getWidth() + 4;
                    }
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return widgets;
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                List<NarratableEntry> out = new ArrayList<>();
                for (GuiEventListener w : widgets) {
                    if (w instanceof NarratableEntry n) out.add(n);
                }
                return out;
            }
        }
    }
}
