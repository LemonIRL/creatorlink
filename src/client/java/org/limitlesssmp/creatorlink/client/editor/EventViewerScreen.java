package org.limitlesssmp.creatorlink.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.limitlesssmp.creatorlink.client.CreatorLinkClient;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cached-events browser: recent stream events with their variables, a Replay
 * button (re-runs the event through the pipeline) and queue clearing.
 */
public final class EventViewerScreen extends Screen {
    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final List<EventContext> events;
    private EventContext selected;
    private EventList eventList;
    private VarsList varsList;

    public EventViewerScreen(Screen parent) {
        super(Component.literal("CreatorLink Event Viewer"));
        this.parent = parent;
        this.events = CreatorLinkClient.RUNTIME.cachedEvents();
        this.selected = events.isEmpty() ? null : events.get(0);
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        eventList = addRenderableWidget(new EventList(minecraft));
        varsList = addRenderableWidget(new VarsList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("Replay"), b -> replay()).width(80).build());
        footer.addChild(Button.builder(Component.literal("Clear queues"), b -> {
            CreatorLinkClient.RUNTIME.scheduler.clearQueues();
        }).width(90).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(80).build());

        layout.visitWidgets(w -> {
            if (w != eventList && w != varsList) addRenderableWidget(w);
        });
        repositionElements();
        eventList.rebuild();
        varsList.rebuild();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        int content = layout.getContentHeight();
        int header = layout.getHeaderHeight();
        int leftWidth = width * 2 / 5;
        if (eventList != null) eventList.updateSizeAndPosition(leftWidth, content, 0, header);
        if (varsList != null) varsList.updateSizeAndPosition(width - leftWidth, content, leftWidth, header);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private void replay() {
        if (selected == null) return;
        CreatorLinkClient.replayCached(selected);
    }

    private static String label(EventContext ctx) {
        return ctx.socketType + (ctx.platform == null ? "" : "/" + ctx.platform) + " → " + ctx.eventType;
    }

    private class EventList extends ContainerObjectSelectionList<EventList.Row> {
        EventList(Minecraft mc) {
            super(mc, EventViewerScreen.this.width * 2 / 5, EventViewerScreen.this.layout.getContentHeight(),
                    EventViewerScreen.this.layout.getHeaderHeight(), 20);
        }

        void rebuild() {
            clearEntries();
            for (EventContext ctx : events) {
                addEntry(new Row(ctx));
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 20;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Button button;
            private final Button play;

            Row(EventContext ctx) {
                String prefix = ctx == selected ? "§e" : "";
                button = Button.builder(Component.literal(prefix + label(ctx)), b -> {
                    selected = ctx;
                    rebuild();
                    varsList.rebuild();
                }).bounds(0, 0, getRowWidth() - 28, 18).build();
                play = Button.builder(Component.literal("§a▶"), b ->
                        CreatorLinkClient.replayCached(ctx)).bounds(0, 0, 22, 18)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("Replay this event"))).build();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                button.setPosition(getContentX(), getContentY() - 1);
                button.extractRenderState(graphics, mouseX, mouseY, partial);
                play.setPosition(getContentX() + button.getWidth() + 2, getContentY() - 1);
                play.extractRenderState(graphics, mouseX, mouseY, partial);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(button, play);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(button, play);
            }
        }
    }

    private class VarsList extends ContainerObjectSelectionList<VarsList.Row> {
        VarsList(Minecraft mc) {
            super(mc, EventViewerScreen.this.width * 3 / 5, EventViewerScreen.this.layout.getContentHeight(),
                    EventViewerScreen.this.layout.getHeaderHeight(), 12);
        }

        void rebuild() {
            clearEntries();
            if (selected == null) {
                addEntry(new Row("no events cached yet — trigger something on stream"));
                return;
            }
            for (Map.Entry<String, Object> e : selected.varsView().entrySet()) {
                String value = Vars.stringify(e.getValue());
                if (value.length() > 120) value = value.substring(0, 120) + "…";
                addEntry(new Row("§b" + e.getKey() + "§r = " + value));
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 20;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final String text;

            Row(String text) {
                this.text = text;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                graphics.text(font, text, getContentX(), getContentY(), 0xFFFFFFFF);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }
    }
}
