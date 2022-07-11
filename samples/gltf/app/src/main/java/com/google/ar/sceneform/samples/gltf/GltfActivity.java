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

import static com.google.ar.sceneform.rendering.MaterialFactory.MATERIAL_TEXTURE;
import static com.google.ar.sceneform.rendering.PlaneRenderer.MATERIAL_UV_SCALE;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
//import android.media.Image;
//import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.ArraySet;
import android.util.Log;
//import android.view.Gravity;
//import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.filament.gltfio.Animator;
//import com.google.android.filament.gltfio.FilamentAsset;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
//import com.google.ar.core.Pose;
//import com.google.ar.core.Session;
//import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
//import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
//import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
//import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
//import com.google.ar.sceneform.ux.TransformableNode;

//import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import java.util.ArrayList;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class GltfActivity extends AppCompatActivity {
	private static final String TAG = GltfActivity.class.getSimpleName();
	private static final double MIN_OPENGL_VERSION = 3.0;

	private ArFragment arFragment;
	private Renderable renderable;
	private List<AnchorNode> lastAnchorNode = new ArrayList<AnchorNode>();

	private static class AnimationInstance {
		Animator animator;
		Long startTime;
		float duration;
		int index;

		AnimationInstance(Animator animator, int index, Long startTime) {
			this.animator = animator;
			this.startTime = startTime;
			this.duration = animator.getAnimationDuration(index);
			this.index = index;
		}
	}

	private final Set<AnimationInstance> animators = new ArraySet<>();

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

//		WeakReference<GltfActivity> weakActivity = new WeakReference<>(this);
//		ModelRenderable.builder()
//						.setSource(
//										this,
//										Uri.parse(
//														"https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"))
//						.setIsFilamentGltf(true)
//						.build()
//						.thenAccept(
//										modelRenderable -> {
//											GltfActivity activity = weakActivity.get();
//											if (activity != null) {
//												activity.renderable = modelRenderable;
//											}
//										})
//						.exceptionally(
//										throwable -> {
//											Toast toast =
//															Toast.makeText(this, "Unable to load Tiger renderable", Toast.LENGTH_LONG);
//											toast.setGravity(Gravity.CENTER, 0, 0);
//											toast.show();
//											return null;
//										});

		arFragment.setOnTapArPlaneListener(
						(HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
							int val = motionEvent.getActionMasked();
							float axisVal = motionEvent.getAxisValue(MotionEvent.AXIS_X, motionEvent.getPointerId(motionEvent.getPointerCount() - 1));
							Anchor anchor = hitResult.createAnchor();
							AnchorNode anchorNode = new AnchorNode(anchor);
							lastAnchorNode.add(anchorNode);
							anchorNode.setParent(arFragment.getArSceneView().getScene());

							MaterialFactory.makeOpaqueWithColor(getApplicationContext(), colors.get(nextColor))
											.thenAccept(
															material -> {
																ModelRenderable model = ShapeFactory.makeCylinder(
																				.005f,
																				.0001f,
																				Vector3.zero(), material);

																Node node = new Node();
																node.setParent(anchorNode);
																node.setRenderable(model);
																node.setWorldPosition(anchorNode.getWorldPosition());
																nextColor = (nextColor + 1) % colors.size();
															}
											);

							if (lastAnchorNode.size() >= 2) {
								Vector3 point1 = lastAnchorNode.get(0).getWorldPosition();
								Vector3 point2 = lastAnchorNode.get(1).getWorldPosition();
								drawLineNDistance(point1, point2, anchorNode);


								lastAnchorNode = new ArrayList<AnchorNode>();
							}
						});
