package net.osmand.plus.mapmarkers;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.osmand.AndroidUtils;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.IconsCache;
import net.osmand.plus.MapMarkersHelper;
import net.osmand.plus.MapMarkersHelper.MapMarker;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.MapViewTrackingUtilities;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.mapmarkers.adapters.MapMarkersItemTouchHelperCallback;
import net.osmand.plus.mapmarkers.adapters.MapMarkersListAdapter;
import net.osmand.plus.measurementtool.RecyclerViewFragment;
import net.osmand.plus.measurementtool.SnapToRoadBottomSheetDialogFragment;
import net.osmand.plus.measurementtool.SnapToRoadBottomSheetDialogFragment.SnapToRoadFragmentListener;
import net.osmand.plus.views.MapMarkersLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarController;

import static net.osmand.plus.OsmandSettings.LANDSCAPE_MIDDLE_RIGHT_CONSTANT;
import static net.osmand.plus.OsmandSettings.MIDDLE_TOP_CONSTANT;

public class PlanRouteFragment extends Fragment implements OsmAndLocationListener {

	public static final String TAG = "PlanRouteFragment";

	private MapMarkersHelper markersHelper;
	private MapMarkersListAdapter adapter;
	private IconsCache iconsCache;
	private PlanRouteToolbarController toolbarController;
	private ApplicationMode appMode;
	private int previousMapPosition;
	private int selectedCount = 0;

	private Location location;
	private boolean locationUpdateStarted;

	private boolean nightMode;
	private boolean portrait;
	private boolean markersListOpened;
	private boolean wasCollapseButtonVisible;

	private View mainView;
	private RecyclerView markersRv;
	private ImageView upDownIconIv;
	private TextView distanceTv;
	private TextView timeTv;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		final MapActivity mapActivity = getMapActivity();
		markersHelper = mapActivity.getMyApplication().getMapMarkersHelper();

		// Handling screen rotation
		FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
		Fragment snapToRoadFragment = fragmentManager.findFragmentByTag(SnapToRoadBottomSheetDialogFragment.TAG);
		if (snapToRoadFragment != null) {
			((SnapToRoadBottomSheetDialogFragment) snapToRoadFragment).setListener(createSnapToRoadFragmentListener());
		}
		// If rotate the screen from landscape to portrait when the list of markers is displayed then
		// the RecyclerViewFragment will exist without view. This is necessary to remove it.
		if (!portrait) {
			hideMarkersListFragment();
		}

