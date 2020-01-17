package com.uwsoft.editor.renderer.systems.render;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.uwsoft.editor.renderer.commons.IExternalItemType;
import com.uwsoft.editor.renderer.components.CompositeTransformComponent;
import com.uwsoft.editor.renderer.components.LayerMapComponent;
import com.uwsoft.editor.renderer.components.MainItemComponent;
import com.uwsoft.editor.renderer.components.NodeComponent;
import com.uwsoft.editor.renderer.components.ParentNodeComponent;
import com.uwsoft.editor.renderer.components.ShaderComponent;
import com.uwsoft.editor.renderer.components.TintComponent;
import com.uwsoft.editor.renderer.components.TransformComponent;
import com.uwsoft.editor.renderer.components.ViewPortComponent;
import com.uwsoft.editor.renderer.components.ZIndexComponent;
import com.uwsoft.editor.renderer.physics.PhysicsBodyLoader;
import com.uwsoft.editor.renderer.systems.render.logic.DrawableLogicMapper;
import com.uwsoft.editor.renderer.utils.ComponentRetriever;

import box2dLight.RayHandler;


public class Overlap2dRenderer extends IteratingSystem {
	private final float TIME_STEP = 1f/60;

	private ComponentMapper<ViewPortComponent> viewPortMapper = ComponentMapper.getFor(ViewPortComponent.class);
	private ComponentMapper<CompositeTransformComponent> compositeTransformMapper = ComponentMapper.getFor(CompositeTransformComponent.class);
	private ComponentMapper<NodeComponent> nodeMapper = ComponentMapper.getFor(NodeComponent.class);
	private ComponentMapper<ParentNodeComponent> parentNodeMapper = ComponentMapper.getFor(ParentNodeComponent.class);
	private ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
	private ComponentMapper<MainItemComponent> mainItemComponentMapper = ComponentMapper.getFor(MainItemComponent.class);
	private ComponentMapper<ShaderComponent> shaderComponentComponentMapper = ComponentMapper.getFor(ShaderComponent.class);
	
	private DrawableLogicMapper drawableLogicMapper;
	private RayHandler rayHandler;
//	private World world;

	//private Box2DDebugRenderer debugRenderer = new Box2DDebugRenderer();

	public static float timeRunning = 0;
	
	public Batch batch;

	public Overlap2dRenderer(Batch batch) {
		super(Family.all(ViewPortComponent.class).get());
		this.batch = batch;
		drawableLogicMapper = new DrawableLogicMapper();
	}

	public void addDrawableType(IExternalItemType itemType) {
		drawableLogicMapper.addDrawableToMap(itemType.getTypeId(), itemType.getDrawable());
	}

