package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import luowei.refugee.client.StaffClientNav;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void refugee$staffScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen != null) {
			return;
		}
		double delta = vertical != 0.0 ? vertical : horizontal;
		if (StaffClientNav.handleWorldScroll(delta)) {
			ci.cancel();
		}
	}
}
