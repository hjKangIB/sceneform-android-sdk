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
import android.view.ViewTreeObserver;
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
import com.google.ar.sceneform.rendering.Material;
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
	private List<AnchorNode> lastAnchorNodes = new ArrayList<AnchorNode>();
	private List<Double> distances = new ArrayList<Double>();
	private float tileWidth = 10.0f; // x (단위 cm)
	private float tileHeight = 20.0f; // z
	private float tileDepth = 0.1f; // y
	private Node reticle = null;
	private Node curDrawingLineNode = null;
	private Node curDistanceLabelNode = null;
	private Double curDistance = -999.0;
	private TextView curLabelView = null;
	private AnchorNode curAnchor = null;
	private Node curGuideSquareNode = null;


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
								handleTap(motionEvent);

								return true;
							}
							return false;
						}
		);

		arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
			updateScreen();
		});
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

	private Node drawLine(Color color, Vector3 point1, Vector3 point2, AnchorNode anchorNode) {
		// drawLine

		final Vector3 difference = Vector3.subtract(point1, point2);
		final Vector3 directionP1ToP2 = difference.normalized();

		Node lineNode = new Node();

		final Quaternion rotationP1ToP2 =
						Quaternion.lookRotation(directionP1ToP2, Vector3.up());

		MaterialFactory.makeOpaqueWithColor(getApplicationContext(), color)
						.thenAccept(
										material -> {
											ModelRenderable model = ShapeFactory.makeCube(
															new Vector3(.005f, .0001f, difference.length()),
															Vector3.zero(), material);
											lineNode.setParent(anchorNode);
											lineNode.setRenderable(model);
											lineNode.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
											lineNode.setWorldRotation(rotationP1ToP2);

										}
						);
		return lineNode;
	}


	private void updateLine(Color color, Vector3 point1, Vector3 point2, AnchorNode anchorNode) {

		if (curDrawingLineNode != null && curDrawingLineNode.isActive() && reticle != null ) {
			final Vector3 difference = Vector3.subtract(point1, point2);
			final Vector3 directionFromTopToBottom = difference.normalized();
			final Quaternion rotationFromAToB =
							Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

			Material material = curDrawingLineNode.getRenderable().getMaterial();
			ModelRenderable model = ShapeFactory.makeCube(
							new Vector3(.005f, .0001f, difference.length()),
							Vector3.zero(), material);

			curDrawingLineNode.setRenderable(model);
			curDrawingLineNode.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
			curDrawingLineNode.setWorldRotation(rotationFromAToB);

		}

	}

	private Node drawDistanceLabel(Vector3 point1, Vector3 point2, AnchorNode parentNode) {
		// represent distance Label
		double distanceCm = ((int) (getDistanceMeters(point1, point2) * 1000)) / 10.0f;
		distances.set(distances.size() - 1, distanceCm);

		Node distanceNode = new Node();
//		distanceNode.setParent(lastAnchorNodes.get(lastAnchorNodes.size() - 2));
		distanceNode.setParent(parentNode);
		distanceNode.setEnabled(false);
		distanceNode.setWorldPosition(new Vector3((point1.x + point2.x) / 2, point1.y, (point1.z + point2.z) / 2));

		final Quaternion rotationToFloor = Quaternion.lookRotation(Vector3.up(), Vector3.forward());
		Vector3 lineVector = point2.x > point1.x ? Vector3.subtract(point2, point1) : Vector3.subtract(point1, point2);
		final Quaternion rotationToP2 = Quaternion.lookRotation(Vector3.cross(Vector3.up(), lineVector), Vector3.up());
		Quaternion rotationResult = Quaternion.multiply(rotationToP2, rotationToFloor);

		ViewRenderable.builder()
						.setView(this, R.layout.tiger_card_view)
						.build()
						.thenAccept(
										(renderable) -> {
											String roundDownDistance = (new DecimalFormat("#.#")).format(distanceCm);
											TextView label = ((TextView) renderable.getView());
											label.setText(roundDownDistance);

// 선위에 라벨을 그릴수 있도록 좌표 보정
//											label.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener( {
//												@Override
//												public void onGlobalLayout() {
//													label.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//													label.getHeight(); //height is ready
//
//													float labelWidth = label.getWidth();
//													float labelHeight = label.getHeight();
//
////											 removeOnGlobalLayoutListener(mParentLayout.getViewTreeObserver(), mGlobalLayoutListener);ㅌ
//													sendToastMessage("width: " + Float.toString(labelWidth / 2 / 1000) + ", height: " + Float.toString(labelHeight / 2 / 1000));
//
//													Vector3 pos = distanceNode.getWorldPosition();
//													Vector3 calibPos = Vector3.add(pos, new Vector3(labelWidth / 2 / 1000, 0, labelHeight / 2 / 1000));
//													distanceNode.setWorldPosition(calibPos);
//												}
//											});

											//	renderable.setShadowCaster(false);
											//	renderable.setShadowReceiver(false);
											distanceNode.setRenderable(renderable);
											distanceNode.setEnabled(true);
											distanceNode.setWorldRotation(rotationResult);
										})
						.exceptionally(
										(throwable) -> {
											throw new AssertionError("Could not load card view.", throwable);
										}
						);

//			// 라벨위치 기준 테스트용 표시
//			Vector3 xAxisPoint = Vector3.add(distanceNode.getWorldPosition(), Vector3.right());
//			Vector3 yAxisPoint = Vector3.add(distanceNode.getWorldPosition(), Vector3.up());
//			Vector3 zAxisPoint = Vector3.add(distanceNode.getWorldPosition(), Vector3.forward());
//				MaterialFactory.makeOpaqueWithColor(getApplicationContext(), colors.get(nextColor))
//								.thenAccept(
//												material -> {
//													ModelRenderable model = ShapeFactory.makeCylinder(
//																	.005f,
//																	.0001f,
//																	Vector3.zero(), material);
//
//													Node node = new Node();
//													node.setParent(distanceNode);
//													node.setRenderable(model);
//													node.setWorldPosition(distanceNode.getWorldPosition());
//													nextColor = (nextColor + 1) % colors.size();
//												}
//								);
// 				AnchorNode curAnchorNode = lastAnchorNodes.get(lastAnchorNodes.size() - 1);
//				drawLine(new Color(255, 0, 0), distanceNode.getWorldPosition(), xAxisPoint, curAnchorNode);
//				drawLine(new Color(0, 255, 0), distanceNode.getWorldPosition(), yAxisPoint, curAnchorNode);
//				drawLine(new Color(0, 0, 255), distanceNode.getWorldPosition(), zAxisPoint, curAnchorNode);
		//	drawLine(new Color(10, 10, 10), distanceNode.getWorldPosition(), Vector3.add(distanceNode.getUp(), distanceNode.getWorldPosition()), curAnchorNode);
//
//		drawLine(new Color(255, 0, 0), Vector3.zero(), Vector3.right(), curAnchorNode);
//		drawLine(new Color(0, 255, 0), Vector3.zero(), Vector3.up(), curAnchorNode);
//		drawLine(new Color(0, 0, 255), Vector3.zero(), Vector3.forward(), curAnchorNode);

		return distanceNode;
	}

	private void set3DTiles() {
		//		draw tile
		if (renderable == null) {
			return;
		}
		AnchorNode anchorNode = lastAnchorNodes.get(0);
		anchorNode.setParent(arFragment.getArSceneView().getScene());

		int colCnt = (int) Math.floor(distances.get(1) / tileWidth);
		int rowCnt = (int) Math.floor(distances.get(2) / tileHeight);

		if (rowCnt > 0 && colCnt > 0) {
			List<Node> nodeBuf = new ArrayList<Node>();

			Vector3 point1 = lastAnchorNodes.get(0).getWorldPosition();
			Vector3 point2 = lastAnchorNodes.get(1).getWorldPosition();
			Vector3 point3 = lastAnchorNodes.get(2).getWorldPosition();

			Node pivotNode = new Node();
			pivotNode.setParent(anchorNode);
			pivotNode.setRenderable(renderable);
			pivotNode.setWorldPosition(anchorNode.getWorldPosition());

			for (int i = 0; i < rowCnt; i++) {
				for (int j = 0; j < colCnt; j++) {
					// Create the transformable model and add it to the anchor.
					Node model = new Node();
					model.setParent(pivotNode);
					model.setRenderable(renderable);
					model.setLocalPosition(new Vector3((tileWidth / 100) * j, 0, (tileHeight / 100) * i));
					nodeBuf.add(model);
				}
			}
			final Vector3 line1 = Vector3.subtract(point1, point2);
			final Vector3 directionLine1 = line1.normalized();
			final Quaternion rotationFromP1ToP2 =
							Quaternion.lookRotation(directionLine1, Vector3.up());

			Quaternion rotation = Quaternion.axisAngle(new Vector3(0f, 1.0f, 0f), -90);
			Quaternion normalResult = Quaternion.multiply(rotationFromP1ToP2, rotation);

			final Vector3 line2 = Vector3.subtract(point3, point2);
			Vector3 crossResult = Vector3.cross(line1, line2);
			if (crossResult.y >= 0) {
				// 처음 선분 기준으로 우측꺾임
				pivotNode.setWorldRotation(normalResult);
			} else {
				// 처음 선분 기준으로 좌측꺾임 ;
				Quaternion reverse = Quaternion.axisAngle(Vector3.right(), 180);
				pivotNode.setWorldRotation(Quaternion.multiply(normalResult, reverse));
			}
		}

	}

	private void sendToastMessage(String message) {
		Toast toast =
						Toast.makeText(this, message, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM, 0, 0);
		toast.show();
	}


	private void handleTap(MotionEvent motionEvent) {
		// hitTest
		Frame frame = arFragment.getArSceneView().getArFrame();
		float viewWidth = arFragment.getArSceneView().getWidth();
		float viewHeight = arFragment.getArSceneView().getHeight();

		List<HitResult> hitResultList = frame.hitTest(viewWidth * .5f, viewHeight * .5f);

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
				lastAnchorNodes.add(anchorNode);
				distances.add(.0);
				curDistance = .0;
				curAnchor = anchorNode;

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


				Vector3 point1 = anchorNode.getWorldPosition();
				Vector3 point2 = anchorNode.getWorldPosition();
				curDrawingLineNode = drawLine(new Color(255, 255, 250), point1, point2, anchorNode);

				if (lastAnchorNodes.size() == 2) {
					point1 = lastAnchorNodes.get(0).getWorldPosition();
					point2 = lastAnchorNodes.get(1).getWorldPosition();
					drawDistanceLabel(point1, point2, lastAnchorNodes.get(lastAnchorNodes.size() - 2));
					curGuideSquareNode = drawGuideSquare(point1, point2, point2, lastAnchorNodes.get(lastAnchorNodes.size() - 2));
				} else if (lastAnchorNodes.size() == 3) {
					point1 = lastAnchorNodes.get(1).getWorldPosition();
					point2 = lastAnchorNodes.get(2).getWorldPosition();
					drawDistanceLabel(point1, point2, lastAnchorNodes.get(lastAnchorNodes.size() - 2));
					set3DTiles();

					resetVariables();
				}
				break;
			}
		}
	}

	private void markReticle() {
		Vector3 camWorldPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
		Vector3 camLookForward = arFragment.getArSceneView().getScene().getCamera().getForward();

		if(reticle == null){
			MaterialFactory.makeTransparentWithColor(getApplicationContext(), new Color(255, 255, 0, 1.0f))
							.thenAccept(
											material -> {

												reticle = new Node();
												reticle.setParent(arFragment.getArSceneView().getScene());


												ModelRenderable model = ShapeFactory.makeCylinder(
																.01f,
																.0001f,
																Vector3.zero(), material);
//											model.setShadowCaster(false);
//											model.setShadowReceiver(false);

												reticle.setRenderable(model);
												reticle.setWorldPosition(Vector3.add(camWorldPos, camLookForward));
											}
							);
		} else if(reticle.isActive() && reticle.getRenderable() != null ){
			reticle.setWorldPosition(Vector3.add(camWorldPos, camLookForward));
		}

	}

	private void updateScreen() {
		markReticle();

		if (curDrawingLineNode != null && reticle != null && lastAnchorNodes.size() > 0) {
			AnchorNode lastAnchor = lastAnchorNodes.get(lastAnchorNodes.size() - 1);

			Vector3 curReticleHitPosition = reticle.getWorldPosition();

			// 화면상에 reticle이 위치하는 부분에서부터 평면상으로 접하는 지점을 찾기위해 hitTest
			Frame frame = arFragment.getArSceneView().getArFrame();
			float viewWidth = arFragment.getArSceneView().getWidth();
			float viewHeight = arFragment.getArSceneView().getHeight();

			List<HitResult> hitResultList = frame.hitTest(viewWidth * .5f, viewHeight * .5f);

			for (HitResult hit : hitResultList) {
				Trackable trackable = hit.getTrackable();
				if ((trackable instanceof Plane
								&& ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
								&& hit.getDistance() > 0)
								|| (trackable instanceof Point
								&& ((Point) trackable).getOrientationMode()
								== Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
				) {
					AnchorNode anchorNode = new AnchorNode(hit.createAnchor());
					curReticleHitPosition = anchorNode.getWorldPosition();
				}
			}

			updateLine(new Color(255, 255, 250), lastAnchor.getWorldPosition(), curReticleHitPosition, lastAnchor);
			updateLabel(lastAnchor.getWorldPosition(), curReticleHitPosition, curAnchor);
			if(curGuideSquareNode != null){
				updateGuideSquare(
								lastAnchorNodes.get(0).getWorldPosition(),
								lastAnchorNodes.get(1).getWorldPosition(),
								curReticleHitPosition,
								lastAnchorNodes.get(0));
			}


		}
	}

	private void updateGuideSquare(Vector3 point1, Vector3 point2, Vector3 point3, AnchorNode parentNode) {
		if(
			curGuideSquareNode != null &&
			curGuideSquareNode.isActive() &&
			curGuideSquareNode.getParent() != null
		){
			curGuideSquareNode.getParent().removeChild(curGuideSquareNode);
			curGuideSquareNode = drawGuideSquare(point1, point2, point3, parentNode);
		}
	}

	private void updateLabel(Vector3 point1, Vector3 point2, AnchorNode parentNode) {

		if (curDrawingLineNode != null && reticle != null && lastAnchorNodes.size() > 0) {
			double distanceCm = ((int) (getDistanceMeters(point1, point2) * 1000)) / 10.0f;
			curDistance = distanceCm;

			if (curDistanceLabelNode == null && curLabelView == null) {
				curDistanceLabelNode = new Node();
				curDistanceLabelNode.setParent(parentNode);
				ViewRenderable.builder()
								.setView(this, R.layout.tiger_card_view)
								.build()
								.thenAccept(
												(renderable) -> {
													String roundDownDistance = (new DecimalFormat("#.#")).format(distanceCm);
													TextView label = ((TextView) renderable.getView());
													label.setText(roundDownDistance);
													//	renderable.setShadowCaster(false);
													//	renderable.setShadowReceiver(false);
													curDistanceLabelNode.setRenderable(renderable);
													curLabelView = label;
												})
								.exceptionally(
												(throwable) -> {
													throw new AssertionError("Could not load card view.", throwable);
												}
								);
				return;
			}

			if (curLabelView == null) return;

			curDistanceLabelNode.setWorldPosition(new Vector3((point1.x + point2.x) / 2, point1.y, (point1.z + point2.z) / 2));

			final Quaternion rotationToFloor = Quaternion.lookRotation(Vector3.up(), Vector3.forward());
			Vector3 lineVector = point2.x > point1.x ? Vector3.subtract(point2, point1) : Vector3.subtract(point1, point2);
			final Quaternion rotationToP2 = Quaternion.lookRotation(Vector3.cross(Vector3.up(), lineVector), Vector3.up());
			Quaternion rotationResult = Quaternion.multiply(rotationToP2, rotationToFloor);

			String roundDownDistance = (new DecimalFormat("#.#")).format(distanceCm);
			curLabelView.setText(roundDownDistance + "cm");

			//	renderable.setShadowCaster(false);
			//	renderable.setShadowReceiver(false);
			curDistanceLabelNode.setWorldRotation(rotationResult);
		}
	}

	private void resetVariables() {
		lastAnchorNodes.clear();
		distances.clear();
		curDrawingLineNode = null;
		if(curDistanceLabelNode.isActive() && curDistanceLabelNode.getParent() != null){
			// warn: memory leak
			curDistanceLabelNode.getParent().removeChild(curDistanceLabelNode);
		}
		curDistanceLabelNode = null;
		curDistance = -999.0;
		curLabelView = null;
		curAnchor = null;
		if(curGuideSquareNode.isActive() && curGuideSquareNode.getParent() != null){
			// warn: memory leak
			curGuideSquareNode.getParent().removeChild(curGuideSquareNode);
		}
		curGuideSquareNode = null;
	}

	private Node drawGuideSquare(Vector3 point1, Vector3 point2, Vector3 point3, AnchorNode parentNode) {

		final Vector3 line1 = Vector3.subtract(point1, point2);
		final Vector3 line2 = Vector3.subtract(point2, point3);
		final Vector3 directionP1ToP2 = line1.normalized();
		final Quaternion rotationP1ToP2 =
						Quaternion.lookRotation(directionP1ToP2, Vector3.up());
		final float width = line1.length();
		final float height = line2.length() + 0.0001f;


		Vector3 crossResult = Vector3.cross(line1, line2);
		Quaternion calibratedRotation;
		if (crossResult.y >= 0) {
			// 처음 선분 기준으로 우측꺾임
			Quaternion reverse = Quaternion.axisAngle(Vector3.forward(), 180);
			calibratedRotation = Quaternion.multiply(rotationP1ToP2, reverse);
		} else {
			// 처음 선분 기준으로 좌측꺾임 ;
			calibratedRotation = rotationP1ToP2;
		}

		Node squareNode = new Node();

		MaterialFactory.makeTransparentWithColor(getApplicationContext(), new Color(1, 1, 0, 0.3f))
						.thenAccept(
										material -> {
											ModelRenderable model = ShapeFactory.makeCube(
															new Vector3(height, 0.0001f, width),
															new Vector3(-0.5f * height, 0, 0), material);
											squareNode.setParent(parentNode);
											squareNode.setRenderable(model);
											squareNode.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
											squareNode.setWorldRotation(calibratedRotation);

										}
						);
		return squareNode;
	}
}

