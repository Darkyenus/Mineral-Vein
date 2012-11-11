package mort.mineralvein;

import org.bukkit.Chunk;

/**
 * @author Martin
 */
class MVChunk {
	public final int x;
	public final int z;

	public MVChunk(Chunk ch) {
		this.x = ch.getX();
		this.z = ch.getZ();
	}

	public MVChunk(int x, int z) {
		this.x = x;
		this.z = z;
	}

	@Override
	public int hashCode() {
		return ((x & 0xFFFF) << 16) + (z & 0xFFFF);
	}

	@Override
	public boolean equals(Object e) {
		return (e instanceof MVChunk) && (e.hashCode() == hashCode());
	}
}
