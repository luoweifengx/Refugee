package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import luowei.refugee.client.RelationDeskScreen;

/**
 * 市政工作台打开时不画底部物品栏。
 */
@Mixin(Gui.class)
public class RelationDeskHotbarMixin {
	@Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
	private void refugee$hideDeskHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (Minecraft.getInstance().screen instanceof RelationDeskScreen) {
			ci.cancel();
		}
	}
}
