package luowei.refugee.special;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 四个特殊难民角色：外观用原版职业皮肤，逻辑用自定义字段，不走原版工作 AI。
 */
public enum RefugeeSpecialRole {
	GUIDE("guide", VillagerProfession.NITWIT),
	NURSE("nurse", VillagerProfession.CLERIC),
	CARTOGRAPHER("cartographer", VillagerProfession.CARTOGRAPHER),
	ENCHANTER("enchanter", VillagerProfession.LIBRARIAN);

	private final String id;
	private final ResourceKey<VillagerProfession> profession;

	RefugeeSpecialRole(String id, ResourceKey<VillagerProfession> profession) {
		this.id = id;
		this.profession = profession;
	}

	public String id() {
		return id;
	}

	public ResourceKey<VillagerProfession> profession() {
		return profession;
	}

	public static RefugeeSpecialRole byId(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		for (RefugeeSpecialRole role : values()) {
			if (role.id.equals(id)) {
				return role;
			}
		}
		return null;
	}

	public static RefugeeSpecialRole of(Villager villager) {
		if (villager == null) {
			return null;
		}
		return RefugeeAttachments.get(villager).specialRole();
	}

	public static boolean isSpecial(Villager villager) {
		return of(villager) != null;
	}

	public static boolean is(Villager villager, RefugeeSpecialRole role) {
		return role != null && of(villager) == role;
	}

	public static ResourceKey<VillagerProfession> lockedProfession(Villager villager) {
		RefugeeSpecialRole role = of(villager);
		return role == null ? null : role.profession;
	}

	public static void apply(Villager villager, RefugeeSpecialRole role) {
		if (villager == null || role == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setSpecialRole(role);
		RefugeeAttachments.markDirty(villager, data);
		villager.setVillagerData(villager.getVillagerData().withProfession(villager.registryAccess(), role.profession));
	}
}