		iconsCache = mapActivity.getMyApplication().getIconsCache();
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		final int themeRes = nightMode ? R.style.OsmandDarkTheme : R.style.OsmandLightTheme;
		final int backgroundColor = ContextCompat.getColor(mapActivity,
				nightMode ? R.color.ctx_menu_info_view_bg_dark : R.color.ctx_menu_info_view_bg_light);
		portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);

		View view = View.inflate(new ContextThemeWrapper(getContext(), themeRes), R.layout.fragment_plan_route, null);

		mainView = view.findViewById(R.id.main_view);
		AndroidUtils.setBackground(mapActivity, mainView, nightMode, R.drawable.bg_bottom_menu_light, R.drawable.bg_bottom_menu_dark);

		distanceTv = (TextView) mainView.findViewById(R.id.markers_distance_text_view);
		timeTv = (TextView) mainView.findViewById(R.id.markers_time_text_view);

		enterPlanRouteMode();

		View markersListContainer = mainView.findViewById(R.id.markers_list_container);
		if (portrait && markersListContainer != null) {
			markersListContainer.setBackgroundColor(backgroundColor);
		}

		upDownIconIv = (ImageView) mainView.findViewById(R.id.up_down_icon);
		upDownIconIv.setImageDrawable(getContentIcon(R.drawable.ic_action_arrow_up));
		((ImageView) mainView.findViewById(R.id.sort_icon)).setImageDrawable(getContentIcon(R.drawable.ic_sort_waypoint_dark));

		mainView.findViewById(R.id.up_down_row).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!markersListOpened) {
					showMarkersList();
				} else {
					hideMarkersList();
				}
			}
		});

		mainView.findViewById(R.id.select_all_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				int activeMarkersCount = markersHelper.getMapMarkers().size();
				if (selectedCount == activeMarkersCount) {
					markersHelper.deselectAllActiveMarkers();
					selectedCount = 0;
				} else {
					markersHelper.selectAllActiveMarkers();
					selectedCount = activeMarkersCount;
				}
				adapter.notifyDataSetChanged();
				updateSelectButton();
			}
		});

		mainView.findViewById(R.id.sort_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Toast.makeText(mapActivity, "Sort", Toast.LENGTH_SHORT).show();
			}
		});

		mainView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Toast.makeText(mapActivity, "Save", Toast.LENGTH_SHORT).show();
			}
		});

		toolbarController = new PlanRouteToolbarController();
		toolbarController.setBackBtnIconIds(R.drawable.ic_action_mode_back, R.drawable.ic_action_mode_back);
		toolbarController.setTitle(getString(R.string.plan_route));
		toolbarController.setOnBackButtonClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				quit(false);
			}
		});
		mapActivity.showTopToolbar(toolbarController);

		if (portrait) {
			markersRv = mainView.findViewById(R.id.markers_recycler_view);
		} else {
			markersRv = new RecyclerView(mapActivity);
		}

		adapter = new MapMarkersListAdapter(mapActivity);
		final ItemTouchHelper touchHelper = new ItemTouchHelper(new MapMarkersItemTouchHelperCallback(adapter));
		touchHelper.attachToRecyclerView(markersRv);
		adapter.setAdapterListener(new MapMarkersListAdapter.MapMarkersListAdapterListener() {

			private int fromPosition;
			private int toPosition;

			@Override
			public void onItemClick(View view) {
				int pos = markersRv.getChildAdapterPosition(view);
				MapMarker marker = adapter.getItem(pos);
				selectedCount = marker.selected ? selectedCount - 1 : selectedCount + 1;
				marker.selected = !marker.selected;
				adapter.notifyItemChanged(pos);
				updateSelectButton();
			}

			@Override
			public void onDragStarted(RecyclerView.ViewHolder holder) {
				fromPosition = holder.getAdapterPosition();
				touchHelper.startDrag(holder);
			}

			@Override
			public void onDragEnded(RecyclerView.ViewHolder holder) {
				toPosition = holder.getAdapterPosition();
				if (toPosition >= 0 && fromPosition >= 0 && toPosition != fromPosition) {
					mapActivity.getMyApplication().getMapMarkersHelper().checkAndFixActiveMarkersOrderIfNeeded();
					adapter.notifyDataSetChanged();
				}
			}
		});
		boolean isSmartphone = getResources().getConfiguration().smallestScreenWidthDp < 600;
		markersRv.setPadding(0, 0, 0, AndroidUtils.dpToPx(mapActivity, isSmartphone ? 72 : 108));
		markersRv.setClipToPadding(false);
		markersRv.setLayoutManager(new LinearLayoutManager(getContext()));
		markersRv.setAdapter(adapter);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		startLocationUpdate();
	}

	@Override
	public void onPause() {
		super.onPause();
		stopLocationUpdate();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		exitPlanRouteMode();
		if (markersListOpened) {
			hideMarkersList();
		}
	}

	@Override
	public void updateLocation(Location location) {
		boolean newLocation = this.location == null && location != null;
		boolean locationChanged = this.location != null && location != null
				&& this.location.getLatitude() != location.getLatitude()
				&& this.location.getLongitude() != location.getLongitude();
		if (newLocation || locationChanged) {
			this.location = location;
			updateLocationUi();
		}
	}

	private MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}

	private MapMarkersLayer getMapMarkersLayer() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return mapActivity.getMapLayers().getMapMarkersLayer();
		}
		return null;
	}

	private Drawable getContentIcon(@DrawableRes int id) {
		return iconsCache.getIcon(id, nightMode ? R.color.ctx_menu_info_text_dark : R.color.icon_color);
	}

	private Drawable getActiveIcon(@DrawableRes int id) {
		return iconsCache.getIcon(id, nightMode ? R.color.osmand_orange : R.color.color_myloc_distance);
	}

	private SnapToRoadFragmentListener createSnapToRoadFragmentListener() {
		return new SnapToRoadFragmentListener() {
			@Override
			public void onDestroyView(boolean snapToRoadEnabled) {

			}

			@Override
			public void onApplicationModeItemClick(ApplicationMode mode) {
				appMode = mode;
				setupAppModesBtn();
			}
		};
	}

	private void enterPlanRouteMode() {
		final MapActivity mapActivity = getMapActivity();
		MapMarkersLayer markersLayer = getMapMarkersLayer();
		if (mapActivity != null && markersLayer != null) {
			markersLayer.setInPlanRouteMode(true);
			mapActivity.disableDrawer();

			mark(portrait ? View.INVISIBLE : View.GONE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info);
			mark(View.GONE,
					R.id.map_route_info_button,
					R.id.map_menu_button,
					R.id.map_compass_button,
					R.id.map_layers_button,
					R.id.map_search_button,
					R.id.map_quick_actions_button);

			View collapseButton = mapActivity.findViewById(R.id.map_collapse_button);
			if (collapseButton != null && collapseButton.getVisibility() == View.VISIBLE) {
				wasCollapseButtonVisible = true;
				collapseButton.setVisibility(View.INVISIBLE);
			} else {
				wasCollapseButtonVisible = false;
			}

			if (appMode == null) {
				appMode = mapActivity.getMyApplication().getSettings().getApplicationMode();
			}
			setupAppModesBtn();

			mapActivity.refreshMap();
			updateText();
			updateSelectButton();
		}
	}

	private void setupAppModesBtn() {
		final MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && appMode != null) {
			final ImageButton appModesBtn = (ImageButton) mapActivity.findViewById(R.id.snap_to_road_image_button);
			appModesBtn.setBackgroundResource(nightMode ? R.drawable.btn_circle_night : R.drawable.btn_circle);
			appModesBtn.setImageDrawable(getActiveIcon(appMode.getSmallIconDark()));
			appModesBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					SnapToRoadBottomSheetDialogFragment fragment = new SnapToRoadBottomSheetDialogFragment();
					fragment.setListener(createSnapToRoadFragmentListener());
					fragment.show(mapActivity.getSupportFragmentManager(), SnapToRoadBottomSheetDialogFragment.TAG);
				}
			});
			appModesBtn.setVisibility(View.VISIBLE);
		}
	}

	private void exitPlanRouteMode() {
		MapActivity mapActivity = getMapActivity();
		MapMarkersLayer markersLayer = getMapMarkersLayer();
		if (mapActivity != null && markersLayer != null) {
			markersLayer.setInPlanRouteMode(false);
			mapActivity.enableDrawer();
			if (toolbarController != null) {
				mapActivity.hideTopToolbar(toolbarController);
			}

			mark(View.VISIBLE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info,
					R.id.map_route_info_button,
					R.id.map_menu_button,
					R.id.map_compass_button,
					R.id.map_layers_button,
					R.id.map_search_button,
					R.id.map_quick_actions_button);

			View collapseButton = mapActivity.findViewById(R.id.map_collapse_button);
			if (collapseButton != null && wasCollapseButtonVisible) {
				collapseButton.setVisibility(View.VISIBLE);
			}

			mapActivity.findViewById(R.id.snap_to_road_image_button).setVisibility(View.GONE);
			mainView.findViewById(R.id.snap_to_road_progress_bar).setVisibility(View.GONE);

			mapActivity.refreshMap();
		}
	}

	private void updateText() {
		distanceTv.setText("1.39 km,");
		timeTv.setText("~ 45 min.");
	}

	private void updateSelectButton() {
		if (selectedCount == markersHelper.getMapMarkers().size()) {
			((TextView) mainView.findViewById(R.id.select_all_button)).setText(getString(R.string.shared_string_deselect_all));
		} else {
			((TextView) mainView.findViewById(R.id.select_all_button)).setText(getString(R.string.shared_string_select_all));
		}
	}


	private void updateLocationUi() {
		final MapActivity mapActivity = (MapActivity) getActivity();
		if (mapActivity != null && adapter != null) {
			mapActivity.getMyApplication().runInUIThread(new Runnable() {
				@Override
				public void run() {
					if (location == null) {
						location = mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation();
					}
					MapViewTrackingUtilities utilities = mapActivity.getMapViewTrackingUtilities();
					boolean useCenter = !(utilities.isMapLinkedToLocation() && location != null);

					adapter.setUseCenter(useCenter);
					adapter.setLocation(useCenter ? mapActivity.getMapLocation() : new LatLon(location.getLatitude(), location.getLongitude()));
					adapter.notifyDataSetChanged();
				}
			});
		}
	}

	private void mark(int status, int... widgets) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			for (int widget : widgets) {
				View v = mapActivity.findViewById(widget);
				if (v != null) {
					v.setVisibility(status);
				}
			}
		}
	}

	private void showMarkersList() {
		MapActivity mapActivity = getMapActivity();
		MapMarkersLayer markersLayer = getMapMarkersLayer();
		if (mapActivity != null && markersLayer != null) {
			markersListOpened = true;
			markersLayer.setMarkersListOpened(true);
			upDownIconIv.setImageDrawable(getContentIcon(R.drawable.ic_action_arrow_down));
			View listContainer = mainView.findViewById(R.id.markers_list_container);
			if (portrait && listContainer != null) {
				listContainer.setVisibility(View.VISIBLE);
			} else {
				showMarkersListFragment();
			}
			OsmandMapTileView tileView = mapActivity.getMapView();
			previousMapPosition = tileView.getMapPosition();
			if (portrait) {
				tileView.setMapPosition(MIDDLE_TOP_CONSTANT);
			} else {
				tileView.setMapPosition(LANDSCAPE_MIDDLE_RIGHT_CONSTANT);
			}
			mapActivity.refreshMap();
		}
	}

	private void hideMarkersList() {
		MapActivity mapActivity = getMapActivity();
		MapMarkersLayer markersLayer = getMapMarkersLayer();
		if (mapActivity != null && markersLayer != null) {
			markersListOpened = false;
			markersLayer.setMarkersListOpened(false);
			upDownIconIv.setImageDrawable(getContentIcon(R.drawable.ic_action_arrow_up));
			View listContainer = mainView.findViewById(R.id.markers_list_container);
			if (portrait && listContainer != null) {
				listContainer.setVisibility(View.GONE);
			} else {
				hideMarkersListFragment();
			}
			mapActivity.getMapView().setMapPosition(previousMapPosition);
			mapActivity.refreshMap();
		}
	}

	private void showMarkersListFragment() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			View upDownRow = mainView.findViewById(R.id.up_down_row);
			int screenHeight = AndroidUtils.getScreenHeight(mapActivity) - AndroidUtils.getStatusBarHeight(mapActivity);
			RecyclerViewFragment fragment = new RecyclerViewFragment();
			fragment.setRecyclerView(markersRv);
			fragment.setWidth(upDownRow.getWidth());
			fragment.setHeight(screenHeight - upDownRow.getHeight());
			mapActivity.getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, fragment, RecyclerViewFragment.TAG)
					.commitAllowingStateLoss();
		}
	}

	private void hideMarkersListFragment() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			try {
				FragmentManager manager = mapActivity.getSupportFragmentManager();
				Fragment fragment = manager.findFragmentByTag(RecyclerViewFragment.TAG);
				if (fragment != null) {
					manager.beginTransaction().remove(fragment).commitNowAllowingStateLoss();
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private void startLocationUpdate() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && !locationUpdateStarted) {
			locationUpdateStarted = true;
			mapActivity.getMyApplication().getLocationProvider().addLocationListener(this);
			updateLocationUi();
		}
	}

	private void stopLocationUpdate() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && locationUpdateStarted) {
			locationUpdateStarted = false;
			mapActivity.getMyApplication().getLocationProvider().removeLocationListener(this);
		}
	}

	public boolean quit(boolean hideMarkersListFirst) {
		if (markersListOpened && hideMarkersListFirst) {
			hideMarkersList();
			return false;
		} else {
			dismiss(getMapActivity());
			return true;
		}
	}

	private void dismiss(MapActivity activity) {
		if (markersListOpened) {
			hideMarkersList();
		}
		markersHelper.deselectAllActiveMarkers();
		activity.getSupportFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
	}

	public static boolean showInstance(FragmentManager fragmentManager) {
		try {
			PlanRouteFragment fragment = new PlanRouteFragment();
			fragment.setRetainInstance(true);
			fragmentManager.beginTransaction()
					.add(R.id.bottomFragmentContainer, fragment, PlanRouteFragment.TAG)
					.commitAllowingStateLoss();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private class PlanRouteToolbarController extends TopToolbarController {

		PlanRouteToolbarController() {
			super(MapInfoWidgetsFactory.TopToolbarControllerType.MEASUREMENT_TOOL);
			setBackBtnIconClrIds(0, 0);
			setTitleTextClrIds(R.color.primary_text_dark, R.color.primary_text_dark);
			setDescrTextClrIds(R.color.primary_text_dark, R.color.primary_text_dark);
			setBgIds(R.drawable.gradient_toolbar, R.drawable.gradient_toolbar,
					R.drawable.gradient_toolbar, R.drawable.gradient_toolbar);
			setCloseBtnVisible(false);
		}

		@Override
		public void updateToolbar(MapInfoWidgetsFactory.TopToolbarView view) {
			super.updateToolbar(view);
			View shadow = view.getShadowView();
			if (shadow != null) {
				shadow.setVisibility(View.GONE);
			}
		}
	}
}