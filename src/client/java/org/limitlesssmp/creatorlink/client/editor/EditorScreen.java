package org.limitlesssmp.creatorlink.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.limitlesssmp.creatorlink.client.CreatorLinkClient;
import org.limitlesssmp.creatorlink.engine.ConfigIO;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;
import org.limitlesssmp.creatorlink.engine.config.Configuration;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The visual event-config editor: navigator tree on the left, fields of the
 * selected object on the right. Save writes the JSON file and reloads the
 * active profile.
 */
public final class EditorScreen extends Screen {
    private final Screen parent;
    private final Path profileDir;
    private final String fileName;
    private final EventConfiguration model;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Set<Object> expandedPaths = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final TreeNode root;

    private Object selected;
    private List<Object> selectedParentList;
    private String selectedLabel;
    private NavigatorList navList;
    private FieldsList fieldsList;
    private boolean dirty;
    private Object lastClickValue;
    private long lastClickMs;

    public EditorScreen(Screen parent, Path profileDir, String fileName) {
        super(Component.literal("CreatorLink Editor — " + fileName + ".json"));
        this.parent = parent;
        this.profileDir = profileDir;
        this.fileName = fileName;
        EventConfiguration loaded = Files.exists(profileDir.resolve(fileName + ".json"))
                ? ConfigIO.loadEventConfiguration(profileDir.resolve(fileName + ".json")) : null;
        if (loaded == null) {
            loaded = new EventConfiguration();
            loaded.type = fileName;
            loaded.configs = new ArrayList<>();
            loaded.init = new ArrayList<>();
        }
        this.model = loaded;
        this.root = TreeNode.root(model, fileName + ".json");
        this.root.expandedPaths = expandedPaths;
        expandedPaths.add(model);
        this.selected = model;
    }

    /** Opens the editor with a specific event pre-selected and its tree path expanded. */
    @SuppressWarnings("unchecked")
    public EditorScreen(Screen parent, Path profileDir, String fileName,
                        String family, String eventType, int index) {
        this(parent, profileDir, fileName);
        for (Configuration c : model.configs) {
            boolean match = family == null ? c.forPlatform == null : family.equalsIgnoreCase(c.forPlatform);
            if (!match || c.types == null) continue;
            List<ConfigEvent> events = c.types.get(eventType);
            if (events == null || index < 0 || index >= events.size()) continue;
            expandedPaths.add(c);
            expandedPaths.add(events);
            selected = events.get(index);
            selectedParentList = (List<Object>) (List<?>) events;
            break;
        }
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        // config tabs, like CCI's socket layers: one button per config file
        LinearLayout tabs = layout.addToHeader(LinearLayout.horizontal().spacing(4));
        for (String tab : tabFiles()) {
            boolean current = tab.equals(fileName);
            Button b = Button.builder(Component.literal(current ? "§e" + tab : tab), x ->
                            minecraft.gui.setScreen(new EditorScreen(parent, profileDir, tab)))
                    .width(Math.max(50, font.width(tab) + 12)).build();
            b.active = !current;
            tabs.addChild(b);
        }
        navList = addRenderableWidget(new NavigatorList(minecraft));
        fieldsList = addRenderableWidget(new FieldsList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(6));
        footer.addChild(Button.builder(Component.literal("Save"), b -> save()).width(60).build());
        footer.addChild(Button.builder(Component.literal("Simple"), b ->
                minecraft.gui.setScreen(new SimpleSetupScreen(this))).width(60).build());
        footer.addChild(Button.builder(Component.literal("File…"), b -> pickFile()).width(55).build());
        footer.addChild(Button.builder(Component.literal("Settings"), b ->
                minecraft.gui.setScreen(new SettingsScreen(this))).width(65).build());
        footer.addChild(Button.builder(Component.literal("Events"), b ->
                minecraft.gui.setScreen(new EventViewerScreen(this))).width(60).build());
        footer.addChild(Button.builder(Component.literal("Connections"), b ->
                minecraft.gui.setScreen(new ConnectionsScreen(this))).width(80).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(55).build());

        layout.visitWidgets(w -> {
            if (w != navList && w != fieldsList) addRenderableWidget(w);
        });
        repositionElements();
        rebuildTree();
        rebuildFields();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        int content = layout.getContentHeight();
        int header = layout.getHeaderHeight();
        int leftWidth = width * 2 / 5;
        if (navList != null) navList.updateSizeAndPosition(leftWidth, content, 0, header);
        if (fieldsList != null) fieldsList.updateSizeAndPosition(width - leftWidth, content, leftWidth, header);
    }

