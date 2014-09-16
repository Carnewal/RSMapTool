package org.fabian.rsmt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

import javax.imageio.ImageIO;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.collision.CollisionResults;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.runescape.cache.Archive;
import com.runescape.cache.Index;
import com.runescape.cache.def.FloorDefinition;
import com.runescape.cache.def.GameObjectDefinition;
import com.runescape.media.Rasterizer3D;
import com.runescape.net.Buffer;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.builder.EffectBuilder;
import de.lessvoid.nifty.builder.HoverEffectBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.Label;
import de.lessvoid.nifty.controls.TabGroup;
import de.lessvoid.nifty.controls.TabSelectedEvent;
import de.lessvoid.nifty.controls.TextField;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.ImageRenderer;
import de.lessvoid.nifty.elements.render.PanelRenderer;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.tools.Color;

public class RSMapTool extends SimpleApplication implements ScreenController, ActionListener {
	private String currentTab = "paint_tab";
	private String selectedPaintLayer = "option-overlay";
	private int currentShapeRotation = 0;
	private int currentShape = 0;
	public Archive getArchive(int id) {
		return new Archive(stores[0].get(id));
	}

	public byte[] getGZIPFile(int index, int id) {
		try {
			byte[] compressed = stores[index+1].get(id);
			ByteArrayInputStream compStream = new ByteArrayInputStream(compressed);
			GZIPInputStream gzis = new GZIPInputStream(compStream);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buff = new byte[1024];
			int read;
			while ((read = gzis.read(buff)) != -1) {
				baos.write(buff, 0, read);
			}
			baos.flush();
			gzis.close();
			byte[] dat = baos.toByteArray();
			baos.close();
			return dat;
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		return new byte[0];
	}

	private Geometry brushGeometry;

	@Override
	public void simpleInitApp() {
		flyCam.setDragToRotate(true);
		flyCam.setMoveSpeed(25f);
		cam.setFrustumPerspective(45f, cam.getWidth()/(cam.getHeight() * 1f), 0.001f, 128f);
		{
			Sphere sphereMesh = new Sphere(16, 16, 0.5f);
			brushGeometry = new Geometry("Brush", sphereMesh);
			Material mat = new Material(assetManager,
					"Common/MatDefs/Misc/Unshaded.j3md");
			mat.setColor("Color", ColorRGBA.Blue);
			mat.getAdditionalRenderState().setWireframe(true);
			brushGeometry.setMaterial(mat);
			rootNode.attachChild(brushGeometry);
		}
		{
			DirectionalLight sun = new DirectionalLight();
			sun.setDirection(new Vector3f(-1,-1,-1).normalizeLocal());
			sun.setColor(ColorRGBA.White);
			rootNode.addLight(sun);
		}
		loadCache();
		setupNifty();
		setupInput();
	}
	public void setupInput() {
		inputManager.addMapping("RMB", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
		inputManager.addListener(this, "RMB");
	}
	@Override
	public void simpleUpdate(float tpf) {
		super.simpleUpdate(tpf);
		Vector3f start = cam.getWorldCoordinates(
				inputManager.getCursorPosition(), 0f);
		Vector3f dir = cam
				.getWorldCoordinates(inputManager.getCursorPosition(), 1f)
				.subtractLocal(start).normalizeLocal();
		Ray r = new Ray(start, dir);
		if (terrain[currentFloor] != null) {
			CollisionResults cr = new CollisionResults();
			terrain[currentFloor].collideWith(r, cr);
			if (cr.size() > 0) {
				Vector3f point = cr.getClosestCollision().getContactPoint();
				brushGeometry.center().setLocalTranslation(point);
				if (isHoldingRMB) {
					if (currentTab.equals("paint_tab") || currentTab.equals("shape_tab")) {
						currentMap.overlayFloorId[currentFloor][(int)point.z][(int)point.x] = selectedOverFloId + 1;
						currentMap.underlayFloorId[currentFloor][(int)point.z][(int)point.x] = selectedUnderFloId + 1;
						currentMap.overlayRotation[currentFloor][(int)point.z][(int)point.x] = currentShapeRotation;
						currentMap.overlayClippingPath[currentFloor][(int)point.z][(int)point.x] = currentShape-1;
						currentMap.updateCachedTile(currentFloor, (int)point.z, (int)point.x);
						updateTerrain(false);
					}
				}
			}
		}
	}

	public void loadCache() {
		int idxCount = -1;
		for (int i = 0; i < 255; i++) {
			if (new File("./cache/main_file_cache.idx" + i).exists())
				idxCount = i + 1;
		}
		stores = new Index[idxCount];
		try {
			RandomAccessFile datFile = new RandomAccessFile(new File(
					"./cache/main_file_cache.dat"), "rw");
			for (int i = 0; i < idxCount; i++) {
				RandomAccessFile idxFile = new RandomAccessFile(new File(
						"./cache/main_file_cache.idx" + i), "rw");
				stores[i] = new Index(datFile, idxFile, i + 1);
			}
		} catch (FileNotFoundException e1) {
			
			e1.printStackTrace();
		}
		System.out.println("Loaded " + idxCount + " cache indices.");
		Archive config = getArchive(2);
		Archive versionList = getArchive(5);
		MapIndex.loadTable(versionList);
		Archive textures = getArchive(6);
		Rasterizer3D.loadIndexedImages(textures);
		Rasterizer3D.method369(0.8);
		Rasterizer3D.method364(20, true);
		System.out.println("Loaded textures.");
		GameObjectDefinition.load(config);
		System.out.println("Loaded game objects.");
		FloorDefinition.load(config);
		System.out.println("Loaded flo.");
	}

	public void setupNifty() {
		NiftyJmeDisplay display = new NiftyJmeDisplay(assetManager,
				inputManager, audioRenderer, viewPort);
		nifty = display.getNifty();
		guiViewPort.addProcessor(display);
		nifty.fromXml("Interface/EditorUI.xml", "start", this);
	}

	public static void main(String[] args) {
		RSMapTool tool = new RSMapTool();
		AppSettings as = new AppSettings(true);
		as.setTitle("RS Map Tool - By Fabian");
		as.setResolution(800, 600);
		as.setSettingsDialogImage("settings.png");
		tool.setSettings(as);
		tool.setDisplayFps(true);
		tool.setDisplayStatView(false);
		tool.start();
	}

	private Nifty nifty;
	private Screen screen;

	@Override
	public void bind(Nifty nifty, Screen screen) {
		this.nifty = nifty;
		this.screen = screen;
	}

	@Override
	public void onEndScreen() {

	}

	@Override
	public void onStartScreen() {
		populateFloList();
		screen.findElementByName("window-open").hide();
		screen.findElementByName("window-pickfloor").hide();
		screen.findElementByName("window-pickshape").hide();
	}
	private int selectedOverFloId = 0;
	private int selectedUnderFloId = 0;
	public void selectFlo(String idStr) {
		int id = Integer.parseInt(idStr);
		FloorDefinition fd = FloorDefinition.cache[id];
		int col = fd.textureId == -1 ? fd.rgbColor : Rasterizer3D.getAverageRgbColorForTexture(fd.textureId, 12660);
		int r = (col >> 16) & 0xFF;
		int g = (col >> 8) & 0xFF;
		int b = (col) & 0xFF;
		Color color = new Color(r/255f, g/255f, b/255f, 1f);
		if (selectedPaintLayer.equals("option-overlay")) {
			selectedOverFloId = id;
			screen.findElementByName("overlayPanel").getRenderer(PanelRenderer.class).setBackgroundColor(color);
		} else {
			selectedUnderFloId = id;
			screen.findElementByName("underlayPanel").getRenderer(PanelRenderer.class).setBackgroundColor(color);
		}
		screen.findElementByName("window-pickfloor").hide();
	}
	public void selectShape(String idStr) {
		int id = Integer.parseInt(idStr);
		currentShape = id;
		screen.findElementByName("window-pickshape").hide();
		screen.findElementByName("shapeImg").getRenderer(ImageRenderer.class).setImage(nifty.createImage("Shapes/"+id+".png", true));
	}
	public void populateFloList() {
		Element parent = screen.findElementByName("floScrollPanelContent");
		for (int i = 0; i < FloorDefinition.cache.length; i++) {
			final int floId = i;
			final FloorDefinition fd = FloorDefinition.cache[i];
			new PanelBuilder() {{
				width("128px");
				height("128px");
				int col = fd.textureId == -1 ? fd.rgbColor : Rasterizer3D.getAverageRgbColorForTexture(fd.textureId, 12660);
				int r = (col >> 16) & 0xFF;
				int g = (col >> 8) & 0xFF;
				int b = (col) & 0xFF;
				backgroundColor(new Color(r / 255f, g / 255f, b / 255f, 1f));
				onHoverEffect(new HoverEffectBuilder("hint") {{
					effectParameter("hintText", fd.name == null ? "Null" : fd.name);
				}});
				interactOnClick("selectFlo("+floId+")");
			}}.build(nifty, screen, parent);
		}
		parent = screen.findElementByName("floMaskScrollPanelContent");
		for (int i = 0; i < RSMap.shapedTilePointData.length; i++) {
			final int shapeId = i;
			new PanelBuilder() {{
				width("128px");
				height("128px");
				backgroundImage("Shapes/"+shapeId+".png");
				onHoverEffect(new HoverEffectBuilder("hint") {{
					effectParameter("hintText", "Shape "+shapeId);
				}});
				onActiveEffect(new EffectBuilder("border") {{
					effectParameter("color", "#FF00FF");
					effectParameter("border", "4px");
				}});
				interactOnClick("selectShape("+shapeId+")");
			}}.build(nifty, screen, parent);
		}
	}
	private Index[] stores;

	public void openMapMenuButton() {
		screen.findElementByName("window-open").show();
	}

	private TerrainQuad[] terrain = new TerrainQuad[4];

	public TerrainQuad createTerrain(int z, RSMap map) {
		AbstractHeightMap heightmap = map.getHeightMap(z);
		heightmap.load();
		/** 1. Create terrain material and load four textures into it. */
		Material mat_terrain = new Material(assetManager,
				"Common/MatDefs/Terrain/TerrainLighting.j3md");

		/** 1.1) Add ALPHA map (for red-blue-green coded splat textures) */

		Texture tex1 = new Texture2D(awtLoader.load(map.getTexture(z), true));
		//tex1.setMinFilter(MinFilter.NearestNoMipMaps);
		//tex1.setMagFilter(MagFilter.Nearest);
		mat_terrain.setTexture("DiffuseMap", tex1);
		mat_terrain.setFloat("DiffuseMap_0_scale", 1f);
		Texture alpha = assetManager
				.loadTexture("Alpha.png");
		mat_terrain.setTexture("AlphaMap", alpha);
		//mat_terrain.getAdditionalRenderState().setWireframe(true);
		TerrainQuad terrain = new TerrainQuad("my terrain", 13, 65,
				heightmap.getHeightMap());

		/**
		 * 4. We give the terrain its material, position & scale it, and attach
		 * it.
		 */
		terrain.setMaterial(mat_terrain);
		terrain.center().setLocalTranslation(32f, 0f, 32f);
		rootNode.attachChild(terrain);
		updateMinimap();
		return terrain;
	}
	public void updateTerrain(boolean height) {
		for (int z = 0; z < 4; z++)
			if (terrain[z] !=  null)
				terrain[z].setCullHint(z == currentFloor ? CullHint.Never : CullHint.Always);
		AbstractHeightMap heightmap = currentMap.getHeightMap(currentFloor);
		if (height)
			heightmap.load();
		/** 1. Create terrain material and load four textures into it. */
		Material mat_terrain = terrain[currentFloor].getMaterial();

		/** 1.1) Add ALPHA map (for red-blue-green coded splat textures) */
		

		Texture tex1 = new Texture2D(awtLoader.load(currentMap.getTexture(currentFloor), true));
		mat_terrain.setTexture("DiffuseMap", tex1);
		if (height) {
			rootNode.detachChild(terrain[currentFloor]);
			terrain[currentFloor] = new TerrainQuad("my terrain", 13, 65,
					heightmap.getHeightMap());
		}
		terrain[currentFloor].setMaterial(mat_terrain);
		if (height) {
			terrain[currentFloor].center().setLocalTranslation(32f, 0f, 32f);
			rootNode.attachChild(terrain[currentFloor]);
		}
		updateMinimap();
		
	}
	private static AWTLoader awtLoader = new AWTLoader();
	private long lastMinimapTime = 0L;
	public void updateMinimap() {
		long t = System.currentTimeMillis();
		TextureKey tk = new TextureKey("minimap"+t+".png", true);
		Texture texture = new Texture2D(awtLoader.load(currentMap.renderMinimap(currentFloor), true));
		((DesktopAssetManager)assetManager).deleteFromCache(new TextureKey("minimap"+lastMinimapTime+".png", true));
		((DesktopAssetManager)assetManager).addToCache(tk, texture);
		screen.findElementByName("minimap").getRenderer(ImageRenderer.class).setImage(nifty.createImage("minimap"+t+".png", true));
		lastMinimapTime = t;
	}
	private RSMap currentMap;
	private int currentFloor = 1;
	private MapIndex currentMapIndex;
	public void loadMapConfirm() {
		for (int z = 0; z < 4; z++)
		if (terrain[z] != null) rootNode.detachChild(terrain[z]);
		int x = Integer.parseInt(screen.findNiftyControl("openMapX",
				TextField.class).getRealText()) / 64;
		int y = Integer.parseInt(screen.findNiftyControl("openMapY",
				TextField.class).getRealText()) / 64;
		currentMapIndex = MapIndex.getMapTile(x, y);
		Buffer mapData = new Buffer(getGZIPFile(3, currentMapIndex.getMapFile()));
		if (new File("./maps/"+currentMapIndex.getMapFile()+".dat").exists()) {
			try {
				mapData = new Buffer(IOUtils.readFile("./maps/"+currentMapIndex.getMapFile()+".dat"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		currentMap = new RSMap(x * 64, y * 64, mapData);
		for (int z = 0; z < 4; z++)
			try {
				ImageIO.write(currentMap.renderMinimap(z), "png", new File("./minimaps/"+currentMapIndex.getMapFile()+"-"+z+".png"));
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		for (int z = 0; z < 4; z++) {
			this.terrain[z] = createTerrain(z, currentMap);
		}
		currentFloor = 0;
		byte[] landBytes = getGZIPFile(3, currentMapIndex.getLandFile());
		screen.findElementByName("window-open").hide();
		for (int z = 0; z < 4; z++)
			if (terrain[z] !=  null)
				terrain[z].setCullHint(z == currentFloor ? CullHint.Never : CullHint.Always);
	}
	@NiftyEventSubscriber(id="btnSaveMap")
	public void saveMap(String id, ButtonClickedEvent e) {
		this.enqueue(new Callable<Integer>() {

			@Override
			public Integer call() throws Exception {
				saveMap();
				return 0;
			}

		});
	}
	public void saveMap() {
		byte[] data = currentMap.saveMap();
		//stores[4].put(data.length, data, currentMapIndex.getMapFile());
		try {
			FileOutputStream fos = new FileOutputStream("maps/"+currentMapIndex.getMapFile()+".dat");
			fos.write(data);
			fos.flush();
			fos.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		System.out.println("Saved Map.");
	}
	public void openMapConfirm() {
		this.enqueue(new Callable<Integer>() {

			@Override
			public Integer call() throws Exception {
				loadMapConfirm();
				return 0;
			}

		});

	}
	private boolean isHoldingRMB = false;
	@Override
	public void onAction(String name, boolean pressed, float tpf) {
		if (name.equalsIgnoreCase("RMB")) {
			isHoldingRMB = pressed;
		}
	}
	public void openFloWindow(String layer) {
		if (layer.equals("option-shape")) {
			screen.findElementByName("window-pickshape").show();
			return;
		}
		selectedPaintLayer = layer;
		screen.findElementByName("window-pickfloor").show();
	}
	public void shapeRotateButton(String direction) {
		if (direction.equals("cw")) // Clockwise
			currentShapeRotation++;
		else // Counter Clockwise
			currentShapeRotation--;
		if (currentShapeRotation < 0) currentShapeRotation = 3;
		if (currentShapeRotation > 3) currentShapeRotation = 0;
		int currentAngle = currentShapeRotation * 90;
		screen.findNiftyControl("currentRotLabel", Label.class).setText("Rotation: "+currentAngle);
	}
	@NiftyEventSubscriber(id="edit_tabs")
	public void onTabChange(String id, TabSelectedEvent event) {
		currentTab = screen.findNiftyControl(id, TabGroup.class).getSelectedTab().getId();
	}
}
