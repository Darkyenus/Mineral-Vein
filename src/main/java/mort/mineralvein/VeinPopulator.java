
package mort.mineralvein;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.util.noise.NoiseGenerator;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

/** @author Martin */
class VeinPopulator extends BlockPopulator {
	private static final HashMap<String, NoiseGenerator[]> noise = new HashMap<>();
	private static final MVMaterial stoneID = new MVMaterial(Material.STONE);

	@Override
	public void populate (World w, Random r, Chunk ch) {
		if (MineralVein.plugin.debug) {
			System.out.print("Populating chunk " + ch);
		}

		OreVein[] ores = MineralVein.plugin.getWorldData(w);
		if (ores == null) // no ores defined for this worlds
		{
			return;
		}

		NoiseGenerator[] noiseGen;
		if (!noise.containsKey(w.getName())) {
			noiseGen = new NoiseGenerator[ores.length * 2];
			for (int i = 0; i < ores.length; i++) {
				noiseGen[i * 2] = new SimplexNoiseGenerator(w.getSeed() * ores[i].seed);
				noiseGen[i * 2 + 1] = new SimplexNoiseGenerator(w.getSeed() * ores[i].seed * 5646468L);
			}
			noise.put(w.getName(), noiseGen);
		} else {
			noiseGen = noise.get(w.getName());
		}

		double roll, chance;
		double[] heightCache = new double[ores.length];
		double[] densCache = new double[ores.length];
		int chX = ch.getX() * 16;
		int chZ = ch.getZ() * 16;
		int maxHeight;
		HashSet<MVMaterial> block = new HashSet<>();
		Block targetBlock, biomeCheck;
		for (OreVein ore : ores) {
			if (!ore.addMode) {
				block.add(ore.material);
			}
		}
		for (int x = chX; x < (16 + chX); x++) {
			for (int z = chZ; z < (16 + chZ); z++) {
				double exclusiveDens = 1;
				maxHeight = ch.getWorld().getHighestBlockAt(x, z).getY();
				biomeCheck = ch.getBlock(x, 64, z);
				for (int i = 0; i < ores.length; i++) {
					heightCache[i] = getVeinHeight(x, z, ores[i], noiseGen[i * 2], ores[i].heightLength);
					if (biomeChecks(biomeCheck, ores[i])) {
						densCache[i] = getVeinDensity(x, z, ores[i], noiseGen[i * 2 + 1], ores[i].densLength) * exclusiveDens;
					} else {
						densCache[i] = 0;
					}
					if (ores[i].exclusive) {
						exclusiveDens -= densCache[i];
					}
					if (ores[i].heightRel) {
						heightCache[i] *= maxHeight;
					}
				}
				for (int y = 0; y < maxHeight; y++) {
					targetBlock = w.getBlockAt(x, y, z);
					MVMaterial blockType = new MVMaterial(targetBlock);
					//Remove old ores
					if (!blockType.equals(stoneID)) {
						if (block.contains(blockType)) {
							targetBlock.setTypeIdAndData(stoneID.id, stoneID.data, false);
						} else {
							continue;
						}
					}
					roll = r.nextDouble();
					for (int i = 0; i < ores.length; i++) {
						chance = getOreChance(y, ores[i], heightCache[i], densCache[i]);
						if (roll < chance) {
							targetBlock.setTypeIdAndData(ores[i].material.id, ores[i].material.data, false);
							break;
						} else {
							roll -= chance;
						}
					}
				}
			}
		}
	}

	double getOreChance (int y, OreVein ore, double veinHeight, double veinDensity) {
		// chance on exact same height - 50%
		double chance = Math.abs(y - veinHeight);
		if (chance > ore.thickness) {
			return 0;
		} else {
			return Math.max(((Math.cos(chance * Math.PI / ore.thickness) + 1) / 2) * veinDensity, 0);
		}
	}

	double getVeinHeight (double x, double z, OreVein ore, NoiseGenerator noise, double heightLength) {
		return noise.noise(x / heightLength, z / heightLength) * ore.heightVar + ore.heightAvg;
	}

	double getVeinDensity (double x, double z, OreVein ore, NoiseGenerator noise, double densLength) {
		return (noise.noise(x / densLength, z / densLength) + ore.densBonus) * ore.density;
	}

	boolean biomeChecks (Block bl, OreVein ore) {
		Biome bm = null;
		if (ore.noBiomes != null) {
			bm = bl.getBiome();
			for (Biome biome : ore.noBiomes) {
				if (bm.equals(biome)) {
					return false;
				}
			}
		}
		if (ore.biomes == null) {
			return true;
		}
		if (bm == null) {
			bm = bl.getBiome();
		}
		for (Biome biome : ore.biomes) {
			if (bm.equals(biome)) {
				return true;
			}
		}

		return false;
	}
}
