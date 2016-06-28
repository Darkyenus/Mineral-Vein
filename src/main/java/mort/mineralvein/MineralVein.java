
package mort.mineralvein;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static org.bukkit.event.EventPriority.LOW;

/** @author Martin */
public class MineralVein extends JavaPlugin implements Listener {
	public static MineralVein plugin;
	private final HashMap<World, OreVein[]> data = new HashMap<>();
	private OreVein[] def = null;
	private Configuration conf;
	public boolean debug;
	private int applyTaskId = Integer.MIN_VALUE;

	public MineralVein () {
		plugin = this;
	}

	@Override
	public void onEnable () {
		getServer().getPluginManager().registerEvents(this, this);

		conf = getConfig();
		conf.options().copyDefaults(true);
		saveConfig();

		debug = conf.getBoolean("debug", false);
	}

	@EventHandler(priority = LOW)
	public void onWorldInit (WorldInitEvent event) {
		if (event.getWorld().getEnvironment() == World.Environment.NORMAL) {
			event.getWorld().getPopulators().add(new VeinPopulator());
		}
	}

	@Override
	public void onDisable () {
		super.onDisable();
		MineralVein.plugin.getServer().getScheduler().cancelTasks(MineralVein.plugin);
	}

	public OreVein[] getWorldData (World w) {
		if (data.containsKey(w)) {
			return data.get(w);
		} else if (conf.contains(w.getName())) {
			data.put(w, OreVein.loadConf(conf.getMapList(w.getName())));
			return data.get(w);
		} else if (def != null) {
			return def;
		} else if (conf.contains("default")) {
			def = OreVein.loadConf(conf.getMapList("default"));
			return def;
		}
		return null;
	}

	@Override
	public boolean onCommand (final CommandSender cs, final Command command, final String string, final String[] args) {

		if (!(cs instanceof ConsoleCommandSender)) {
			cs.sendMessage("Only console may call this");
			return true;
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {
			cs.sendMessage("\n\nMineralVein apply canceled.");
			MineralVein.plugin.getServer().getScheduler().cancelTasks(MineralVein.plugin);
			return true;
		}

		if ((args.length == 0) || (!args[0].equalsIgnoreCase("apply"))) {
			cs.sendMessage("Usage:" + command.getUsage());
			return true;
		}

		World w = args.length >= 2 ? getServer().getWorld(args[1]) : null;

		if (w == null) {
			final List<World> worlds = getServer().getWorlds();
			cs.sendMessage("Given world not found. Existing worlds:");
			for (World world : worlds) {
				cs.sendMessage(world.getName() + " (" + world.getEnvironment() + ")");
			}
			return true;
		}

		if (applyTaskId != Integer.MIN_VALUE) {
			cs.sendMessage("A mineralvein apply is already in process.");
			return true;
		}

		final int x, z;
		double chunksPerRun = 500;

		if (args.length >= 4) {
			try {
				x = Integer.parseInt(args[2]);
				z = Integer.parseInt(args[3]);
			} catch (Exception ex) {
				return false;
			}
		} else {
			final Chunk spawnChunk = w.getSpawnLocation().getChunk();
			x = spawnChunk.getX();
			z = spawnChunk.getZ();
		}

		if (args.length >= 5) {
			try {
				chunksPerRun = Double.parseDouble(args[4]);
			} catch (Exception ex) {
				return false;
			}
		}

		applyTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new WorldApplier(w, x, z, cs, chunksPerRun), 0,
			1);

		cs.sendMessage("Mineral Vein generation started from [" + x + ", " + z + "] in batches of " + chunksPerRun);
		return true;
	}

	private class WorldApplier implements Runnable {

		private final World world;
		private final VeinPopulator pop;
		private final Random random = new Random();

		private final int chunksPerRun;
		private final double chunkChance;
		private final CommandSender owner;

		private final int xOff, zOff;
		private int x = 0, z = 0, dx = 0, dz = -1;
		private int processed = 0;

		public WorldApplier (World world, int x, int z, CommandSender owner, double chunksPerRun) {
			this.world = world;
			this.owner = owner;

			pop:
			{
				for (BlockPopulator pop : world.getPopulators()) {
					if (pop instanceof VeinPopulator) {
						this.pop = (VeinPopulator)pop;
						break pop;
					}
				}
				this.pop = new VeinPopulator();
			}

			if (chunksPerRun < 1) {
				this.chunkChance = chunksPerRun;
				this.chunksPerRun = 1;
			} else {
				this.chunkChance = 1;
				this.chunksPerRun = (int)chunksPerRun;
			}

			this.xOff = x;
			this.zOff = z;
		}

		/*
		 * World applier goes in a spiral from the given origin, populating chunks. When it processes a side of a spiral without any
		 * generated chunks to populate, it increments turnsWithoutProcessing. When this number is greater than
		 * TURNS_WITHOUT_PROCESSING_CUTOFF, algorithm terminates.
		 */
		private boolean processedOnThisSide = false;
		private int turnsWithoutProcessing = 0;
		private static final int TURNS_WITHOUT_PROCESSING_CUTOFF = 5;

		@Override
		public void run () {

			for (int c = 0; c < chunksPerRun; c++) {
				if (random.nextDouble() < chunkChance) {
					if (populateChunk()) {
						processedOnThisSide = true;
					}
					processed++;
				}

				if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
					int t = dx;
					dx = -dz;
					dz = t;

					if (processedOnThisSide) {
						turnsWithoutProcessing = 0;
					} else {
						turnsWithoutProcessing++;
						if (turnsWithoutProcessing >= TURNS_WITHOUT_PROCESSING_CUTOFF) {
							owner.sendMessage("MineralVein applied to world " + world.getName() + ".");
							MineralVein.plugin.getServer().getScheduler().cancelTask(applyTaskId);
							applyTaskId = Integer.MIN_VALUE;
						}
					}
				}

				x += dx;
				z += dz;
			}

			System.out.println(String.format("Applying MineralVein to %s. %3d processed, %4.1fMB free", world.getName(), processed,
				((Runtime.getRuntime().freeMemory()) / (double)(1024 * 1024))));
		}

		public boolean populateChunk () {
			final World world = this.world;
			final int x = this.x + xOff;
			final int z = this.z + zOff;

			final boolean unload;
			if (world.isChunkLoaded(x, z)) {
				unload = false;
			} else {
				unload = true;
				if (!world.loadChunk(x, z, false)) {
					if (debug) System.out.println("Not populating " + x + ", " + z);
					return false;
				}
			}

			pop.populate(world, random, world.getChunkAt(x, z));

			if (unload) {
				world.unloadChunk(x, z);
			}
			if (debug) System.out.println("Populated " + x + ", " + z + "   " + unload);
			return true;
		}
	}
}