//		original sample source
//        (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
//          if (renderable == null) {
//            return;
//          }
//
//          // Create the Anchor.
//          Anchor anchor = hitResult.createAnchor();
//          AnchorNode anchorNode = new AnchorNode(anchor);
//          anchorNode.setParent(arFragment.getArSceneView().getScene());
//
//          // Create the transformable model and add it to the anchor.
//          TransformableNode model = new TransformableNode(arFragment.getTransformationSystem());
//          model.setParent(anchorNode);
//          model.setRenderable(renderable);
//          model.select();
//
//          FilamentAsset filamentAsset = model.getRenderableInstance().getFilamentAsset();
//          if (filamentAsset.getAnimator().getAnimationCount() > 0) {
//            animators.add(new AnimationInstance(filamentAsset.getAnimator(), 0, System.nanoTime()));
//          }
//
//          Color color = colors.get(nextColor);
//          nextColor++;
//          for (int i = 0; i < renderable.getSubmeshCount(); ++i) {
//            Material material = renderable.getMaterial(i);
//            material.setFloat4("baseColorFactor", color);
//          }
//
//          Node distanceNode = new Node();
//          distanceNode.setParent(model);
//          distanceNode.setEnabled(false);
//          distanceNode.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));
//          ViewRenderable.builder()
//                  .setView(this, R.layout.tiger_card_view)
//                  .build()
//                  .thenAccept(
//                          (renderable) -> {
//                              distanceNode.setRenderable(renderable);
//                              distanceNode.setEnabled(true);
//                          })
//                  .exceptionally(
//                          (throwable) -> {
//                              throw new AssertionError("Could not load card view.", throwable);
//                          }
//                  );
//        });

		arFragment
						.getArSceneView()
						.getScene()
						.addOnUpdateListener(
										frameTime -> {
											Frame frame = arFragment.getArSceneView().getArFrame();
											Camera camera = frame.getCamera();
										});
	} // onCreate end

//	// 호랑이 애니메이션
//	arFrament
//					.getArSceneView()
//					.getScene()
//						.addOnUpdateListener(
//					frameTime -> {
//		Long time = System.nanoTime();
//		for (AnimationInstance animator : animators) {
//			animator.animator.applyAnimation(
//							animator.index,
//							(float) ((time - animator.startTime) / (double) SECONDS.toNanos(1))
//											% animator.duration);
//			animator.animator.updateBoneMatrices();
//		}
//	});

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
															new Vector3(.001f, .0001f, difference.length()),
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

	private void drawLineNDistance(Vector3 point1, Vector3 point2, AnchorNode anchorNode) {
		//drawLine
		if (lastAnchorNode.size() >= 2) {
			drawLine(new Color(255, 255, 244), point1, point2, anchorNode);

			// represent distance Label
			double distanceCm = ((int) (getDistanceMeters(point1, point2) * 1000)) / 10.0f;

			Node distanceNode = new Node();
			distanceNode.setParent(lastAnchorNode.get(0));
			distanceNode.setEnabled(false);
//			distanceNode.setLocalPosition(Vector3.add(Vector3.subtract(point2, point1).scaled(.5f), new Vector3(0, 0.05f, 0)));
			distanceNode.setLocalPosition(Vector3.subtract(point2, point1).scaled(.5f));

			final Vector3 labelPoint = distanceNode.getWorldPosition();
			final Vector3 zAxisPoint = Vector3.add(distanceNode.getWorldPosition(), new Vector3(0, 0, 1));
			final Vector3 difference = Vector3.subtract(labelPoint, zAxisPoint);
			final Vector3 directionFromTopToBottom = difference.normalized();
			final Quaternion rotationFromAToB =
							Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

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
												distanceNode.setWorldRotation(rotationFromAToB);
												distanceNode.setWorldRotation(rotationToFloor);
											})
							.exceptionally(
											(throwable) -> {
												throw new AssertionError("Could not load card view.", throwable);
											}
							);

			// 라벨위치 기준 테스트용 표시
			MaterialFactory.makeOpaqueWithColor(getApplicationContext(), colors.get(nextColor))
							.thenAccept(
											material -> {
												ModelRenderable model = ShapeFactory.makeCylinder(
																.005f,
																.0001f,
																Vector3.zero(), material);

												Node node = new Node();
												node.setParent(distanceNode);
												node.setRenderable(model);
												node.setWorldPosition(distanceNode.getWorldPosition());
												nextColor = (nextColor + 1) % colors.size();
											}
							);
			drawLine(new Color(0, 255, 0), labelPoint, zAxisPoint, anchorNode);
		}
	}
}

