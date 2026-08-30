package luowei.refugee.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

import luowei.refugee.client.StaffClientNav;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void refugee$staffKeys(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.screen != null) {
			return;
		}
		if (StaffClientNav.handleWorldKey(client, key, scancode)) {
			ci.cancel();
		}
	}
}
