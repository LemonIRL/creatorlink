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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.limitlesssmp.creatorlink.client.CreatorLinkClient;
import org.limitlesssmp.creatorlink.config.ClientConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Edits creatorlink.toml in-game: streamer name, behaviour flags, channel
 * lists and service tokens. Token fields are masked until "Show secrets" is
 * toggled so the screen is safe to open on stream. Save writes the toml and
 * restarts all sockets.
 */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final ClientConfig config;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private SettingsList list;
    private boolean showSecrets;

    public SettingsScreen(Screen parent) {
        super(Component.literal("CreatorLink Settings — creatorlink.toml"));
        this.parent = parent;
        this.config = CreatorLinkClient.config;
    }

    private static boolean isSecret(String fieldName) {
        return fieldName.contains("Token") || fieldName.contains("ApiKey");
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        list = layout.addToContents(new SettingsList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("Save + Reconnect"), b -> {
            CreatorLinkClient.saveConfigAndReconnect();
            CreatorLinkClient.RUNTIME.bridge().toast(3, "CreatorLink", "Settings saved, sockets restarting", null, null);
        }).width(120).build());
        footer.addChild(Button.builder(Component.literal("Show secrets"), b -> {
            showSecrets = !showSecrets;
            b.setMessage(Component.literal(showSecrets ? "Hide secrets" : "Show secrets"));
            list.rebuild();
        }).width(100).build());
        footer.addChild(Button.builder(Component.literal("Connections"), b ->
                minecraft.gui.setScreen(new ConnectionsScreen(this))).width(80).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(80).build());

        layout.visitWidgets(w -> {
            if (w != list) addRenderableWidget(w);
        });
        addRenderableWidget(list);
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

    private class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
        SettingsList(Minecraft mc) {
            super(mc, SettingsScreen.this.width, SettingsScreen.this.layout.getContentHeight(),
                    SettingsScreen.this.layout.getHeaderHeight(), 24);
        }

        void rebuild() {
            clearEntries();
            addEntry(new Row("§7— general —", List.of()));
            for (EditorModel.FieldRef f : EditorModel.fieldsOf(config)) {
                String name = f.name();
                if (name.equals("firstRun")) continue;
                boolean secret = isSecret(name);
                switch (f.kind) {
                    case STRING -> addEntry(new Row(name, List.of(stringBox(f, secret))));
                    case INT -> addEntry(new Row(name, List.of(numberBox(f))));
                    case BOOL -> addEntry(new Row(name, List.of(boolButton(f))));
                    case STRING_LIST -> {
                        if (name.equals("twitchChats")) {
                            addEntry(new Row("§7— connections —", List.of()));
                        }
                        addEntry(new Row(name + (secret ? " §8(secret)" : " §8(comma-separated)"),
                                List.of(listBox(f, secret))));
                    }
                    default -> {}
                }
            }
        }

        private AbstractWidget stringBox(EditorModel.FieldRef f, boolean secret) {
            Object v = f.get();
            String value = v == null ? "" : String.valueOf(v);
            if (secret && !showSecrets && !value.isEmpty()) return hiddenLabel();
            EditBox box = new EditBox(font, 0, 0, 220, 18, Component.literal(f.name()));
            box.setMaxLength(8000);
            box.setValue(value);
            box.setResponder(s -> f.set(s));
            return box;
        }

        private AbstractWidget listBox(EditorModel.FieldRef f, boolean secret) {
            Object v = f.get();
            StringBuilder sb = new StringBuilder();
            if (v instanceof List<?> l) {
                for (Object o : l) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(o);
                }
            }
            if (secret && !showSecrets && sb.length() > 0) return hiddenLabel();
            EditBox box = new EditBox(font, 0, 0, 220, 18, Component.literal(f.name()));
            box.setMaxLength(16000);
            box.setValue(sb.toString());
            box.setResponder(s -> f.set(new ArrayList<>(Arrays.stream(s.split(","))
                    .map(String::trim).filter(x -> !x.isEmpty()).toList())));
            return box;
        }

        private AbstractWidget numberBox(EditorModel.FieldRef f) {
            EditBox box = new EditBox(font, 0, 0, 80, 18, Component.literal(f.name()));
            Object v = f.get();
            box.setValue(v == null ? "" : String.valueOf(v));
            box.setResponder(s -> {
                try {
                    f.set((int) Double.parseDouble(s));
                } catch (NumberFormatException ignored) {}
            });
            return box;
        }

        private AbstractWidget boolButton(EditorModel.FieldRef f) {
            boolean current = Boolean.TRUE.equals(f.get());
            return Button.builder(Component.literal(current ? "true" : "false"), b -> {
                boolean now = !Boolean.TRUE.equals(f.get());
                f.set(now);
                b.setMessage(Component.literal(now ? "true" : "false"));
            }).bounds(0, 0, 60, 20).build();
        }

        private AbstractWidget hiddenLabel() {
            Button b = Button.builder(Component.literal("§8•••••••• (set — toggle Show secrets to edit)"), x -> {})
                    .bounds(0, 0, 220, 18).build();
            b.active = false;
            return b;
        }

        @Override
        public int getRowWidth() {
            return Math.min(getWidth() - 20, 500);
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
                graphics.text(font, label, getContentX(), getContentYMiddle() - 4, 0xFFFFFFFF);
                int x = getContentX() + Math.max(getContentWidth() * 2 / 5, 140);
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
