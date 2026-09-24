package luowei.refugee.client;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.IronGolemFlowerLayer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;
import luowei.refugee.entity.CopperGolem;

/**
 * 铁傀儡模型和动画，铜色贴图。
 */
public class CopperGolemRenderer extends MobRenderer<CopperGolem, IronGolemRenderState, IronGolemModel> {
	private static final ResourceLocation TEXTURE = Refugee.id("textures/entity/copper_golem/copper_golem.png");

	public CopperGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new IronGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.7F);
		this.addLayer(new CopperGolemCrackinessLayer(this));
		this.addLayer(new IronGolemFlowerLayer(this, context.getBlockRenderDispatcher()));
	}

	@Override
	public ResourceLocation getTextureLocation(IronGolemRenderState state) {
		return TEXTURE;
	}

	@Override
	public IronGolemRenderState createRenderState() {
		return new IronGolemRenderState();
	}

	@Override
	public void extractRenderState(CopperGolem golem, IronGolemRenderState state, float partialTick) {
		super.extractRenderState(golem, state, partialTick);
		state.attackTicksRemaining = golem.getAttackAnimationTick() > 0
				? golem.getAttackAnimationTick() - partialTick
				: 0.0F;
		state.offerFlowerTick = golem.getOfferFlowerTick();
		state.crackiness = golem.getCrackiness();
	}
}
