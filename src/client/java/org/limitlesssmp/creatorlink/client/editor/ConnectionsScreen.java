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
import org.limitlesssmp.creatorlink.socket.SocketBase;

import java.util.List;

/**
 * Live connection panel: one row per configured socket with its current
 * status and per-socket Connect/Disconnect + Reconnect buttons. Status text
 * is read from the socket every frame, so reconnect attempts and drops show
 * up while the screen is open.
 */
public final class ConnectionsScreen extends Screen {
    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private ConnectionList list;

    public ConnectionsScreen(Screen parent) {
        super(Component.literal("CreatorLink Connections"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        list = layout.addToContents(new ConnectionList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("Reconnect All"), b -> {
            CreatorLinkClient.reloadConfigAndReconnect();
            list.rebuild();
            CreatorLinkClient.RUNTIME.bridge().toast(3, "CreatorLink", "Restarting all sockets", null, null);
        }).width(100).build());
        footer.addChild(Button.builder(Component.literal("Settings"), b ->
                minecraft.gui.setScreen(new SettingsScreen(this))).width(80).build());
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

    private class ConnectionList extends ContainerObjectSelectionList<ConnectionList.Row> {
        ConnectionList(Minecraft mc) {
            super(mc, ConnectionsScreen.this.width, ConnectionsScreen.this.layout.getContentHeight(),
                    ConnectionsScreen.this.layout.getHeaderHeight(), 24);
        }

        void rebuild() {
            clearEntries();
            List<SocketBase> sockets = CreatorLinkClient.sockets.sockets();
            if (sockets.isEmpty()) {
                addEntry(new Row(null));
                return;
            }
            for (SocketBase s : sockets) addEntry(new Row(s));
        }

        @Override
        public int getRowWidth() {
            return Math.min(getWidth() - 20, 500);
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final SocketBase socket;
            private final Button toggle;
            private final Button reconnect;

            Row(SocketBase socket) {
                this.socket = socket;
                if (socket == null) {
                    this.toggle = null;
                    this.reconnect = null;
                    return;
                }
                this.toggle = Button.builder(Component.literal("Disconnect"), b -> {
                    if (isUp()) socket.stop();
                    else socket.start();
                }).bounds(0, 0, 75, 20).build();
                this.reconnect = Button.builder(Component.literal("Reconnect"), b -> socket.restart())
                        .bounds(0, 0, 75, 20).build();
            }

            /** Treat connecting as "up" so the toggle reads Disconnect while retrying. */
            private boolean isUp() {
                SocketBase.Status s = socket.status();
                return s == SocketBase.Status.CONNECTED || s == SocketBase.Status.CONNECTING;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partial) {
                if (socket == null) {
                    graphics.text(font, "§7no sockets configured — add tokens in Settings, then Reconnect All",
                            getContentX(), getContentYMiddle() - 4, 0xFFFFFFFF);
                    return;
                }
                String color = switch (socket.status()) {
                    case CONNECTED -> "§a";
                    case CONNECTING -> "§e";
                    case ERROR -> "§c";
                    case DISCONNECTED -> "§7";
                };
                String detail = socket.statusDetail();
                if (detail.length() > 32) detail = detail.substring(0, 32) + "…";
                String line = color + "● §r" + socket.name() + " " + color + socket.status()
                        + (detail.isEmpty() ? "" : " §8(" + detail + ")");
                graphics.text(font, line, getContentX(), getContentYMiddle() - 4, 0xFFFFFFFF);

                toggle.setMessage(Component.literal(isUp() ? "Disconnect" : "Connect"));
                int x = getContentX() + getContentWidth() - reconnect.getWidth() - toggle.getWidth() - 4;
                toggle.setPosition(x, getContentY() - 1);
                toggle.extractRenderState(graphics, mouseX, mouseY, partial);
                reconnect.setPosition(x + toggle.getWidth() + 4, getContentY() - 1);
                reconnect.extractRenderState(graphics, mouseX, mouseY, partial);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return socket == null ? List.of() : List.of(toggle, reconnect);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return socket == null ? List.of() : List.of(toggle, reconnect);
            }
        }
    }
}
