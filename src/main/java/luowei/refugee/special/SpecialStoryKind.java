package luowei.refugee.special;

/**
 * 特殊居民主动剧情：开屏 key 前缀与句数。
 */
public enum SpecialStoryKind {
	GUIDE_WOOD("guide.wood", RefugeeSpecialRole.GUIDE, 6, false),
	GUIDE_COPPER("guide.copper", RefugeeSpecialRole.GUIDE, 4, false),
	GUIDE_IRON("guide.iron", RefugeeSpecialRole.GUIDE, 7, false),
	NURSE_INJURY("nurse.injury", RefugeeSpecialRole.NURSE, 4, false),
	NURSE_DEATH("nurse.death", RefugeeSpecialRole.NURSE, 5, false),
	NURSE_HEAL("nurse.heal", RefugeeSpecialRole.NURSE, 1, false),
	CARTO_FIRST("cartographer.first", RefugeeSpecialRole.CARTOGRAPHER, 7, false),
	CARTO_RETURN("cartographer.return", RefugeeSpecialRole.CARTOGRAPHER, 4, false),
	CARTO_ANCIENT("cartographer.ancient", RefugeeSpecialRole.CARTOGRAPHER, 4, false),
	ENCHANTER_ARRIVAL("enchanter.arrival", RefugeeSpecialRole.ENCHANTER, 3, false),
	ENCHANTER_BOOK("enchanter.book", RefugeeSpecialRole.ENCHANTER, 3, false),
	DRAGON_VANILLA("dragon.vanilla", null, 1, false),
	DRAGON_GLORY("dragon.glory", null, 3, true);

	private final String id;
	private final RefugeeSpecialRole role;
	private final int lines;
	private final boolean loop;

	SpecialStoryKind(String id, RefugeeSpecialRole role, int lines, boolean loop) {
		this.id = id;
		this.role = role;
		this.lines = lines;
		this.loop = loop;
	}

	public String id() {
		return id;
	}

	public RefugeeSpecialRole role() {
		return role;
	}

	public int lines() {
		return lines;
	}

	public boolean loop() {
		return loop;
	}

	public String keyPrefix() {
		return "screen.refugee.splash.story." + id + ".";
	}

	public String lineKey(int index) {
		return keyPrefix() + Math.max(0, index);
	}

	public static SpecialStoryKind byId(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		for (SpecialStoryKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
