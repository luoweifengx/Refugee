package luowei.refugee.zone;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 由两顶点确定的闭区间方块 AABB。
 */
public final class AreaBox {
	public static final Codec<AreaBox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("min").forGetter(AreaBox::min),
			BlockPos.CODEC.fieldOf("max").forGetter(AreaBox::max)
	).apply(instance, AreaBox::new));

	private final BlockPos min;
	private final BlockPos max;

	public AreaBox(BlockPos min, BlockPos max) {
		this.min = min.immutable();
		this.max = max.immutable();
	}

	public static AreaBox of(BlockPos a, BlockPos b) {
		return new AreaBox(
				new BlockPos(
						Math.min(a.getX(), b.getX()),
						Math.min(a.getY(), b.getY()),
						Math.min(a.getZ(), b.getZ())
				),
				new BlockPos(
						Math.max(a.getX(), b.getX()),
						Math.max(a.getY(), b.getY()),
						Math.max(a.getZ(), b.getZ())
				)
		);
	}

	public BlockPos min() {
		return min;
	}

	public BlockPos max() {
		return max;
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
				&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
				&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	public BlockPos center() {
		return new BlockPos(
				(min.getX() + max.getX()) / 2,
				(min.getY() + max.getY()) / 2,
				(min.getZ() + max.getZ()) / 2
		);
	}

	public AABB aabb() {
		return AABB.encapsulatingFullBlocks(min, max);
	}

	public int sizeX() {
		return max.getX() - min.getX() + 1;
	}

	public int sizeY() {
		return max.getY() - min.getY() + 1;
	}

	public int sizeZ() {
		return max.getZ() - min.getZ() + 1;
	}

	public int maxAxis() {
		return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
	}

	public long volume() {
		return (long) sizeX() * sizeY() * sizeZ();
	}

	public static Optional<AreaBox> fromOptional(Optional<BlockPos> min, Optional<BlockPos> max) {
		if (min.isEmpty() || max.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new AreaBox(min.get(), max.get()));
	}
}
