package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.client.model.RefugeeVillagerArmorLayer;
import luowei.refugee.client.model.RefugeeVillagerArmState;
import luowei.refugee.client.model.RefugeeVillagerHeldItemLayer;
import luowei.refugee.client.model.RefugeeVillagerModel;
import luowei.refugee.client.talk.RefugeeBubbleRenderState;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.talk.RefugeeBubble;

@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin extends AgeableMobRenderer<Villager, VillagerRenderState, VillagerModel> {
	@Unique
	private ItemModelResolver refugee$itemModels;

	protected VillagerRendererMixin(
			EntityRendererProvider.Context context,
			VillagerModel model,
			VillagerModel babyModel,
			float shadowRadius
	) {
		super(context, model, babyModel, shadowRadius);
	}

	@ModifyArgs(
			method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/AgeableMobRenderer;<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;Lnet/minecraft/client/model/EntityModel;F)V"
			)
	)
	private static void refugee$swapModels(Args args) {
		EntityRendererProvider.Context context = args.get(0);
		args.set(1, new RefugeeVillagerModel(context.bakeLayer(RefugeeVillagerModel.LAYER)));
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void refugee$addItemLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
		this.refugee$itemModels = context.getItemModelResolver();
		this.addLayer(new RefugeeVillagerArmorLayer(this, context));
		this.addLayer(new RefugeeVillagerHeldItemLayer(this));
	}

	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/npc/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V",
			at = @At("TAIL")
	)
	private void refugee$extractBubble(Villager villager, VillagerRenderState state, float tickDelta, CallbackInfo ci) {
		if (state instanceof RefugeeBubbleRenderState bubbleState) {
			bubbleState.refugee$setBubbleIcon(RefugeeBubble.get(villager));
		}
		if (!(state instanceof RefugeeVillagerArmState arms)) {
			return;
		}
		if (villager.isBaby()) {
			arms.refugee$setIndependentArms(false);
			arms.refugee$mainHandItem().clear();
			arms.refugee$offHandItem().clear();
			return;
		}
		boolean eating = villager.isUsingItem() && RefugeeRoles.isFood(villager.getUseItem());
		boolean independent = eating
				|| RefugeeAttachments.isRefugee(villager)
				|| RefugeeRoles.isGiveableTool(villager.getMainHandItem())
				|| RefugeeRoles.isGiveableTool(villager.getOffhandItem())
				|| RefugeeRoles.isShield(villager.getMainHandItem())
				|| RefugeeRoles.isShield(villager.getOffhandItem());
		arms.refugee$setIndependentArms(independent);
		arms.refugee$setAttackTime(villager.getAttackAnim(tickDelta));
		arms.refugee$setUsingItem(villager.isUsingItem());
		arms.refugee$setUseTicks(villager.getTicksUsingItem());
		ItemStack use = villager.getUseItem();
		arms.refugee$setUseItem(use.copy());
		arms.refugee$setUseDuration(use.isEmpty() ? 0.0F : use.getUseDuration(villager));
		arms.refugee$setMainHand(villager.getMainHandItem().copy());
		arms.refugee$setOffHand(villager.getOffhandItem().copy());
		arms.refugee$setHeadArmor(villager.getItemBySlot(EquipmentSlot.HEAD).copy());
		arms.refugee$setChestArmor(villager.getItemBySlot(EquipmentSlot.CHEST).copy());
		arms.refugee$setLegsArmor(villager.getItemBySlot(EquipmentSlot.LEGS).copy());
		arms.refugee$setFeetArmor(villager.getItemBySlot(EquipmentSlot.FEET).copy());
		HumanoidArm swinging = villager.swingingArm == InteractionHand.OFF_HAND
				? villager.getMainArm().getOpposite()
				: villager.getMainArm();
		arms.refugee$setSwingingArm(swinging);
		HumanoidArm using = villager.getUsedItemHand() == InteractionHand.OFF_HAND
				? villager.getMainArm().getOpposite()
				: villager.getMainArm();
		arms.refugee$setUseArm(using);
		ItemModelResolver resolver = this.refugee$itemModels;
		if (resolver == null) {
			resolver = Minecraft.getInstance().getItemModelResolver();
		}
		if (independent) {
			boolean rightHanded = villager.getMainArm() == HumanoidArm.RIGHT;
			ItemStack right = rightHanded ? villager.getMainHandItem() : villager.getOffhandItem();
			ItemStack left = rightHanded ? villager.getOffhandItem() : villager.getMainHandItem();
			resolver.updateForLiving(arms.refugee$mainHandItem(), right, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, villager);
			resolver.updateForLiving(arms.refugee$offHandItem(), left, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, villager);
		} else {
			arms.refugee$mainHandItem().clear();
			arms.refugee$offHandItem().clear();
		}
	}
}
