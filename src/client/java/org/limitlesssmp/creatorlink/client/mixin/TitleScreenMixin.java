package org.limitlesssmp.creatorlink.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.limitlesssmp.creatorlink.client.CreatorLinkClient;
import org.limitlesssmp.creatorlink.client.editor.EditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a CreatorLink button to the title screen so configs can be edited before joining a world. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void creatorlink$addButton(CallbackInfo ci) {
		addRenderableWidget(Button.builder(Component.literal("CreatorLink"), b ->
						minecraft.gui.setScreen(new EditorScreen((TitleScreen) (Object) this,
								CreatorLinkClient.activeProfileDir(), "twitcheventsub")))
				.bounds(2, 2, 80, 20)
				.build());
	}
}
