package mort.mineralvein;

import org.bukkit.World.Environment;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.EventExecutor;

/**
 * @author Martin
 */
class MVExecutor implements EventExecutor {
	@Override
	public void execute(Listener ll, Event e) throws EventException {
		if (e instanceof WorldInitEvent) {
			WorldInitEvent event = (WorldInitEvent) e;
			if (event.getWorld().getEnvironment() == Environment.NORMAL) {
				event.getWorld().getPopulators().add(new VeinPopulator());
			}
		}
	}
}
