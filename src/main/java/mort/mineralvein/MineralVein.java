package mort.mineralvein;

import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
/**
 *
 * @author Martin
 */
public class MineralVein extends JavaPlugin{
	
	public static MineralVein plugin;
	public HashMap<World, OreVein[]> data = new HashMap<World, OreVein[]>();
	public OreVein[] def = null;
	public Configuration conf;
	private MVExecutor executor;
	public boolean debug;
	
	public MineralVein(){
		plugin = this;
	}
	
	@Override
	public void onEnable(){
		executor = new MVExecutor();
		getServer().getPluginManager().registerEvent( WorldInitEvent.class, new MVListener(), EventPriority.LOW, executor, this);

		getServer().getPluginCommand("mineralvein").setExecutor(this);
		
		conf = getConfig();
		conf.options().copyDefaults(true);
		saveConfig();

		debug = conf.getBoolean("debug", false);
		
	}
	
	@Override
	public void onDisable(){
	}
	
	public OreVein[] getWorldData(World w){
		if(data.containsKey(w))
			return data.get(w);
		else if ( conf.contains(w.getName()) ){
			data.put(w, OreVein.loadConf( conf.getMapList( w.getName() ) ) );
			return data.get(w);
		}
		else if( def!=null )
			return def;
		else if( conf.contains( "default" ) ){
			def = OreVein.loadConf( conf.getMapList("default") );
			return def;
		}
		return null;
	}
	
	@Override
	public boolean onCommand(CommandSender cs, Command cmnd, String string, String[] args){
		if( !(cs instanceof ConsoleCommandSender) ){
			cs.sendMessage("Only console may call this");
			return true;
		}
		if(args.length<5 || !args[0].equalsIgnoreCase("apply") ){
			cs.sendMessage( "Usage:" + cmnd.getUsage() );
			return true;
		}
		
		World w = getServer().getWorld( args[1] );
		
		if( w==null){
			int id = -1;
			java.util.List<World> worlds = getServer().getWorlds();
			try{
				id = Integer.parseInt(args[1]);
			}catch(Exception e){
				cs.sendMessage("Given world not found. Try using world id: ");	
				for( int i = 0; i < worlds.size(); i++ ){
					cs.sendMessage( i+". "+worlds.get(i).getName()+" ("+worlds.get(i).getEnvironment()+")" );
				}
				return true;
			}
			if( id < 0 || id >= worlds.size() ){
				cs.sendMessage( "No world at this ID." );
				return true;
			}
			w = worlds.get(id);
		}
		
		int x, z, width, length, chunksPerRun = 20;
		boolean around = false;
		
		try{
			x = Integer.parseInt( args[2] );
			z = Integer.parseInt( args[3] );
			width = Integer.parseInt( args[4] );
			length = Integer.parseInt( args[4] );
		}catch(Exception ex){
			return false;
		}
		if(length == 0 || width == 0){
			return false;
		}
		if(length < 0){
			x -= length;
			length = -length;
		}
		if(width < 0){
			z -= width;
			width = -width;
		}
		if(args.length>5){
			if("1".equals(args[5]) || "true".equalsIgnoreCase(args[5])){
				around = true;
			}
			if(args.length>6){
				try{
					chunksPerRun = Integer.parseInt( args[6] );
				}catch(Exception ex){
					return false;
				}
			}
		}
		
		if(around){
			x -= width/2;
			z -= length/2;
		}
		
		getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new WorldApplier(w, x, z, cs, width, length, chunksPerRun), 0, 1);
		
		cs.sendMessage("Mineral Vein application started");
		return true;
	}
	
	private class WorldApplier implements Runnable{
		private World w;
		int x;
		int z;
		int width;
		int length;
		int chunksPerRun;
		CommandSender cs;
		VeinPopulator pop;
		Random rnd;
		List<MVChunk> chunks;
		int chunksLength;
		PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));
		
		public WorldApplier(World w, int x, int z, CommandSender cs, int width, int length, int chunksPerRun){
			this.w = w;
			this.x = x;
			this.z = z;
			this.width = width;
			this.length = length;
			this.cs = cs;
			this.chunks = new ArrayList<MVChunk>(width*length);
			this.rnd = new Random();
			
			for( BlockPopulator pop : w.getPopulators() ){
				if(pop instanceof VeinPopulator){
					this.pop = (VeinPopulator) pop;
					break;
				}
			}
			
			if(this.pop==null){
				this.pop = new VeinPopulator();
			}
			
			for(int X = x; X < (x+length); X++){
				for(int Z = z; Z < (z+width);Z++ ){
					chunks.add(new MVChunk(X,Z));
				}
			}
			chunksLength = chunks.size();
		}
		
		@Override
		public void run(){
			if(chunksPerRun > chunks.size()){
				chunksPerRun = chunks.size();
			}
			if(Runtime.getRuntime().freeMemory()<(25*1024*1024)){
				cs.sendMessage("MineralVein apply stopped - out of memory.");
				MineralVein.plugin.getServer().getScheduler().cancelTasks(MineralVein.plugin);
				return;
			}
			for(MVChunk chunk : chunks.subList(0, chunksPerRun)){
				if(!applyChunkSimple(w, chunk.x, chunk.z, pop, rnd, true)){
					//TODO ?
				}
			}
			chunks = chunks.subList(chunksPerRun,chunks.size());
			System.runFinalization();//Oh no... Why am I doing this? D:
			System.gc();
			out.printf("Applying MineralVein to "+w.getName()+". %3.2f%%, %4.1fMB free   \r",((((chunksLength-chunks.size()))*100)/(double)chunksLength),((Runtime.getRuntime().freeMemory())/(double)(1024*1024)));
			if(chunks.size() == 0){
				out.print("\n");
				cs.sendMessage("MineralVein applied to world " + w.getName() + ".");
				MineralVein.plugin.getServer().getScheduler().cancelTasks(MineralVein.plugin);
			}
		}
		
		public boolean applyChunkSimple(World w, int x, int z, BlockPopulator pop, Random r, boolean checkLoad){
			boolean unload = false;
			if(checkLoad){
				if( !w.isChunkLoaded( x,z ) ){
					if( !w.loadChunk(x, z, true) ){
						cs.sendMessage("Failed to load chunk, coordinates: x:"+x*16+", z:"+z*16+"\n");
						return false;
					}
					unload = true;
				}
			}
			
			pop.populate(w, r, w.getChunkAt(x, z));
			
			if(unload){
				try{
					w.unloadChunk(x,z);
				}catch (Exception e){
					return false;
				}
			}
			return true;
		}
	
	}
	
}
