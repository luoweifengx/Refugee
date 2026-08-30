package luowei.refugee.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 把所属主体写到原版实体 NBT，方便 {@code /data get entity} 查看。不替代 Attachment 存盘。
 */
@Mixin(Villager.class)
public abstract class VillagerSubjectNbtMixin {
	private static final String SUBJECT_KEY = "RefugeeSubject";

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void refugee$writeSubject(CompoundTag tag, CallbackInfo ci) {
		UUID subjectId = RefugeeAttachments.get((Villager) (Object) this).subjectId();
		if (subjectId != null) {
			tag.putString(SUBJECT_KEY, subjectId.toString());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void refugee$readSubject(CompoundTag tag, CallbackInfo ci) {
		if (!tag.contains(SUBJECT_KEY)) {
			return;
		}
		String raw = tag.getStringOr(SUBJECT_KEY, "");
		if (raw.isBlank()) {
			return;
		}
		try {
			UUID subjectId = UUID.fromString(raw);
			Villager self = (Villager) (Object) this;
			RefugeeVillagerData data = RefugeeAttachments.get(self);
			if (data.subjectId() == null) {
				data.setSubjectId(subjectId);
				RefugeeAttachments.markDirty(self, data);
			}
		} catch (IllegalArgumentException ignored) {
		}
	}
}
