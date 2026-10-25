package com.kongbai.kculauncher.control;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.GsonBuilder;
import com.kongbai.kculauncher.mio.touchcontroller.TouchController;
import com.kongbai.kculauncher.mio.touchcontroller.TouchControllerInputView;
import com.kongbai.kculauncher.mio.ui.dialog.GamepadMapDialog;
import com.kongbai.kculauncher.mio.ui.view.CursorView;
import com.kongbai.kculauncher.mio.ui.view.DraggableTextView;
import com.kongbai.kculauncher.mio.util.AndroidUtilKt;
import com.kongbai.kculauncher.mio.util.ImageUtil;
import com.kongbai.kculauncher.BuildConfig;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.JVMActivity;
import com.kongbai.kculauncher.activity.JVMCrashActivity;
import com.kongbai.kculauncher.control.data.ButtonStyles;
import com.kongbai.kculauncher.control.data.ControlButtonData;
import com.kongbai.kculauncher.control.data.ControlButtonStyle;
import com.kongbai.kculauncher.control.view.ControlButton;
import com.kongbai.kculauncher.control.data.ControlDirectionData;
import com.kongbai.kculauncher.control.data.ControlDirectionStyle;
import com.kongbai.kculauncher.control.data.ControlViewGroup;
import com.kongbai.kculauncher.control.data.CustomControl;
import com.kongbai.kculauncher.control.data.DirectionStyles;
import com.kongbai.kculauncher.control.data.QuickInputTexts;
import com.kongbai.kculauncher.control.keyboard.LwjglCharSender;
import com.kongbai.kculauncher.control.keyboard.TouchCharInput;
import com.kongbai.kculauncher.control.view.GameItemBar;
import com.kongbai.kculauncher.control.view.LogWindow;
import com.kongbai.kculauncher.control.view.MenuView;
import com.kongbai.kculauncher.control.view.TouchPad;
import com.kongbai.kculauncher.control.view.ViewManager;
import com.kongbai.kculauncher.game.sdl.GamepadInputMode;
import com.kongbai.kculauncher.game.sdl.SdlBridge;
import com.kongbai.kculauncher.game.sdl.SdlSettings;
import com.kongbai.kculauncher.setting.Controller;
import com.kongbai.kculauncher.setting.Controllers;
import com.kongbai.kculauncher.setting.GameOption;
import com.kongbai.kculauncher.setting.MenuSetting;
import com.kongbai.kculauncher.launcher.bridge.KCUBridge;
import com.kongbai.kculauncher.launcher.bridge.KCUBridgeCallback;
import com.kongbai.kculauncher.launcher.keycodes.AndroidKeycodeMap;
import com.kongbai.kculauncher.launcher.keycodes.KCUKeycodes;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleBooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleIntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleObjectProperty;
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.core.util.io.FileUtils;
import com.kongbai.kculauncher.library.component.KCUActivity;
import com.kongbai.kculauncher.library.component.dialog.EditDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUProgressBar;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUTabLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import fr.spse.gamepad_remapper.Remapper;
import kotlin.Unit;

public class GameMenu implements MenuCallback, KCUBridgeCallback {

    private boolean simulated;
    private KCUActivity activity;
    @Nullable
    private KCUBridge fclBridge;
    private KCUInput fclInput;
    private MenuSetting menuSetting;
    private float cursorX;
    private float cursorY;
    private float pointerX;
    private float pointerY;

    private View layout;
    private RelativeLayout baseLayout;
    private TouchPad touchPad;
    private GameItemBar gameItemBar;
    private LogWindow logWindow;
    public DraggableTextView fpsText;
    public DraggableTextView memoryText;
    private TouchCharInput touchCharInput;
    private TouchControllerInputView touchControllerInputView;
    private KCUProgressBar launchProgress;
    private CursorView cursorView;
    private ViewManager viewManager;
    private Gyroscope gyroscope;
    private GameOption gameOption;

    private LeftMenuAdapter leftMenuAdapter;
    private RightMenuAdapter rightMenuAdapter;
    private KCUTextView rightMenuTitle;
    private KCUTabLayout menuTabs;
    private KCUButton addGroupButton;
    private RecyclerView rightMenuList;

    private MultiplayerDialog multiplayerDialog;

    private MenuView menuView;

    private TouchController touchController;

    private boolean gamepadControl = true;
    private Thread fpsThread;
    private Thread memoryThread;
    private int lastCursorMode = KCUBridge.CursorEnabled;

    public void setMenuView(MenuView menuView) {
        this.menuView = menuView;
    }

    public MenuView getMenuView() {
        return menuView;
    }

