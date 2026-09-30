package luowei.refugee.livability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * {@code refugee.json} 的 {@code livability} 段。缺省即规格里的默认数。
 */
public final class LivabilityRules {
	public static final LivabilityRules CURRENT = new LivabilityRules();

	public double statMin = 0.0;
	public double statMax = 20.0;
	public double satietyBase = -2.0;
	public double satietyLabor = -2.0;
	public double satietyNoSleep = -3.0;
	/** 挖、放或催熟满这么多次，体力减 1、饱食减 {@link #laborSatietyCost}。共用一条计数。 */
	public int laborBlockThreshold = 200;
	public double laborSatietyCost = 0.5;
	/** 真正打出这么多次，饱食和体力各减 1。 */
	public int attackThreshold = 20;
	public double dawnStaminaGain = 2.0;
	public double satietyToStaminaRate = 0.20;
	/** 当晚没躺下，清晨体力再扣这么多。睡了不扣。 */
	public double missedSleepStamina = 3.0;
	public double rebellionSpreadRadius = 16.0;
	public double[] foodGains = {3.0, 5.0, 6.5, 8.0, 9.0};
	public double foodTailDecay = 0.7;
	public double breadNutrition = 5.0;
	public double[][] hungerKnots = {
			{0, -1},
			{1, -0.5},
			{5, -0.5},
			{6, -0.1},
			{10, -0.1},
			{11, 0.1},
			{15, 0.1},
			{16, 0.25},
			{20, 0.25}
	};
	public double staminaLaborScale = 5.0;
	public double staminaRestScale = 5.0;
	public double staminaSleptScale = 5.0;
	public double staminaAwakeScale = 7.0;
	public double comfortBase = 10.0;
	public double comfortKeep = 0.6;
	public double comfortBlend = 0.4;
	/** 每次实际掉血，舒适减去掉血量乘这个数。 */
	public double hurtComfortScale = 0.01;
	/** 间距 0 / 1 / 2 / 3 及以上。 */
	public double gap0 = 0.0;
	public double gap1 = 2.0;
	public double gap2 = 3.0;
	public double gapFar = 4.0;
	/** 密度按张数：1–2、3–5、6–10、11–30、31–80、81 及以上。 */
	public double density2 = 7.0;
	public double density5 = 5.0;
	public double density10 = 3.0;
	public double density30 = 2.0;
	public double density80 = 1.0;
	public double density81 = 0.0;
	/** 放床、拆床时统计周围床的切比雪夫半径。 */
	public int bedLinkRadius = 7;
	public int metabolismInterval = 3000;
	public double comfortMetabolism = 0.01;
	public double staminaFromSatiety = 0.05;
	public double sleepLivingScale = 0.0005;
	public double effectBase = 1.0;
	public double effectDivisor = 2.0;
	public double laborStaminaOrigin = 10.0;
	public double laborStaminaWeight = 0.10;
	public double laborSatietyOrigin = 5.0;
	public double laborSatietyWeight = 0.05;
	public double laborComfortOrigin = 10.0;
	public double laborComfortWeight = 0.05;
	public double healStaminaOrigin = 5.0;
	public double healStaminaWeight = 0.20;
	public double healSatietyOrigin = 5.0;
	public double healSatietyWeight = 0.08;
	public double healComfortOrigin = 10.0;
	public double healComfortWeight = 0.12;
	/** 忠诚度不超过这里时，叛乱概率为 1。 */
	public double rebellionCertainLoyalty = 1.5;
	/** 忠诚度到这里时，叛乱概率为 {@link #rebellionMidChance}。 */
	public double rebellionMidLoyalty = 3.0;
	public double rebellionMidChance = 0.25;
	/** 超过中间点之后，忠诚度每高 1，概率乘上这个数。 */
	public double rebellionDecay = 0.04;
	public double initialSatiety = 10.0;
	public double initialStamina = 10.0;
	public double initialComfort = 10.0;

	public static LivabilityRules defaults() {
		return new LivabilityRules();
	}

