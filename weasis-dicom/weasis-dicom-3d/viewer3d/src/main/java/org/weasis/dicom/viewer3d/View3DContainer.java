/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.LutToolBar;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MPRContainer;
import org.weasis.dicom.viewer3d.dockable.DisplayTool;
import org.weasis.dicom.viewer3d.dockable.VolumeTool;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.DicomVolTextureFactory;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.View3d.ViewType;
import org.weasis.dicom.viewer3d.vr.VolumeBuilder;

public class View3DContainer extends ImageViewerPlugin<DicomImageElement>
    implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(View3DContainer.class);

  static SynchView defaultMpr;

  static {
    HashMap<String, Boolean> actions = new HashMap<>();
    actions.put(ActionW.RESET.cmd(), true);
    actions.put(ActionW.ZOOM.cmd(), true);
    actions.put(ActionW.WINDOW.cmd(), true);
    actions.put(ActionW.LEVEL.cmd(), true);
    actions.put(ActionW.PRESET.cmd(), true);
    actions.put(ActionW.LUT_SHAPE.cmd(), true);
    actions.put(ActionVol.VOL_PRESET.cmd(), true);
    actions.put(ActionW.INVERT_LUT.cmd(), true);
    actions.put(ActionW.FILTER.cmd(), true);
    actions.put(ActionVol.MIP_TYPE.cmd(), true);
    actions.put(ActionVol.MIP_DEPTH.cmd(), true);
    defaultMpr =
        new SynchView(
            "MPR synch", // NON-NLS
            "mpr", // NON-NLS
            SynchData.Mode.STACK,
            ActionIcon.TILE,
            actions);
  }

  public static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE, defaultMpr);

  public static final GridBagLayoutModel VIEWS_vr =
      new GridBagLayoutModel("vr", "Volume Rendering", 1, 1, View3d.class.getName());

  public static final GridBagLayoutModel VIEWS_vr_1x2 =
      new GridBagLayoutModel(
          "vr_1x2", VIEWS_vr.getUIName() + " (1x2)", 1, 2, View3d.class.getName());
  public static final GridBagLayoutModel VIEWS_2x2_mpr =
      new GridBagLayoutModel(new LinkedHashMap<>(4), "mpr4", "MPR and Volume Rendering");

  static {
    Map<LayoutConstraints, Component> constraints = VIEWS_2x2_mpr.getConstraints();
    constraints.put(
        new LayoutConstraints(
            View3d.class.getName(),
            0,
            0,
            0,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    constraints.put(
        new LayoutConstraints(
            View3d.class.getName(),
            1,
            1,
            0,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    constraints.put(
        new LayoutConstraints(
            View3d.class.getName(),
            2,
            0,
            1,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    constraints.put(
        new LayoutConstraints(
            View3d.class.getName(),
            3,
            1,
            1,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
  }

  //  public static final List<GridBagLayoutModel> LAYOUT_LIST =
  //      Stream.concat(MPRContainer.LAYOUT_LIST.stream(), Stream.of(VIEWS_2x2_mpr,
  // VIEWS_vr)).toList();
  public static final List<GridBagLayoutModel> LAYOUT_LIST =
      Stream.of(VIEWS_vr, VIEWS_vr_1x2).toList();

  public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<>());
  public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<>());
  private static volatile boolean initComponents = false;

  // protected ControlAxes controlAxes;
  protected final DicomVolTextureFactory factory;
  protected VolumeBuilder volumeBuilder;

  public View3DContainer() {
    this(
        MPRContainer.view1,
        null,
        View3DFactory.NAME,
        ResourceUtil.getIcon(ActionIcon.VOLUME),
        null);
  }

  public View3DContainer(
      GridBagLayoutModel layoutModel, String uid, String pluginName, Icon icon, String tooltips) {
    super(EventManager.getInstance(), layoutModel, uid, pluginName, icon, tooltips);
    setSynchView(SynchView.NONE);
    this.factory = new DicomVolTextureFactory();
    initTools();

    //    final ViewerToolBar toolBar = getViewerToolBar();
    //    if (toolBar != null) {
    //      String command = ActionW.CROSSHAIR.cmd();
    //      MouseActions mouseActions = eventManager.getMouseActions();
    //      String lastAction = mouseActions.getAction(MouseActions.T_LEFT);
    //      if (!command.equals(lastAction)) {
    //        mouseActions.setAction(MouseActions.T_LEFT, command);
    //        toolBar.changeButtonState(MouseActions.T_LEFT, command);
    //      }
    //    }
    factory.addPropertyChangeListener(this);
  }

  @Override
  public List<Toolbar> getToolBar() {
    return TOOLBARS;
  }

  @Override
  public List<DockableTool> getToolPanel() {
    return TOOLS;
  }

  private void initTools() {
    setSynchView(defaultMpr);
    if (!initComponents) {
      initComponents = true;

      // Add standard toolbars
      final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

      String bundleName = context.getBundle().getSymbolicName();
      String componentName = InsertableUtil.getCName(this.getClass());
      String key = "enable"; // NON-NLS

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ViewerToolBar.class),
          key,
          true)) {
        TOOLBARS.add(
            new ViewerToolBar<>(
                eventManager,
                eventManager.getMouseActions().getActiveButtons(),
                BundleTools.SYSTEM_PREFERENCES,
                10));
      }
      //      if (InsertableUtil.getBooleanProperty(
      //          BundleTools.SYSTEM_PREFERENCES,
      //          bundleName,
      //          componentName,
      //          InsertableUtil.getCName(MeasureToolBar.class),
      //          key,
      //          true)) {
      //        TOOLBARS.add(new MeasureToolBar(eventManager, 11));
      //      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ZoomToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new ZoomToolBar(eventManager, 20, false));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(RotationToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new RotationToolBar(eventManager, 30));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(LutToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new VolLutToolBar(eventManager, 40));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(View3DToolbar.class),
          key,
          true)) {
        TOOLBARS.add(new View3DToolbar(50));
      }

      PluginTool tool;

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(MiniTool.class),
          key,
          true)) {
        tool =
            new MiniTool(MiniTool.BUTTON_NAME) {

              @Override
              public SliderChangeListener[] getActions() {
                ArrayList<SliderChangeListener> listeners = new ArrayList<>(3);
                eventManager.getAction(ActionVol.SCROLLING).ifPresent(listeners::add);
                eventManager.getAction(ActionW.ZOOM).ifPresent(listeners::add);
                eventManager.getAction(ActionW.ROTATION).ifPresent(listeners::add);
                return listeners.toArray(new SliderChangeListener[0]);
              }
            };
        TOOLS.add(tool);
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(VolumeTool.class),
          key,
          true)) {
        TOOLS.add(new VolumeTool(VolumeTool.BUTTON_NAME));
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(DisplayTool.class),
          key,
          true)) {
        tool = new DisplayTool(DisplayTool.BUTTON_NAME);
        TOOLS.add(tool);
        eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(MeasureTool.class),
          key,
          true)) {
        tool = new MeasureTool(eventManager);
        TOOLS.add(tool);
      }

      InsertableUtil.sortInsertable(TOOLS);

      // Send event to synchronize the series selection.
      DataExplorerView dicomView = UIManager.getExplorerPlugin(DicomExplorer.NAME);
      if (dicomView != null) {
        eventManager.addSeriesViewerListener((SeriesViewerListener) dicomView);
      }

      Preferences prefs = BundlePreferences.getDefaultPreferences(context);
      if (prefs != null) {
        InsertableUtil.applyPreferences(TOOLBARS, prefs, bundleName, componentName, Type.TOOLBAR);
        InsertableUtil.applyPreferences(TOOLS, prefs, bundleName, componentName, Type.TOOL);
      }
    }
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> series) {
    if (series == null) {
      return;
    }
    try {
      if (View3DFactory.isOpenglEnable()) {
        MediaSeries<DicomImageElement> oldSequence = null;
        if (volumeBuilder != null) {
          oldSequence = volumeBuilder.getVolTexture().getSeries();
        }
        if (!series.equals(oldSequence)) {
          UIManager.closeSeries(oldSequence);
          synchronized (this) {
            this.volumeBuilder = new VolumeBuilder(factory.createImageSeries(series));
            for (ViewCanvas<DicomImageElement> view : view2ds) {
              if (view instanceof View3d v) {
                v.setVolTexture(volumeBuilder.getVolTexture());
              }
            }
            volumeBuilder.getVolTexture().getSeries().setOpen(true);
            startVolumeBuilder();
          }
        }
      } else {
        View3DFactory.showOpenglErrorMessage(this);
      }
    } catch (Exception ex) {
      close();
      handleFocusAfterClosing();
      if (ex instanceof IllegalArgumentException) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), null, JOptionPane.ERROR_MESSAGE);
      } else {
        View3DFactory.showOpenglErrorMessage(this);
      }
      return;
    }

    setPluginName(TagD.getTagValue(series, Tag.PatientName, String.class));
    setSelected(true);
  }

  public void reload() {
    if (volumeBuilder != null) {
      MediaSeries<DicomImageElement> oldSequence = volumeBuilder.getVolTexture().getSeries();
      // Force to rebuild
      this.volumeBuilder = null;
      addSeries(oldSequence);
    }
  }

  protected void startVolumeBuilder() {
    VolumeBuilder builder = volumeBuilder;
    if (!builder.isRunning() && !builder.isDone()) {
      builder.start();
    }
  }

  private DicomVolTexture getVolTexture() {
    VolumeBuilder builder = volumeBuilder;
    if (builder != null) {
      return builder.getVolTexture();
    }
    return null;
  }

  @Override
  protected synchronized void setLayoutModel(GridBagLayoutModel layoutModel) {
    super.setLayoutModel(layoutModel);

    DicomVolTexture curVolTexture = getVolTexture();
    final Map<LayoutConstraints, Component> elements = getLayoutModel().getConstraints();
    Iterator<Component> enumVal = elements.values().iterator();
    int position = 0;
    while (enumVal.hasNext()) {
      Component next = enumVal.next();
      if (next instanceof View3d vt) {
        ViewType viewType;
        if (layoutModel.getConstraints().size() < 3) {
          viewType = ViewType.VOLUME3D;
        } else {
          viewType =
              switch (position) {
                case 0 -> ViewType.AXIAL;
                case 1 -> ViewType.CORONAL;
                case 2 -> ViewType.SAGITTAL;
                default -> ViewType.VOLUME3D;
              };
        }
        vt.setViewType(viewType);
        position++;
        vt.setVolTexture(curVolTexture);
      }
    }
    eventManager.updateComponentsListener(selectedImagePane);
    repaint();
  }

  protected void removeContent(final ObservableEvent event) {
    Object newVal = event.getNewValue();
    if (newVal instanceof MediaSeriesGroup group) {
      View2dContainer.removeViews(this, group, event);
    }
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    if (menuRoot != null) {
      menuRoot.removeAll();
      if (eventManager instanceof EventManager manager) {
        GuiUtils.addItemToMenu(menuRoot, manager.getPresetMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutShapeMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutMenu(null));
        menuRoot.add(new JSeparator());
        GuiUtils.addItemToMenu(menuRoot, manager.getViewTypeMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getMipTypeMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getShadingMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getSProjectionMenu(null));
        menuRoot.add(new JSeparator());
        GuiUtils.addItemToMenu(menuRoot, manager.getZoomMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getOrientationMenu(null));
        menuRoot.add(new JSeparator());
        menuRoot.add(manager.getResetMenu(null));
      }
    }
    return menuRoot;
  }

  @Override
  public void close() {
    View3DFactory.closeSeriesViewer(this);
    super.close();

    GuiExecutor.instance()
        .execute(
            () -> {
              for (ViewCanvas v : view2ds) {
                resetMaximizedSelectedImagePane(v);
                v.disposeView();
              }
            });
  }

  @Override
  public void propertyChange(final PropertyChangeEvent event) {
    String command = event.getPropertyName();
    boolean fullyLoaded = DicomVolTextureFactory.FULLY_LOADED.equals(command);
    if (fullyLoaded || DicomVolTextureFactory.PARTIALLY_LOADED.equals(command)) {
      if (event.getNewValue() instanceof DicomVolTexture texture) {
        for (ViewCanvas<DicomImageElement> view : view2ds) {
          if (view instanceof View3d v) {
            if (fullyLoaded) {
              v.getCamera().resetAll();
              v.setVolTexture(texture);
            } else {
              v.display();
            }
          }
        }
      }
    }

    if (event instanceof ObservableEvent e) {
      ObservableEvent.BasicAction action = e.getActionCommand();
      if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        removeContent(e);
      }
    }
  }

  /**
   * Overridden because this Container can be closed before the first call to setSelected.
   *
   * <p>If that happens, all toolbars get visible and viewer not. Need a way out.
   *
   * @param selected
   */
  @Override
  public void setSelected(boolean selected) {
    EventManager eventManager = EventManager.getInstance();
    if (selected) {
      eventManager.setSelectedView2dContainer(this);

      // Send event to select the related patient in Dicom Explorer.
      DataExplorerView dicomView = UIManager.getExplorerPlugin(DicomExplorer.NAME);
      if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
        dicomView
            .getDataExplorerModel()
            .firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, this, null, getGroupID()));
      }

    } else {
      eventManager.setSelectedView2dContainer(null);
    }
  }

  @Override
  public void addSeriesList(
      List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
    if (seriesList != null && !seriesList.isEmpty()) {
      addSeries(seriesList.get(0));
    }
  }

  @Override
  public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
    // Do it in addSeries()
  }

  @Override
  public boolean isViewType(Class defaultClass, String type) {
    if (defaultClass != null) {
      try {
        Class clazz = Class.forName(type);
        return defaultClass.isAssignableFrom(clazz);
      } catch (Exception e) {
        LOGGER.error("Checking view type", e);
      }
    }
    return false;
  }

  @Override
  public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
    return View3DFactory.getViewTypeNumber(layout, defaultClass);
  }

  @Override
  public ViewCanvas<DicomImageElement> createDefaultView(String classType) {
    try {
      return new View3d(eventManager, null);
    } catch (Exception e) {
      LOGGER.error("Cannot create a 3D view: {}", e.getMessage());
    }
    return null;
  }

  @Override
  public Component createComponent(String clazz) {
    if (isViewType(View3d.class, clazz)) {
      return createDefaultView(clazz).getJComponent();
    }

    try {
      // FIXME use classloader.loadClass or injection
      Class cl = Class.forName(clazz);
      JComponent component = (JComponent) cl.newInstance();
      if (component instanceof SeriesViewerListener) {
        eventManager.addSeriesViewerListener((SeriesViewerListener) component);
      }
      return component;

    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public WtoolBar getStatusBar() {
    return null;
  }

  @Override
  public List<Action> getExportActions() {
    return null;
  }

  @Override
  public List<Action> getPrintActions() {
    return null;
  }

  @Override
  public List<SynchView> getSynchList() {
    return SYNCH_LIST;
  }

  @Override
  public List<GridBagLayoutModel> getLayoutList() {
    return LAYOUT_LIST;
  }
}