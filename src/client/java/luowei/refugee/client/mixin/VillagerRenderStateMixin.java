package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.VillagerRenderState;

import luowei.refugee.client.talk.RefugeeBubbleRenderState;
import luowei.refugee.talk.RefugeeBubbleIcon;

@Mixin(VillagerRenderState.class)
public class VillagerRenderStateMixin implements RefugeeBubbleRenderState {
	@Unique
	private RefugeeBubbleIcon refugee$bubbleIcon = RefugeeBubbleIcon.NONE;

	@Override
	public RefugeeBubbleIcon refugee$bubbleIcon() {
		return refugee$bubbleIcon == null ? RefugeeBubbleIcon.NONE : refugee$bubbleIcon;
	}

	@Override
	public void refugee$setBubbleIcon(RefugeeBubbleIcon icon) {
		this.refugee$bubbleIcon = icon == null ? RefugeeBubbleIcon.NONE : icon;
	}
}
