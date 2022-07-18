/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.gltf;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Trackable;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class GltfActivity extends AppCompatActivity {
	private static final String TAG = GltfActivity.class.getSimpleName();
	private static final double MIN_OPENGL_VERSION = 3.0;

	private ArFragment arFragment;
	private Renderable renderable;
	private List<AnchorNode> lastAnchorNode = new ArrayList<AnchorNode>();
	private List<Double> distances = new ArrayList<Double>();
	private float tileWidth = 10.0f; // x (단위 cm)
	private float tileHeight = 20.0f; // z
	private float tileDepth = 0.1f; // y
	private Node reticle = null;


	private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);


	private final List<Color> colors =
					Arrays.asList(
									new Color(0, 0, 0, 1),
									new Color(1, 0, 0, 1),
									new Color(0, 1, 0, 1),
									new Color(0, 0, 1, 1),
									new Color(1, 1, 0, 1),
									new Color(0, 1, 1, 1),
									new Color(1, 0, 1, 1),
									new Color(1, 1, 1, 1));
	private int nextColor = 0;

	@Override
	@SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
	// CompletableFuture requires api level 24
	// FutureReturnValueIgnored is not valid
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!checkIsSupportedDeviceOrFinish(this)) {
			return;
		}

		setContentView(R.layout.activity_ux);
		arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

		load3DObject("woodTile_tiny.glb");

		arFragment.getArSceneView().getScene().setOnTouchListener(
						(HitTestResult hitTestResult, MotionEvent motionEvent) -> {
							if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
								handleTap();
								return true;
							}
							return false;
						}
		);

		arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> markReticle());
	} // onCreate end

	/**
	 * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
	 * on this device.
	 *
	 * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
	 *
	 * <p>Finishes the activity if Sceneform can not run
	 */
	public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
		if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
			Log.e(TAG, "Sceneform requires Android N or later");
			Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
			activity.finish();
			return false;
		}
		String openGlVersionString =
						((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
										.getDeviceConfigurationInfo()
										.getGlEsVersion();
		if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
			Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
			Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
							.show();
			activity.finish();
			return false;
		}
		return true;
	}

	private void load3DObject(String path) {
		// load 3d object
		WeakReference<GltfActivity> weakActivity = new WeakReference<>(this);
		ModelRenderable.builder()
						.setSource(
										this,
										Uri.parse(path))
						.setIsFilamentGltf(true)
						.build()
						.thenAccept(
										modelRenderable -> {
											GltfActivity activity = weakActivity.get();
											if (activity != null) {
												activity.renderable = modelRenderable;
											}
										})
						.exceptionally(
										throwable -> {
											Toast toast =
															Toast.makeText(this, "Unable to load Tiger renderable", Toast.LENGTH_LONG);
											toast.setGravity(Gravity.CENTER, 0, 0);
											toast.show();
											return null;
										});
	}

	private double getDistanceMeters(Vector3 v1, Vector3 v2) {
		float distanceX = v1.x - v2.x;
		float distanceY = v1.y - v2.y;
		float distanceZ = v1.z - v2.z;

		return Math.sqrt(distanceX * distanceX +
						distanceY * distanceY +
						distanceZ * distanceZ);
	}

	private void drawLine(Color color, Vector3 point1, Vector3 point2, AnchorNode anchorNode) {
		// drawLine

		final Vector3 difference = Vector3.subtract(point1, point2);
		final Vector3 directionFromTopToBottom = difference.normalized();
		final Quaternion rotationFromAToB =
						Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
		MaterialFactory.makeOpaqueWithColor(getApplicationContext(), color)
						.thenAccept(
										material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
											ModelRenderable model = ShapeFactory.makeCube(
															new Vector3(.005f, .0001f, difference.length()),
															Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */
											Node node = new Node();
											node.setParent(anchorNode);
											node.setRenderable(model);
											node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
											node.setWorldRotation(rotationFromAToB);
										}
						);
	}

	private double drawDistanceLabel(Vector3 point1, Vector3 point2, AnchorNode curAnchorNode) {
		// represent distance Label
		double distanceCm = ((int) (getDistanceMeters(point1, point2) * 1000)) / 10.0f;
		distances.set(distances.size()-1, distanceCm);

		Node distanceNode = new Node();
		distanceNode.setParent(lastAnchorNode.get(0));
		distanceNode.setEnabled(false);
		distanceNode.setLocalPosition(Vector3.subtract(point2, point1).scaled(.5f));

		final Vector3 labelPoint = distanceNode.getWorldPosition();
		final Vector3 xAxisPoint = Vector3.add(distanceNode.getWorldPosition(), new Vector3(1, 0, 0));

		final Vector3 yAxisPoint = Vector3.add(distanceNode.getWorldPosition(), new Vector3(0, 1, 0));
		final Vector3 diffWithFloor = Vector3.subtract(labelPoint, yAxisPoint);
		final Quaternion rotationToFloor =
						Quaternion.lookRotation(diffWithFloor, Vector3.forward());

		ViewRenderable.builder()
						.setView(this, R.layout.tiger_card_view)
						.build()
						.thenAccept(
										(renderable) -> {
											String roundDownDistance = (new DecimalFormat("#.#")).format(distanceCm);
											((TextView) renderable.getView()).setText(roundDownDistance);
//												renderable.setShadowCaster(false);
//												renderable.setShadowReceiver(false);
											distanceNode.setRenderable(renderable);
											distanceNode.setEnabled(true);
											distanceNode.setWorldRotation(rotationToFloor);
										})
						.exceptionally(
										(throwable) -> {
											throw new AssertionError("Could not load card view.", throwable);
										}
						);

		// 라벨위치 기준 테스트용 표시
//		MaterialFactory.makeOpaqueWithColor(getApplicationContext(), colors.get(nextColor))
//						.thenAccept(
//										material -> {
//											ModelRenderable model = ShapeFactory.makeCylinder(
//															.005f,
//															.0001f,
//															Vector3.zero(), material);
//
//											Node node = new Node();
//											node.setParent(distanceNode);
//											node.setRenderable(model);
//											node.setWorldPosition(distanceNode.getWorldPosition());
//											nextColor = (nextColor + 1) % colors.size();
//										}
//						);
//		drawLine(new Color(255, 0, 0), labelPoint, xAxisPoint, curAnchorNode);
		return distanceCm;
	}

	private void set3DTiles() {
		//		draw tile
		if (renderable == null) {
			return;
		}
		AnchorNode anchorNode = lastAnchorNode.get(0);
		anchorNode.setParent(arFragment.getArSceneView().getScene());

		int colCnt = (int) Math.floor(distances.get(1) / tileWidth);
		int rowCnt = (int) Math.floor(distances.get(2) / tileHeight);

		if (rowCnt > 0 && colCnt > 0) {
			List<Node> nodeBuf = new ArrayList<Node>();

			Vector3 point1 = lastAnchorNode.get(0).getWorldPosition();
			Vector3 point2 = lastAnchorNode.get(1).getWorldPosition();

			Node pivotNode = new Node();
			pivotNode.setParent(anchorNode);
			pivotNode.setRenderable(renderable);
			pivotNode.setWorldPosition(anchorNode.getWorldPosition());

			for (int i = 0; i< rowCnt; i++){
				for (int j = 0; j < colCnt; j++) {
					// Create the transformable model and add it to the anchor.
					Node model = new Node();
					model.setParent(pivotNode);
					model.setRenderable(renderable);
					model.setLocalPosition(new Vector3((tileWidth / 100) * j, 0, (tileHeight / 100) * i));
					nodeBuf.add(model);
				}
			}


			final Vector3 difference = Vector3.subtract(point1, point2);
			final Vector3 directionFromTopToBottom = difference.normalized();
			final Quaternion rotationFromAToB =
							Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
			Quaternion rotation = Quaternion.axisAngle(new Vector3(0f, 1.0f, 0f), -90);
			pivotNode.setWorldRotation(Quaternion.multiply(rotationFromAToB, rotation));
		}

	}

	private void drawLineNTile(Vector3 point1, Vector3 point2, AnchorNode curAnchorNode) {
		//drawLine
		if (lastAnchorNode.size() >= 2) {
			drawLine(new Color(255, 255, 244), point1, point2, curAnchorNode);
			double distanceCm = drawDistanceLabel(point1, point2, curAnchorNode);
			if(lastAnchorNode.size() == 3){
				set3DTiles();
			}
		}
	}

	private void sendToastMessage(String message) {
		Toast toast =
						Toast.makeText(this, message, Toast.LENGTH_LONG);
		toast.setGravity(Gravity.BOTTOM, 0, 0);
		toast.show();
	}


	private void handleTap() {
		// hitTest
		Frame frame = arFragment.getArSceneView().getArFrame();
		float viewWidth = arFragment.getArSceneView().getWidth() * .5f;
		float viewHeight = arFragment.getArSceneView().getHeight() * .5f;

		// Camera camera = arFragment.getArSceneView().getScene().getCamera();
		// Ray ray = new Ray(camera.getWorldPosition(), camera.getForward());

		List<HitResult> hitResultList = frame.hitTest(viewWidth, viewHeight);

		for (HitResult hit : hitResultList) {
			Trackable trackable = hit.getTrackable();
			if ((trackable instanceof Plane
							&& ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
							&& hit.getDistance() > 0)
							|| (trackable instanceof Point
							&& ((Point) trackable).getOrientationMode()
							== Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
			) {

				sendToastMessage("카메라에서 선택 바닥 지점까지 거리는 \n" + Float.toString(Math.round(hit.getDistance() * 1000) / 10) + "cm 입니다.");
				// mark anchor point
				AnchorNode anchorNode = new AnchorNode(hit.createAnchor());
				anchorNode.setParent(arFragment.getArSceneView().getScene());
				lastAnchorNode.add(anchorNode);
				distances.add(.0);

				MaterialFactory.makeOpaqueWithColor(getApplicationContext(), colors.get(nextColor))
								.thenAccept(
												material -> {
													ModelRenderable model = ShapeFactory.makeCylinder(
																	.01f,
																	.0001f,
																	Vector3.zero(), material);

													Node node = new Node();
													node.setParent(anchorNode);
													node.setRenderable(model);
													nextColor = (nextColor + 1) % colors.size();
												}
								);

				if (lastAnchorNode.size() == 2) {
					Vector3 point1 = lastAnchorNode.get(0).getWorldPosition();
					Vector3 point2 = lastAnchorNode.get(1).getWorldPosition();
					drawLineNTile(point1, point2, anchorNode);
				} else if(lastAnchorNode.size() == 3){
					Vector3 point1 = lastAnchorNode.get(1).getWorldPosition();
					Vector3 point2 = lastAnchorNode.get(2).getWorldPosition();
					drawLineNTile(point1, point2, anchorNode);

					lastAnchorNode = new ArrayList<AnchorNode>();
				}
				break;
			}
		}
	}

	private void markReticle() {
		Vector3 camWorldPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
		Vector3 camLookForward = arFragment.getArSceneView().getScene().getCamera().getForward();

		MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(255, 255, 0))
						.thenAccept(
										material -> {

											if (reticle == null)
												reticle = new Node();

											ModelRenderable model = ShapeFactory.makeCylinder(
															.01f,
															.0001f,
															Vector3.zero(), material);
											model.setShadowCaster(false);
											model.setShadowReceiver(false);
											reticle.setParent(arFragment.getArSceneView().getScene());
											reticle.setRenderable(model);
											reticle.setWorldPosition(Vector3.add(camWorldPos, camLookForward));
										}
						);
	}
}