	public void copyFrom(JsonObject json) {
		if (json == null || !json.has("livability") || !json.get("livability").isJsonObject()) {
			return;
		}
		JsonObject node = json.getAsJsonObject("livability");
		statMin = num(node, "statMin", statMin);
		statMax = num(node, "statMax", statMax);
		satietyBase = num(node, "satietyBase", satietyBase);
		satietyLabor = num(node, "satietyLabor", satietyLabor);
		satietyNoSleep = num(node, "satietyNoSleep", satietyNoSleep);
		laborBlockThreshold = Math.max(1, (int) num(node, "laborBlockThreshold", laborBlockThreshold));
		laborSatietyCost = num(node, "laborSatietyCost", laborSatietyCost);
		attackThreshold = Math.max(1, (int) num(node, "attackThreshold", attackThreshold));
		dawnStaminaGain = num(node, "dawnStaminaGain", dawnStaminaGain);
		satietyToStaminaRate = num(node, "satietyToStaminaRate", satietyToStaminaRate);
		missedSleepStamina = num(node, "missedSleepStamina", missedSleepStamina);
		rebellionSpreadRadius = num(node, "rebellionSpreadRadius", rebellionSpreadRadius);
		foodGains = nums(node, "foodGains", foodGains);
		foodTailDecay = num(node, "foodTailDecay", foodTailDecay);
		breadNutrition = Math.max(0.001, num(node, "breadNutrition", breadNutrition));
		hungerKnots = knots(node, "hungerKnots", hungerKnots);
		staminaLaborScale = num(node, "staminaLaborScale", staminaLaborScale);
		staminaRestScale = num(node, "staminaRestScale", staminaRestScale);
		staminaSleptScale = num(node, "staminaSleptScale", staminaSleptScale);
		staminaAwakeScale = num(node, "staminaAwakeScale", staminaAwakeScale);
		comfortBase = num(node, "comfortBase", comfortBase);
		comfortKeep = num(node, "comfortKeep", comfortKeep);
		comfortBlend = num(node, "comfortBlend", comfortBlend);
		hurtComfortScale = 0.01;
		gap0 = num(node, "gap0", gap0);
		gap1 = num(node, "gap1", gap1);
		gap2 = num(node, "gap2", gap2);
		gapFar = num(node, "gapFar", gapFar);
		density2 = num(node, "density2", density2);
		density5 = num(node, "density5", density5);
		density10 = num(node, "density10", density10);
		density30 = num(node, "density30", density30);
		density80 = num(node, "density80", density80);
		density81 = num(node, "density81", density81);
		bedLinkRadius = Math.max(1, (int) num(node, "bedLinkRadius", bedLinkRadius));
		metabolismInterval = Math.max(1, (int) num(node, "metabolismInterval", metabolismInterval));
		comfortMetabolism = num(node, "comfortMetabolism", comfortMetabolism);
		staminaFromSatiety = num(node, "staminaFromSatiety", staminaFromSatiety);
		sleepLivingScale = num(node, "sleepLivingScale", sleepLivingScale);
		effectBase = num(node, "effectBase", effectBase);
		effectDivisor = num(node, "effectDivisor", effectDivisor);
		if (Math.abs(effectDivisor) < 0.0001) {
			effectDivisor = 2.0;
		}
		laborStaminaOrigin = num(node, "laborStaminaOrigin", laborStaminaOrigin);
		laborStaminaWeight = num(node, "laborStaminaWeight", laborStaminaWeight);
		laborSatietyOrigin = num(node, "laborSatietyOrigin", laborSatietyOrigin);
		laborSatietyWeight = num(node, "laborSatietyWeight", laborSatietyWeight);
		laborComfortOrigin = num(node, "laborComfortOrigin", laborComfortOrigin);
		laborComfortWeight = num(node, "laborComfortWeight", laborComfortWeight);
		healStaminaOrigin = 5.0;
		healStaminaWeight = 0.20;
		healSatietyOrigin = 5.0;
		healSatietyWeight = 0.08;
		healComfortOrigin = 10.0;
		healComfortWeight = 0.12;
		rebellionCertainLoyalty = num(node, "rebellionCertainLoyalty", rebellionCertainLoyalty);
		rebellionMidLoyalty = num(node, "rebellionMidLoyalty", rebellionMidLoyalty);
		rebellionMidChance = clamp01(num(node, "rebellionMidChance", rebellionMidChance));
		rebellionDecay = clamp01(num(node, "rebellionDecay", rebellionDecay));
		initialSatiety = num(node, "initialSatiety", initialSatiety);
		initialStamina = num(node, "initialStamina", initialStamina);
		initialComfort = num(node, "initialComfort", initialComfort);
	}

