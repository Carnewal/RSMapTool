package org.fabian.rsmt;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import com.jme3.math.ColorRGBA;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.HeightMap;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.runescape.cache.def.FloorDefinition;
import com.runescape.media.Rasterizer3D;
import com.runescape.net.Buffer;
import com.runescape.scene.Region;
import com.zero_separation.plugins.imagepainter.ImagePainter;
import com.zero_separation.plugins.imagepainter.ImagePainter.BlendMode;

public class RSMap {
	private int baseX;
	private int baseY;
	private Buffer buffer;
	public int[][][] heightmap = new int[4][64][64];
	public int[][][] overlayFloorId = new int[4][64][64];
	public int[][][] overlayClippingPath = new int[4][64][64];
	public int[][][] overlayRotation = new int[4][64][64];
	public int[][][] renderRuleFlag = new int[4][64][64];
	public int[][][] underlayFloorId = new int[4][64][64];
	public boolean[][][] autoHeight = new boolean[4][64][64];
	public int[][][] getHeightmap() {
		return heightmap;
	}

	public RSMap(int baseX, int baseY, Buffer buffer) {
		this.baseX = baseX;
		this.baseY = baseY;
		this.buffer = buffer;
		for (int z = 0; z < 4; z++) {
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 64; y++) {
					readTile(z, x, y);
				}
			}
		}
	}
	public byte[] saveMap() {
		Buffer buffer = new Buffer(new byte[1024*300]); // 300KB should suffice for a big map.
		for (int z = 0; z < 4; z++) {
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 64; y++) {
					if (overlayFloorId[z][x][y] != 0) {
						buffer.put((overlayClippingPath[z][x][y] * 4) + (overlayRotation[z][x][y] & 3) + 2);
						buffer.put(overlayFloorId[z][x][y]);
					}
					if (renderRuleFlag[z][x][y] != 0)
						buffer.put(renderRuleFlag[z][x][y] + 49);
					if (underlayFloorId[z][x][y] != 0)
						buffer.put(underlayFloorId[z][x][y] + 81);
					if (autoHeight[z][x][y]) {
						buffer.put(0);
					} else {
						if (z == 0) {
							buffer.put(1);
							buffer.put(heightmap[z][x][y] / 8);
						} else {
							buffer.put(1);
							int height = (heightmap[z][x][y] - heightmap[z-1][x][y]) / 8;
							if (height == 0) height = 1;
							buffer.put(height);
						}
					}
				}
			}
		}
		byte[] compacted = new byte[buffer.offset];
		System.arraycopy(buffer.payload, 0, compacted, 0, compacted.length);
		return compacted;
	}
	private void readTile(int z, int x, int y) {
		while (true) {
			int opcode = buffer.getUnsignedByte();
			if (opcode == 0) {
				if (z == 0) {
					heightmap[z][x][y] = Region.calculateVertexHeight(932731
							+ baseX + x, 556238 + baseY + y) * 8;
				} else {
					heightmap[z][x][y] = heightmap[z - 1][x][y] + 240;
				}
				autoHeight[z][x][y] = true;
				break;
			} else if (opcode == 1) {
				int height = buffer.getUnsignedByte();
				if (height == 1)
					height = 0;
				if (z == 0) {
					heightmap[z][x][y] = height * 8;
				} else {
					heightmap[z][x][y] = heightmap[z-1][x][y] + (height * 8);
				}
				break;
			} else if (opcode <= 49) {
				overlayFloorId[z][x][y] = buffer.get();
				overlayClippingPath[z][x][y] = (byte) ((opcode - 2) / 4);
				overlayRotation[z][x][y] = (byte) (opcode - 2);
			} else if (opcode <= 81) {
				renderRuleFlag[z][x][y] = (byte) (opcode - 49);
			} else {
				underlayFloorId[z][x][y] = (byte) (opcode - 81);
			}
		}
	}

	public AbstractHeightMap getHeightMap(int z) {
		final int[][] hm = heightmap[z];
		return new AbstractHeightMap() {

			@Override
			public boolean load() {
				
				size = 64;
				heightData = new float[64 * 64];
				for (int x = 0; x < 64; x++) {
					for (int y = 0; y < 64; y++) {
						heightData[(x * 64) + y] = hm[x][y] / 128f;
					}
				}
				return true;
			}

		};
	}
	public static final int[][] shapedTilePointData = { { 1, 3, 5, 7 },
		{ 1, 3, 5, 7 }, { 1, 3, 5, 7 }, { 1, 3, 5, 7, 6 },
		{ 1, 3, 5, 7, 6 }, { 1, 3, 5, 7, 6 }, { 1, 3, 5, 7, 6 },
		{ 1, 3, 5, 7, 2, 6 }, { 1, 3, 5, 7, 2, 8 }, { 1, 3, 5, 7, 2, 8 },
		{ 1, 3, 5, 7, 11, 12 }, { 1, 3, 5, 7, 11, 12 },
		{ 1, 3, 5, 7, 13, 14 } };
	public static final int[][] shapedTileElementData = {
		{ 0, 1, 2, 3, 0, 0, 1, 3 },
		{ 1, 1, 2, 3, 1, 0, 1, 3 },
		{ 0, 1, 2, 3, 1, 0, 1, 3 },
		{ 0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3 },
		{ 0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4 },
		{ 0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4 },
		{ 0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3 },
		{ 0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3 },
		{ 0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5 },
		{ 0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5 },
		{ 0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4,
			2, 3 },
			{ 1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4,
				2, 3 },
				{ 1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1,
					2, 5 } };

	public static void drawShape(Graphics graphics, byte shapeA, byte shapeB, int x, int y, int colO, int colU) {
		int tileW = TILE_RESOLUTION;
		int halfW = tileW / 2;
		int quarterW = tileW / 4;
		int tqW = (tileW * 3) / 4;
		int shapedTileMesh[] = shapedTilePointData[shapeA+1];
		int meshLength = shapedTileMesh.length;
		int[] vx = new int[meshLength];
		int[] vy = new int[meshLength];
		for (int vertexPtr = 0; vertexPtr < meshLength; vertexPtr++) {
			int vertexType = shapedTileMesh[vertexPtr];
			if ((vertexType & 1) == 0 && vertexType <= 8)
				vertexType = (vertexType - shapeB - shapeB - 1 & 7) + 1;
			if (vertexType > 8 && vertexType <= 12)
				vertexType = (vertexType - 9 - shapeB & 3) + 9;
			if (vertexType > 12 && vertexType <= 16)
				vertexType = (vertexType - 13 - shapeB & 3) + 13;
			int vertexX;
			int vertexY;
			if (vertexType == 1) {
				vertexX = x;
				vertexY = y;
			} else if (vertexType == 2) {
				vertexX = x + halfW;
				vertexY = y;
			} else if (vertexType == 3) {
				vertexX = x + tileW;
				vertexY = y;
			} else if (vertexType == 4) {
				vertexX = x + tileW;
				vertexY = y + halfW;
			} else if (vertexType == 5) {
				vertexX = x + tileW;
				vertexY = y + tileW;
			} else if (vertexType == 6) {
				vertexX = x + halfW;
				vertexY = y + tileW;
			} else if (vertexType == 7) {
				vertexX = x;
				vertexY = y + tileW;
			} else if (vertexType == 8) {
				vertexX = x;
				vertexY = y + halfW;
			} else if (vertexType == 9) {
				vertexX = x + halfW;
				vertexY = y + quarterW;
			} else if (vertexType == 10) {
				vertexX = x + tqW;
				vertexY = y + halfW;
			} else if (vertexType == 11) {
				vertexX = x + halfW;
				vertexY = y + tqW;
			} else if (vertexType == 12) {
				vertexX = x + quarterW;
				vertexY = y + halfW;
			} else if (vertexType == 13) {
				vertexX = x + quarterW;
				vertexY = y + quarterW;
			} else if (vertexType == 14) {
				vertexX = x + tqW;
				vertexY = y + quarterW;
			} else if (vertexType == 15) {
				vertexX = x + tqW;
				vertexY = y + tqW;
			} else {
				vertexX = x + quarterW;
				vertexY = y + tqW;
			}
			vx[vertexPtr] = vertexX;
			vy[vertexPtr] = vertexY;
		}
		int shapedTileElements[] = shapedTileElementData[shapeA+1];
		int vertexCount = shapedTileElements.length / 4;
		int offset = 0;
		for (int vID = 0; vID < vertexCount; vID++) {
			int overlayOrUnderlay = shapedTileElements[offset];
			int idxA = shapedTileElements[offset + 1];
			int idxB = shapedTileElements[offset + 2];
			int idxC = shapedTileElements[offset + 3];
			offset += 4;
			if (idxA < 4)
				idxA = idxA - shapeB & 3;
			if (idxB < 4)
				idxB = idxB - shapeB & 3;
			if (idxC < 4)
				idxC = idxC - shapeB & 3;
			if (colO != 0) colO += 0xFF000000;
			if (colU != 0) colU += 0xFF000000;
			graphics.setColor(overlayOrUnderlay != 0 ? new Color(colO) : new Color(colU));
			graphics.fillPolygon(new int[] {vy[idxA], vy[idxB], vy[idxC] }, new int[] { vx[idxA], vx[idxB], vx[idxC] }, 3);
		}
	}
	private final int[][] tileShapePoints = {
	        new int[16], {
	            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	            1, 1, 1, 1, 1, 1
	        }, {
	            1, 0, 0, 0, 1, 1, 0, 0, 1, 1,
	            1, 0, 1, 1, 1, 1
	        }, {
	            1, 1, 0, 0, 1, 1, 0, 0, 1, 0,
	            0, 0, 1, 0, 0, 0
	        }, {
	            0, 0, 1, 1, 0, 0, 1, 1, 0, 0,
	            0, 1, 0, 0, 0, 1
	        }, {
	            0, 1, 1, 1, 0, 1, 1, 1, 1, 1,
	            1, 1, 1, 1, 1, 1
	        }, {
	            1, 1, 1, 0, 1, 1, 1, 0, 1, 1,
	            1, 1, 1, 1, 1, 1
	        }, {
	            1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
	            0, 0, 1, 1, 0, 0
	        }, {
	            0, 0, 0, 0, 0, 0, 0, 0, 1, 0,
	            0, 0, 1, 1, 0, 0
	        }, {
	            1, 1, 1, 1, 1, 1, 1, 1, 0, 1,
	            1, 1, 0, 0, 1, 1
	        },
	        {
	            1, 1, 1, 1, 1, 1, 0, 0, 1, 0,
	            0, 0, 1, 0, 0, 0
	        }, {
	            0, 0, 0, 0, 0, 0, 1, 1, 0, 1,
	            1, 1, 0, 1, 1, 1
	        }, {
	            0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
	            1, 0, 1, 1, 1, 1
	        }
	    };
	    private final int[][] tileShapeIndices = {
	        {
	            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
	            10, 11, 12, 13, 14, 15
	        }, {
	            12, 8, 4, 0, 13, 9, 5, 1, 14, 10,
	            6, 2, 15, 11, 7, 3
	        }, {
	            15, 14, 13, 12, 11, 10, 9, 8, 7, 6,
	            5, 4, 3, 2, 1, 0
	        }, {
	            3, 7, 11, 15, 2, 6, 10, 14, 1, 5,
	            9, 13, 0, 4, 8, 12
	        }
	    };
	public void drawMinimapTile(BufferedImage bi, byte shapeA, byte shapeB,
			int overlayRGB, int underlayRGB, int x, int y) {

		int tileRGB = shapeA == 0 ? underlayRGB : overlayRGB;
		/*if (shapeA < 1) {
			for (int xx = 0; xx < 4; xx++)
				for (int yy = 0; yy < 4; yy++)
					bi.setRGB(y+yy, x+xx, tileRGB);
			return;
		}*/
		int shapePoints[] = tileShapePoints[shapeA + 1];
		int shapeIndices[] = tileShapeIndices[shapeB & 0x3];
		int shapePtr = 0;
		for (int linePtr = 0; linePtr < 4; linePtr++) {
			for (int pixelPtr = 0; pixelPtr < 4; pixelPtr++) {
				int col = shapePoints[shapeIndices[shapePtr++]] != 0 ? underlayRGB
						: overlayRGB;
				if (col == 0 || col == 0xFF00FF) continue;
				bi.setRGB(y + 3 - linePtr, x + pixelPtr, col);
			}
		}

	}
	public static final int TILE_RESOLUTION = 16;
	private BufferedImage[] images = new BufferedImage[4];
	public void updateCachedTile(int z, int x, int y) {
		if (images[z] == null) getTexture(z);
		int overlay = overlayFloorId[z][x][y] - 1;
		int underlay = underlayFloorId[z][x][y] - 1;
		int shape = overlayClippingPath[z][x][y];
		int shapeRot = overlayRotation[z][x][y];
		int underCol = 0;
		int overCol = 0;
		if (overlay > -1) {
			overCol = FloorDefinition.cache[overlay].rgbColor;
			if (FloorDefinition.cache[overlay].textureId > -1) {
				overCol = Rasterizer3D
						.getAverageRgbColorForTexture(
								FloorDefinition.cache[overlay].textureId,
								12660);
			}
		}
		if (underlay > -1) {
			underCol = FloorDefinition.cache[underlay].rgbColor;
			if (FloorDefinition.cache[underlay].textureId > -1) {
				underCol = Rasterizer3D.getAverageRgbColorForTexture(
						FloorDefinition.cache[underlay].textureId,
						12660);
			}
		}
		if (shape == 1 && underCol == 0) underCol = overCol;
		if (shape == 0 && overCol == 0) overCol = underCol;
		if (overCol == 0xFF00FF)
			overCol = 0;
		if (underCol == 0xFF00FF)
			underCol = 0;
		drawShape(images[z].getGraphics(), (byte) shape, (byte) shapeRot, x * TILE_RESOLUTION, y * TILE_RESOLUTION, overCol, underCol);
	}
	public BufferedImage renderMinimap(int cz) {
		BufferedImage bi = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		for (int z = 0; z <= cz; z++) {
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 64; y++) {
					int overlay = overlayFloorId[z][x][y] - 1;
					int underlay = underlayFloorId[z][x][y] - 1;
					int shape = overlayClippingPath[z][x][y];
					int shapeRot = overlayRotation[z][x][y];
					int underCol = 0;
					int overCol = 0;
					if (overlay > -1) {
						overCol = FloorDefinition.cache[overlay].rgbColor;
						if (FloorDefinition.cache[overlay].textureId > -1) {
							overCol = Rasterizer3D
									.getAverageRgbColorForTexture(
											FloorDefinition.cache[overlay].textureId,
											12660);
						}
					}
					if (underlay > -1) {
						underCol = FloorDefinition.cache[underlay].rgbColor;
						if (FloorDefinition.cache[underlay].textureId > -1) {
							underCol = Rasterizer3D.getAverageRgbColorForTexture(
									FloorDefinition.cache[underlay].textureId,
									12660);
						}
					}
					if (shape == 1 && underCol == 0) underCol = overCol;
					if (shape == 0 && overCol == 0) overCol = underCol;
					if (overCol == 0xFF00FF)
						overCol = 0;
					if (underCol == 0xFF00FF)
						underCol = 0;
					drawMinimapTile(bi, (byte) shape, (byte) shapeRot, underCol, overCol, x * 4, y * 4);
				}
			}
		}
		return bi;
	}
	public BufferedImage getTexture(int z) {
		if (images[z] != null) return images[z];
		
		BufferedImage bi = new BufferedImage(64*TILE_RESOLUTION, 64*TILE_RESOLUTION,
				BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 64; x++) {
			for (int y = 0; y < 64; y++) {
				int overlay = overlayFloorId[z][x][y] - 1;
				int underlay = underlayFloorId[z][x][y] - 1;
				int shape = overlayClippingPath[z][x][y];
				int shapeRot = overlayRotation[z][x][y];
				int underCol = 0;
				int overCol = 0;
				if (overlay > -1) {
					overCol = FloorDefinition.cache[overlay].rgbColor;
					if (FloorDefinition.cache[overlay].textureId > -1) {
						overCol = Rasterizer3D
								.getAverageRgbColorForTexture(
										FloorDefinition.cache[overlay].textureId,
										12660);
					}
				}
				if (underlay > -1) {
					underCol = FloorDefinition.cache[underlay].rgbColor;
					if (FloorDefinition.cache[underlay].textureId > -1) {
						underCol = Rasterizer3D.getAverageRgbColorForTexture(
								FloorDefinition.cache[underlay].textureId,
								12660);
					}
				}
				if (shape == 1 && underCol == 0) underCol = overCol;
				if (shape == 0 && overCol == 0) overCol = underCol;
				if (overCol == 0xFF00FF)
					overCol = 0;
				if (underCol == 0xFF00FF)
					underCol = 0;
				//drawMinimapTile(bi, (byte) shape, (byte) shapeRot,
				//		underCol, overCol, x * 4, y * 4);
				drawShape(bi.getGraphics(), (byte) shape, (byte) shapeRot, x * TILE_RESOLUTION, y * TILE_RESOLUTION, overCol, underCol);
			}
		}
		images[z] = bi;
		return bi;
	}
}
