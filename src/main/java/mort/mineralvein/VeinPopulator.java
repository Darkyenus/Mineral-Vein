package mort.mineralvein;

import org.bukkit.generator.BlockPopulator;
import org.bukkit.World;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.Chunk;
import java.util.HashSet;
import java.util.HashMap;
import org.bukkit.util.noise.NoiseGenerator;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
/** 
 *
 * @author Martin
 */
public class VeinPopulator extends BlockPopulator{
	
	private static HashMap<World,NoiseGenerator[]> noise = new HashMap<World,NoiseGenerator[]>();
	private static MVMaterial stoneID = new MVMaterial( Material.STONE );
	
	@Override
	public void populate( World w, Random r, Chunk ch ){
		if(MineralVein.plugin.debug)
			System.out.print("Populating chunk "+ch);
		
		OreVein[] ores = MineralVein.plugin.getWorldData(w);
		if( ores==null ) //no ores defined for this worlds
			return;
		
		NoiseGenerator[] noiseGen;
		if( !noise.containsKey(w) ){
			noiseGen = new NoiseGenerator[ores.length*2];
			for(int i=0;i<ores.length;i++){
				noiseGen[i*2] = new SimplexNoiseGenerator( w.getSeed() * ores[i].seed );
				noiseGen[i*2+1] = new SimplexNoiseGenerator( w.getSeed() * ores[i].seed * 5646468L );
			}
			noise.put(w, noiseGen);
		}
		else
			noiseGen = noise.get(w);
		
		double roll, chance;
		double[] heightCache = new double[ores.length];
		double[] densCache = new double[ores.length];
		int chX = ch.getX()*16;
		int chZ = ch.getZ()*16;
		int maxHeight;
		HashSet block = new HashSet();
		Block targetBlock;
		for(OreVein ore:ores){
			if( !ore.addMode ){
				block.add(ore.mat);
			}
		}
		for(int x=chX;x<(16+chX);x++){
			for(int z=chZ;z<(16+chZ);z++){
				double exclusiveDens = 1;
				maxHeight = ch.getWorld().getHighestBlockAt(x, z).getY();
				for(int i=0;i<ores.length;i++){
					heightCache[i] = getVeinHeight( x,z,ores[i],noiseGen[i*2], ores[i].heightLength );
					if( biomeChecks( ch.getBlock(x, 64, z) , ores[i]) )
						densCache[i] = getVeinDensity( x,z,ores[i],noiseGen[i*2+1], ores[i].densLength ) * exclusiveDens;
					else
						densCache[i] = 0;
					if(ores[i].exclusive)
						exclusiveDens -= densCache[i];
					if( ores[i].heighRel )
						heightCache[i] *= maxHeight;
				}
				for(int y=0;y<128;y++){
					targetBlock = w.getBlockAt(x,y,z);
					MVMaterial blockType = new MVMaterial( targetBlock );
					if( !blockType.equals(stoneID) ){
							if( block.contains(blockType) )
								targetBlock.setTypeIdAndData(stoneID.id, stoneID.data, false);
							else
								continue;
					}
					roll = r.nextDouble();
					for(int i=0;i<ores.length;i++){
						chance = getOreChance(y,ores[i],w.getSeed(),heightCache[i],densCache[i] );
						if( roll < chance ){
							targetBlock.setTypeIdAndData(ores[i].mat.id, ores[i].mat.data, false);
							break;
						}
						else roll-= chance;
					}
				}
			}
		}
	}
	
	public double getOreChance( int y, OreVein ore, long seed, double veinHeight, double veinDensity ){
		//chance on exact same height - 50%
		double chance = Math.abs(y-veinHeight );
		if(chance>ore.maxSpan) return 0;
		else return Math.max( ((Math.cos( chance*Math.PI/ore.maxSpan ) +1)/2)*veinDensity, 0);
	}
	
	double getVeinHeight(double x, double z, OreVein ore, NoiseGenerator noise, double heightLength){
		return noise.noise(x/heightLength, z/heightLength)*ore.areaSpan + ore.areaHeight;
	}
	
	double getVeinDensity(double x, double z, OreVein ore, NoiseGenerator noise, double densLength){
		return (noise.noise(x/densLength, z/densLength)+ore.densBonus)*ore.density;
	}
	
	public boolean biomeChecks( Block bl, OreVein ore ){
		Biome bm = null;
		if( ore.noBiomes != null ){
			bm = bl.getBiome();
			for( Biome biome : ore.noBiomes ){
				if( bm.equals(biome) )
					return false;
			}
		}
		if(ore.biomes == null)
			return true;
		if(bm==null) 
		   bm = bl.getBiome();
		for( Biome biome : ore.biomes ){
				if( bm.equals(biome) )
					return true;
			}
		
		return false;
	}
	
}