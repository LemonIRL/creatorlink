package org.limitlesssmp.creatorlink.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Scrollable "pick one of these strings" screen (type pickers, file picker)
 * with a search box. The "minecraft:" prefix is hidden in the list but kept
 * in the value handed back, so non-vanilla ids stay distinguishable.
 */
public final class PickerScreen extends Screen {
    private final Screen parent;
    private final List<String> options;
    private final Consumer<String> onPick;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private EditBox search;
    private PickList list;

    public PickerScreen(Screen parent, Component title, List<String> options, Consumer<String> onPick) {
        super(title);
        this.parent = parent;
        this.options = options;
        this.onPick = onPick;
    }

    private static String display(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        search = new EditBox(font, 0, 0, 200, 18, Component.literal("Search"));
        search.setResponder(q -> list.rebuild(q));
        layout.addToHeader(search);
        list = layout.addToContents(new PickList(minecraft));
        layout.addToFooter(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        list.rebuild("");
        setInitialFocus(search);
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

    private void picked(String value) {
        minecraft.gui.setScreen(parent);
        onPick.accept(value);
    }

    private class PickList extends ContainerObjectSelectionList<PickList.Row> {
        PickList(Minecraft mc) {
            super(mc, PickerScreen.this.width, PickerScreen.this.layout.getContentHeight(),
                    PickerScreen.this.layout.getHeaderHeight(), 22);
        }

        void rebuild(String query) {
            clearEntries();
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            for (String option : options) {
                if (!q.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(q)) continue;
                addEntry(new Row(option));
            }
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Button button;

            Row(String value) {
                button = Button.builder(Component.literal(display(value)), b -> picked(value))
                        .bounds(0, 0, 250, 20).build();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                button.setPosition(getContentX(), getContentY() - 2);
                button.extractRenderState(graphics, mouseX, mouseY, partial);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(button);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(button);
            }
        }
    }
}