    public KCUActivity getActivity() {
        return activity;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public MenuSetting getMenuSetting() {
        return menuSetting;
    }

    @Override
    public int getCursorMode() {
        return cursorModeProperty.get();
    }

    public float getCursorX() {
        return cursorX;
    }

    public float getCursorY() {
        return cursorY;
    }

    public float getPointerX() {
        return pointerX;
    }

    public float getPointerY() {
        return pointerY;
    }

    public void setCursorX(float cursorX) {
        this.cursorX = cursorX;
    }

    public void setCursorY(float cursorY) {
        this.cursorY = cursorY;
    }

    public void setPointerX(float pointerX) {
        this.pointerX = pointerX;
    }

    public void setPointerY(float pointerY) {
        this.pointerY = pointerY;
    }

    public ViewManager getViewManager() {
        return viewManager;
    }

    public RelativeLayout getBaseLayout() {
        return baseLayout;
    }

    public TouchPad getTouchPad() {
        return touchPad;
    }

    public TouchCharInput getTouchCharInput() {
        return touchCharInput;
    }

    private final BooleanProperty editModeProperty = new SimpleBooleanProperty(this, "editMode", false);

    public BooleanProperty editModeProperty() {
        return editModeProperty;
    }

    public void setEditMode(boolean editMode) {
        editModeProperty.set(editMode);
    }

    public boolean isEditMode() {
        return editModeProperty.get();
    }

    private final IntegerProperty cursorModeProperty = new SimpleIntegerProperty(this, "cursorMode", KCUBridge.CursorEnabled);

    public IntegerProperty cursorModeProperty() {
        return cursorModeProperty;
    }

    private final BooleanProperty showViewBoundariesProperty = new SimpleBooleanProperty(this, "showViewBoundaries", false);

    public BooleanProperty showViewBoundariesProperty() {
        return showViewBoundariesProperty;
    }

    public void setShowViewBoundaries(boolean showViewBoundaries) {
        showViewBoundariesProperty.set(showViewBoundaries);
    }

    public boolean isShowViewBoundaries() {
        return showViewBoundariesProperty.get();
    }

    private final BooleanProperty hideAllViewsProperty = new SimpleBooleanProperty(this, "hideAllViews", false);

    public BooleanProperty hideAllViewsProperty() {
        return hideAllViewsProperty;
    }

    public void setHideAllViews(boolean viewVisible) {
        hideAllViewsProperty.set(viewVisible);
    }

    public boolean isHideAllViews() {
        return hideAllViewsProperty.get();
    }

    /**
     * 编辑会话内手动开启显示的控件组（默认只显示当前编辑组，避免大型布局多组叠加渲染卡顿）
     */
    private final Set<String> editorVisibleGroups = new HashSet<>();

    public boolean isEditorGroupHidden(@NonNull ControlViewGroup group) {
        // 当前编辑组始终显示
        return group != getViewGroup() && !editorVisibleGroups.contains(group.getId());
    }

    public void setEditorGroupHidden(@NonNull ControlViewGroup group, boolean hidden) {
        if (hidden) {
            editorVisibleGroups.remove(group.getId());
        } else {
            editorVisibleGroups.add(group.getId());
        }
    }

    /**
     * 复制控件组：完整克隆按键与方向键数据（控件 id 重新生成），
     * 新组插入源组之后并立即在编辑画布显示
     */
    private void copyViewGroup(@NonNull ControlViewGroup group) {
        ControlViewGroup copy = new ControlViewGroup(UUID.randomUUID().toString());
        copy.setName(group.getName() + getActivity().getString(R.string.menu_control_view_group_copy_suffix));
        copy.setVisibility(group.getVisibility());
        ControlViewGroup.ViewData viewData = new ControlViewGroup.ViewData();
        for (ControlButtonData button : group.getViewData().buttonList()) {
            viewData.buttonList().add(button.clone());
        }
        for (ControlDirectionData direction : group.getViewData().directionList()) {
            viewData.directionList().add(direction.clone());
        }
        copy.setViewData(viewData);
        copy.setDataLoaded(true);
        List<ControlViewGroup> groups = getController().viewGroups();
        groups.add(groups.indexOf(group) + 1, copy);
        // 副本在编辑画布立即显示
        setEditorGroupHidden(copy, false);
        rightMenuAdapter.rebuild();
        viewManager.initializeController();
    }

    /**
     * 新建控件组（右侧面板"添加控件组"）
     */
    private void addEditGroup() {
        EditViewGroupDialog dialog = new EditViewGroupDialog(getActivity(), this, new ControlViewGroup(UUID.randomUUID().toString()), (name, visibility) -> {
            ControlViewGroup viewGroup = new ControlViewGroup(UUID.randomUUID().toString());
            viewGroup.setName(name);
            viewGroup.setVisibility(visibility);
            // 内存新建的组按键数据天然完整，无需从磁盘补全；不标记会因按需加载失败而无法添加按键
            viewGroup.setDataLoaded(true);
            getController().addViewGroup(viewGroup);
            rightMenuAdapter.rebuild();
            viewManager.initializeController();
        });
        dialog.show();
    }

    /**
     * 切换当前编辑组（含样式名重解析），并刷新控件组面板
     */
    private void selectViewGroup(@Nullable ControlViewGroup viewGroup) {
        setViewGroup(viewGroup);
        if (viewGroup != null) {
            viewGroup.getViewData().buttonList().forEach(it -> {
                String name = it.getStyle().getName();
                ControlButtonStyle style = ButtonStyles.findStyleByName(name);
                if (name.equals(style.getName())) {
                    it.setStyle(style);
                }
            });
            viewGroup.getViewData().directionList().forEach(it -> {
                String name = it.getStyle().getName();
                ControlDirectionStyle style = DirectionStyles.findStyleByName(name);
                if (name.equals(style.getName())) {
                    it.setStyle(style);
                }
            });
        }
        if (rightMenuAdapter != null) {
            rightMenuAdapter.rebuild();
        }
    }

    private final ObjectProperty<Controller> controllerProperty = new SimpleObjectProperty<>(this, "controller", null);

    public ObjectProperty<Controller> controllerProperty() {
        return controllerProperty;
    }

    public void setController(Controller controller) {
        controllerProperty.set(controller);
    }

    public Controller getController() {
        return controllerProperty.get();
    }

    private final ObjectProperty<ControlViewGroup> viewGroupProperty = new SimpleObjectProperty<>(this, "viewGroup", null);

    public ObjectProperty<ControlViewGroup> viewGroupProperty() {
        return viewGroupProperty;
    }

    public void setViewGroup(ControlViewGroup viewGroup) {
        viewGroupProperty.set(viewGroup);
    }

    /**
     * 编辑模式下未选中视图组时选中第一个，保证编辑视图立即可加载，不依赖菜单列表绑定时的兜底回调
     */
    private void selectDefaultViewGroup() {
        if (editModeProperty.get() && getViewGroup() == null && !getController().viewGroups().isEmpty()) {
            setViewGroup(getController().viewGroups().get(0));
        }
    }

    @Nullable
    public ControlViewGroup getViewGroup() {
        return viewGroupProperty.get();
    }

    public boolean isGamepadControl() {
        return gamepadControl;
    }

    public void setGamepadControl(boolean gamepadControl) {
        this.gamepadControl = gamepadControl;
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("launcher", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("gamepad_control", gamepadControl).apply();
    }

    private void initLeftMenu() {
        RecyclerView leftMenuList = findViewById(R.id.left_menu_list);
        leftMenuList.setLayoutManager(new LinearLayoutManager(activity));
        leftMenuAdapter = new LeftMenuAdapter(activity, this, new LeftMenuAdapter.Listener() {
            @Override
            public void onButtonClick(@NonNull LeftMenuTag tag) {
                handleLeftButtonClick(tag);
            }

            @Override
            public void onSwitchToggle(@NonNull LeftMenuTag tag, boolean checked) {
                handleLeftSwitchToggle(tag, checked);
            }

            @Override
            public void onSpinnerSelect(@NonNull LeftMenuTag tag, int position) {
                handleLeftSpinnerSelect(tag, position);
            }

            @Override
            public void onSeekBarChange(@NonNull LeftMenuTag tag, int progress) {
                handleLeftSeekBarChange(tag, progress);
            }
        });
        leftMenuList.setAdapter(leftMenuAdapter);
        leftMenuAdapter.rebuild();
        leftMenuList.addItemDecoration(new SpacingDecoration(
                Math.round(6 * activity.getResources().getDisplayMetrics().density)));

        getController().addListener(i -> leftMenuAdapter.rebuild());
        controllerProperty.addListener(invalidate -> {
            setViewGroup(null);
            if (isEditMode()) {
                selectDefaultViewGroup();
            }
            leftMenuAdapter.rebuild();
            if (rightMenuAdapter != null) {
                rightMenuAdapter.rebuild();
            }
            getController().addListener(i -> leftMenuAdapter.rebuild());
        });
        editModeProperty.addListener(i -> {
            leftMenuAdapter.rebuild();
            if (!isEditMode()) {
                // 退出编辑：恢复分类标签页面板（分类保持标签栏当前选中项）
                if (rightMenuAdapter != null) {
                    rightMenuAdapter.rebuild();
                }
                if (rightMenuList != null) {
                    rightMenuList.scrollToPosition(0);
                }
                if (addGroupButton != null) {
                    addGroupButton.setVisibility(View.GONE);
                }
                if (rightMenuTitle != null) {
                    rightMenuTitle.setVisibility(View.GONE);
                }
                if (menuTabs != null) {
                    menuTabs.setVisibility(View.VISIBLE);
                }
                return;
            }
            // 进入编辑：隐藏标签栏，右菜单整体替换为控件组面板
            if (rightMenuAdapter != null) {
                rightMenuAdapter.rebuild();
            }
            if (addGroupButton != null) {
                addGroupButton.setVisibility(View.VISIBLE);
            }
            if (rightMenuTitle != null) {
                rightMenuTitle.setVisibility(View.VISIBLE);
            }
            if (menuTabs != null) {
                menuTabs.setVisibility(View.GONE);
            }
        });

        hideAllViewsProperty.addListener(i -> {
            if (isHideAllViews()) {
                // 菜单视图隐藏后音量键用于唤回菜单，不再保留未完成的按键监听
                cancelKeyBindListen();
                Toast.makeText(activity, R.string.tip_hide_menu_view, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void initRightMenu() {
        rightMenuList = findViewById(R.id.right_menu_list);
        rightMenuList.setLayoutManager(new LinearLayoutManager(activity));
        rightMenuAdapter = new RightMenuAdapter(activity, this, new RightMenuAdapter.Listener() {
            @Override
            public void onButtonClick(@NonNull RightMenuTag tag) {
                handleRightButtonClick(tag);
            }

            @Override
            public void onSwitchToggle(@NonNull RightMenuTag tag, boolean checked) {
                handleRightSwitchToggle(tag, checked);
            }

            @Override
            public void onSwitchLongClick(@NonNull RightMenuTag tag) {
                handleRightSwitchLongClick(tag);
            }

            @Override
            public void onSpinnerSelect(@NonNull RightMenuTag tag, int position) {
                handleRightSpinnerSelect(tag, position);
            }

            @Override
            public void onKeyBindClick(@NonNull RightMenuTag tag) {
                startKeyBindListen(tag);
            }

            @Override
            public void onSeekBarChange(@NonNull RightMenuTag tag, int progress) {
                handleRightSeekBarChange(tag, progress);
            }

            @Override
            public void onEditGroupSelect(@NonNull ControlViewGroup group) {
                selectViewGroup(group);
            }

            @Override
            public void onEditGroupToggle(@NonNull ControlViewGroup group, boolean visible) {
                setEditorGroupHidden(group, !visible);
                viewManager.initializeController();
            }

            @Override
            public void onEditGroupCopy(@NonNull ControlViewGroup group) {
                // 轻量加载的组先补全 viewData 再整体克隆
                Controllers.loadViewGroup(getController(), group, new Controllers.ViewGroupLoadCallback() {
                    @Override
                    public void onLoaded(ControlViewGroup loaded) {
                        copyViewGroup(loaded);
                    }

                    @Override
                    public void onFailed(Throwable e) {
                        Logging.LOG.log(Level.SEVERE, "Failed to copy view group", e);
                        Toast.makeText(getActivity(), getActivity().getString(R.string.menu_control_view_group_copy_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onEditGroupAdd() {
                addEditGroup();
            }

            @Override
            public void onEditGroupEdit(@NonNull ControlViewGroup group) {
                EditViewGroupDialog dialog = new EditViewGroupDialog(getActivity(), GameMenu.this, group, (name, visibility) -> {
                    group.setName(name);
                    group.setVisibility(visibility);
                    getController().updateViewGroup(group);
                    rightMenuAdapter.rebuild();
                });
                dialog.show();
            }

            @Override
            public void onEditGroupRemove(@NonNull ControlViewGroup group) {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(activity);
                builder.setCancelable(false);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
                builder.setMessage(activity.getString(R.string.menu_control_view_group_delete));
                builder.setPositiveButton(() -> {
                    getController().removeViewGroup(group);
                    if (group == getViewGroup()) {
                        setViewGroup(null);
                    }
                    rightMenuAdapter.rebuild();
                    viewManager.initializeController();
                });
                builder.setNegativeButton(null);
                builder.create().show();
            }
        });
        rightMenuList.setAdapter(rightMenuAdapter);
        rightMenuAdapter.rebuild();
        rightMenuList.addItemDecoration(new SpacingDecoration(
                Math.round(6 * activity.getResources().getDisplayMetrics().density)));
        // 编辑模式控件组长按拖动排序：拖动中实时交换组顺序，松手持久化并按新 z 序重建控件
        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return isEditMode();
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return rightMenuAdapter.isControlGroupPosition(viewHolder.getBindingAdapterPosition())
                        ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0)
                        : makeMovementFlags(0, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = rightMenuAdapter.groupIndexOf(viewHolder.getBindingAdapterPosition());
                int to = rightMenuAdapter.groupIndexOf(target.getBindingAdapterPosition());
                List<ControlViewGroup> groups = getController().viewGroups();
                if (from < 0 || to < 0 || from >= groups.size() || to >= groups.size()) {
                    return false;
                }
                Collections.swap(groups, from, to);
                rightMenuAdapter.notifyItemMoved(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 拖动结束：持久化组顺序并按新 z 序重建控件
                getViewManager().saveController();
                viewManager.initializeController();
            }
        };
        new ItemTouchHelper(dragCallback).attachToRecyclerView(rightMenuList);

        // 分类标签栏：顶部图标切换分类，下方列表直接显示所选分类的功能项。
        // menu_right.xml 的 TabItem 顺序须与 RightMenuCategory 声明顺序一致
        menuTabs = findViewById(R.id.menu_tabs);
        RightMenuCategory[] categories = RightMenuCategory.values();
        for (int i = 0; i < menuTabs.getTabCount() && i < categories.length; i++) {
            TabLayout.Tab tab = menuTabs.getTabAt(i);
            if (tab != null) {
                // 纯图标 Tab 无文字，以分类名作无障碍描述
                tab.setContentDescription(activity.getString(categories[i].getTitleRes()));
            }
        }
        menuTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull TabLayout.Tab tab) {
                if (tab.getPosition() < categories.length) {
                    rightMenuAdapter.showCategory(categories[tab.getPosition()]);
                    rightMenuList.scrollToPosition(0);
                }
            }

            @Override
            public void onTabUnselected(@NonNull TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(@NonNull TabLayout.Tab tab) {
            }
        });
        // XML 声明的 TabItem 无默认选中态，需显式选中第一个分类
        menuTabs.selectTab(menuTabs.getTabAt(0));

        rightMenuTitle = findViewById(R.id.menu_title);
        // 编辑模式控件组面板的底部固定按钮
        addGroupButton = findViewById(R.id.add_group_button);
        addGroupButton.setOnClickListener(v -> addEditGroup());
        addGroupButton.setVisibility(isEditMode() ? View.VISIBLE : View.GONE);
        // editModeProperty 的监听注册于本方法之前，启动即处于编辑态（布局编辑器）时
        // 监听回调会因标题未初始化而跳过，此处补一次同步
        rightMenuTitle.setVisibility(isEditMode() ? View.VISIBLE : View.GONE);
        menuTabs.setVisibility(isEditMode() ? View.GONE : View.VISIBLE);

        logWindow.setVisibility(menuSetting.isShowLog() || (!isSimulated() && menuSetting.isAutoShowLog()));
    }

    @Override
    public void setup(KCUActivity activity, KCUBridge fclBridge) {
        this.activity = activity;
        this.fclBridge = fclBridge;
        this.simulated = fclBridge == null;
        this.gamepadControl = activity.getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("gamepad_control", true);
        this.fclInput = new KCUInput(this);
        if (!Controllers.isInitialized()) {
            Controllers.init();
        }
        if (!ButtonStyles.isInitialized()) {
            ButtonStyles.init();
        }
        if (!DirectionStyles.isInitialized()) {
            DirectionStyles.init();
        }
        if (!QuickInputTexts.isInitialized()) {
            QuickInputTexts.init();
        }

        if (Files.exists(new File(KCUPath.FILES_DIR + "/menu_setting.json").toPath())) {
            try {
                this.menuSetting = new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .fromJson(FileUtils.readText(new File(KCUPath.FILES_DIR + "/menu_setting.json")), MenuSetting.class);
                //如果文件损坏，menuSetting可能为空
                if (this.menuSetting == null) {
                    this.menuSetting = new MenuSetting();
                    new File(KCUPath.FILES_DIR + "/menu_setting.json").delete();
                }
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to load menu setting, use default", e);
                this.menuSetting = new MenuSetting();
            }
        } else {
            this.menuSetting = new MenuSetting();
        }

        this.menuSetting.addOnChangeListener(() -> {
            String content = new GsonBuilder().setPrettyPrinting().create().toJson(menuSetting);
            try {
                FileUtils.writeText(new File(KCUPath.FILES_DIR + "/menu_setting.json"), content);
            } catch (IOException e) {
                Logging.LOG.log(Level.SEVERE, "Failed to save menu setting", e);
            }
        });

        editModeProperty.set(isSimulated());
        // getExtras() 可能为 null，直接取值会抛 NPE 让整个菜单构造失败。
        // 取不到时传 null，findControllerById 会回退到列表第一项，行为与"未指定"一致
        android.os.Bundle extras = activity.getIntent() == null ? null : activity.getIntent().getExtras();
        String controllerId = extras == null ? null : extras.getString("controller");
        controllerProperty.set(Controllers.findControllerById(controllerId));
        // 有外设时自动关布局：放在布局确定之后，否则会把刚设好的布局又切走
        applyPeripheralPolicy();
        selectDefaultViewGroup();

        baseLayout = findViewById(R.id.base_layout);
        touchPad = findViewById(R.id.touch_pad);
        gameItemBar = findViewById(R.id.game_item_bar);
        logWindow = findViewById(R.id.log_window);
        fpsText = findViewById(R.id.fps);
        memoryText = findViewById(R.id.memory);
        touchCharInput = findViewById(R.id.input_scanner);
        touchControllerInputView = findViewById(R.id.touchcontroller_input_view);
        launchProgress = findViewById(R.id.launch_progress);
        cursorView = findViewById(R.id.cursor);

        if (!isSimulated()) {
            ImageUtil.loadInto(baseLayout, ThemeEngine.getInstance().getTheme().getBackground(activity));
            launchProgress.setVisibility(View.VISIBLE);
            assert getBridge() != null;
            gameOption = new GameOption(getBridge().getGameDir());
            touchPad.post(() -> gameItemBar.setup(this, gameOption));
        }
        touchPad.init(this);
        touchCharInput.setCharacterSender(this, new LwjglCharSender(this));
        initCursorView(activity);

        gyroscope = new Gyroscope(this);

        viewManager = new ViewManager(this);

        initLeftMenu();
        initRightMenu();

        viewManager.setup();

        // 初始化时应用开关副作用（FPS/内存线程、持续性能模式），
        // 使重启后已开启的设置保持生效（开关行绑定不触发副作用回调）
        toggleFps(menuSetting.isShowFps());
        toggleMemory(menuSetting.isShowMemory());
        activity.getWindow().setSustainedPerformanceMode(menuSetting.isPerformanceMode());

        if (new File(KCUPath.FILES_DIR, "cursor.gif").exists()) {
            Glide.with(getCursor()).asGif().skipMemoryCache(true).load(new File(KCUPath.FILES_DIR, "cursor.gif")).into(new CustomViewTarget<CursorView, GifDrawable>(getCursor()) {
                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                }

                @Override
                public void onResourceReady(@NonNull GifDrawable resource, @Nullable Transition<? super GifDrawable> transition) {
                    getCursor().setImageDrawable(resource);
                    resource.start();
                }

                @Override
                protected void onResourceCleared(@Nullable Drawable placeholder) {
                }
            });
        } else if (new File(KCUPath.FILES_DIR, "cursor.png").exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(new File(KCUPath.FILES_DIR, "cursor.png").getAbsolutePath());
            BitmapDrawable drawable = new BitmapDrawable(getActivity().getResources(), bitmap);
            getCursor().setImageDrawable(drawable);
        }

        if (getBridge() != null && getBridge().hasTouchController()) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("launcher", MODE_PRIVATE);
            touchController = new TouchController(getActivity(), AndroidUtilKt.getScreenWidth(), AndroidUtilKt.getScreenHeight(), sharedPreferences.getInt("vibrationDuration", 100));

            touchControllerInputView.setClient(touchController.getClient());
            touchControllerInputView.setFclInput(fclInput);
            touchControllerInputView.setSize(AndroidUtilKt.getScreenWidth(), AndroidUtilKt.getScreenHeight());
            touchControllerInputView.setDisableFullScreenInput(sharedPreferences.getBoolean("disableFullscreenInput", true));
        }

        touchPad.setOnGenericMotionListener((view, motionEvent) -> {
            if (motionEvent.isFromSource(InputDevice.SOURCE_MOUSE) && menuSetting.isPhysicalMouseMode()) {
                if (getCursorMode() == KCUBridge.CursorEnabled && motionEvent.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
                    getInput().setPointer(motionEvent.getRawX(), motionEvent.getRawY());
                    return true;
                }
                return fclInput.handleExternalMouseEvent(motionEvent);
            }
            return false;
        });
        if (menuSetting.isHideMenuView()) {
            Toast.makeText(activity, R.string.tip_hide_menu_view, Toast.LENGTH_LONG).show();
        }
    }

    private void initCursorView(KCUActivity activity) {
        ViewGroup.LayoutParams layoutParams = cursorView.getLayoutParams();
        layoutParams.width = ConvertUtils.dip2px(activity, menuSetting.getMouseSize());
        layoutParams.height = ConvertUtils.dip2px(activity, menuSetting.getMouseSize());
        cursorView.setLayoutParams(layoutParams);
        cursorView.setOffsetX(menuSetting.getMouseOffsetX());
        cursorView.setOffsetY(menuSetting.getMouseOffsetY());
    }

    @Override
    public View getLayout() {
        if (layout == null) {
            layout = LayoutInflater.from(activity).inflate(R.layout.view_game_menu, null);
            DrawerLayout drawerLayout = (DrawerLayout) layout;
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            // 右菜单收起即退出按键监听态：悬置的监听会吞掉所有按键（含唤回菜单的音量键），
            // 并把下一个按下的键误绑定为快捷键
            drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerClosed(@NonNull View drawerView) {
                    if (isRightMenuDrawer(drawerView)) {
                        cancelKeyBindListen();
                    }
                }
            });
        }
        return layout;
    }

    @Override
    @Nullable
    public KCUBridge getBridge() {
        return fclBridge;
    }

    @Override
    public KCUBridgeCallback getCallbackBridge() {
        return this;
    }

    @Override
    public KCUInput getInput() {
        return fclInput;
    }

    @Override
    public CursorView getCursor() {
        return cursorView;
    }

    public GameOption getGameOption() {
        return gameOption;
    }

    @Override
    public void onPause() {
        if (cursorModeProperty.get() == KCUBridge.CursorDisabled) {
            fclInput.sendKeyEvent(KCUKeycodes.KEY_ESC, true);
            fclInput.sendKeyEvent(KCUKeycodes.KEY_ESC, false);
        }
        fclInput.stopCaptureWatchdog();
        fclInput.resetExternalMouseState();
        cancelKeyBindListen();
        gyroscope.disableSensor();
    }

    @Override
    public void onResume() {
        fclInput.startCaptureWatchdog();
        if (menuSetting != null && menuSetting.isEnableGyroscope() && gyroscope != null) {
            gyroscope.enableSensor();
        }
    }

    /**
     * 停止本对象开启的后台线程。
     *
     * FPS / 内存采样线程是匿名内部类，隐式持有本对象，本对象又持有 Activity 与
     * 若干 View。宿主销毁时若不停止，这两个线程会一直持有整条引用链，
     * Activity 无法被回收，且每进入一次游戏就多一个线程。
     *
     * 必须由宿主显式调用：本类不继承 Activity，没有可覆写的销毁回调。
     */
    public void onDestroy() {
        stopFpsThread();
        stopMemoryThread();
        // 取消按键上未执行的延时任务：自动点击每 20 毫秒自我重投一次，
        // 玩家开着它退出游戏时消息队列会一直持有控件与整条 Activity 引用链
        if (baseLayout != null) {
            for (int i = 0; i < baseLayout.getChildCount(); i++) {
                android.view.View child = baseLayout.getChildAt(i);
                if (child instanceof ControlButton) {
                    ((ControlButton) child).cancelPendingEvents();
                }
            }
        }
    }


    @Override
    public void onGraphicOutput() {
        baseLayout.setBackground(null);
        baseLayout.removeView(launchProgress);
        if (!menuSetting.isShowLog() && menuSetting.isAutoShowLog()) {
            logWindow.setVisibility(false);
        }
    }

    @Override
    public void onCursorModeChange(int mode) {
        activity.runOnUiThread(() -> {
            if (lastCursorMode == mode)
                return;
            lastCursorMode = mode;
            this.cursorModeProperty.set(mode);
            // 模式切换只重对齐来源，不补发鼠标键释放：按住中的键等来源自身抬起时再释放，
            // 抢先释放会打断「按住某键」的交互（如机械动力数值设置界面松开右键才确认）；
            // 手动捕获接管同步回归自动管理
            getInput().onCursorModeChanged();
            getInput().resetManualCaptureControl();
            if (mode == KCUBridge.CursorEnabled) {
                getCursor().setVisibility(View.VISIBLE);
                gameItemBar.setVisibility(View.GONE);
                getInput().setPointer(AndroidUtilKt.getScreenWidth() / 2, AndroidUtilKt.getScreenHeight() / 2, "Gyro");
                if (menuSetting.isPhysicalMouseMode()) {
                    getInput().getFocusableView().releasePointerCapture();
                    getInput().getFocusableView().clearFocus();
                } else {
                    // 游戏退出捕获时系统可能顺带释放 pointer capture，立即补回
                    getInput().ensurePointerCapture();
                }
            } else {
                getCursor().setVisibility(View.GONE);
                if (getBridge() != null && !getBridge().hasTouchController()) {
                    gameItemBar.setVisibility(View.VISIBLE);
                }
                if (menuSetting.isPhysicalMouseMode()) {
                    getInput().getFocusableView().requestFocus();
                    getInput().getFocusableView().requestPointerCapture();
                } else {
                    getInput().ensurePointerCapture();
                }
            }
        });
    }

    private boolean firstLog = true;

    @Override
    public void onLog(String log) {
        if (fclBridge != null) {
            if (log.contains("version string:") || log.contains("OR:") || log.contains("ERROR:") || log.contains("INTERNAL ERROR:")) {
                return;
            }
            logWindow.appendLog(log);
            if (BuildConfig.DEBUG) {
                Log.d("FCL Debug", log);
            }
            try {
                if (firstLog) {
                    FileUtils.writeText(new File(fclBridge.getLogPath()), log);
                    firstLog = false;
                } else {
                    FileUtils.writeTextWithAppendMode(new File(fclBridge.getLogPath()), log);
                }
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Can't log game log to target file", e.getMessage());
            }
        }
    }

    @Override
    public void onExit(int exitCode) {
        if (exitCode != 0 && fclBridge != null) {
            JVMCrashActivity.startCrashActivity(true, activity, exitCode, fclBridge.getLogPath());
            Logging.LOG.log(Level.INFO, "JVM crashed, start jvm crash activity to show errors now!");
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @NonNull
    public final <T extends View> T findViewById(int id) {
        return getLayout().findViewById(id);
    }

    public void openQuickInput() {
        QuickInputDialog dialog = new QuickInputDialog(activity, this);
        dialog.show();
    }

    private void handleLeftButtonClick(LeftMenuTag tag) {
        switch (tag) {
            case ADD_BUTTON: {
                if (getViewGroup() == null) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.edit_view_no_group), Toast.LENGTH_SHORT).show();
                } else {
                    // 新控件默认落在屏幕中心：默认 (0,0) 会被打开着的左侧菜单挡住，看起来像"添加后消失"
                    ControlButtonData data = new ControlButtonData(UUID.randomUUID().toString());
                    data.getBaseInfo().setXPosition(500);
                    data.getBaseInfo().setYPosition(500);
                    EditViewDialog dialog = new EditViewDialog(getActivity(), data, this, new EditViewDialog.Callback() {
                        @Override
                        public void onPositive(CustomControl view) {
                            viewManager.addView(view);
                        }

                        @Override
                        public void onClone(CustomControl view) {
                            // Ignore
                        }
                    }, false);
                    dialog.show();
                }
                break;
            }
            case ADD_DIRECTION: {
                if (getViewGroup() == null) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.edit_view_no_group), Toast.LENGTH_SHORT).show();
                } else {
                    // 新控件默认落在屏幕中心：默认 (0,0) 会被打开着的左侧菜单挡住，看起来像"添加后消失"
                    ControlDirectionData data = new ControlDirectionData(UUID.randomUUID().toString());
                    data.getBaseInfo().setXPosition(500);
                    data.getBaseInfo().setYPosition(500);
                    EditViewDialog dialog = new EditViewDialog(getActivity(), data, this, new EditViewDialog.Callback() {
                        @Override
                        public void onPositive(CustomControl view) {
                            viewManager.addView(view);
                        }

                        @Override
                        public void onClone(CustomControl view) {
                            // Ignore
                        }
                    }, false);
                    dialog.show();
                }
                break;
            }
            case MANAGE_BUTTON_STYLE: {
                ButtonStyleDialog dialog = new ButtonStyleDialog(getActivity(), false, null, null);
                dialog.show();
                break;
            }
            case MANAGE_DIRECTION_STYLE: {
                DirectionStyleDialog dialog = new DirectionStyleDialog(getActivity(), false, null, null);
                dialog.show();
                break;
            }
        }
    }

    private void handleLeftSwitchToggle(LeftMenuTag tag, boolean checked) {
        switch (tag) {
            case EDIT_MODE:
                setEditMode(checked);
                if (checked) {
                    // 每次进入编辑模式重置为只显示当前编辑组，其他组由右侧面板手动开启
                    editorVisibleGroups.clear();
                    selectDefaultViewGroup();
                    Toast.makeText(getActivity(), R.string.menu_controls_edit_hint, Toast.LENGTH_LONG).show();
                }
                break;
            case SHOW_BOUNDARY:
                setShowViewBoundaries(checked);
                break;
            case HIDE_ALL:
                setHideAllViews(checked);
                break;
            case AUTO_FIT:
                menuSetting.setAutoFit(checked);
                break;
            case SNAP_ADJACENT_ONLY:
                menuSetting.setSnapAdjacentOnly(checked);
                break;
        }
    }

    private void handleLeftSpinnerSelect(LeftMenuTag tag, int position) {
        if (tag == LeftMenuTag.CURRENT_CONTROLLER) {
            setController(Controllers.getControllers().get(position));
        }
    }

    private void handleLeftSeekBarChange(LeftMenuTag tag, int progress) {
        if (tag == LeftMenuTag.AUTO_FIT_DIST) {
            menuSetting.setAutoFitDist(progress);
        } else if (tag == LeftMenuTag.CONTROLS_OPACITY) {
            menuSetting.setControlsOpacity(progress);
            viewManager.applyControlsOpacity();
        }
    }

    private void handleRightButtonClick(RightMenuTag tag) {
        switch (tag) {
            case OPEN_MULTIPLAYER: {
                if (multiplayerDialog == null) {
                    int width = (int) (AndroidUtilKt.getScreenWidth() * 0.7);
                    int height = (int) (AndroidUtilKt.getScreenHeight() * 0.9);
                    multiplayerDialog = new MultiplayerDialog(getActivity(), getActivity(), width, height);
                }
                multiplayerDialog.show();
                break;
            }
            case OPEN_QUICK_INPUT:
                openQuickInput();
                break;
            case OPEN_SEND_KEY: {
                ObservableList<Integer> list = FXCollections.observableList(new ArrayList<>());
                new SelectKeycodeDialog(getActivity(), list, false, true, (dialog) -> {
                    Schedulers.io().execute(() -> {
                        list.forEach(key -> getInput().sendKeyEvent(key, true));
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignore) {
                        }
                        list.forEach(key -> getInput().sendKeyEvent(key, false));
                    });
                    return Unit.INSTANCE;
                }).show();
                break;
            }
            case GAMEPAD_RESET_MAPPER:
                Remapper.wipePreferences(getActivity());
                getInput().resetMapper();
                break;
            case GAMEPAD_BUTTON_BINDING:
                fclInput.checkGamepad();
                if (fclInput.getGamepad() != null) {
                    new GamepadMapDialog(getActivity(), fclInput).show();
                }
                break;
            case FORCE_RESOLUTION_SIZE:
                editForceResolutionSize();
                break;
            case FORCE_EXIT: {
                KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(activity);
                builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
                builder.setMessage(activity.getString(R.string.menu_settings_force_exit_msg));
                builder.setPositiveButton(() -> android.os.Process.killProcess(android.os.Process.myPid()));
                builder.setNegativeButton(null);
                builder.setCancelable(false);
                builder.create().show();
                break;
            }
        }
    }

    private void handleRightSwitchToggle(RightMenuTag tag, boolean checked) {
        switch (tag) {
            case LOCK_VIEW:
                menuSetting.setLockMenuView(checked);
                break;
            case HIDE_VIEW:
                menuSetting.setHideMenuView(checked);
                menuView.setVisibility(checked ? View.INVISIBLE : View.VISIBLE);
                if (checked) {
                    Toast.makeText(activity, R.string.tip_hide_menu_view, Toast.LENGTH_LONG).show();
                }
                break;
            case SHOW_FPS:
                toggleFps(checked);
                break;
            case SHOW_MEMORY:
                toggleMemory(checked);
                break;
            case SOFT_KEYBOARD_ADJUST:
                menuSetting.setDisableSoftKeyAdjust(checked);
                break;
            case FORCE_RESOLUTION:
                menuSetting.setForceResolution(checked);
                if (checked) {
                    // 实验性选项提示；默认 1920x1080 直接生效，宽高经"设置"按钮修改
                    Toast.makeText(activity, R.string.settings_advanced_force_resolution_desc, Toast.LENGTH_LONG).show();
                }
                applyForceResolution();
                break;
            case KCU_LAYOUT_ENABLED:
                menuSetting.setKcuLayoutEnabled(checked);
                layoutUserOverride = true;
                applyLayoutEnabled(checked);
                break;
            case DISABLE_GESTURE:
                menuSetting.setDisableGesture(checked);
                break;
            case DISABLE_LEFT_TOUCH:
                menuSetting.setDisableLeftTouch(checked);
                break;
            case GYRO:
                menuSetting.setEnableGyroscope(checked);
                if (checked) {
                    gyroscope.enableSensor();
                } else {
                    gyroscope.disableSensor();
                }
                break;
            case GYRO_INVERT_X:
                menuSetting.setInvertGyroscopeX(checked);
                break;
            case GYRO_INVERT_Y:
                menuSetting.setInvertGyroscopeY(checked);
                break;
            case KCU_DP_THRESHOLD:
                menuSetting.setKcuDpThreshold(checked);
                // 阈值单位切换后重建菜单，使依赖该值的行重新取值
                rightMenuAdapter.rebuild();
                break;
            case KCU_MOUSE_DISGUISE:
                menuSetting.setKcuMouseDisguise(checked);
                break;
            case PHYSICAL_MOUSE:
                menuSetting.setPhysicalMouseMode(checked);
                // 模式切换即时生效：菜单态改用系统指针时释放捕获，改回虚拟指针时立即补回，不等看门狗轮询
                if (checked) {
                    if (getCursorMode() == KCUBridge.CursorEnabled) {
                        View focusView = getInput().getFocusableView();
                        if (focusView != null && focusView.hasPointerCapture()) {
                            focusView.releasePointerCapture();
                        }
                    }
                } else {
                    getInput().ensurePointerCapture();
                }
                break;
            case SLIDE_ACCELERATION:
                menuSetting.setSlideAcceleration(checked);
                break;
            case DISTANCE_ACCELERATION:
                menuSetting.setDistanceAcceleration(checked);
                break;
            case SIMULTANEOUS_VIEW_CONTROL:
                menuSetting.setSimultaneousViewControl(checked);
                // 切换后清空占用者，避免上一模式的占用残留阻塞另一来源
                getInput().resetLookOwner();
                break;
            case GAMEPAD_CONTROL:
                setGamepadControl(checked);
                // 联动刷新：手柄关闭时输入模式选择器禁用
                rightMenuAdapter.rebuild();
                break;
            case PERFORMANCE_MODE:
                menuSetting.setPerformanceMode(checked);
                activity.getWindow().setSustainedPerformanceMode(checked);
                break;
            case REQUEST_MAX_REFRESH_RATE:
                menuSetting.setRequestMaxRefreshRate(checked);
                if (getActivity() instanceof JVMActivity jvmActivity) {
                    jvmActivity.applyMaxRefreshRatePolicy();
                }
                break;
            case FULLSCREEN:
                // 与启动器设置页同一功能：更新主题持久化值并应用到当前窗口，
                // 后续焦点恢复/onPostResume 会按主题值重放全屏标志
                ThemeEngine.getInstance().applyAndSave(activity, activity.getWindow(), checked);
                break;
            case SHOW_LOG:
                menuSetting.setShowLog(checked);
                logWindow.setVisibility(menuSetting.isShowLog());
                break;
            case AUTO_SHOW_LOG:
                menuSetting.setAutoShowLog(checked);
                if (baseLayout.getBackground() != null) {
                    logWindow.setVisibility(menuSetting.isAutoShowLog());
                }
                break;
            case SDL_AUTO_SHOW_IME:
                SdlSettings.setSdlAutoShowIme(checked);
                break;
        }
    }

    private void handleRightSwitchLongClick(RightMenuTag tag) {
        if (tag == RightMenuTag.SHOW_FPS) {
            fpsText.resetPosition();
        } else if (tag == RightMenuTag.SHOW_MEMORY) {
            memoryText.resetPosition();
        }
    }

    /**
     * 布局总开关：开=恢复玩家选中的布局，关=切到空布局。
     *
     * 关闭时不清空玩家的选择，只切到空布局；
     * 再打开时从记录的 id 恢复，找不到就回退到列表第一项。
     * 若直接把选择置空，玩家再打开时会发现自己原来的布局丢了。
     */
    private void applyLayoutEnabled(boolean enabled) {
        if (enabled) {
            Controller target = null;
            String id = menuSetting.getKcuLastControllerId();
            if (id != null && !id.isEmpty()) {
                target = Controllers.findControllerById(id);
            }
            if (target == null) {
                java.util.List<Controller> all = Controllers.getControllers();
                for (Controller c : all) {
                    if (!EMPTY_CONTROLLER_ID.equals(c.getId())) {
                        target = c;
                        break;
                    }
                }
            }
            if (target != null) {
                setController(target);
            }
        } else {
            Controller current = getController();
            if (current != null && !EMPTY_CONTROLLER_ID.equals(current.getId())) {
                menuSetting.setKcuLastControllerId(current.getId());
            }
            Controller empty = Controllers.findControllerById(EMPTY_CONTROLLER_ID);
            if (empty != null) {
                setController(empty);
            }
        }
    }

    /** 空布局的 id，与 assets 下 kcu_empty.json 保持一致 */
    private static final String EMPTY_CONTROLLER_ID =
            "a1000000-0000-4000-8000-0000000000ff";

    /**
     * 玩家本次会话内手动操作过布局开关。
     *
     * 只存在内存中，不持久化：下次启动仍应按外设情况重新判断，
     * 否则"手动开过一次"会让自动策略在以后永远失效。
     */
    private boolean layoutUserOverride = false;

    /**
     * 本次会话的"无按键"状态是否由外设自动触发。
     *
     * 只有自动关掉的才需要在条件消失时还原。玩家自己在设置里选中空布局
     * （想用纯外设、不要虚拟按键）是明确意图，不能替他换成有按键的布局。
     */
    private boolean layoutAutoClosed = false;

    /**
     * 按当前外设情况决定布局开或关。
     *
     * 【为什么不能直接写 isKcuLayoutEnabled】
     * menuSetting 有持久化监听，写一次就落盘。若检测到外设时把它置 false，
     * 玩家拔掉外设后下次启动读到的仍是 false，布局永久消失 —— 玩家只会以为坏了。
     * "有外设就关"是条件行为，不是一次性设置，必须每次启动重新判断。
     * 因此这里只读玩家意图，不改它：每次启动重新计算该不该显示按键。
     *
     * 【为什么状态一致时直接返回】
     * 每次启动无脑调 applyLayoutEnabled(true) 会把玩家自己选的布局重置成
     * "记住的那个"，等于玩家在管理页的切换白做了。只在确实需要切换时才动。
     *
     * 【为什么手动操作后本次不再自动覆盖】
     * 玩家在插着外设时手动打开布局，说明他此刻想要按键；
     * 若策略继续生效，他刚打开就会被关掉，看起来像开关失灵。
     */
    private void applyPeripheralPolicy() {
        if (layoutUserOverride) {
            return;
        }
        boolean userOff = !menuSetting.isKcuLayoutEnabled();
        boolean peripheral = hasPhysicalInputDevice();
        boolean wantEmpty = userOff || peripheral;
        if (wantEmpty == isCurrentLayoutEmpty()) {
            return;
        }
        if (wantEmpty) {
            applyLayoutEnabled(false);
            // 只有"玩家没关、纯因外设而关"才算自动关闭，这类才需要在条件消失时还原，
            // 也只有这类需要提示：玩家自己关的不必再告诉他
            layoutAutoClosed = peripheral && !userOff;
            if (layoutAutoClosed) {
                Toast.makeText(activity, R.string.menu_settings_kcu_layout_enabled_auto_off,
                        Toast.LENGTH_LONG).show();
            }
        } else if (layoutAutoClosed) {
            // 想显示按键却正显示空布局：只在本次是自动关掉的情况下还原。
            // 玩家自己选的空布局属于明确意图，不能替他换回来
            applyLayoutEnabled(true);
            layoutAutoClosed = false;
        }
    }

    /** 当前生效的是否为空布局（无按键） */
    private boolean isCurrentLayoutEmpty() {
        Controller current = getController();
        return current == null || EMPTY_CONTROLLER_ID.equals(current.getId());
    }

    private static boolean hasPhysicalInputDevice() {
        int[] ids = android.view.InputDevice.getDeviceIds();
        if (ids == null) {
            return false;
        }
        for (int id : ids) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) {
                continue;
            }
            int sources = device.getSources();
            boolean keyboard = (sources & android.view.InputDevice.SOURCE_KEYBOARD)
                    == android.view.InputDevice.SOURCE_KEYBOARD;
            boolean mouse = (sources & android.view.InputDevice.SOURCE_MOUSE)
                    == android.view.InputDevice.SOURCE_MOUSE;
            boolean gamepad = (sources & android.view.InputDevice.SOURCE_GAMEPAD)
                    == android.view.InputDevice.SOURCE_GAMEPAD;
            if (keyboard || mouse || gamepad) {
                return true;
            }
        }
        return false;
    }

    private void handleRightSpinnerSelect(RightMenuTag tag, int position) {
        if (tag == RightMenuTag.GESTURE_MODE) {
            menuSetting.setGestureMode(GestureMode.getById(position));
        } else if (tag == RightMenuTag.MOUSE_MODE) {
            menuSetting.setMouseMoveMode(MouseMoveMode.getById(position));
        } else if (tag == RightMenuTag.GAMEPAD_INPUT_MODE) {
            SdlSettings.setGamepadInputMode(GamepadInputMode.values()[position]);
        } else if (tag == RightMenuTag.CAPTURE_POINTER_MODIFIER) {
            if (isHotkeyConflict(menuSetting.getCapturePointerKey(), position,
                    menuSetting.getImeToggleKey(), menuSetting.getImeToggleModifier())) {
                rejectHotkeyConflict();
                return;
            }
            menuSetting.setCapturePointerModifier(position);
        } else if (tag == RightMenuTag.IME_TOGGLE_MODIFIER) {
            if (isHotkeyConflict(menuSetting.getImeToggleKey(), position,
                    menuSetting.getCapturePointerKey(), menuSetting.getCapturePointerModifier())) {
                rejectHotkeyConflict();
                return;
            }
            menuSetting.setImeToggleModifier(position);
        }
    }

    /**
     * 两个快捷键的键码与修饰键完全一致时会同时触发，视为冲突
     */
    private static boolean isHotkeyConflict(int key, int modifier, int otherKey, int otherModifier) {
        return key != 0 && key == otherKey && modifier == otherModifier;
    }

    /**
     * 拒绝冲突的快捷键配置并还原菜单显示
     */
    private void rejectHotkeyConflict() {
        Toast.makeText(activity, R.string.key_bind_conflict, Toast.LENGTH_SHORT).show();
        if (rightMenuAdapter != null) {
            rightMenuAdapter.rebuild();
        }
    }

    /**
     * 正在等待快捷键绑定按键时为对应菜单项，null 表示未监听
     */
    @Nullable
    private RightMenuTag keyBindListeningTag;

    /**
     * 快捷键设置行点击：进入按键监听，下一个按下的物理键即被绑定
     */
    private void startKeyBindListen(@NonNull RightMenuTag tag) {
        keyBindListeningTag = tag;
        Toast.makeText(activity, R.string.key_bind_listening, Toast.LENGTH_SHORT).show();
    }

    /**
     * 放弃按键监听（右菜单收起、菜单视图隐藏、页面暂停）：未完成的绑定不生效
     */
    private void cancelKeyBindListen() {
        keyBindListeningTag = null;
    }

    /**
     * 抽屉是否为右菜单，快捷键设置行位于其中
     */
    private static boolean isRightMenuDrawer(@NonNull View drawerView) {
        ViewGroup.LayoutParams params = drawerView.getLayoutParams();
        return params instanceof DrawerLayout.LayoutParams
                && ((DrawerLayout.LayoutParams) params).gravity == GravityCompat.END;
    }

    public boolean isKeyBindListening() {
        return keyBindListeningTag != null;
    }

    /**
     * 按键监听期间由 KCUInput 在分发最前调用，消费所有按键；
     * BACK 取消绑定，无法识别的键忽略继续等待
     */
    public void handleKeyBindCaptured(@NonNull KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return;
        }
        RightMenuTag tag = keyBindListeningTag;
        keyBindListeningTag = null;
        if (tag == null) {
            return;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return;
        }
        int fclKeycode = AndroidKeycodeMap.convertKeycode(event.getKeyCode());
        if (fclKeycode == KCUKeycodes.KEY_UNKNOWN) {
            Toast.makeText(activity, R.string.key_bind_unknown, Toast.LENGTH_SHORT).show();
            return;
        }
        // 绑定键码配合当前修饰键后不得与另一快捷键完全一致，否则一次按键两个功能同时触发
        if (tag == RightMenuTag.CAPTURE_POINTER_KEY && isHotkeyConflict(fclKeycode, menuSetting.getCapturePointerModifier(),
                menuSetting.getImeToggleKey(), menuSetting.getImeToggleModifier())) {
            Toast.makeText(activity, R.string.key_bind_conflict, Toast.LENGTH_SHORT).show();
            return;
        }
        if (tag == RightMenuTag.IME_TOGGLE_KEY && isHotkeyConflict(fclKeycode, menuSetting.getImeToggleModifier(),
                menuSetting.getCapturePointerKey(), menuSetting.getCapturePointerModifier())) {
            Toast.makeText(activity, R.string.key_bind_conflict, Toast.LENGTH_SHORT).show();
            return;
        }
        if (tag == RightMenuTag.CAPTURE_POINTER_KEY) {
            menuSetting.setCapturePointerKey(fclKeycode);
        } else if (tag == RightMenuTag.IME_TOGGLE_KEY) {
            menuSetting.setImeToggleKey(fclKeycode);
        }
        Toast.makeText(activity, activity.getString(R.string.key_bind_done, RightMenuAdapter.keycodeName(fclKeycode)), Toast.LENGTH_SHORT).show();
        if (rightMenuAdapter != null) {
            rightMenuAdapter.rebuild();
        }
    }

    private void handleRightSeekBarChange(RightMenuTag tag, int progress) {
        int screenWidth = AndroidUtilKt.getScreenWidth();
        int screenHeight = AndroidUtilKt.getScreenHeight();
        switch (tag) {
            case ITEM_BAR_WIDTH:
                menuSetting.setItemBarWidth((int) (screenWidth / 100f * progress));
                GameOption.GameOptionListener widthListener = gameItemBar.getOptionListener();
                if (widthListener != null) {
                    widthListener.onOptionChanged(true);
                }
                break;
            case ITEM_BAR_HEIGHT:
                menuSetting.setItemBarHeight((int) (screenHeight / 100f * progress));
                GameOption.GameOptionListener heightListener = gameItemBar.getOptionListener();
                if (heightListener != null) {
                    heightListener.onOptionChanged(true);
                }
                break;
            case WINDOW_SCALE: {
                double doubleValue = progress / 100d;
                menuSetting.setWindowScale(doubleValue);
                refreshWindowsSize(doubleValue);
                break;
            }
            case CURSOR_OFFSET:
                menuSetting.setCursorOffset(progress);
                if (fclBridge != null) {
                    refreshWindowsSize(menuSetting.getWindowScale());
                }
                break;
            case MOUSE_SENSITIVITY:
                menuSetting.setMouseSensitivity(progress / 100d);
                break;
            case MOUSE_CURSOR_SENSITIVITY:
                menuSetting.setMouseSensitivityCursor(progress / 100d);
                break;
            case MOUSE_SIZE:
                menuSetting.setMouseSize(progress);
                initCursorView(activity);
                break;
            case MOUSE_OFFSET_X:
                menuSetting.setMouseOffsetX(progress);
                cursorView.setOffsetX(menuSetting.getMouseOffsetX());
                break;
            case MOUSE_OFFSET_Y:
                menuSetting.setMouseOffsetY(progress);
                cursorView.setOffsetY(menuSetting.getMouseOffsetY());
                break;
            case GAMEPAD_DEADZONE:
                menuSetting.setGamepadDeadzone(progress / 100d);
                break;
            case GYRO_SENSITIVITY_X:
                // 进度值按百分比显示（100% = 存储倍率 10）
                menuSetting.setGyroscopeSensitivityX(progress / 10);
                break;
            case GYRO_SENSITIVITY_Y:
                menuSetting.setGyroscopeSensitivityY(progress / 10);
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void toggleFps(boolean checked) {
        menuSetting.setShowFps(checked);
        if (isSimulated()) {
            return;
        }
        // 先无条件停掉上一个线程：每次进入游戏都会调用一次本方法，
        // 不停会让线程随进入次数累积，且旧线程持有本对象导致 Activity 无法回收
        stopFpsThread();
        if (checked) {
            fpsThread = new Thread(() -> {
                KCUBridge.getFps();
                while (menuSetting.isShowFps() && !Thread.currentThread().isInterrupted()) {
                    Schedulers.androidUIThread().execute(() -> fpsText.setText("FPS:" + KCUBridge.getFps()));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // 必须恢复中断标志：抛出 InterruptedException 时标志已被清除，
                        // 不恢复的话上面的 while 条件永远为真，线程停不下来
                        Thread.currentThread().interrupt();
                    }
                }
            });
            fpsThread.setName("FCL FPS Thread");
            fpsThread.start();
        } else {
            fpsText.setText("");
        }
    }

    /** 停止 FPS 采样线程；线程持有本对象引用，不停止会让 Activity 无法回收 */
    private void stopFpsThread() {
        if (fpsThread != null) {
            fpsThread.interrupt();
            fpsThread = null;
        }
    }

    @SuppressLint("SetTextI18n")
    private void toggleMemory(boolean checked) {
        menuSetting.setShowMemory(checked);
        if (isSimulated()) {
            return;
        }
        // 同 toggleFps：先停掉上一个，避免线程累积与本对象被旧线程持有
        stopMemoryThread();
        if (checked) {
            memoryThread = new Thread(() -> {
                while (menuSetting.isShowMemory() && !Thread.currentThread().isInterrupted()) {
                    long usedMemory = AndroidUtilKt.getUsedMemory(getActivity()) / 1024 / 1024;
                    long totalMemory = AndroidUtilKt.getTotalMemory(getActivity()) / 1024 / 1024;
                    long usage;
                    if (totalMemory > 0) {
                        usage = usedMemory * 100 / totalMemory;
                    } else {
                        usage = -1;
                    }
                    Schedulers.androidUIThread().execute(() -> memoryText.setText("Mem(" + usage + "%): " + usedMemory + " / " + totalMemory + " MB"));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // 同 FPS 线程：不恢复中断标志会让 while 条件永远为真
                        Thread.currentThread().interrupt();
                    }
                }
            });
            memoryThread.setName("FCL Memory Thread");
            memoryThread.start();
        } else {
            memoryText.setText("");
        }
    }

    /** 停止内存采样线程；线程持有本对象引用，不停止会让 Activity 无法回收 */
    private void stopMemoryThread() {
        if (memoryThread != null) {
            memoryThread.interrupt();
            memoryThread = null;
        }
    }

    private void refreshWindowsSize(double factor) {
        int screenWidth = AndroidUtilKt.getScreenWidth();
        int screenHeight = AndroidUtilKt.getScreenHeight();
        if (fclBridge != null) {
            fclBridge.setScaleFactor(factor);
            int width = (int) ((screenWidth + menuSetting.getCursorOffset()) * factor);
            int height = (int) (screenHeight * factor);
            if (KCUBridge.FORCE_RESOLUTION) {
                width = KCUBridge.FORCE_RESOLUTION_WIDTH;
                height = KCUBridge.FORCE_RESOLUTION_HEIGHT;
            }
            fclBridge.getSurfaceTexture().setDefaultBufferSize(width, height);
            fclBridge.pushEventWindow(width, height);
            // SDL 窗口尺寸不消费 GLFW 窗口事件，调整缩放等不改视图尺寸的变化须显式同步 SDL 分辨率；
            // 须在 pushEventWindow 更新 CallbackBridge.windowWidth/Height 之后调，surfaceChanged() 读的才是新值
            SdlBridge.syncResolution(width, height);
        }
    }

    /**
     * 强制分辨率实时应用：同步静态配置 → 重排 TextureView letterbox → 刷新渲染 buffer 与窗口事件
     */
    private void applyForceResolution() {
        KCUBridge.initForceResolution(menuSetting);
        if (fclBridge == null || isSimulated()) {
            return;
        }
        ((JVMActivity) activity).applyForceResolutionLayout();
        refreshWindowsSize(menuSetting.getWindowScale());
    }

    /**
     * 强制分辨率宽高编辑：输入 "宽x高"，确认后实时应用（仅经"设置"按钮进入）
     */
    private void editForceResolutionSize() {
        EditDialog dialog = new EditDialog(activity,
                menuSetting.getForceResolutionWidth() + "x" + menuSetting.getForceResolutionHeight(),
                str -> {
                    String[] split = str.toLowerCase().trim().split("x");
                    if (split.length == 2) {
                        try {
                            int w = Integer.parseInt(split[0].trim());
                            int h = Integer.parseInt(split[1].trim());
                            if (w > 0 && h > 0) {
                                menuSetting.setForceResolutionWidth(w);
                                menuSetting.setForceResolutionHeight(h);
                                if (menuSetting.isForceResolution()) {
                                    applyForceResolution();
                                }
                                return;
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    }
                    Toast.makeText(activity, R.string.menu_settings_force_resolution_invalid, Toast.LENGTH_SHORT).show();
                });
        dialog.setTitle(R.string.menu_settings_force_resolution_size);
        dialog.show();
    }

    @Nullable
    public TouchController getTouchController() {
        return touchController;
    }
}
