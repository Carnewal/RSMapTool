package org.fabian.rsmt;

import java.util.HashMap;

import com.runescape.cache.Archive;
import com.runescape.net.Buffer;

public class MapIndex {
	private static HashMap<Integer, MapIndex> mapIndices = new HashMap<>();
	public static void loadTable(Archive versionList) {
		Buffer buffer = new Buffer(versionList.getFile("map_index"));
		boolean isOld = buffer.payload.length % 7 == 0;
		int length = buffer.payload.length / 7;
		if (!isOld)
			length = buffer.getShort();
		for (int i = 0; i < length; i++) {
			int hash = buffer.getUnsignedLEShort();
			int map = buffer.getUnsignedLEShort();
			int land = buffer.getUnsignedLEShort();
			if (isOld) buffer.getUnsignedByte();
			mapIndices.put(hash, new MapIndex(map, land));
		}
		System.out.println("Loaded "+mapIndices.size()+" map indices.");
	}
	public static MapIndex getMapTile(int x, int y) {
		return getMapTile((x << 8) + y);
	}
	private static MapIndex getMapTile(int hash) {
		return mapIndices.get(hash);
	}
	private int mapFile;
	public int getMapFile() {
		return mapFile;
	}
	public int getLandFile() {
		return landFile;
	}
	private int landFile;
	public MapIndex(int mapFile, int landFile) {
		super();
		this.mapFile = mapFile;
		this.landFile = landFile;
	}
}
