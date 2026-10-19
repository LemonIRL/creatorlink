package org.limitlesssmp.creatorlink.client.editor;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Text-input dialog for the InputOutcome ("input" type). */
public final class InputScreen extends Screen {
    private final Screen parent;
    private final String text;
    private final String defaultValue;
    private final Consumer<String> onDone;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private EditBox box;

    public InputScreen(Screen parent, String text, String defaultValue, Consumer<String> onDone) {
        super(Component.literal("CreatorLink"));
        this.parent = parent;
        this.text = text;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(Component.literal(text), font);
        LinearLayout content = layout.addToContents(LinearLayout.vertical().spacing(8));
        content.addChild(new MultiLineTextWidget(Component.literal(text), font).setMaxWidth(width - 60));
        box = content.addChild(new EditBox(font, 0, 0, 240, 20, Component.literal("input")));
        box.setMaxLength(4000);
        box.setValue(defaultValue);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> {
            String value = box.getValue();
            minecraft.gui.setScreen(parent);
            onDone.accept(value);
        }).width(120).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        setInitialFocus(box);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