    @Override
    public void onClose() {
        if (dirty) {
            // deletes/edits only reach the file via Save — ask instead of silently dropping them
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(save -> {
                if (save) save();
                minecraft.gui.setScreen(parent);
            }, Component.literal("Save changes to " + fileName + ".json?"),
                    Component.literal("Unsaved edits (including deletes) are lost otherwise."),
                    Component.literal("Save + close"), Component.literal("Discard")));
            return;
        }
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        org.limitlesssmp.creatorlink.client.CreatorLinkClient.importDropped(paths);
        // reopen so the tree shows the imported configs
        minecraft.gui.setScreen(new EditorScreen(parent, profileDir, fileName));
    }

    /**
     * Every socket type that can receive events, plus any extra profile
     * configs — all tabs exist even before their file does (like CCI's
     * layers); Save creates the file.
     */
    private List<String> tabFiles() {
        List<String> files = new ArrayList<>(List.of("twitcheventsub", "chat", "streamelements",
                "streamlabs", "donationalerts", "gamehooks"));
        try (var stream = Files.list(profileDir)) {
            stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .filter(n -> !List.of("constants", "notes", "stats", "dump").contains(n))
                    .sorted()
                    .forEach(n -> {
                        if (!files.contains(n)) files.add(n);
                    });
        } catch (IOException ignored) {}
        return files.size() > 7 ? files.subList(0, 7) : files;
    }

    private void pickFile() {
        List<String> files = new ArrayList<>();
        try (var stream = Files.list(profileDir)) {
            stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .filter(n -> !List.of("constants", "notes", "stats", "dump").contains(n))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException ignored) {}
        for (String known : new String[] {"chat", "twitcheventsub", "streamlabs", "streamelements",
                "donationalerts", "gamehooks"}) {
            if (!files.contains(known)) files.add(known + " (new)");
        }
        minecraft.gui.setScreen(new PickerScreen(this, Component.literal("Open config file"), files,
                picked -> minecraft.gui.setScreen(new EditorScreen(parent, profileDir, picked.replace(" (new)", "")))));
    }

    private void save() {
        try {
            Files.createDirectories(profileDir);
            if (model.type == null) model.type = fileName;
            Files.writeString(profileDir.resolve(fileName + ".json"), ConfigIO.GSON.toJson(model));
            CreatorLinkClient.reloadActiveProfile();
            dirty = false;
            CreatorLinkClient.RUNTIME.bridge().toast(3, "CreatorLink", "Saved " + fileName + ".json", null, null);
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Save failed", e);
            CreatorLinkClient.RUNTIME.bridge().toast(3, "CreatorLink", "Save FAILED: " + e.getMessage(), null, null);
        }
    }

    private void markDirty() {
        dirty = true;
    }

    private void select(TreeNode node) {
        selected = node.value;
        selectedParentList = node.parentList;
        selectedLabel = node.label;
        rebuildFields();
    }

    /**
     * CCI-style add: the full registered type list ("chat — ChatOutcome", ...)
     * with search, appending the pick to the list. Reached by double-clicking a
     * conditions/outcomes/types node in the tree or the "+ Add…" button.
     */
    private void addToList(List<Object> target, String nodeLabel) {
        Class<?> base = null;
        if (!target.isEmpty()) {
            Object first = target.get(0);
            if (first instanceof org.limitlesssmp.creatorlink.engine.condition.Condition) {
                base = org.limitlesssmp.creatorlink.engine.condition.Condition.class;
            } else if (first instanceof org.limitlesssmp.creatorlink.engine.outcome.Outcome) {
                base = org.limitlesssmp.creatorlink.engine.outcome.Outcome.class;
            } else {
                base = ConfigEvent.class;
            }
        } else if (nodeLabel != null) {
            if (nodeLabel.startsWith("conditions")) base = org.limitlesssmp.creatorlink.engine.condition.Condition.class;
            else if (nodeLabel.startsWith("outcomes")) base = org.limitlesssmp.creatorlink.engine.outcome.Outcome.class;
        }
        if (base == null || base == ConfigEvent.class) {
            target.add(new ConfigEvent());
            refreshAll();
            return;
        }
        Class<?> fbase = base;
        openTypePicker(fbase, picked -> {
            Object created = EditorModel.create(fbase, picked);
            if (created != null) {
                target.add(created);
                refreshAll();
            }
        });
    }

    private void openTypePicker(Class<?> base, java.util.function.Consumer<String> onPick) {
        String title = base == org.limitlesssmp.creatorlink.engine.outcome.Outcome.class ? "Add Outcome"
                : base == org.limitlesssmp.creatorlink.engine.condition.Condition.class ? "Add Condition"
                : "Pick a type";
        minecraft.gui.setScreen(new PickerScreen(this, Component.literal(title),
                EditorModel.pickList(base),
                picked -> onPick.accept(picked.contains(" — ")
                        ? picked.substring(0, picked.indexOf(" — ")) : picked)));
    }

    private void rebuildTree() {
        navList.rebuild();
    }

    private void rebuildFields() {
        fieldsList.rebuild();
    }

    private void refreshAll() {
        markDirty();
        rebuildTree();
        rebuildFields();
    }

    // ------------------------------------------------------------------ tree

    private class NavigatorList extends ContainerObjectSelectionList<NavigatorList.Row> {
        NavigatorList(Minecraft mc) {
            super(mc, EditorScreen.this.width * 2 / 5, EditorScreen.this.layout.getContentHeight(),
                    EditorScreen.this.layout.getHeaderHeight(), 20);
        }

        void rebuild() {
            double scroll = scrollAmount();
            clearEntries();
            List<TreeNode> rows = new ArrayList<>();
            root.expanded = true;
            TreeNode.flatten(root, rows);
            for (TreeNode node : rows) {
                addEntry(new Row(node));
            }
            setScrollAmount(scroll);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 20;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final TreeNode node;
            private final Button expand;
            private final Button select;

            Row(TreeNode node) {
                this.node = node;
                boolean expandable = node.hasChildren();
                this.expand = expandable
                        ? Button.builder(Component.literal(node.expanded ? "−" : "+"), b -> {
                            node.setExpanded(!node.expanded);
                            rebuild();
                        }).bounds(0, 0, 14, 18).build()
                        : null;
                String label = node.label + (EditorModel.isValid(node.value) ? "" : " §c!");
                String prefix = node.value == selected ? "§e" : "";
                int width = Math.max(60, getRowWidth() - node.depth * 10 - 18);
                this.select = Button.builder(Component.literal(prefix + label), b -> {
                    boolean doubleClick = node.value == lastClickValue
                            && System.currentTimeMillis() - lastClickMs < 400;
                    lastClickValue = node.value;
                    lastClickMs = System.currentTimeMillis();
                    select(node);
                    rebuild();
                    if (doubleClick && node.value instanceof List<?> l) {
                        // CCI behavior: double-click a conditions/outcomes node → full Add list
                        @SuppressWarnings("unchecked")
                        List<Object> target = (List<Object>) l;
                        addToList(target, node.label);
                    }
                }).bounds(0, 0, width, 18).build();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                int x = getContentX() + node.depth * 10;
                int y = getContentY() - 1;
                if (expand != null) {
                    expand.setPosition(x, y);
                    expand.extractRenderState(graphics, mouseX, mouseY, partial);
                }
                select.setPosition(x + 16, y);
                select.extractRenderState(graphics, mouseX, mouseY, partial);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return expand == null ? List.of(select) : List.of(expand, select);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return expand == null ? List.of(select) : List.of(expand, select);
            }
        }
    }

    // ---------------------------------------------------------------- fields

    private class FieldsList extends ContainerObjectSelectionList<FieldsList.Row> {
        FieldsList(Minecraft mc) {
            super(mc, EditorScreen.this.width * 3 / 5, EditorScreen.this.layout.getContentHeight(),
                    EditorScreen.this.layout.getHeaderHeight(), 24);
        }

        @SuppressWarnings("unchecked")
        void rebuild() {
            clearEntries();
            Object obj = selected;
            if (obj == null) return;

            // Header row: label + delete/reorder when the object sits in a list.
            addEntry(new Row(EditorModel.label(obj), headerButtons()));

            if (obj instanceof List) {
                // container node (conditions/outcomes/types entry/init): only "add" applies
                List<Object> list = (List<Object>) obj;
                addEntry(new Row("", List.of(Button.builder(Component.literal("+ Add…"), b ->
                        addToList(list, selectedLabel)).bounds(0, 0, 100, 20).build())));
                return;
            }

            for (EditorModel.FieldRef f : EditorModel.fieldsOf(obj)) {
                switch (f.kind) {
                    case STRING -> addEntry(new Row(f.name(), List.of(stringBox(f))));
                    case INT, DOUBLE -> addEntry(new Row(f.name(), List.of(numberBox(f))));
                    case BOOL -> addEntry(new Row(f.name(), List.of(boolButton(f))));
                    case STRING_LIST -> addEntry(new Row(f.name() + " (comma-separated)", List.of(stringListBox(f))));
                    case OBJECT -> {
                        Button set = Button.builder(Component.literal(f.get() == null ? "Set…" : "Replace…"), b ->
                                openPicker(f.elementType, picked -> {
                                    f.set(EditorModel.create(f.elementType, picked));
                                    refreshAll();
                                })).bounds(0, 0, 70, 20).build();
                        Button clear = Button.builder(Component.literal("Clear"), b -> {
                            f.set(null);
                            refreshAll();
                        }).bounds(0, 0, 50, 20).build();
                        addEntry(new Row(f.name(), List.of(set, clear)));
                    }
                    case OBJECT_LIST -> addEntry(new Row(f.name(),
                            List.of(addButton(f.elementType, f.list()))));
                    case TYPES_MAP -> {
                        addEntry(new Row("types (event type → events)", List.of()));
                        var map = f.typesMap();
                        for (String key : new ArrayList<>(map.keySet())) {
                            Button del = Button.builder(Component.literal("Remove"), b -> {
                                map.remove(key);
                                refreshAll();
                            }).bounds(0, 0, 60, 20).build();
                            addEntry(new Row("  " + key + " (" + map.get(key).size() + ")", List.of(del)));
                        }
                        EditBox newKey = new EditBox(font, 0, 0, 110, 18, Component.literal("new type"));
                        newKey.setMaxLength(64);
                        Button add = Button.builder(Component.literal("Add type"), b -> {
                            String key = newKey.getValue().trim();
                            if (!key.isEmpty() && !map.containsKey(key)) {
                                map.put(key, new ArrayList<>());
                                refreshAll();
                            }
                        }).bounds(0, 0, 70, 20).build();
                        addEntry(new Row("", List.of(newKey, add)));
                    }
                    case UNSUPPORTED -> {}
                }
            }

            if (obj instanceof EventConfiguration cfg) {
                addEntry(new Row("", List.of(Button.builder(Component.literal("+ Add Configuration"), b -> {
                    if (cfg.configs == null) cfg.configs = new ArrayList<>();
                    cfg.configs.add(new Configuration());
                    refreshAll();
                }).bounds(0, 0, 140, 20).build())));
            }
        }

        private List<GuiEventListener> headerButtons() {
            List<GuiEventListener> buttons = new ArrayList<>();
            if (selectedParentList != null && selected != null && !(selected instanceof List)) {
                List<Object> list = selectedParentList;
                Object obj = selected;
                buttons.add(Button.builder(Component.literal("↑"), b -> {
                    int i = list.indexOf(obj);
                    if (i > 0) {
                        list.remove(i);
                        list.add(i - 1, obj);
                        refreshAll();
                    }
                }).bounds(0, 0, 20, 20).build());
                buttons.add(Button.builder(Component.literal("↓"), b -> {
                    int i = list.indexOf(obj);
                    if (i >= 0 && i < list.size() - 1) {
                        list.remove(i);
                        list.add(i + 1, obj);
                        refreshAll();
                    }
                }).bounds(0, 0, 20, 20).build());
                buttons.add(Button.builder(Component.literal("Delete"), b -> {
                    list.remove(obj);
                    selected = model;
                    selectedParentList = null;
                    refreshAll();
                }).bounds(0, 0, 60, 20).build());
            }
            return buttons;
        }

        private Button addButton(Class<?> base, List<Object> target) {
            String label = base == ConfigEvent.class ? "+ Add Event" : "+ Add…";
            return Button.builder(Component.literal(label), b -> {
                if (base == ConfigEvent.class) {
                    target.add(new ConfigEvent());
                    refreshAll();
                } else {
                    openTypePicker(base, picked -> {
                        Object created = EditorModel.create(base, picked);
                        if (created != null) {
                            target.add(created);
                            refreshAll();
                        }
                    });
                }
            }).bounds(0, 0, 100, 20).build();
        }

        private void openPicker(Class<?> base, java.util.function.Consumer<String> onPick) {
            openTypePicker(base, onPick);
        }

        private EditBox stringBox(EditorModel.FieldRef f) {
            EditBox box = new EditBox(font, 0, 0, 180, 18, Component.literal(f.name()));
            box.setMaxLength(32000);
            Object v = f.get();
            box.setValue(v == null ? "" : String.valueOf(v));
            boolean isCommand = f.name().equals("command");
            if (isCommand) lintCommandBox(box, box.getValue());
            box.setResponder(s -> {
                f.set(s.isEmpty() ? null : s);
                if (isCommand) lintCommandBox(box, s);
                markDirty();
            });
            return box;
        }

        /** Old-version command syntax turns the box red with the reason as tooltip. */
        private void lintCommandBox(EditBox box, String value) {
            String reason = CommandLint.legacyReason(value);
            box.setTextColor(reason == null ? 0xFFE0E0E0 : 0xFFFF5555);
            box.setTooltip(reason == null ? null
                    : net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("§c⚠ Won't work on 26.2: §r" + reason)));
        }

        private EditBox numberBox(EditorModel.FieldRef f) {
            EditBox box = new EditBox(font, 0, 0, 80, 18, Component.literal(f.name()));
            Object v = f.get();
            box.setValue(v == null ? "" : String.valueOf(v));
            box.setResponder(s -> {
                try {
                    if (s.isEmpty()) {
                        f.set(f.field.getType().isPrimitive() ? (f.kind == EditorModel.Kind.INT ? 0 : 0.0) : null);
                    } else if (f.kind == EditorModel.Kind.INT) {
                        f.set((int) Double.parseDouble(s));
                    } else {
                        f.set(Double.parseDouble(s));
                    }
                    markDirty();
                } catch (NumberFormatException ignored) {}
            });
            return box;
        }

        private Button boolButton(EditorModel.FieldRef f) {
            boolean current = Boolean.TRUE.equals(f.get());
            Button button = Button.builder(Component.literal(current ? "true" : "false"), b -> {
                boolean now = !Boolean.TRUE.equals(f.get());
                f.set(now);
                b.setMessage(Component.literal(now ? "true" : "false"));
                markDirty();
            }).bounds(0, 0, 60, 20).build();
            return button;
        }

        private EditBox stringListBox(EditorModel.FieldRef f) {
            EditBox box = new EditBox(font, 0, 0, 180, 18, Component.literal(f.name()));
            box.setMaxLength(4000);
            Object v = f.get();
            if (v instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object o : list) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(o);
                }
                box.setValue(sb.toString());
            }
            box.setResponder(s -> {
                if (s.trim().isEmpty()) {
                    f.set(null);
                } else {
                    f.set(new ArrayList<>(Arrays.stream(s.split(",")).map(String::trim)
                            .filter(x -> !x.isEmpty()).toList()));
                }
                markDirty();
            });
            return box;
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 20;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String label;
            private final List<? extends GuiEventListener> widgets;

            Row(String label, List<? extends GuiEventListener> widgets) {
                this.label = label;
                this.widgets = widgets;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                int labelWidth = Math.min(font.width(label), getContentWidth() / 2);
                graphics.text(font, label, getContentX(), getContentYMiddle() - 4, 0xFFFFFFFF);
                int x = getContentX() + Math.max(labelWidth + 8, label.isEmpty() ? 0 : getContentWidth() * 2 / 5);
                for (GuiEventListener w : widgets) {
                    if (w instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
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
            @SuppressWarnings("unchecked")
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