	public JsonObject write() {
		JsonObject node = new JsonObject();
		node.addProperty("statMin", statMin);
		node.addProperty("statMax", statMax);
		node.addProperty("satietyBase", satietyBase);
		node.addProperty("satietyLabor", satietyLabor);
		node.addProperty("satietyNoSleep", satietyNoSleep);
		node.addProperty("laborBlockThreshold", laborBlockThreshold);
		node.addProperty("laborSatietyCost", laborSatietyCost);
		node.addProperty("attackThreshold", attackThreshold);
		node.addProperty("dawnStaminaGain", dawnStaminaGain);
		node.addProperty("satietyToStaminaRate", satietyToStaminaRate);
		node.addProperty("missedSleepStamina", missedSleepStamina);
		node.addProperty("rebellionSpreadRadius", rebellionSpreadRadius);
		node.add("foodGains", array(foodGains));
		node.addProperty("foodTailDecay", foodTailDecay);
		node.addProperty("breadNutrition", breadNutrition);
		node.add("hungerKnots", knotArray(hungerKnots));
		node.addProperty("staminaLaborScale", staminaLaborScale);
		node.addProperty("staminaRestScale", staminaRestScale);
		node.addProperty("staminaSleptScale", staminaSleptScale);
		node.addProperty("staminaAwakeScale", staminaAwakeScale);
		node.addProperty("comfortBase", comfortBase);
		node.addProperty("comfortKeep", comfortKeep);
		node.addProperty("comfortBlend", comfortBlend);
		node.addProperty("hurtComfortScale", hurtComfortScale);
		node.addProperty("gap0", gap0);
		node.addProperty("gap1", gap1);
		node.addProperty("gap2", gap2);
		node.addProperty("gapFar", gapFar);
		node.addProperty("density2", density2);
		node.addProperty("density5", density5);
		node.addProperty("density10", density10);
		node.addProperty("density30", density30);
		node.addProperty("density80", density80);
		node.addProperty("density81", density81);
		node.addProperty("bedLinkRadius", bedLinkRadius);
		node.addProperty("metabolismInterval", metabolismInterval);
		node.addProperty("comfortMetabolism", comfortMetabolism);
		node.addProperty("staminaFromSatiety", staminaFromSatiety);
		node.addProperty("sleepLivingScale", sleepLivingScale);
		node.addProperty("effectBase", effectBase);
		node.addProperty("effectDivisor", effectDivisor);
		node.addProperty("laborStaminaOrigin", laborStaminaOrigin);
		node.addProperty("laborStaminaWeight", laborStaminaWeight);
		node.addProperty("laborSatietyOrigin", laborSatietyOrigin);
		node.addProperty("laborSatietyWeight", laborSatietyWeight);
		node.addProperty("laborComfortOrigin", laborComfortOrigin);
		node.addProperty("laborComfortWeight", laborComfortWeight);
		node.addProperty("healStaminaOrigin", healStaminaOrigin);
		node.addProperty("healStaminaWeight", healStaminaWeight);
		node.addProperty("healSatietyOrigin", healSatietyOrigin);
		node.addProperty("healSatietyWeight", healSatietyWeight);
		node.addProperty("healComfortOrigin", healComfortOrigin);
		node.addProperty("healComfortWeight", healComfortWeight);
		node.addProperty("rebellionCertainLoyalty", rebellionCertainLoyalty);
		node.addProperty("rebellionMidLoyalty", rebellionMidLoyalty);
		node.addProperty("rebellionMidChance", rebellionMidChance);
		node.addProperty("rebellionDecay", rebellionDecay);
		node.addProperty("initialSatiety", initialSatiety);
		node.addProperty("initialStamina", initialStamina);
		node.addProperty("initialComfort", initialComfort);
		return node;
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static double num(JsonObject json, String key, double fallback) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return json.get(key).getAsDouble();
	}

	private static double[] nums(JsonObject json, String key, double[] fallback) {
		if (!json.has(key) || !json.get(key).isJsonArray()) {
			return fallback;
		}
		JsonArray array = json.getAsJsonArray(key);
		if (array.isEmpty()) {
			return fallback;
		}
		double[] values = new double[array.size()];
		for (int i = 0; i < array.size(); i++) {
			JsonElement element = array.get(i);
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			values[i] = element.getAsDouble();
		}
		return values;
	}

	private static double[][] knots(JsonObject json, String key, double[][] fallback) {
		if (!json.has(key) || !json.get(key).isJsonArray()) {
			return fallback;
		}
		JsonArray array = json.getAsJsonArray(key);
		if (array.isEmpty()) {
			return fallback;
		}
		double[][] values = new double[array.size()][2];
		for (int i = 0; i < array.size(); i++) {
			JsonElement element = array.get(i);
			if (!element.isJsonArray() || element.getAsJsonArray().size() < 2) {
				return fallback;
			}
			JsonArray pair = element.getAsJsonArray();
			if (!pair.get(0).isJsonPrimitive() || !pair.get(1).isJsonPrimitive()) {
				return fallback;
			}
			values[i][0] = pair.get(0).getAsDouble();
			values[i][1] = pair.get(1).getAsDouble();
		}
		return values;
	}

	private static JsonArray array(double[] values) {
		JsonArray array = new JsonArray();
		for (double value : values) {
			array.add(value);
		}
		return array;
	}

	private static JsonArray knotArray(double[][] knots) {
		JsonArray array = new JsonArray();
		for (double[] knot : knots) {
			JsonArray pair = new JsonArray();
			pair.add(knot[0]);
			pair.add(knot[1]);
			array.add(pair);
		}
		return array;
	}
}
