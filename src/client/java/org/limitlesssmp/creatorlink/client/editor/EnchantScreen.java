package org.limitlesssmp.creatorlink.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Multi-select enchant editor for one equipment piece. Mutually exclusive
 * enchants (the protection variants, sharpness variants, depth strider vs
 * frost walker) gray out while a conflicting one is on — the same rules the
 * game enforces at an anvil. Edits the given map in place; the parent screen
 * rebuilds from it when this closes.
 */
public final class EnchantScreen extends Screen {
    /** group != 0 marks a mutual-exclusion group (only one of the group at a time). */
    public record Ench(String id, int maxLevel, int group) {}

    private final Screen parent;
    private final List<Ench> options;
    private final Map<String, Integer> current;
    private final Runnable onDone;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private EnchList list;

    /** onDone runs after returning to the parent (which is NOT re-inited) so it can rebuild its rows. */
    public EnchantScreen(Screen parent, Component title, List<Ench> options, Map<String, Integer> current, Runnable onDone) {
        super(title);
        this.parent = parent;
        this.options = options;
        this.current = current;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        list = layout.addToContents(new EnchList(minecraft));
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
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
        if (onDone != null) onDone.run();
    }

    private boolean conflicted(Ench opt) {
        if (opt.group() == 0 || current.containsKey(opt.id())) return false;
        for (Ench other : options) {
            if (other.group() == opt.group() && !other.id().equals(opt.id())
                    && current.containsKey(other.id())) {
                return true;
            }
        }
        return false;
    }

    private class EnchList extends ContainerObjectSelectionList<EnchList.Row> {
        EnchList(Minecraft mc) {
            super(mc, EnchantScreen.this.width, EnchantScreen.this.layout.getContentHeight(),
                    EnchantScreen.this.layout.getHeaderHeight(), 24);
        }

        void rebuild() {
            clearEntries();
            for (Ench opt : options) {
                addEntry(new Row(opt));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(getWidth() - 20, 400);
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Ench opt;
            private final List<AbstractWidget> widgets = new ArrayList<>();

            Row(Ench opt) {
                this.opt = opt;
                boolean on = current.containsKey(opt.id());
                boolean blocked = conflicted(opt);
                Button toggle = Button.builder(
                        Component.literal(on ? "§aON" : blocked ? "§8✕" : "§cOFF"), b -> {
                            if (current.containsKey(opt.id())) current.remove(opt.id());
                            else current.put(opt.id(), 1);
                            rebuild();
                        }).bounds(0, 0, 50, 20).build();
                toggle.active = !blocked;
                widgets.add(toggle);
                if (on && opt.maxLevel() > 1) {
                    EditBox level = new EditBox(font, 0, 0, 40, 18, Component.literal("level"));
                    level.setValue(String.valueOf(current.getOrDefault(opt.id(), 1)));
                    level.setResponder(s -> {
                        try {
                            int v = Math.max(1, Math.min((int) Double.parseDouble(s.trim()), opt.maxLevel()));
                            current.put(opt.id(), v);
                        } catch (NumberFormatException ignored) {}
                    });
                    widgets.add(level);
                }
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                boolean blocked = conflicted(opt);
                String label = (blocked ? "§8" : "") + opt.id()
                        + " §8(max " + opt.maxLevel() + (blocked ? ", conflicts" : "") + ")";
                graphics.text(font, label, getContentX(), getContentYMiddle() - 4, 0xFFFFFFFF);
                int x = getContentX() + Math.max(getContentWidth() * 3 / 5, 180);
                for (AbstractWidget w : widgets) {
                    w.setPosition(x, getContentY() - 1);
                    w.extractRenderState(graphics, mouseX, mouseY, partial);
                    x += w.getWidth() + 4;
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return widgets;
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return new ArrayList<>(widgets);
            }
        }
    }
}