	@Override
	public void processEntity(Entity entity, float deltaTime) {
		timeRunning+=deltaTime;

		ViewPortComponent ViewPortComponent = viewPortMapper.get(entity);
		Viewport viewport = ViewPortComponent.viewPort;
		Camera camera = viewport.getCamera();
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		drawRecursively(entity, 1f);
		batch.end();

			//TODO kinda not cool (this should be done in separate lights renderer maybe?
			if (rayHandler != null) {
				rayHandler.setCulling(false);
				OrthographicCamera orthoCamera = (OrthographicCamera) camera;
				camera.combined.scl(1f / PhysicsBodyLoader.getScale());
				rayHandler.useCustomViewport(viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
				rayHandler.setCombinedMatrix(orthoCamera);
				rayHandler.updateAndRender();
			}

		//debugRenderer.render(world, camera.combined);
		//TODO Spine rendere thing
	}

	private void drawRecursively(Entity rootEntity, float parentAlpha) {

		
		//currentComposite = rootEntity;
		CompositeTransformComponent curCompositeTransformComponent = compositeTransformMapper.get(rootEntity);
		TransformComponent transform = transformMapper.get(rootEntity);
		ShaderComponent shaderComponent = shaderComponentComponentMapper.get(rootEntity);
		
		boolean shaderExist = shaderComponent!=null && shaderComponent.getShader()!=null;
		if(shaderExist){
			batch.setShader(shaderComponent.getShader());
		}
		
		if (curCompositeTransformComponent.transform || transform.rotation != 0 || transform.scaleX !=1 || transform.scaleY !=1){
			MainItemComponent childMainItemComponent = mainItemComponentMapper.get(rootEntity);
			//System.out.println(" Name " +childMainItemComponent.itemIdentifier);
		//System.out.println(curCompositeTransformComponent.computedTransform.toString());
			computeTransform(rootEntity);
		//System.out.println(curCompositeTransformComponent.computedTransform.toString());
			applyTransform(rootEntity, batch);
		}
        TintComponent tintComponent = ComponentRetriever.get(rootEntity, TintComponent.class);
        parentAlpha *= tintComponent.color.a;

		drawChildren(rootEntity, batch, curCompositeTransformComponent, parentAlpha);
		if (curCompositeTransformComponent.transform || transform.rotation != 0 || transform.scaleX !=1 || transform.scaleY !=1)
			resetTransform(rootEntity, batch);
			
		if(shaderExist){
			batch.setShader(null);
		}
	}

	private void drawChildren(Entity rootEntity, Batch batch, CompositeTransformComponent curCompositeTransformComponent, float parentAlpha) {
		NodeComponent nodeComponent = nodeMapper.get(rootEntity);
		Entity[] children = nodeComponent.children.begin();
		TransformComponent transform = transformMapper.get(rootEntity);
		if (curCompositeTransformComponent.transform || transform.rotation != 0 || transform.scaleX !=1 || transform.scaleY !=1) {
			for (int i = 0, n = nodeComponent.children.size; i < n; i++) {
				Entity child = children[i];

				LayerMapComponent rootLayers = ComponentRetriever.get(rootEntity, LayerMapComponent.class);
				ZIndexComponent childZIndexComponent = ComponentRetriever.get(child, ZIndexComponent.class);

				if(!rootLayers.isVisible(childZIndexComponent.layerName)) {
					continue;
				}

				MainItemComponent childMainItemComponent = mainItemComponentMapper.get(child);
				if(!childMainItemComponent.visible){
					continue;
				}
				
				int entityType = childMainItemComponent.entityType;

				NodeComponent childNodeComponent = nodeMapper.get(child);
				
				
				if(childNodeComponent ==null){
					//Find logic from the mapper and draw it
					drawableLogicMapper.getDrawable(entityType).draw(batch, child, parentAlpha);
				}else{
					//Step into Composite
					drawRecursively(child, parentAlpha);
				}
			}
		} else {
			// No transform for this group, offset each child.
			TransformComponent compositeTransform = transformMapper.get(rootEntity);
			
			float offsetX = compositeTransform.x, offsetY = compositeTransform.y;
			
			if(viewPortMapper.has(rootEntity)){
				offsetX = 0;
				offsetY = 0;
			}
			
			for (int i = 0, n = nodeComponent.children.size; i < n; i++) {
				Entity child = children[i];

				LayerMapComponent rootLayers = ComponentRetriever.get(rootEntity, LayerMapComponent.class);
				ZIndexComponent childZIndexComponent = ComponentRetriever.get(child, ZIndexComponent.class);

				if(!rootLayers.isVisible(childZIndexComponent.layerName)) {
					continue;
				}

				MainItemComponent childMainItemComponent = mainItemComponentMapper.get(child);
				if(!childMainItemComponent.visible){
					continue;
				}

				TransformComponent childTransformComponent = transformMapper.get(child);
				float cx = childTransformComponent.x, cy = childTransformComponent.y;
				childTransformComponent.x = cx + offsetX;
				childTransformComponent.y = cy + offsetY;
				
				NodeComponent childNodeComponent = nodeMapper.get(child);
				int entityType = mainItemComponentMapper.get(child).entityType;
				
				if(childNodeComponent ==null){
					//Find the logic from mapper and draw it
					drawableLogicMapper.getDrawable(entityType).draw(batch, child, parentAlpha);
				}else{
					//Step into Composite
					drawRecursively(child, parentAlpha);
				}
				childTransformComponent.x = cx;
				childTransformComponent.y = cy;
			}
		}
		nodeComponent.children.end();
	}

	/** Returns the transform for this group's coordinate system. 
	 * @param rootEntity */
	protected Matrix4 computeTransform (Entity rootEntity) {
		CompositeTransformComponent curCompositeTransformComponent = compositeTransformMapper.get(rootEntity);
		//NodeComponent nodeComponent = nodeMapper.get(rootEntity);
		ParentNodeComponent parentNodeComponent = parentNodeMapper.get(rootEntity);
		TransformComponent curTransform = transformMapper.get(rootEntity);
		Affine2 worldTransform = curCompositeTransformComponent.worldTransform;
		//TODO origin thing
		float originX = 0;
		float originY = 0;
		float x = curTransform.x;
		float y = curTransform.y;
		float rotation = curTransform.rotation;
		float scaleX = curTransform.scaleX;
		float scaleY = curTransform.scaleY;

		worldTransform.setToTrnRotScl(x + originX, y + originY, rotation, scaleX, scaleY);
		if (originX != 0 || originY != 0) worldTransform.translate(-originX, -originY);

		// Find the first parent that transforms.
		
		CompositeTransformComponent parentTransformComponent = null;
		//NodeComponent parentNodeComponent;
		
		Entity parentEntity = null;
		if(parentNodeComponent != null){
			parentEntity = parentNodeComponent.parentEntity;
		}
//		if (parentEntity != null){
//			
//		}
		
//		while (parentEntity != null) {
//			parentNodeComponent = nodeMapper.get(parentEntity);
//			if (parentTransformComponent.transform) break;
//			System.out.println("Gand");
//			parentEntity = parentNodeComponent.parentEntity;
//			parentTransformComponent = compositeTransformMapper.get(parentEntity);
//
//		}
		
		if (parentEntity != null){
			parentTransformComponent = compositeTransformMapper.get(parentEntity);
			TransformComponent transform = transformMapper.get(parentEntity);
			if(curCompositeTransformComponent.transform || transform.rotation != 0 || transform.scaleX !=1 || transform.scaleY !=1)
				worldTransform.preMul(parentTransformComponent.worldTransform);
			//MainItemComponent main = parentEntity.getComponent(MainItemComponent.class);
			//System.out.println("NAME " + main.itemIdentifier);
		}

		curCompositeTransformComponent.computedTransform.set(worldTransform);
		return curCompositeTransformComponent.computedTransform;
	}

	protected void applyTransform (Entity rootEntity, Batch batch) {
		CompositeTransformComponent curCompositeTransformComponent = compositeTransformMapper.get(rootEntity);
		curCompositeTransformComponent.oldTransform.set(batch.getTransformMatrix());
		batch.setTransformMatrix(curCompositeTransformComponent.computedTransform);
	}

	protected void resetTransform (Entity rootEntity, Batch batch) {
		CompositeTransformComponent curCompositeTransformComponent = compositeTransformMapper.get(rootEntity);
		batch.setTransformMatrix(curCompositeTransformComponent.oldTransform);
	}
	
	public void setRayHandler(RayHandler rayHandler){
		this.rayHandler = rayHandler;
	}

//	public void setBox2dWorld(World world) {
//		this.world = world;
//	}

	//this method has been left to avoid any compatibility issue
	//setPhysicsOn has been moved in PhysicsSystem class
	//Physics is now totally decoupled from rendering
	@Deprecated
	public void setPhysicsOn(boolean isPhysicsOn) {
		//empty
	}


	public Batch getBatch() {
        return batch;
    }
}

