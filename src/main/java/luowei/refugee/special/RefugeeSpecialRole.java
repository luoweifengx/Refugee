package luowei.refugee.special;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 四个特殊难民角色：外观用对应原版职业村民模型，逻辑用自定义字段，不走原版工作 AI。
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
		String synced = villager.getAttached(RefugeeAttachments.SPECIAL_ROLE);
		if (synced != null && !synced.isBlank()) {
			return byId(synced);
		}
		RefugeeVillagerData data = villager.getAttached(RefugeeAttachments.VILLAGER);
		return data == null ? null : data.specialRole();
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
		villager.setAttached(RefugeeAttachments.SPECIAL_ROLE, role.id());
		villager.setVillagerData(villager.getVillagerData().withProfession(villager.registryAccess(), role.profession));
	}
}
